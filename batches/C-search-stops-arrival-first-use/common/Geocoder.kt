package com.drivemate.app.data

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import kotlin.math.log10

/**
 * Address and place search from the lists on the phone (1.14: nothing is sent): every UK postcode,
 * street and place (Ordnance Survey Open Names) and DriveMate's business list. Ranks them ourselves:
 * results matching every word you typed come first (allowing for typos and half-typed
 * words), and among equally good matches the nearest wins.
 */
object Geocoder {
    // How long each source may take before the list is shown without it (milliseconds).
    internal const val OWN_MS = 1_500L

    /**
     * [thorough], [useTomTom] and [online] are kept for callers and no longer change anything (1.14).
     * @param onUpdate gets the merged, ranked list each time another source answers, so the
     * first good results can be shown straight away.
     */
    suspend fun search(
        query: String,
        near: LatLon?,
        @Suppress("UNUSED_PARAMETER") thorough: Boolean = false,
        @Suppress("UNUSED_PARAMETER") useTomTom: Boolean = thorough,
        offlineSearch: suspend (String, LatLon?) -> List<Place> = { _, _ -> emptyList() },
        @Suppress("UNUSED_PARAMETER") online: Boolean = true,
        onUpdate: suspend (List<Place>) -> Unit = {},
    ): List<Place> {
        // 1.14 (owner, 21 Oct 2026: nothing about you leaves the phone): search reads only what is on the
        // phone: every UK postcode, street and place (Ordnance Survey Open Names) and DriveMate's business
        // list. What you type and where you are are never sent (it used to go to Photon, Nominatim,
        // postcodes.io and TomTom).
        val match = fullPostcode.find(query)
        val asked = query.trim()
        val sources = ArrayList<SearchSource>()
        if (asked.length >= 3 || match != null) sources += SearchSource(LOCAL, OWN_MS, online = false) {
            offlineSearch(match?.value ?: query, near)
        }
        near?.let { c -> sources += SearchSource(OWN, OWN_MS, online = false) { OwnPlaces.search(asked, c) } }
        return collectSearch(query, near, sources, onUpdate)
    }

    /** Shared orchestration allows local results to appear without awaiting postcode lookup. */
    internal suspend fun collectSearch(
        query: String,
        near: LatLon?,
        sources: List<SearchSource>,
        onUpdate: suspend (List<Place>) -> Unit = {},
    ): List<Place> {
        var shown: List<Place> = emptyList()
        val outcomes = ParallelSearch.run(sources) { sofar ->
            val merged = mergeSearch(query, sofar, near)
            if (merged.isNotEmpty() && merged != shown) { shown = merged; onUpdate(merged) }
        }
        val merged = mergeSearch(query, outcomes, near)
        if (merged.isEmpty() && outcomes.any { it.online } && outcomes.filter { it.online }.all { it.failed }) {
            throw outcomes.firstNotNullOfOrNull { it.result?.exceptionOrNull() }
                ?: java.net.SocketTimeoutException("Search servers didn't answer in time")
        }
        return merged
    }

    /** Only an exact matching postcode centroid may remove the postcode from ranking text. */
    internal fun mergeSearch(query: String, outcomes: List<SourceOutcome>, near: LatLon?): List<Place> {
        val match = fullPostcode.find(query)
        val code = match?.value?.let(::compactCode)
        val postcode = if (code == null) emptyList() else outcomes
            .filter { it.name == POSTCODE || it.name == LOCAL || it.name == "results" || it.name == "saved" }
            .flatMap { it.places }
            .filter { it.keywords == "postcode" && compactCode(it.name) == code }
            .take(1)
        val addressText = match?.let { query.replace(it.value, " ").trim() } ?: query
        val numberOnly = code != null && tokens(addressText).let { words ->
            words.isNotEmpty() && words.all { houseNumber.matches(it) }
        }
        // Before a centroid arrives, a nearby "12" in another postcode must not become
        // the first selectable address. Keep exact matching houses and labelled centroids.
        val safeOutcomes = if (!numberOnly) outcomes else outcomes.map { outcome ->
            SourceOutcome(outcome.name, outcome.online, outcome.result?.map { places ->
                places.filter { place ->
                    val exactCode = fullPostcode.findAll("${place.name} ${place.address}")
                        .any { compactCode(it.value) == code }
                    exactCode && (place.keywords == "postcode" ||
                        scored(addressText, listOf(place), near).single().coverage >= 1.0)
                }
            })
        }
        val text = if (postcode.isNotEmpty()) addressText else query
        return merge(text, safeOutcomes.filter { it.name != POSTCODE }, postcode.firstOrNull()?.pos ?: near,
            postcode.takeIf { it.isNotEmpty() })
    }

    /** A sole approximate result remains a choice, never an assumed exact destination. */
    fun canAutoSelectOnlyResult(query: String, place: Place, near: LatLon? = null): Boolean {
        val asked = query.trim()
        if (asked.isEmpty()) return false
        val placeName = place.name.trim()
        val postcodeResult = place.keywords == "postcode" || fullPostcode.matches(placeName) ||
            outcodeOnly.matches(placeName)
        if (postcodeResult) return compactCode(asked) == compactCode(placeName)

        val requestedPostcode = fullPostcode.find(asked)
        if (requestedPostcode != null && fullPostcode.findAll("${place.name} ${place.address}")
                .none { compactCode(it.value) == compactCode(requestedPostcode.value) }) return false
        // Remove the postcode before looking for house numbers: SK8 2EZ is not house 2.
        val addressText = requestedPostcode?.let { asked.replace(it.value, " ") } ?: asked
        val numbers = tokens(addressText).filter { houseNumber.matches(it) }
        val availableAddress = "${place.name} ${place.address}"
        val availableWords = tokens(availableAddress)
        if (!(numbers.all { hasNumber(availableAddress, availableWords, it) } &&
            scored(asked, listOf(place), null).single().coverage >= 1.0)) return false
        // Only an exact match is chosen without asking: never a similar spelling, a name that only
        // starts with what was typed, or a place far away (MX01/WA06).
        val match = SearchMatch.describe(asked, place, near)
        return match.quality == SearchMatch.Quality.EXACT && !match.far
    }

    /** Local-only candidates: never used to construct a provider request or ranking location. */
    fun savedMatches(query: String, candidates: List<Place>, near: LatLon?): List<Place> {
        if (query.trim().length < 2) return emptyList()
        val matching = scored(query, candidates, near).filter { it.coverage >= 1.0 }.map { it.place }
        return mergeSearch(query, listOf(SourceOutcome("saved", false, Result.success(matching))), near)
    }

    /** Merge locally in the caller; the saved list is never passed to an online provider. */
    fun mergeSaved(query: String, saved: List<Place>, fetched: List<Place>, near: LatLon?): List<Place> =
        mergeSearch(query, listOf(
            SourceOutcome("saved", false, Result.success(saved)),
            SourceOutcome("results", false, Result.success(fetched)),
        ), near)

    private const val LOCAL = "local"
    private const val POSTCODE = "postcode"
    private const val OUTCODE = "outcode"
    private const val OWN = "own"

    /**
     * Everything the sources have found so far, merged and ranked. With a postcode, places in
     * that postcode area come first, then the postcode itself; a postcode area ("SK4") goes
     * third, after the two best matches.
     */
    internal fun merge(text: String, outcomes: List<SourceOutcome>, centre: LatLon?, postcode: List<Place>?): List<Place> {
        val all = outcomes.filter { it.name != OUTCODE }.flatMap { it.places }
        val scored = scored(text, all, centre)
        val ranked = scored.map { it.place }
        return if (postcode != null && postcode.isNotEmpty()) {
            val here = postcode.first().pos
            val code = compactCode(postcode.first().name)
            val numberOnly = tokens(text).let { w -> w.isNotEmpty() && w.all { houseNumber.matches(it) } }
            // Before the postcode: places there matching every word you typed. A house number
            // must be in that very postcode — "12" isn't any 12 within a mile.
            val inArea = scored.filter { s ->
                s.coverage >= 1.0 && if (numberOnly) code in compactCode(s.place.address) || compactCode(s.place.name).contains(code)
                else Geo.distance(s.place.pos, here) < 1500
            }.map { it.place }
            distinctNearby(inArea.take(8) + postcode + ranked.filter { it !in inArea }, 15)
        } else {
            val area = outcomes.firstOrNull { it.name == OUTCODE }?.places?.firstOrNull()
            when {
                area == null -> distinctNearby(ranked, 12)
                // "SK4": the area first, unless something is called exactly that (the M1 motorway for "M1").
                ranked.none { compactCode(it.name) == compactCode(text) } -> distinctNearby(listOf(area) + ranked, 12)
                else -> distinctNearby(ranked.take(2) + area + ranked.drop(2), 12)
            }
        }
    }

    /** With no signal: the offline postcode/place list and your own nearby places, ranked together. */
    internal fun mergeOffline(query: String, offline: List<Place>, own: List<Place>, near: LatLon?): List<Place> =
        distinctNearby(rank(query, offline + own, near), 12)

    internal fun compactCode(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    /** The same place from two sources (same name, within 150 m) is shown once, at its best position. */
    internal fun distinctNearby(places: List<Place>, limit: Int): List<Place> {
        val kept = ArrayList<Place>(limit)
        for (p in places) {
            if (kept.size >= limit) break
            val n = compactCode(p.name)
            if (kept.none { compactCode(it.name) == n && Geo.distance(it.pos, p.pos) < 150 }) kept += p
        }
        return kept
    }

    internal val fullPostcode = Regex("\\b([A-Za-z]{1,2}\\d[A-Za-z\\d]?)\\s*(\\d[A-Za-z]{2})\\b")
    internal val outcodeOnly = Regex("^\\s*([A-Za-z]{1,2}\\d[A-Za-z\\d]?)\\s*$")

    // ---------- Ranking ----------

    private val stopWords = setOf("the", "a", "an", "of", "in", "at", "near", "and", "to", "on")

    fun rank(query: String, places: List<Place>, near: LatLon?): List<Place> = scored(query, places, near).map { it.place }

    /** A place with how many of the typed words it matched (1.0 = all of them) and its overall score. */
    internal class Scored(val place: Place, val coverage: Double, val score: Double)

    internal fun scored(query: String, places: List<Place>, near: LatLon?): List<Scored> {
        val words = rankingWords(query)
        if (words.isEmpty()) return places.map { Scored(it, 0.0, 0.0) }
        return places.map { p ->
            val scores = wordScores(words, p)
            val coverage = scores.count { it > 0 }.toDouble() / words.size
            val quality = scores.average()
            val km = near?.let { Geo.distance(it, p.pos) / 1000 } ?: 0.0
            val exact = exactName(words, p)
            // Matching all words matters most, then how well, then how close — and places in
            // another country (hundreds of km away) drop right down.
            val far = if (km > 400) 4.0 else 0.0
            val score = coverage * 10 + quality * 3 + exact - log10(km + 1) * 1.5 - far
            Scored(p, coverage, score)
        }.sortedByDescending { it.score }
    }

    /**
     * How well each typed word matches [p]: 1.0 exact (or a joined pair like "best way" =
     * "Bestway"), 0.85 the start of a word, 0.5-0.7 a typo, 0 not found.
     */
    internal fun wordScores(words: List<String>, p: Place): DoubleArray {
        val text = "${p.name} ${p.address} ${p.keywords} ${p.personalName.orEmpty()} ${p.group.orEmpty()}"
        val placeWords = tokens(text)
        // The place's words joined in pairs too, so "bestway" matches a place called "Best Way".
        val candidates = placeWords + placeWords.zipWithNext { a, b -> a + b }
        val scores = DoubleArray(words.size) { i ->
            // A house number must be that number ("12" is not "123"), or inside a range ("10-14").
            if (houseNumber.matches(words[i])) { if (hasNumber(text, placeWords, words[i])) 1.0 else 0.0 }
            else candidates.maxOfOrNull { match(words[i], it) } ?: 0.0
        }
        // And typed words joined in pairs, so "best way" matches "Bestway".
        for (i in 0 until words.size - 1) {
            val merged = words[i] + words[i + 1]
            val m = placeWords.maxOfOrNull { match(merged, it) } ?: 0.0
            // Must be a close match (exact, start of the word, or a typo) — not just sharing a start.
            if (m >= 0.7) {
                scores[i] = maxOf(scores[i], m)
                scores[i + 1] = maxOf(scores[i + 1], m)
            }
        }
        return scores
    }

    internal fun rankingWords(query: String): List<String> = tokens(query).filter { it !in stopWords }.ifEmpty { tokens(query) }

    internal val houseNumber = Regex("^\\d{1,5}[a-z]?$")
    private val numberRange = Regex("(\\d{1,5})\\s*[-\u2013/]\\s*(\\d{1,5})")

    internal fun hasNumber(text: String, placeWords: List<String>, n: String): Boolean {
        if (n in placeWords) return true
        val v = n.toIntOrNull() ?: return false
        return numberRange.findAll(text).any { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            b > a && b - a <= 40 && v in a..b
        }
    }

    // Words that mark part of an address as a street rather than a town ("Stockport Road").
    internal val streetWords = setOf(
        "road", "rd", "street", "st", "lane", "ln", "avenue", "ave", "drive", "dr", "way", "close", "grove",
        "crescent", "place", "square", "terrace", "court", "gardens", "mews", "walk", "parade", "row", "boulevard", "estate",
    )

    /** Town and area words in a place's address ("Stockport", "Cheadle"): not streets, numbers or postcodes. */
    private fun areaWords(address: String): Set<String> =
        address.split(',').map { tokens(it) }
            .filter { part -> part.isNotEmpty() && part.none { w -> w in streetWords || w.any(Char::isDigit) } }
            .flatten().toSet()

    /**
     * Bonus for the exact name you typed (spaces ignored: "best way" = "Bestway"). Town names in
     * the place's address and house numbers don't count against it, so "bestway" finds the
     * branch called "Bestway Stockport" in Stockport as exactly as one called just "Bestway",
     * and "tesco stockport" treats a Tesco in Stockport as named exactly — but not a Tesco on a
     * "Stockport Road" in Manchester.
     */
    private fun exactName(words: List<String>, p: Place): Double {
        val typed = words.joinToString("")
        if (typed.length < 3) return 0.0
        val name = p.name.lowercase().replace(Regex("[^\\p{L}\\p{N}&]"), "")
        val whole = when {
            name == typed -> 2.5
            name.startsWith(typed) -> 1.5
            else -> 0.0
        }
        val area = areaWords(p.address)
        fun rest(ws: List<String>, skip: Set<String>) = ws.filter { it !in skip && !houseNumber.matches(it) }.joinToString("")
        val nameWords = tokens(p.name)
        // Typed town words may also be the place's district ("aldi cheadle": an Aldi in Cheadle Heath),
        // but not its brand ("Bestway" is both a name and a category).
        val typedRest = rest(words, area + (tokens(p.keywords) - nameWords.toSet()))
        val nameRest = rest(nameWords, area)
        if (typedRest.length < 3 || nameRest.isEmpty()) return whole
        val trimmed = when {
            nameRest == typedRest -> 2.5
            nameRest.startsWith(typedRest) -> 1.5
            else -> 0.0
        }
        return maxOf(whole, trimmed)
    }

    internal fun tokens(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}&]+")).filter { it.isNotEmpty() }

    /** How well a typed word matches a word in a place: exact, start of word, or a typo away. */
    private fun match(typed: String, word: String): Double = when {
        typed == word -> 1.0
        word.startsWith(typed) -> if (typed.length >= 2) 0.85 else 0.3
        typed.startsWith(word) && word.length >= 3 -> 0.6
        typed.length >= 4 && editDistance(typed, word, 1) <= 1 -> 0.7
        typed.length >= 7 && editDistance(typed, word, 2) <= 2 -> 0.5
        else -> 0.0
    }

    /** Levenshtein distance, giving up early once it's over [max]. */
    private fun editDistance(a: String, b: String, max: Int): Int {
        if (Math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                rowMin = minOf(rowMin, cur[j])
            }
            if (rowMin > max) return max + 1
            prev = cur
        }
        return prev[b.length]
    }

    // ---------- Parsing ----------
}
