package com.byteflow.app

import android.content.Context
import android.graphics.Bitmap
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

class SlimmingRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var program = 0
    private var textureId = 0
    private var mvpMatrixHandle = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var deformCenterHandle = 0
    private var deformVectorHandle = 0
    
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    
    private var vertexBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    
    private var deformCenterX = 0f
    private var deformCenterY = 0f
    private var deformVectorX = 0f
    private var deformVectorY = 0f

    private val vertexShaderCode = """
        #version 300 es
        uniform mat4 uMVPMatrix;
        uniform vec2 uDeformCenter;
        uniform vec2 uDeformVector;
        
        layout(location = 0) in vec4 aPosition;
        layout(location = 1) in vec2 aTexCoord;
        
        out vec2 vTexCoord;
        
        void main() {
            vec4 position = aPosition;
            vec2 vertex = position.xy;
            vec2 deformDir = vertex - uDeformCenter;
            float dist = length(deformDir);
            
            // 调整变形范围和强度
            float deformRadius = 0.3;  // 减小变形影响半径，使效果更集中
            float deformFactor = exp(-dist * dist / (deformRadius * deformRadius));
            
            // 应用变形
            vertex += uDeformVector * deformFactor;
            
            // 限制最大变形量
            float maxDeform = 0.5;
            vec2 deform = vertex - position.xy;
            float deformLength = length(deform);
            if (deformLength > maxDeform) {
                vertex = position.xy + deform * (maxDeform / deformLength);
            }
            
            position.xy = vertex;
            gl_Position = uMVPMatrix * position;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        precision highp float;
        
        in vec2 vTexCoord;
        uniform sampler2D sTexture;
        
        out vec4 fragColor;
        
        void main() {
            fragColor = texture(sTexture, vTexCoord);
        }
    """.trimIndent()

    init {
        setupVertexBuffer()
    }

    private fun setupVertexBuffer() {
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 0.0f
        )

        val texCoords = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer?.position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer?.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        if (program == 0) {
            throw RuntimeException("Failed to create program")
        }
        
        // 获取uniform和attribute位置
        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        deformCenterHandle = GLES30.glGetUniformLocation(program, "uDeformCenter")
        deformVectorHandle = GLES30.glGetUniformLocation(program, "uDeformVector")
        
        // 创建纹理
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]
        
        // 加载图片
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.test_1)
        if (bitmap == null) {
            throw RuntimeException("Failed to load texture")
        }
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        screenWidth = width
        screenHeight = height
        
        // 计算宽高比
        val imageRatio = 1.0f  // 假设图片是正方形，如果不是，需要根据实际图片尺寸计算
        val screenRatio = width.toFloat() / height
        
        if (screenRatio > imageRatio) {
            // 屏幕更宽，以高度为准
            val scaledWidth = height * imageRatio
            val xOffset = (width - scaledWidth) / 2
            Matrix.orthoM(projectionMatrix, 0, -screenRatio, screenRatio, -1f, 1f, -1f, 1f)
        } else {
            // 屏幕更高，以宽度为准
            val scaledHeight = width / imageRatio
            val yOffset = (height - scaledHeight) / 2
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f/screenRatio, 1f/screenRatio, -1f, 1f)
        }
        
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        GLES30.glUseProgram(program)
        
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES30.glUniform2f(deformCenterHandle, deformCenterX, deformCenterY)
        GLES30.glUniform2f(deformVectorHandle, deformVectorX, deformVectorY)
        
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)
        
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        
        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(texCoordHandle)
    }

    fun applyDeformation(x: Float, y: Float, dx: Float, dy: Float) {
        deformCenterX = x
        deformCenterY = y
        deformVectorX = dx
        deformVectorY = dy
    }

    fun resetDeformation() {
        deformCenterX = 0f
        deformCenterY = 0f
        deformVectorX = 0f
        deformVectorY = 0f
    }

    fun screenToOpenGL(screenX: Float, screenY: Float): FloatArray {
        val x = (screenX / screenWidth) * 2 - 1
        val y = -((screenY / screenHeight) * 2 - 1)
        return floatArrayOf(x, y)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        
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
} 