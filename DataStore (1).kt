package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeyClass
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKeyClass
import com.lagradost.cloudstream3.mvvm.logError
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import android.util.Base64
import androidx.core.content.edit

const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"
const val PREFERENCES_NAME = "rebuild_preference"

// =======================
// XOR PATCH (SAFE & MINIMAL)
// =======================

// === REPO URL PATCH ===
private const val URL_XOR_KEY: Byte = 0x3C

private fun xorBytes(data: ByteArray): ByteArray {
    for (i in data.indices) {
        data[i] = (data[i].toInt() xor URL_XOR_KEY.toInt()).toByte()
    }
    return data
}

private fun encodeRepoUrl(url: String): String {
    val xored = xorBytes(url.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(xored, Base64.NO_WRAP)
}

private fun decodeRepoUrl(data: String): String {
    return try {
        val decoded = Base64.decode(data, Base64.NO_WRAP)
        String(xorBytes(decoded), Charsets.UTF_8)
    } catch (e: Exception) {
        data
    }
}

private val repoUrlRegex = Regex(
    "(https://(raw\\.)?githubusercontent\\.com|" +
            "https://github\\.com|" +
            "https://gitlab\\.com|" +
            "https://codeberg\\.org|" +
            "https://forgejo)[^\"]+"
)

private val base64Regex = Regex("[A-Za-z0-9+/=]{20,}")

private fun String.encodeRepoUrls(): String {
    return repoUrlRegex.replace(this) {
        encodeRepoUrl(it.value)
    }
}

private fun String.decodeRepoUrls(): String {
    return base64Regex.replace(this) {
        decodeRepoUrl(it.value)
    }
}

// =======================
// EXISTING CODE (UNTOUCHED)
// =======================

class PreferenceDelegate<T : Any>(
    val key: String, val default: T
) {
    private val klass: KClass<out T> = default::class
    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>) =
        cache ?: getKeyClass(key, klass.java).also { newCache -> cache = newCache } ?: default

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        t: T?
    ) {
        cache = t
        if (t == null) {
            removeKey(key)
        } else {
            setKeyClass(key, t)
        }
    }
}

data class Editor(
    val editor: SharedPreferences.Editor
) {
    fun <T> setKeyRaw(path: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        if (isStringSet(value)) {
            editor.putStringSet(path, value as Set<String>)
        } else {
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
            }
        }
    }

    private fun isStringSet(value: Any?): Boolean {
        if (value is Set<*>) {
            return value.filterIsInstance<String>().size == value.size
        }
        return false
    }

    fun apply() {
        editor.apply()
        System.gc()
    }
}

object DataStore {
    val mapper: JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getPreferences(this)
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    fun editor(context: Context, isEditingAppSettings: Boolean = false): Editor {
        val editor: SharedPreferences.Editor =
            if (isEditingAppSettings)
                context.getDefaultSharedPrefs().edit()
            else
                context.getSharedPrefs().edit()
        return Editor(editor)
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }

    fun Context.getKeys(folder: String): List<String> {
        return this.getSharedPrefs().all.keys.filter { it.startsWith(folder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun Context.containsKey(path: String): Boolean {
        val prefs = getSharedPrefs()
        return prefs.contains(path)
    }

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) {
                prefs.edit { remove(path) }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        return try {
            getSharedPrefs().edit {
                keys.forEach { remove(it) }
            }
            keys.size
        } catch (e: Exception) {
            logError(e)
            0
        }
    }

    fun <T> Context.setKey(path: String, value: T) {
    try {
        val json = mapper.writeValueAsString(value)
        val patched = json.encodeRepoUrls()
        getSharedPrefs().edit {
            putString(path, patched)
        }
    } catch (e: Exception) {
        logError(e)
    }
}

    fun <T> Context.setKey(path: String, value: T) {
    try {
        val json = mapper.writeValueAsString(value)
        val patched = json.encodeRepoUrls()
        getSharedPrefs().edit {
            putString(path, patched)
        }
    } catch (e: Exception) {
        logError(e)
    }
}

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    fun <T> String.toKotlinObject(valueType: Class<T>): T {
        return mapper.readValue(this, valueType)
    }

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            json.toKotlinObject()
        } catch (e: Exception) {
            null
        }
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal) ?: defVal
    }
}