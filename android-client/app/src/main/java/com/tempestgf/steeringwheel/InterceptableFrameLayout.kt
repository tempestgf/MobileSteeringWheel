package com.tempestgf.steeringwheel

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

class InterceptableFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true  // Interceptar todos los eventos de toque
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Simplemente delegar el evento al padre, para que lo maneje la actividad.
        return super.onTouchEvent(event)
    }
}
