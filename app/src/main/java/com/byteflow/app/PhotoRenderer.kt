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
    private val fragmentShaderCode = """
        #version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uCenter;
        uniform float uRadius;
        uniform float uStrength;
        out vec4 fragColor;
        
        void main() {
            vec2 coord = vTexCoord;
            float dist = distance(coord, uCenter);
            if(dist < uRadius) {
                float factor = 1.0 - (dist / uRadius) * uStrength;
                coord = coord + (coord - uCenter) * factor;
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
        
        // 加载纹理
        loadTexture()
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
        
        // 使用程序
        GLES30.glUseProgram(mProgram)
        
        // 设置MVP矩阵
        val mvpMatrixHandle = GLES30.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        
        // 设置凹陷效果参数
        GLES30.glUniform2f(GLES30.glGetUniformLocation(mProgram, "uCenter"), 0.5f, 0.5f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mProgram, "uRadius"), 0.3f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mProgram, "uStrength"), 0.5f)
        
        // 绘制
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
            0.0f, 0.0f,  // 左下
            1.0f, 0.0f,  // 右下
            0.0f, 1.0f,  // 左上
            1.0f, 1.0f   // 右上
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

    private fun loadTexture() {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        mTextureId = textureIds[0]
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId)
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
} 