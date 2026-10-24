package com.drivemate.app.nav
import java.io.File

/** Actual proposed Voice.say/enqueue/allowed/pump/sound setter/start callback methods.
 * Android scheduling, audio route and output engines are adapters; physical playback is not tested.
 */
internal class VoiceDeliveryHarness {
    enum class SoundMode { ALL, ALERTS, OFF }
    private val queue = VoiceMessageQueue()
    private var current: VoiceMessage? = null
    private var speechStarted = false
    private var utterance: String? = null
    private var file: File? = null
    private var closed = false
    private var failed = false
    private var ready = true
    private var output = VoiceOutput.CAR
    private object AudioManager { const val MODE_NORMAL = 0 }
    private object audio { var mode = 0 }
    private object SystemClock { var time = 200_000L; fun elapsedRealtime() = time }
    val dispatched = mutableListOf<String>()
    var cancelled = 0; private set
    private var nextId = 0
    init { SystemClock.time = 200_000L; audio.mode = 0 }
    private fun onMain(action: () -> Unit) = action()
    private fun interruptCurrent() { current?.let { val old = it; cancelCurrent(); queue.resume(old,SystemClock.time) } }
    private fun deferCurrent() { current?.let { val old = it; cancelCurrent(); queue.defer(old,SystemClock.time) } }
    private fun startCallRoute() = false
    private fun speakCurrent() {
        val message = current ?: return
        if (!allowed(message)) { complete(); return }
        utterance = "id-"+(++nextId)
        dispatched += message.text
        queue.markSpoken(message,SystemClock.time)
    }
    private fun playTone() { if (current?.let { allowed(it) } == true) dispatched += "TONE" }
    private fun cancelCurrent() {
        speechStarted = false; current = null; utterance = null; file = null; cancelled++
    }
    fun complete() { cancelCurrent(); pump() }
    fun advance(ms: Long) { SystemClock.time += ms }
    fun waitingSpeech(value: Boolean) { ready = !value }
    fun currentId() = utterance
    fun hasCurrent() = current != null
    fun playing() = speechStarted
    fun begin(id: String? = utterance, synthesizing: Boolean = false) {
        file = if (synthesizing) File("synthesis-only.wav") else null
        speechDidStart(id)
    }
    fun queueDirect(text: String) { queue.add(text,true,false,SystemClock.time) }
    fun pumpAgain() = pump()
    private fun soundState(): VoiceSoundState = when (sound) {
        SoundMode.ALL -> VoiceSoundState.FULL
        SoundMode.ALERTS -> VoiceSoundState.ALERTS
        SoundMode.OFF -> VoiceSoundState.MUTED
    }
    var sound = SoundMode.ALL
        set(value) {
            if (field == value) return
            field = value
            onMain {
                when (value) {
                    SoundMode.OFF -> queue.clear()
                    SoundMode.ALERTS -> queue.dropSpeech()
                    else -> Unit
                }
                val message = current
                if (message != null && !VoicePlaybackPolicy.mayKeepCurrent(soundState(), message.tone, speechStarted)) cancelCurrent()
                pump()
            }
        }

    fun say(text: String, interrupt: Boolean = false, alert: Boolean = false, policyRepeat: Boolean = false, mainAlert: Boolean = false) =
        enqueueSpeech(text, interrupt, alert, preview = false, policyRepeat = policyRepeat, mainAlert = mainAlert)
    /** Ignore late callbacks and synthesis starts; only the currently audible utterance counts. */
    private fun speechDidStart(id: String?) {
        if (id != null && id == utterance && file == null && current != null) speechStarted = true
    }

    private fun enqueueSpeech(text: String, interrupt: Boolean, alert: Boolean, preview: Boolean, policyRepeat: Boolean = false, mainAlert: Boolean = false) = onMain {
        if (closed) return@onMain
        if (sound == SoundMode.ALERTS && mainAlert) { beep(urgent = true); return@onMain }
        if (!VoicePlaybackPolicy.canStart(soundState(), tone = false)) return@onMain
        if (failed) { beep(alert); return@onMain }
        val now = SystemClock.elapsedRealtime()
        val incoming = VoiceMessage(text, alert, interrupt, now, queue.routeEpoch, preview = preview, policyRepeat = policyRepeat)
        if (!preview && current?.suppresses(incoming) == true) return@onMain
        if (!queue.add(text, alert, interrupt, now, preview, policyRepeat)) return@onMain
        // Imminent directions cut in; a cut-off warning is resumed afterwards if still fresh.
        if (interrupt && current?.urgent != true) interruptCurrent()
        pump()
    }
    private fun allowed(message: VoiceMessage) = message.valid(SystemClock.elapsedRealtime(), queue.routeEpoch) &&
        VoicePlaybackPolicy.canStart(soundState(), message.tone)
    private fun pump() {
        if (closed || current != null || sound == SoundMode.OFF) return
        speechStarted = false
        current = queue.next(SystemClock.elapsedRealtime(), speechReady = ready,
            speechAllowed = VoicePlaybackPolicy.canStart(soundState(), tone = false)) ?: return
        // Do not hijack an actual call or somebody else's communication route: wait instead.
        if (audio.mode != AudioManager.MODE_NORMAL) { deferCurrent(); return }
        if (current?.tone == true) { playTone(); return }
        if (output == VoiceOutput.CALL && startCallRoute()) return
        speakCurrent()
    }

    fun beep(urgent: Boolean = false) = onMain {
        if (closed || sound == SoundMode.OFF) return@onMain
        val now = SystemClock.elapsedRealtime()
        val incoming = VoiceMessage("", true, false, now, queue.routeEpoch, tone = true, doubleTone = urgent)
        if (current?.sameAnnouncement(incoming) == true) return@onMain
        queue.addTone(urgent, now)
        pump()
    }

}
