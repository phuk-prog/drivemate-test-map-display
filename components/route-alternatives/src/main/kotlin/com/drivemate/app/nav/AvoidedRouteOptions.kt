package com.drivemate.app.nav

/** Preference handling only. Unknown/legal/vehicle restrictions are never relaxed. */
object AvoidedRouteOptions {
    private val labels=linkedMapOf("motorway" to "motorways","toll" to "tolls","ferry" to "ferries","charge" to "charge zones")
    data class Request(val avoid: Set<String>,val relaxed: Set<String>)
    data class Evidence<T>(val route:T,val key:String,val seconds:Double,
        val reportedUses:Set<String>,val relaxed:Set<String> = emptySet(),
        val knownClosed:Boolean=false,val liveCheckedAtMs:Long=0,val timingSource:String="",
        val timingReliable:Boolean=false,val complianceConfirmed:Boolean=false)
    data class Choice<T>(val evidence:Evidence<T>,val warning:String?,val requiresExplicitChoice:Boolean,val fasterConfirmed:Boolean)
    data class Result<T>(val choices:List<Choice<T>>,val defaultKey:String?)

    fun requests(avoid:Set<String>):List<Request> {
        val preferences=avoid.intersect(labels.keys)
        if(preferences.isEmpty())return emptyList()
        val relaxations=preferences.map{setOf(it)}+listOf(preferences)
        return relaxations.distinct().map{Request(avoid-it,it)}
    }
    private fun valid(e:Evidence<*>)=e.key.isNotBlank() && e.seconds.isFinite() && e.seconds>0 && !e.knownClosed
    private fun fresh(e:Evidence<*>,now:Long)=e.timingReliable && e.timingSource.isNotBlank() &&
        e.liveCheckedAtMs>0 && now>=e.liveCheckedAtMs && now-e.liveCheckedAtMs<=300_000
    /** Freshness/skew are existing traffic-comparison engineering limits, never legal limits. */
    fun <T> build(avoid:Set<String>,strict:List<Evidence<T>>,relaxed:List<Evidence<T>>,now:Long):Result<T> {
        // Missing road metadata is not proof that requested avoidances were honoured.
        val ordinary=strict.filter{valid(it) && it.reportedUses.intersect(avoid).isEmpty() &&
            (avoid.isEmpty() || it.complianceConfirmed)}.distinctBy{it.key}
        val baseline=ordinary.filter{fresh(it,now)}.minByOrNull{it.seconds}
        val keys=ordinary.map{it.key}.toSet()
        val extras=relaxed.filter { e->
            valid(e) && e.key !in keys && e.relaxed.isNotEmpty() &&
                e.relaxed.all{it in avoid && it in labels} &&
                (e.complianceConfirmed || avoid.all{it in labels}) &&
                e.reportedUses.intersect(avoid).isNotEmpty() &&
                (e.reportedUses.intersect(avoid)-e.relaxed).isEmpty() &&
                (ordinary.isEmpty() || (baseline!=null && fresh(e,now) &&
                    e.timingSource==baseline.timingSource &&
                    kotlin.math.abs(e.liveCheckedAtMs-baseline.liveCheckedAtMs)<=120_000 && e.seconds<baseline.seconds))
        }.distinctBy{it.key}.sortedBy{it.seconds}.take(3).map { e->
            val uses=e.reportedUses.intersect(avoid).mapNotNull{labels[it]}.joinToString(", ")
            val unapplied=e.relaxed.mapNotNull{labels[it]}.joinToString(", ")
            val faster=baseline!=null && ordinary.isNotEmpty()
            val unconfirmed=if(!e.complianceConfirmed && (avoid-e.relaxed).isNotEmpty())
                " Avoidance compliance not confirmed: ${(avoid-e.relaxed).mapNotNull{labels[it]}.joinToString(", ")}." else ""
            Choice(e,(if(faster)"Estimated faster option. " else "")+
                "Route data reports $uses. Avoidance settings not applied: $unapplied.$unconfirmed",true,faster)
        }
        val offeredKeys=keys+extras.map{it.evidence.key}
        val unknown=if(ordinary.isEmpty() && avoid.isNotEmpty() && avoid.all{it in labels}) strict.filter {
            valid(it) && !it.complianceConfirmed && it.key !in offeredKeys &&
                it.reportedUses.intersect(avoid).isEmpty()
        }.distinctBy{it.key}.map { e->
            val requested=avoid.mapNotNull{labels[it]}.joinToString(", ")
            Choice(e,"Avoidance compliance not confirmed: $requested.",true,false)
        } else emptyList()
        val choices=ordinary.map{Choice(it,null,false,false)}+extras+unknown
        return Result(choices,ordinary.firstOrNull()?.key)
    }
    fun mayStart(requiresExplicitChoice:Boolean,key:String,explicitlyChosenKey:String?):Boolean =
        key.isNotBlank() && (!requiresExplicitChoice || key==explicitlyChosenKey)
}
