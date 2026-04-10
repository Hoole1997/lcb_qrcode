package net.corekit.core.ext

import android.content.Context
import android.content.SharedPreferences
import net.corekit.core.utils.ProviderContext

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

val kv: SharedPreferences by lazy {
    ProviderContext.getAppContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
}

val kvEditor: SharedPreferences.Editor by lazy {
    kv.edit()
}

class DataStoreStringDelegate(private val key: String, private val def: String? = null) :
    ReadWriteProperty<Any?, String?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): String? {
        return kv.getString(key, def)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        kvEditor.putString(key, value).apply()
    }
}

class DataStoreLongDelegate(private val key: String, private val def: Long = 0L) :
    ReadWriteProperty<Any?, Long> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Long {
        return kv.getLong(key, def)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
        kvEditor.putLong(key, value).apply()
    }
}

class DataStoreBoolDelegate(private val key: String, private val def: Boolean = false) :
    ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        return kv.getBoolean(key, def)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        kvEditor.putBoolean(key, value).apply()
    }
}

class DataStoreFloatDelegate(private val key: String, private val def: Float = 0f) :
    ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Float {
        return kv.getFloat(key, def)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        kvEditor.putFloat(key, value).apply()
    }
}


class DataStoreIntDelegate(private val key: String, private val def: Int = 0) :
    ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        return kv.getInt(key, def)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        kvEditor.putInt(key, value).apply()
    }
}

class DataStoreStringSetDelegate(private val key: String, private val def: Set<String> = setOf()) :
    ReadWriteProperty<Any?, Set<String>?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String>? {
        return kv.getStringSet(key, def)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<String>?) {
        kvEditor.putStringSet(key, value).apply()
    }
}
