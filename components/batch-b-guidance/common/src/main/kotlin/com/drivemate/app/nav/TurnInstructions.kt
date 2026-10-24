package com.drivemate.app.nav

/** Pure instruction wording shared by routing providers and offline navigation. */
internal object TurnInstructions {
    internal fun describe(type: String, mod: String, name: String, exit: Int, lastLeg: Boolean, towards: String = ""): String {
        // On slip roads and forks, the sign text ("towards A34 Oxford") is clearer than a road name.
        val signed = towards.isNotBlank() && type in setOf("on ramp", "off ramp", "fork", "merge")
        val onto = if (signed) " towards $towards" else if (name.isNotBlank()) " onto $name" else ""
        return when (type) {
            "depart" -> if (name.isNotBlank()) "Head out on $name" else "Head out"
            "arrive" -> {
                // The server says which side of the road the destination is on.
                val side = explicitArrivalSide(mod)?.let { " on the $it" }.orEmpty()
                if (lastLeg) "Your destination is$side".let { if (side.isEmpty()) "You have arrived at your destination" else it }
                else "Your stop is$side".let { if (side.isEmpty()) "You have reached your stop" else it }
            }
            "roundabout", "rotary" -> {
                if (exit > 0) "At the roundabout, take the ${ordinal(exit)} exit$onto" else "Enter the roundabout"
            }
            "exit roundabout", "exit rotary" -> "Exit the roundabout$onto"
            // Joining a main road from a slip road (owner, 20 Oct 2026): "Merge onto the M60", not
            // "bear right". It is still not said on its own when the slip road was already named.
            "merge" -> if (name.isNotBlank()) "Merge onto ${theRoad(name)}" else if (towards.isNotBlank()) "Merge towards $towards" else "Merge"
            "on ramp" -> "Take the slip road${side(mod)}" +
                (if (name.isNotBlank()) " onto ${theRoad(name)}" else "") +
                signWithout(towards, name).let { if (it.isNotBlank()) " towards $it" else "" }
            "off ramp" -> "Take the exit${side(mod)}$onto"
            "fork" -> "Keep ${simple(mod)} at the fork$onto"
            "end of road" -> "Turn ${simple(mod)} at the end of the road$onto"
            "new name", "continue", "notification" ->
                if (mod.contains("left") || mod.contains("right") || mod == "uturn") turn(mod, onto) else "Continue$onto"
            else -> turn(mod, onto)
        }
    }
    private fun turn(mod: String, onto: String) = when (mod) {
        "uturn" -> "Make a U-turn$onto"
        "straight" -> "Continue straight$onto"
        "slight left" -> "Bear left$onto"
        "slight right" -> "Bear right$onto"
        "sharp left" -> "Turn sharp left$onto"
        "sharp right" -> "Turn sharp right$onto"
        "left" -> "Turn left$onto"
        "right" -> "Turn right$onto"
        else -> "Continue$onto"
    }

    private val roadNumber = Regex("^(M\\d+[A-Z]?|A\\d+[A-Z]?(\\(M\\))?|B\\d+)$")

    /** "the M60" for a road number (as said in the UK), a street name as it is. */
    internal fun theRoad(name: String) = if (roadNumber.matches(name.trim())) "the ${name.trim()}" else name

    /** Sign text without the road number already named: ("M60 Ring Rd E & N", "M60") → "Ring Rd E & N". */
    internal fun signWithout(towards: String, name: String): String {
        val t = towards.trim()
        val first = t.substringBefore(" ")
        if (name.isBlank() || first != name.trim()) return t
        return t.removePrefix(first).trim()
    }

    private fun side(mod: String) = when {
        mod.contains("left") -> " on the left"
        mod.contains("right") -> " on the right"
        else -> ""
    }

    private fun simple(mod: String) = when {
        mod.contains("left") -> "left"
        mod.contains("right") -> "right"
        else -> "straight"
    }


    private fun ordinal(n: Int) = when (n) {
        1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"
        6 -> "sixth"; 7 -> "seventh"; 8 -> "eighth"
        else -> "${n}th"
    }
}
