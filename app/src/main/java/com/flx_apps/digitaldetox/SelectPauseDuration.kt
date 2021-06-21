package com.flx_apps.digitaldetox

import android.os.Bundle
import android.widget.CheckedTextView
import androidx.appcompat.app.AppCompatActivity

class SelectPauseDuration : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_select_pause_duration)

        init()
    }

    private fun init() {
        findViewById<CheckedTextView>(R.id.b_one).setOnClickListener { pauseForDuration(5)}
        findViewById<CheckedTextView>(R.id.b_two).setOnClickListener { pauseForDuration(10)}
        findViewById<CheckedTextView>(R.id.b_three).setOnClickListener { pauseForDuration(20)}
        findViewById<CheckedTextView>(R.id.b_four).setOnClickListener { pauseForDuration(30)}
    }

    private fun pauseForDuration(minutes: Int) {
        DetoxUtil.togglePause(baseContext, minutes)
        finish()
    }
}