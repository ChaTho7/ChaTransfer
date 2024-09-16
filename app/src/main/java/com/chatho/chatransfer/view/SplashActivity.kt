package com.chatho.chatransfer.view

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chatho.chatransfer.R
import com.chatho.chatransfer.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fadeInAnimation = AnimationUtils.loadAnimation(this@SplashActivity, R.anim.fade)
        fadeInAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                binding.appName.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(p0: Animation?) {}
            override fun onAnimationRepeat(p0: Animation?) {}
        })
        setAppName()

        binding.animationView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {
                binding.appName.startAnimation(fadeInAnimation)
            }

            override fun onAnimationEnd(p0: Animator) {
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            }

            override fun onAnimationCancel(p0: Animator) {}

            override fun onAnimationRepeat(p0: Animator) {}
        })
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()
        binding.animationView.playAnimation()
    }

    private fun setAppName() {
        val spannableStringBuilder = SpannableStringBuilder()

        val text1 = "CHA"
        val spannableString1 = SpannableString(text1)
        val color1 = ContextCompat.getColor(this, R.color.dark_blue)
        val colorSpan1 = ForegroundColorSpan(color1)
        spannableString1.setSpan(colorSpan1, 0, text1.length, 0)
        spannableStringBuilder.append(spannableString1)

        val text2 = "TRANSFER"
        val spannableString2 = SpannableString(text2)
        val color2 = ContextCompat.getColor(this, R.color.red)
        val colorSpan2 = ForegroundColorSpan(color2)
        spannableString2.setSpan(colorSpan2, 0, text2.length, 0)
        spannableStringBuilder.append(spannableString2)

        binding.appName.text = spannableStringBuilder
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}