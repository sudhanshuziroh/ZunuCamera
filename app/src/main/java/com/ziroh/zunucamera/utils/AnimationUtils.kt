package com.ziroh.zunucamera.utils

import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView

object AnimationUtils {
    fun animateCameraIcon(imageView: ImageView) {
        val rotateAnimation = RotateAnimation(
            0f,
            360f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        rotateAnimation.duration = 600
        rotateAnimation.fillAfter = true
        imageView.startAnimation(rotateAnimation)
    }
}