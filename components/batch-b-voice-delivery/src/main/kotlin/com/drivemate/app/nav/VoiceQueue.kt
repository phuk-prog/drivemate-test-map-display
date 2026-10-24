package com.drivemate.app.nav

import java.util.Locale

/** Platform-independent ordering and freshness rules; time must be monotonic. */
internal data class VoiceMessage(
    val text: String,
    val alert: Boolean,
    val urgent: Boolean,
    val createdAt: Long,
    val routeEpoch: Long,
    val tone: Boolean = false,
    val doubleTone: Boolean = false,
    /** Already cut off once (call, other app, urgent direction); never resumed twice. */
    val interrupted: Boolean = false,
    /** Settings sample: the driver asked for it, so recent-repeat suppression does not apply. */
    val preview: Boolean = false,
    /** Only a separately paced speeding policy may bypass spoken-history suppression. */
    val policyRepeat: Boolean = false,
) {
    fun sameAnnouncement(other: VoiceMessage): Boolean = alert == other.alert && tone == other.tone &&
        doubleTone == other.doubleTone && (tone || announcementKey(text) == announcementKey(other.text))

    /** Identical urgent guidance can replace routine speech, but must not restart urgent speech. */
    fun suppresses(other: VoiceMessage): Boolean = sameAnnouncement(other) && (!other.urgent || urgent)

    private fun announcementKey(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun valid(now: Long, epoch: Long): Boolean = now >= createdAt &&
        now - createdAt < (if (alert) ALERT_LIFE_MS else DIRECTION_LIFE_MS) && (alert || routeEpoch == epoch)

    companion object {
        const val DIRECTION_LIFE_MS = 8_000L
        const val ALERT_LIFE_MS = 12_000L
        /** The same spoken warning is not repeated within this time (for example GPS lost/back flapping). */
        const val ALERT_REPEAT_MS = 30_000L
        /** The same direction on the same route is not repeated within this time. */
        const val DIRECTION_REPEAT_MS = 10_000L
    }
}

/**
 * One queue for every sound DriveMate makes, so directions, warnings and beeps never overlap.
 * Order: imminent (urgent) directions, then warnings, then routine directions.
 */
internal class VoiceMessageQueue {
    private val messages = mutableListOf<VoiceMessage>()
    private data class Spoken(val message: VoiceMessage, val at: Long)
    private val history = ArrayDeque<Spoken>()
    /** Creation time of the newest direction accepted; an older interrupted direction is out of date. */
    private var lastDirectionAt = Long.MIN_VALUE
    var routeEpoch: Long = 0; private set
    private var manoeuvre: Int? = null
    fun add(text: String, alert: Boolean, interrupt: Boolean, now: Long, preview: Boolean = false, policyRepeat: Boolean = false): Boolean =
        enqueue(VoiceMessage(text, alert, interrupt, now, routeEpoch, preview = preview, policyRepeat = policyRepeat), now)

    /** The double-beep pattern is an alert, not a reason to interrupt an imminent manoeuvre. */
    fun addTone(doubleTone: Boolean, now: Long): Boolean =
        enqueue(VoiceMessage("", true, false, now, routeEpoch, tone = true, doubleTone = doubleTone), now)

    /** Record speech that actually started, for repeat suppression. Beeps are not recorded. */
    fun markSpoken(message: VoiceMessage, now: Long) {
        if (message.tone) return
        history.addLast(Spoken(message, now))
        while (history.size > 20) history.removeFirst()
    }

    fun recentlySpoken(message: VoiceMessage, now: Long): Boolean = !message.tone && !message.preview && !message.policyRepeat && history.any { (old, at) ->
        val window = if (message.alert) VoiceMessage.ALERT_REPEAT_MS else VoiceMessage.DIRECTION_REPEAT_MS
        now >= at && now - at < window && old.suppresses(message) && (message.alert || old.routeEpoch == message.routeEpoch)
    }

    /**
     * Speech that was cut off part-way is put back once, at the front of its priority band, only
     * while it is still fresh, on the same route, and not replaced by a newer direction.
     */
    fun resume(message: VoiceMessage, now: Long): Boolean {
        if (message.tone || message.interrupted) return false
        return reinsert(message.copy(interrupted = true), now)
    }

    /** A message that could not start yet (phone call, audio focus refused) waits until it is stale. */
    fun defer(message: VoiceMessage, now: Long): Boolean = reinsert(message, now)

    private fun reinsert(message: VoiceMessage, now: Long): Boolean {
        prune(now)
        if (!message.valid(now, routeEpoch)) return false
        if (!message.alert && message.createdAt < lastDirectionAt) return false
        if (messages.any { it.sameAnnouncement(message) && priority(it) >= priority(message) }) return false
        messages.removeAll { it.sameAnnouncement(message) }
        val priority = priority(message)
        val index = messages.indexOfFirst { priority(it) <= priority }
        if (index < 0) messages.add(message) else messages.add(index, message)
        return true
    }

    private fun enqueue(message: VoiceMessage, now: Long): Boolean {
        prune(now)
        if (recentlySpoken(message, now)) return false
        val same = messages.firstOrNull { it.sameAnnouncement(message) }
        if (same != null && priority(same) >= priority(message)) return false
        if (same != null) messages.remove(same)
        if (message.urgent) messages.removeAll { !it.alert }
        if (messages.size >= 5) {
            val lowest = messages.minOf(::priority)
            // Urgent directions must displace a lower-priority alert in a saturated queue.
            // Otherwise preserve alerts; routine directions may replace older routine text.
            val replace = messages.indexOfFirst { priority(it) == lowest &&
                (lowest < priority(message) || (!it.alert && !it.urgent)) }
            if (replace < 0) return false
            messages.removeAt(replace)
        }
        if (!message.alert) lastDirectionAt = maxOf(lastDirectionAt, message.createdAt)
        val priority = priority(message)
        val index = messages.indexOfFirst { priority(it) < priority }
        if (index < 0) messages.add(message) else messages.add(index, message)
        return true
    }
    fun next(now: Long, speechReady: Boolean = true, speechAllowed: Boolean = true): VoiceMessage? {
        prune(now)
        val index = messages.indexOfFirst { it.tone || (speechReady && speechAllowed) }
        return if (index < 0) null else messages.removeAt(index)
    }
    fun isEmpty(now: Long): Boolean { prune(now); return messages.isEmpty() }
    fun clear() = messages.clear()
    fun dropDirections() { messages.removeAll { !it.alert } }
    fun dropSpeech() { messages.removeAll { !it.tone } }
    fun newRoute() { routeEpoch++; manoeuvre = null; dropDirections() }
    /** Advancing a manoeuvre invalidates its old directions even before their TTL expires. */
    fun newManoeuvre(index: Int): Boolean {
        require(index >= 0)
        if (manoeuvre == index) return false
        manoeuvre = index
        routeEpoch++
        dropDirections()
        return true
    }
    private fun prune(now: Long) { messages.removeAll { !it.valid(now, routeEpoch) } }
    private fun priority(message: VoiceMessage) = when { message.urgent -> 2; message.alert -> 1; else -> 0 }
}

/** Offline voices first, UK then US then other English voices. Null means unsuitable. */
internal object OfflineVoicePolicy {
    fun rank(language: String, country: String, networkRequired: Boolean, installed: Boolean): Int? {
        if (language != "en" || networkRequired || !installed) return null
        return when (country) { "GB" -> 0; "US" -> 1; else -> 2 }
    }
}

/** Restoring our mode is independent from restoring an accepted communication device. */
internal object VoiceRoutingOwnership {
    fun mayRestoreMode(inCommunicationMode: Boolean, previousId: Int?, acceptedId: Int?, currentId: Int?): Boolean =
        inCommunicationMode && (currentId == null || currentId == previousId ||
            (acceptedId != null && currentId == acceptedId))
}

/** What each audio choice does, for Settings → Voice and music (3.3). */
fun voiceOutputExplanation(output: VoiceOutput): String = when (output) {
    VoiceOutput.CAR -> "Directions play like other navigation apps: through the car when the phone is connected by Bluetooth for media, otherwise the phone. Music is turned down or paused."
    VoiceOutput.CALL -> "For cars that only play Bluetooth phone calls, or whose stereo is on radio, DAB or USB: directions are sent as a short phone call. If the call audio doesn't connect, normal audio is used and it tries again next time. Never used while you're on a real call."
    VoiceOutput.PHONE -> "Directions play on the phone's own speaker. If Android can't confirm the speaker alone is playing (for example the car takes the audio), that direction is not spoken and a message says so; your music is not rerouted. Needs Android 9 or later."
}

/** What DriveMate last asked Android to play through. It cannot hear the car, so it never claims more. */
enum class VoiceRouteUsed { NAVIGATION_AUDIO, CALL_AUDIO, CALL_FALLBACK, PHONE_SPEAKER }
enum class VoiceEngineState { STARTING, READY, BEEPS_ONLY }

data class VoiceStatus(
    val engine: VoiceEngineState = VoiceEngineState.STARTING,
    val lastRoute: VoiceRouteUsed? = null,
    /** Speech held back because a phone call or another app has the audio. */
    val waiting: Boolean = false,
) {
    /** Plain-English line for Settings → Sound. */
    fun describe(selected: VoiceOutput): String {
        val engineText = when (engine) {
            VoiceEngineState.STARTING -> "Voice: starting the speech engine."
            VoiceEngineState.READY -> "Voice: ready."
            VoiceEngineState.BEEPS_ONLY -> "Voice: the speech engine isn't working, so beeps are used instead."
        }
        val waitText = if (waiting) " Waiting for a phone call or another app to finish; directions play afterwards only if still useful." else ""
        val routeText = when (lastRoute) {
            null -> " Nothing sent yet. Selected: ${selected.label}."
            VoiceRouteUsed.NAVIGATION_AUDIO -> " Selected: ${selected.label}. Last sent as navigation audio (your car if connected by Bluetooth, otherwise the phone)."
            VoiceRouteUsed.CALL_AUDIO -> " Selected: ${selected.label}. Last sent as a Bluetooth phone call."
            VoiceRouteUsed.CALL_FALLBACK -> " Selected: ${selected.label}. Phone-call audio didn't connect last time, so normal navigation audio was used. It will try a phone call again next time."
            VoiceRouteUsed.PHONE_SPEAKER -> " Selected: ${selected.label}. Last sent to the phone speaker."
        }
        // Tones use normal media audio before any call or speaker routing (3.3): say so where it differs.
        val toneText = if (selected != VoiceOutput.CAR) " Warning beeps play as normal media audio (the car if connected), not through ${selected.label.lowercase()}." else ""
        return engineText + waitText + routeText + toneText + " DriveMate can't hear the car; use Play sample to check."
    }
}
