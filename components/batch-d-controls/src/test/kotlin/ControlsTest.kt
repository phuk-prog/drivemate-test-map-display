import com.drivemate.app.data.*
import android.content.Context
import org.junit.Test
import org.junit.Assert.*
class ControlsTest {
 @Test fun exactSoundLabels() { assertEquals(listOf("Full","Alerts only","Muted"), SoundMode.entries.map { it.label }) }
 @Test fun alertsIconHasNoWaves() { assertEquals("🔈", SoundMode.ALERTS.icon) }
 @Test fun soundCycleOrder() { assertEquals(listOf("ALL","ALERTS","OFF"), SoundMode.entries.map { it.name }) }
 @Test fun historyStartsDisabled() { val p=LocalPrivacySettings(Context()); assertEquals(false, p.javaClass.methods.firstOrNull { it.name=="journeyHistoryEnabled" }?.invoke(p)) }
 @Test fun explicitHistoryChoicePersists() { val c=Context(); val p=LocalPrivacySettings(c); val setter=p.javaClass.methods.firstOrNull { it.name=="setJourneyHistoryEnabled" }; assertNotNull(setter); setter!!.invoke(p,true); assertEquals(true, p.javaClass.getMethod("journeyHistoryEnabled").invoke(LocalPrivacySettings(c))) }
 @Test fun historyDisablePersists() { val p=LocalPrivacySettings(Context()); val setter=p.javaClass.methods.firstOrNull { it.name=="setJourneyHistoryEnabled" }; assertNotNull(setter); setter!!.invoke(p,true); setter.invoke(p,false); assertEquals(false,p.javaClass.getMethod("journeyHistoryEnabled").invoke(p)) }
 @Test fun failedHistorySaveIsReported() { val c=Context(); val p=LocalPrivacySettings(c); c.getSharedPreferences("drivemate_local_privacy",0).failCommit=true; val setter=p.javaClass.methods.firstOrNull { it.name=="setJourneyHistoryEnabled" }; assertNotNull(setter); try { setter!!.invoke(p,true); fail("Expected failed durable write") } catch (e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is IllegalStateException) }; assertEquals(false,p.javaClass.getMethod("journeyHistoryEnabled").invoke(p)) }
}
