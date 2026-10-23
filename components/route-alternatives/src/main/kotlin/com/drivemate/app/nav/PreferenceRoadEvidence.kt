package com.drivemate.app.nav

/** Reports supplied routing/zone metadata; never infers access or legality from a road's name. */
object PreferenceRoadEvidence {
    fun reported(refs:List<String>,stepTypes:List<String>,hasTolls:Boolean,chargeCrossing:Boolean):Set<String> = buildSet {
        val motorway=Regex("^(M\\d+|A\\d+\\(M\\))$")
        if(refs.any{ref->
            val tokens=ref.split(';',',','/',' ').map{it.trim().uppercase()}.filter{it.isNotBlank()}
            tokens.isNotEmpty() && tokens.all{Regex("^[ABM]\\d+(?:\\(M\\))?$").matches(it)} && tokens.any{motorway.matches(it)}
        })add("motorway")
        if(hasTolls)add("toll")
        if(stepTypes.any{it=="ferry"})add("ferry")
        if(chargeCrossing)add("charge")
    }
}
