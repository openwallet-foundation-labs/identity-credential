package com.android.mdl.appreader.settings

import android.content.SharedPreferences

class InMemorySharedPreferences : SharedPreferences {

    private val valuesMap = HashMap<String, Any?>()
    private val uncommittedValuesMap = HashMap<String, Any?>()
    private val editor = Editor(valuesMap, uncommittedValuesMap)

    override fun getAll(): MutableMap<String, *> {
        TODO("Not yet implemented")
    }

    override fun getString(key: String?, defaultValue: String?): String? {
        return valuesMap.getOrDefault(key, defaultValue) as? String?
    }

    @Suppress("unchecked_cast")
    override fun getStringSet(
        key: String?,
        defaultValue: MutableSet<String>?
    ): MutableSet<String>? {
        return valuesMap.getOrDefault(key, defaultValue) as? MutableSet<String>
    }

    override fun getInt(key: String?, defaultValue: Int): Int {
        return valuesMap.getOrDefault(key, defaultValue) as Int
    }

    override fun getLong(key: String?, defaultValue: Long): Long {
        return valuesMap.getOrDefault(key, defaultValue) as Long
    }

    override fun getFloat(key: String?, defaultValue: Float): Float {
        return valuesMap.getOrDefault(key, defaultValue) as Float
    }

    override fun getBoolean(key: String?, defaultValue: Boolean): Boolean {
        return valuesMap.getOrDefault(key, defaultValue) as Boolean
    }

    override fun contains(key: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun edit(): SharedPreferences.Editor = editor

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        TODO("Not yet implemented")
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        TODO("Not yet implemented")
    }

    inner class Editor(
        private val valuesMap: MutableMap<String, Any?>,
        private val uncommittedValuesMap: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            uncommittedValuesMap[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            value: MutableSet<String>?
        ): SharedPreferences.Editor {
            uncommittedValuesMap[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            uncommittedValuesMap[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            uncommittedValuesMap[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            uncommittedValuesMap[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            uncommittedValuesMap[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            uncommittedValuesMap.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            uncommittedValuesMap.clear()
            valuesMap.clear()
            return this
        }

        override fun commit(): Boolean {
            uncommittedValuesMap.forEach {
                valuesMap[it.key] = it.value
            }
            uncommittedValuesMap.clear()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}