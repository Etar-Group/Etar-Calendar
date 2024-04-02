package com.android.calendar.theme.model

import androidx.annotation.StyleRes
import ws.xsoh.etar.R

enum class Color(@StyleRes val resource: Int) {
    MONET(R.style.colorAccentPrimaryDefault), // SYSTEM or DEFAULT
    TEAL(R.style.colorAccentPrimaryTeal),
    BLUE(R.style.colorAccentPrimaryBlue),
    ORANGE(R.style.colorAccentPrimaryOrange),
    GREEN(R.style.colorAccentPrimaryGreen),
    RED(R.style.colorAccentPrimaryRed),
    PURPLE(R.style.colorAccentPrimaryPurple)
}
