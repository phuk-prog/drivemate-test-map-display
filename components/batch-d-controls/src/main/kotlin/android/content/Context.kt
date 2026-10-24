package android.content
open class Context {
 companion object { const val MODE_PRIVATE = 0 }
 private val stores = mutableMapOf<String, SharedPreferences>()
 fun getSharedPreferences(name: String, mode: Int): SharedPreferences = stores.getOrPut(name) { SharedPreferences() }
}
class SharedPreferences {
 private val values = mutableMapOf<String, Boolean>()
 var failCommit = false
 fun getBoolean(key: String, default: Boolean) = values[key] ?: default
 fun edit() = Editor()
 inner class Editor {
  private val changes = mutableMapOf<String, Boolean>()
  fun putBoolean(key: String, value: Boolean): Editor { changes[key] = value; return this }
  fun commit(): Boolean { if (failCommit) return false; values.putAll(changes); return true }
  fun apply() { commit() }
 }
}
