package ru.yumani.terminal

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_success)

        findViewById<View>(R.id.successCircle).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.pop)
        )
        findViewById<ImageView>(R.id.ivCheck).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.fade_in)
        )
        findViewById<TextView>(R.id.tvDone).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.fade_in)
        )

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 2600)
    }
}
