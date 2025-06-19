@file:Suppress("unused")

package de.lemke.commonutils.data

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import de.lemke.commonutils.SaveLocation
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Advanced delegation, allowing for custom keys and defaults, and type safety. A little more verbose at the usage site.
 * https://www.youtube.com/watch?v=KFgb6l1PUJI&t=600s
 */
val SharedPreferences.delegates get() = SharedPreferenceDelegates(this)

@SuppressLint("CommitPrefEdits")
class SharedPreferenceDelegates(private val prefs: SharedPreferences) {
    fun boolean(default: Boolean = false, key: String? = null): ReadWriteProperty<Any, Boolean> =
        create(default, key, prefs::getBoolean, prefs.edit()::putBoolean)

    fun int(default: Int = 0, key: String? = null): ReadWriteProperty<Any, Int> =
        create(default, key, prefs::getInt, prefs.edit()::putInt)

    fun float(default: Float = 0f, key: String? = null): ReadWriteProperty<Any, Float> =
        create(default, key, prefs::getFloat, prefs.edit()::putFloat)

    fun long(default: Long = 0L, key: String? = null): ReadWriteProperty<Any, Long> =
        create(default, key, prefs::getLong, prefs.edit()::putLong)

    fun string(default: String = "", key: String? = null): ReadWriteProperty<Any, String> =
        create(default, key, { k, d -> prefs.getString(k, d) as String }, prefs.edit()::putString)

    fun stringSet(default: Set<String> = emptySet(), key: String? = null): ReadWriteProperty<Any, Set<String>> =
        create(default, key, { k, d -> prefs.getStringSet(k, d) as Set<String> }, prefs.edit()::putStringSet)

    fun darkMode(default: Boolean = false, key: String? = null): ReadWriteProperty<Any, Boolean> =
        create(
            default, key, { k, d -> prefs.getString(k, if (d) "1" else "0") == "1" },
            { k, v -> prefs.edit().putString(k, if (v) "1" else "0") }
        )

    fun saveLocation(default: SaveLocation = SaveLocation.default, key: String? = null): ReadWriteProperty<Any, SaveLocation> =
        create(
            default, key,
            { k, d ->
                if (SDK_INT <= VERSION_CODES.Q) SaveLocation.CUSTOM
                else SaveLocation.fromStringOrDefault(prefs.getString(k, d.name))
            },
            { k, v -> prefs.edit().putString(k, if (SDK_INT <= VERSION_CODES.Q) SaveLocation.CUSTOM.name else v.name) }
        )

    private fun <T> create(
        default: T,
        key: String? = null,
        getter: (key: String, default: T) -> T,
        setter: (key: String, value: T) -> SharedPreferences.Editor,
    ) = object : ReadWriteProperty<Any, T> {
        private fun key(property: KProperty<*>) = key ?: property.name

        override fun getValue(thisRef: Any, property: KProperty<*>): T =
            getter(key(property), default)

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) =
            setter(key(property), value).apply()
    }
}