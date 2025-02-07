package com.byteflow.app

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PhotoRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var mProgram = 0
    private var mTextureId = 0
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    
    // MVP矩阵
    private val mMVPMatrix = FloatArray(16)
    private val mProjectMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    
    // 顶点着色器
    private val vertexShaderCode = """
        #version 300 es
        layout (location = 0) in vec4 aPosition;
        layout (location = 1) in vec2 aTexCoord;
        uniform mat4 uMVPMatrix;
        out vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // 片段着色器
    private var touchX = 0.5f
    private var touchY = 0.5f
    private var effectRadius = 0.15f
    private var effectStrength = 0.3f
    private var isEffectActive = false
    
    // 添加帧缓冲相关变量
    private var frameBuffer = 0
    private var frameBufferTexture = 0
    private var confirmedTexture = 0
    private var isFirstFrame = true

    // 添加变量控制内缩或外扩
    private var isExpanding = true  // true为外扩，false为内缩

    private val fragmentShaderCode = """
        #version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uCenter;
        uniform float uRadius;
        uniform float uStrength;
        uniform bool uIsExpanding;  // 添加uniform变量
        out vec4 fragColor;
        
        void main() {
            vec2 coord = vTexCoord;
            float dist = distance(coord, uCenter);
            
            if(dist < uRadius) {
                float smoothFactor = smoothstep(uRadius, 0.0, dist);
                float factor = smoothFactor * uStrength;
                
                vec2 direction = normalize(uCenter - coord);
                // 根据isExpanding决定方向
                direction *= uIsExpanding ? -1.0 : 1.0;
                
                coord += direction * factor * 0.1;
                coord = clamp(coord, 0.0, 1.0);
            }
            
            fragColor = texture(uTexture, coord);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // 创建程序和着色器
        mProgram = createProgram()
        
        // 初始化顶点数据
        initVertexData()
        
        // 初始化纹理和帧缓冲
        initTextures()
        initFrameBuffer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        
        // 计算投影和视图矩阵
        val ratio = width.toFloat() / height
        Matrix.frustumM(mProjectMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        GLES30.glUseProgram(mProgram)
        
        val mvpMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        
        if (isEffectActive) {
            GLES30.glUniform2f(GLES30.glGetUniformLocation(mProgram, "uCenter"), touchX, touchY)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(mProgram, "uRadius"), effectRadius)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(mProgram, "uStrength"), effectStrength)
            // 添加expanding状态
            GLES30.glUniform1i(GLES30.glGetUniformLocation(mProgram, "uIsExpanding"), if(isExpanding) 1 else 0)
        } else {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(mProgram, "uStrength"), 0.0f)
        }
        
        drawPhoto()
    }

    private fun createProgram(): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        return shader
    }

    private fun initVertexData() {
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f,  // 左下
             1.0f, -1.0f, 0.0f,  // 右下
            -1.0f,  1.0f, 0.0f,  // 左上
             1.0f,  1.0f, 0.0f   // 右上
        )
        
        val texCoords = floatArrayOf(
            0.0f, 1.0f,  // 左下
            1.0f, 1.0f,  // 右下
            0.0f, 0.0f,  // 左上
            1.0f, 0.0f   // 右上
        )
        
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)
        
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)
    }

    private fun initTextures() {
        // 创建原始纹理
        val textureIds = IntArray(2)
        GLES30.glGenTextures(2, textureIds, 0)
        mTextureId = textureIds[0]
        confirmedTexture = textureIds[1]
        
        // 加载原始图片到两个纹理
        loadTexture(mTextureId)
        loadTexture(confirmedTexture)
    }

    private fun initFrameBuffer() {
        val fbs = IntArray(1)
        val fbTextures = IntArray(1)
        
        // 创建帧缓冲
        GLES30.glGenFramebuffers(1, fbs, 0)
        frameBuffer = fbs[0]
        
        // 创建帧缓冲纹理
        GLES30.glGenTextures(1, fbTextures, 0)
        frameBufferTexture = fbTextures[0]
        
        // 设置帧缓冲纹理参数
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameBufferTexture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
            0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        
        // 绑定帧缓冲和纹理
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, frameBufferTexture, 0)
        
        // 解绑
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun loadTexture(textureId: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.test_1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    private fun drawPhoto() {
        // 设置顶点属性
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(0)
        
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)
        GLES30.glEnableVertexAttribArray(1)
        
        // 绑定纹理
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(mProgram, "uTexture"), 0)
        
        // 绘制矩形
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun updateTouchPosition(x: Float, y: Float) {
        touchX = x
        touchY = y
        isEffectActive = true
    }

    fun clearEffect() {
        isEffectActive = false
    }

    fun setEffectRadius(radius: Float) {
        effectRadius = radius
    }

    fun setEffectStrength(strength: Float) {
        effectStrength = strength
    }

    fun confirmEffect() {
        // 将当前效果确认到确认纹理
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBuffer)
        drawPhoto()
        
        // 将帧缓冲的内容复制到确认纹理
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, confirmedTexture)
        GLES30.glCopyTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            0, 0, context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels, 0)
        
        // 重置绑定
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        
        // 更新绘制源为确认纹理
        mTextureId = confirmedTexture
    }

    // 添加切换方法
    fun toggleExpandMode() {
        isExpanding = !isExpanding
    }
} 