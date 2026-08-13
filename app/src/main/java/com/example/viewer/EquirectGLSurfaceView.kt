package com.example.viewer

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * [GLSurfaceView] that shows an equirectangular panorama and responds to drags
 * and device sensors.
 */
class EquirectGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val equirectRenderer: EquirectRenderer

    init {
        setEGLContextClientVersion(2)
        equirectRenderer = EquirectRenderer()
        setRenderer(equirectRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setPanorama(bitmap: Bitmap?) {
        queueEvent { equirectRenderer.setPanorama(bitmap) }
    }

    fun setSensor(yawDeg: Float, pitchDeg: Float, enabled: Boolean) {
        queueEvent { equirectRenderer.setSensor(yawDeg, pitchDeg, enabled) }
    }

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    val finalDx = dx
                    val finalDy = dy
                    queueEvent { equirectRenderer.applyDrag(finalDx, finalDy) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
