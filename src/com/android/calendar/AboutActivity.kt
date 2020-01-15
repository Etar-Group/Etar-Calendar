package com.android.calendar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ws.xsoh.etar.R

private val dynamicTheme = DynamicTheme()

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynamicTheme.onCreate(this)
        setContentView(R.layout.about_activity)

        val fragment = AboutFragment()

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .add(R.id.fragment_container, fragment, "About Fragment")
                    .commit()
        }
    }

}
