package com.byteflow.app

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.graphics.PointF

/**
 * @author PengHaiChen
 * @date 2025/2/6 21:53:34
 * @description
 */
class TestActivity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: SlimmingRenderer
    private lateinit var btnReset: Button
    private lateinit var strengthSeekBar: SeekBar
    
    private val lastTouch = PointF()
    private var deformationStrength = 0.5f
    private var isDeforming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        btnReset = findViewById(R.id.btnReset)
        strengthSeekBar = findViewById(R.id.strengthSeekBar)

        // 设置OpenGL ES 3.0
        glSurfaceView.setEGLContextClientVersion(3)
        
        // 初始化渲染器
        renderer = SlimmingRenderer(this)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // 设置触摸事件
        glSurfaceView.setOnTouchListener { v, event ->
            val x = event.x / v.width * 2 - 1
            val y = -(event.y / v.height * 2 - 1)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDeforming = true
                    lastTouch.set(event.x, event.y)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDeforming) {
                        val deltaX = (event.x - lastTouch.x) / v.width
                        val deltaY = (event.y - lastTouch.y) / v.height
                        
                        renderer.applyDeformation(
                            x,
                            y,
                            deltaX * deformationStrength * 2,
                            deltaY * deformationStrength * 2
                        )
                        
                        lastTouch.set(event.x, event.y)
                        glSurfaceView.requestRender()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDeforming = false
                    true
                }
                else -> false
            }
        }

        // 重置按钮点击事件
        btnReset.setOnClickListener {
            renderer.resetDeformation()
            glSurfaceView.requestRender()
        }

        // 强度调节
        strengthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                deformationStrength = progress / 25f  // 增加变形强度范围
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }
}