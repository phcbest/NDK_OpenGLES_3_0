package com.byteflow.app

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * @author PengHaiChen
 * @date 2025/2/6 21:53:34
 * @description
 */
class TestActivity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var photoRenderer: PhotoRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        
        // 初始化 GLSurfaceView
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            photoRenderer = PhotoRenderer(context)
            setRenderer(photoRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        
        // 将 GLSurfaceView 添加到容器中
        findViewById<FrameLayout>(R.id.glSurfaceContainer).addView(glSurfaceView)
        
        // 设置强度调节SeekBar
        findViewById<SeekBar>(R.id.strengthSeekBar).setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    photoRenderer.setEffectStrength(progress / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        // 设置半径调节SeekBar
        findViewById<SeekBar>(R.id.radiusSeekBar).setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    photoRenderer.setEffectRadius(progress / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        // 设置确认按钮
        findViewById<Button>(R.id.confirmButton).setOnClickListener {
            photoRenderer.confirmEffect()
        }

        // 添加内缩/外扩切换按钮
        findViewById<Button>(R.id.expandModeButton).setOnClickListener {
            photoRenderer.toggleExpandMode()
            (it as Button).text = if (it.text == "外扩模式") "内缩模式" else "外扩模式"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.y < glSurfaceView.height) {  // 只处理GLSurfaceView区域的触摸
            val x = event.x
            val y = event.y
            
            // 修正坐标转换
            val normalizedX = x / glSurfaceView.width
            val normalizedY = y / glSurfaceView.height
            
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    photoRenderer.updateTouchPosition(normalizedX, normalizedY)
                }
                MotionEvent.ACTION_UP -> {
                    photoRenderer.clearEffect()
                }
            }
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }
}