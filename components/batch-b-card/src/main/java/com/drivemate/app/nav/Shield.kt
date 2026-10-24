package com.drivemate.app.nav
/** Published model subset; no routing or provider code. */
data class Shield(val ref:String,val kind:Kind) { enum class Kind { MOTORWAY, PRIMARY, OTHER } }
