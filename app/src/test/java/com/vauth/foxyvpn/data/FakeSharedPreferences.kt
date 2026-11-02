package com.vauth.foxyvpn.data

import android.content.SharedPreferences

/**
 * In-memory SharedPreferences for plain-JVM unit tests. Editor changes are
 * applied atomically on commit()/apply() (like the real file write); with
 * [failCommit] set, commit() changes nothing and returns false.
 */
internal class FakeSharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {

    private val data = LinkedHashMap<String, Any?>(initial)
    var failCommit = false
    var commitCount = 0
        private set

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        if (data.containsKey(key)) data[key] as String? else defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (data.containsKey(key)) data[key] as MutableSet<String>? else defValues

    override fun getInt(key: String?, defValue: Int): Int =
        if (data.containsKey(key)) data[key] as Int else defValue

    override fun getLong(key: String?, defValue: Long): Long =
        if (data.containsKey(key)) data[key] as Long else defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        if (data.containsKey(key)) data[key] as Float else defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        if (data.containsKey(key)) data[key] as Boolean else defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private val removals = LinkedHashSet<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?) = also { changes[key!!] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) =
            also { changes[key!!] = values }
        override fun putInt(key: String?, value: Int) = also { changes[key!!] = value }
        override fun putLong(key: String?, value: Long) = also { changes[key!!] = value }
        override fun putFloat(key: String?, value: Float) = also { changes[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean) = also { changes[key!!] = value }
        override fun remove(key: String?) = also { removals += key!! }
        override fun clear() = also { clearAll = true }

        override fun commit(): Boolean {
            commitCount++
            if (failCommit) return false
            flush()
            return true
        }

        override fun apply() = flush()

        private fun flush() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(changes)
        }
    }
}
