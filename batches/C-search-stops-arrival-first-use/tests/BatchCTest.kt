package com.drivemate.app.nav
import com.drivemate.app.data.*
var failures=0
fun case(name:String, body:()->Unit) { try { body(); println("CASE|"+name+"|PASS") } catch(e:Throwable) { failures++; println("CASE|"+name+"|FAIL|"+e.message) } }
fun expect(ok:Boolean) { check(ok) { "Independent scenario expectation failed" } }
fun main() {
    case("15.01 measured route end") { expect(ArrivalPolicy.arrived(10.0,5.0,9.0,false)) }
    case("15.01 estimated route end cannot arrive") { expect(!ArrivalPolicy.arrived(0.0,0.0,0.0,true)) }
    case("15.01 opposite road near destination") { expect(!ArrivalPolicy.arrived(100.0,40.0,15.0,false)) }
    case("15.01 long remaining route near destination") { expect(!ArrivalPolicy.arrived(800.0,5.0,8.0,false)) }
    for ((label,v) in listOf("NaN" to Double.NaN,"infinite" to Double.POSITIVE_INFINITY,"negative" to -1.0)) {
        case("15.01 invalid remaining "+label) { expect(!ArrivalPolicy.arrived(v,0.0,5.0,false)) }
        case("15.01 invalid offroute "+label) { expect(!ArrivalPolicy.arrived(0.0,v,5.0,false)) }
        case("15.01 invalid endpoint "+label) { expect(!ArrivalPolicy.arrived(0.0,0.0,v,false)) }
    }
    case("15.01 no turn side borrowed") { expect(ArrivalPolicy.side(listOf(RouteStep("turn","left"))) == null) }
    case("15.01 explicit destination right") { expect(ArrivalPolicy.side(listOf(RouteStep("arrive","right"))) == "right") }
    case("15.01 malformed arrival side") { expect(ArrivalPolicy.side(listOf(RouteStep("arrive","slight left"))) == null) }
    case("15.01 arrival ahead wording") { expect(arrivalApproachInstruction("arrive","") == "Destination ahead") }
    case("15.01 stop wording") { expect(arrivalApproachInstruction("stop","left") == "Stop on the left") }
    case("15.01 completed has no approach") { expect(arrivalApproachInstruction("arrive","right",true) == null) }
    case("07.01 actual road detour arithmetic") { expect(StopDetour.detour(300.0,400.0,500.0) == 200.0) }
    case("07.01 missing road timing stays unknown") { expect(StopDetour.detour(null,400.0,500.0) == null) }
    case("07.01 shorter alternative never negative") { expect(StopDetour.detour(10.0,10.0,30.0) == 0.0) }
    for ((label,v) in listOf("NaN" to Double.NaN,"infinite" to Double.POSITIVE_INFINITY,"negative" to -1.0)) {
        case("07.01 invalid timing "+label) { expect(StopDetour.detour(v,5.0,5.0) == null) }
        case("07.01 invalid label "+label) { expect(StopDetour.label(v) == "detour not checked") }
    }
    case("07.01 overflow timing") { expect(StopDetour.detour(Double.MAX_VALUE,Double.MAX_VALUE,1.0) == null) }
    val origin=LatLon(53.0,-2.0)
    fun rp(n:String,a:Double,d:Double?)=RoutePlace(Place(n,"",origin),PlaceKind.FUEL,a,0.0,detourSeconds=d)
    case("07.01 smaller real detour before rounded minute tie") {
        expect(StopDetour.rank(listOf(rp("slow",1.0,80.0),rp("quick",50.0,61.0))).first().place.name=="quick")
    }
    case("07.01 known before unknown and excessive omitted") {
        expect(StopDetour.rank(listOf(rp("unknown",0.0,null),rp("far",0.0,1201.0),rp("known",2.0,50.0))).map{it.place.name}==listOf("known","unknown"))
    }
    case("07.01 precise duration tie prefers ahead") { expect(StopDetour.rank(listOf(rp("later",50.0,60.0),rp("earlier",10.0,60.0))).first().place.name=="earlier") }
    case("07.01 real route polyline and injected road durations") {
        val line=listOf(origin,LatLon(53.03,-2.0)); var calls=0
        val result=StopDetour.withDetours(listOf(rp("station",1000.0,null)),line) { points,_ -> calls++; if(points.size==3) 600.0 else 420.0 }
        expect(calls==2 && result.single().detourSeconds==180.0)
    }
    case("07.01 road engine failure remains unknown") {
        expect(StopDetour.withDetours(listOf(rp("station",1000.0,null)),listOf(origin,LatLon(53.03,-2.0))) { _,_ -> error("No road data") }.single().detourSeconds==null)
    }
    case("07.04 essential stop categories") { expect(PlaceKind.entries.map{it.name}.containsAll(listOf("FUEL","FOOD","TOILETS","PARKING"))) }
    case("07.08 nearest parking filters outside radius") {
        AlongRoute.places={_,_,_,_,_->listOf(Place("outside","",LatLon(53.02,-2.0)),Place("near","",LatLon(53.0001,-2.0)))}
        expect(AlongRoute.near(PlaceKind.PARKING,origin).map{it.place.name}==listOf("near"))
    }
    case("06.02 absent entrance not invented") { expect(AlongRoute.arrivalHelp(origin).entrance==null) }
    case("07.07 measured past first stop continues prefix") { expect(StopCompletionPolicy.passed(listOf(100.0,500.0),150.0,0.0,false)==1) }
    case("07.07 measured before stop retains it") { expect(StopCompletionPolicy.passed(listOf(100.0),110.0,0.0,false)==0) }
    case("07.07 estimated position cannot complete stop") { expect(StopCompletionPolicy.passed(listOf(100.0),200.0,0.0,true)==0) }
    case("07.07 wrong road cannot complete stop") { expect(StopCompletionPolicy.passed(listOf(100.0),200.0,40.0,false)==0) }
    case("07.07 invalid progress cannot complete stop") { expect(StopCompletionPolicy.passed(listOf(100.0),Double.POSITIVE_INFINITY,0.0,false)==0) }
    case("07.07 malformed stop order cannot skip first") { expect(StopCompletionPolicy.passed(listOf(500.0,100.0),200.0,0.0,false)==0) }
    case("07.07 invalid stop position not completed") { expect(StopCompletionPolicy.passed(listOf(-1.0),200.0,0.0,false)==0) }
    case("07.07 measured beyond all stops continues destination") { expect(StopCompletionPolicy.passed(listOf(100.0,500.0),600.0,0.0,false)==2) }
    val coffeeNear=Place("Coffee House","Synthetic Manchester",origin)
    val coffeeFar=Place("Coffee House","Synthetic London",LatLon(51.5,-0.1))
    case("06.01 partial prefix prioritises nearest relevant place") { expect(Geocoder.rank("coff",listOf(coffeeFar,coffeeNear),origin).first()==coffeeNear) }
    case("06.01 partial result remains suggested") { expect(SearchMatch.describe("coff",coffeeNear,origin).quality==SearchMatch.Quality.SUGGESTED) }
    case("06.01 partial result never auto selected as exact") { expect(!Geocoder.canAutoSelectOnlyResult("coff",coffeeNear,origin)) }
    case("06.01 missing house number never auto selected") { expect(!Geocoder.canAutoSelectOnlyResult("12 King Street",Place("King Street","Synthetic Manchester",origin),origin)) }
    case("06.01 far-away result never auto selected") { expect(!Geocoder.canAutoSelectOnlyResult("Coffee House",coffeeFar,origin)) }
    case("06.01 duplicates merged while different areas retained") { expect(Geocoder.mergeSaved("Coffee House",listOf(coffeeNear),listOf(coffeeNear,coffeeFar),origin).size==2) }
    case("06.01 progressive results visible before second source releases") { kotlinx.coroutines.runBlocking {
        val gate=kotlinx.coroutines.CompletableDeferred<Unit>();val updates=mutableListOf<List<Place>>()
        val found=Geocoder.collectSearch("Coffee House",origin,listOf(
            SearchSource("first",2000,false){listOf(coffeeNear)},
            SearchSource("second",2000,false){gate.await();listOf(coffeeFar)}
        )) { updates.add(it);gate.complete(Unit) }
        expect(updates.first()==listOf(coffeeNear) && found.size==2)
    } }
    case("06.01 one failed local source preserves successful answers") { kotlinx.coroutines.runBlocking {
        val found=Geocoder.collectSearch("Coffee House",origin,listOf(
            SearchSource("failed",2000,false){error("Synthetic missing index")},
            SearchSource("working",2000,false){listOf(coffeeNear)}
        ))
        expect(found==listOf(coffeeNear))
    } }
    println("TOTAL_FAILURES|"+failures)
}
