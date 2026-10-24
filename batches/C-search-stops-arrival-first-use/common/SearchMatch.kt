package com.drivemate.app.data

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon

/**
 * What a search result is (an address, a street, a town, a postcode, a business) and how well it
 * matches what was typed, in plain English for the result list (backlog MX01 / WA06).
 *
 * Only labels what the sources said: it never adds a house number or postcode a source didn't
 * give. A house number that no source has is said plainly ("Street only · no. 12 not found").
 */
data class SearchMatch(
    /** One of [HOUSE], [HOUSE_RANGE], [STREET], [LOCALITY], [POSTCODE], [POSTCODE_AREA], [PLACE]. */
    val kind: String,
    val quality: Quality,
    /** A typed house number this result doesn't have. */
    val missingNumber: String? = null,
    /** A postcode was typed and this result is in a different one. */
    val postcodeDiffers: Boolean = false,
    /** A postcode was typed and this result doesn't say which postcode it is in. */
    val postcodeUnconfirmed: Boolean = false,
    /** Typed words this result doesn't contain at all. */
    val unmatched: List<String> = emptyList(),
    /** Some typed word only matched a similar spelling. */
    val similarSpelling: Boolean = false,
    /** Some typed word is only the start of a word in the result (still typing). */
    val completesTyping: Boolean = false,
    /** Typed words found only in the result's type or area, not its name or shown address ("petrol"). */
    val byTypeOrArea: List<String> = emptyList(),
    /** Further than [FAR_KM] from you. */
    val far: Boolean = false,
    /** How many results in the list have this same name. */
    val sameNameCount: Int = 1,
) {
    enum class Quality { EXACT, SUGGESTED, INCOMPLETE }

    val typeLabel: String
        get() = when (kind) {
            HOUSE -> "Address"
            HOUSE_RANGE -> "Address (position estimated along the street)"
            STREET -> "Street"
            LOCALITY -> "Town or area"
            POSTCODE -> "Postcode (centre of the area)"
            POSTCODE_AREA -> "Postcode area"
            else -> "Place or business"
        }

    /** One short line for under the result, e.g. "Street only · no. 12 not found". */
    val text: String
        get() {
            val parts = ArrayList<String>()
            when (quality) {
                Quality.INCOMPLETE -> {
                    parts += when {
                        missingNumber != null && kind == STREET -> "Street only"
                        missingNumber != null && (kind == POSTCODE || kind == POSTCODE_AREA) -> "Postcode centre only"
                        else -> typeLabel
                    }
                    missingNumber?.let { parts += "no. ${it.uppercase()} not found" }
                    if (postcodeDiffers) parts += "different postcode"
                    if (unmatched.isNotEmpty()) parts += notFound()
                }
                Quality.EXACT -> { parts += typeLabel; parts += "exact match" }
                Quality.SUGGESTED -> {
                    parts += typeLabel
                    val why = buildList {
                        if (unmatched.isNotEmpty()) add(notFound())
                        if (byTypeOrArea.isNotEmpty()) add("matched by type or area")
                        if (similarSpelling) add("similar spelling")
                        if (completesTyping && unmatched.isEmpty() && !similarSpelling) add("completes what you typed")
                        if (postcodeUnconfirmed) add("postcode not confirmed")
                    }
                    parts += "suggested" + if (why.isEmpty()) "" else ": " + why.joinToString(", ")
                }
            }
            if (sameNameCount > 1) parts += "$sameNameCount places with this name, check the area"
            if (far && (quality != Quality.EXACT || sameNameCount > 1)) parts += "far from you"
            return parts.joinToString(" · ")
        }

    private fun notFound() = unmatched.joinToString(", ") { "“$it”" } + " not found"

    companion object {
        const val HOUSE = "house"
        const val HOUSE_RANGE = "house-range"
        const val STREET = "street"
        const val LOCALITY = "locality"
        const val POSTCODE = "postcode"
        const val POSTCODE_AREA = "postcode-area"
        const val PLACE = "place"

        /** Beyond this a similar name is flagged and never chosen automatically (about 50 miles). */
        const val FAR_KM = 80.0

        /** The kind a source gave, or (for saved places and older data) worked out from the name. */
        fun kindOf(p: Place): String {
            if (p.kind.isNotBlank()) return p.kind
            val name = p.name.trim()
            if (Geocoder.fullPostcode.matches(name)) return POSTCODE
            if (p.keywords.trim() == "postcode") return if (Geocoder.outcodeOnly.matches(name)) POSTCODE_AREA else POSTCODE
            val w = Geocoder.tokens(name)
            if (w.size >= 2 && Geocoder.houseNumber.matches(w.first()) && w.last() in Geocoder.streetWords) return HOUSE
            if (w.size >= 2 && w.last() in Geocoder.streetWords) return STREET
            return PLACE
        }

        fun describe(query: String, place: Place, near: LatLon?, sameNameCount: Int = 1): SearchMatch {
            val asked = query.trim()
            val kind = kindOf(place)
            val typedCode = Geocoder.fullPostcode.find(asked)
            val addressText = typedCode?.let { asked.replace(it.value, " ") } ?: asked
            val words = Geocoder.rankingWords(addressText)
            val numbers = words.filter { Geocoder.houseNumber.matches(it) }
            val nameWords = words.filter { !Geocoder.houseNumber.matches(it) }

            val placeText = "${place.name} ${place.address}"
            val placeWords = Geocoder.tokens(placeText)
            val isPostcodeResult = kind == POSTCODE || kind == POSTCODE_AREA
            val missingNumber = numbers.firstOrNull { !Geocoder.hasNumber(placeText, placeWords, it) }

            var postcodeDiffers = false
            var postcodeUnconfirmed = false
            if (typedCode != null) {
                val code = Geocoder.compactCode(typedCode.value)
                val found = Geocoder.fullPostcode.findAll(placeText).map { Geocoder.compactCode(it.value) }.toList()
                when {
                    code in found -> {}
                    found.isNotEmpty() -> postcodeDiffers = true
                    else -> postcodeUnconfirmed = true
                }
            } else if (isPostcodeResult && nameWords.size == 1 && Geocoder.outcodeOnly.matches(asked)) {
                // "SK4" typed: only that postcode area is an exact postcode answer.
                postcodeDiffers = Geocoder.compactCode(asked) != Geocoder.compactCode(place.name)
            }

            val scores = if (nameWords.isEmpty()) DoubleArray(0) else Geocoder.wordScores(nameWords, place)
            val shown = if (nameWords.isEmpty()) DoubleArray(0) else Geocoder.wordScores(nameWords, place.copy(keywords = ""))
            val unmatched = nameWords.filterIndexed { i, _ -> scores[i] <= 0.0 }
            val byTypeOrArea = nameWords.filterIndexed { i, _ -> scores[i] > 0.0 && shown[i] <= 0.0 }
            val similar = scores.any { it > 0.0 && it < 0.85 }
            val completes = scores.any { it == 0.85 }
            val far = near != null && Geo.distance(near, place.pos) > FAR_KM * 1000

            val quality = when {
                missingNumber != null || postcodeDiffers -> Quality.INCOMPLETE
                // A postcode centre isn't the business or street typed with it.
                isPostcodeResult && unmatched.isNotEmpty() -> Quality.INCOMPLETE
                unmatched.isNotEmpty() || byTypeOrArea.isNotEmpty() || similar || completes || postcodeUnconfirmed -> Quality.SUGGESTED
                words.isEmpty() && typedCode == null -> Quality.SUGGESTED
                else -> Quality.EXACT
            }
            return SearchMatch(kind, quality, missingNumber, postcodeDiffers, postcodeUnconfirmed,
                unmatched, similar, completes, byTypeOrArea, far, sameNameCount)
        }

        /** Every result in a list, noting when several share a name (so the area tells them apart). */
        fun describeAll(query: String, places: List<Place>, near: LatLon?): List<SearchMatch> {
            val counts = places.groupingBy { Geocoder.compactCode(it.name) }.eachCount()
            return places.map { describe(query, it, near, counts[Geocoder.compactCode(it.name)] ?: 1) }
        }
    }
}
