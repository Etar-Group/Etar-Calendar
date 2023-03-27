package com.android.calendar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import ws.xsoh.etar.R
import ws.xsoh.etar.databinding.SimpleFrameLayoutMaterialBinding

private val dynamicTheme = DynamicTheme()

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynamicTheme.onCreate(this)

        val binding: SimpleFrameLayoutMaterialBinding = SimpleFrameLayoutMaterialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.include.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.preferences_menu_about)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .add(R.id.body_frame, AboutFragment())
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}
