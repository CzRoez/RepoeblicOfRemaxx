package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import android.util.Base64
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeyClass
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKeyClass
import com.lagradost.cloudstream3.mvvm.logError
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import java.io.File

const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"
const val PREFERENCES_NAME = "imprint_class"


// ====================== URL HASH MAPPING =========================
// untuk restore URL repo dari hashed ke original
private val repoHashMap = mutableMapOf<String, String>()

// file names for persistence
private const val await_const = "impl_ca"
private const val navigate_this = "intent_await"

// ====================== MAPPER ============================
val internalMapper: JsonMapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

// helper to persist/load repoHashMap from internal storage (filesDir)
private fun saveRepoMapToFile(context: Context) {
    try {
        val json = internalMapper.writeValueAsString(repoHashMap)
        File(context.filesDir, await_const).writeText(json)
    } catch (e: Exception) {
        logError(e)
    }
}

private fun loadRepoMapFromFile(context: Context) {
    try {
        if (repoHashMap.isNotEmpty()) return // already loaded
        val f = File(context.filesDir, await_const)
        if (!f.exists()) return
        val json = f.readText()
        val restored: Map<String, String> = internalMapper.readValue(
            json, object : TypeReference<Map<String, String>>() {}
        )
        repoHashMap.putAll(restored)
    } catch (e: Exception) {
        logError(e)
    }
}

// ====================== RUNTIME CACHE ======================
private var repoCacheLoaded = false

private fun Context.ensureRepoCache() {
    if (!repoCacheLoaded) {
        loadRepoMapFromFile(this)
        repoCacheLoaded = true
    }
}

// ====================== XOR + BASE64 ======================
private const val XOR_KEY = 0x5A

private fun xorBytes(input: ByteArray): ByteArray {
    val key = XOR_KEY.toByte()
    return ByteArray(input.size) { i ->
        (input[i].toInt() xor key.toInt()).toByte()
    }
}

private fun secureEncode(text: String): String {
    val xorData = xorBytes(text.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(xorData, Base64.NO_WRAP)
}

private fun secureDecode(encoded: String): String {
    val xorData = Base64.decode(encoded, Base64.NO_WRAP)
    return String(xorBytes(xorData), Charsets.UTF_8)
}

// ====================== HASH + ENCODE (Context-aware) ====================
private fun Context.hashRepo(url: String): String {
    ensureRepoCache()

    val hashed = "ios_" + url.hashCode().toUInt().toString()

    if (!repoHashMap.containsKey(hashed)) {
        repoHashMap[hashed] = secureEncode(url)
        saveRepoMapToFile(this)   // save sekali per hash baru
    }

    return hashed
}

private fun Context.sanitizeJsonString(input: String): String {
    ensureRepoCache()

    val repoRegex = Regex("(https?|ftp)://[^\"\\s]+")
    var changed = false

    val result = repoRegex.replace(input) { match ->
        val urlLower = match.value.lowercase()

        return@replace when {
            "github.com" in urlLower ||
            "raw.githubusercontent.com" in urlLower ||
            "gitlab.com" in urlLower ||
            "forgejo" in urlLower ||
            urlLower.endsWith(".json") -> {
                changed = true
                hashRepo(match.value)
            }
            else -> match.value
        }
    }

    if (changed) saveRepoMapToFile(this)

    return result
}

// ====================== RESTORE URL (Context-aware) =====================
private fun Context.restoreRepoUrlsInternal(input: String): String {
    ensureRepoCache()

    if (repoHashMap.isEmpty()) return input

    val pattern = repoHashMap.keys.joinToString("|") { Regex.escape(it) }
    val regex = Regex(pattern)

    return regex.replace(input) { match ->
        val encoded = repoHashMap[match.value] ?: return@replace match.value
        secureDecode(encoded)
    }
}

// ===================================================================

class PreferenceDelegate<T : Any>(
    val key: String, val default: T
) {
    private val klass: KClass<out T> = default::class
    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>): T {
        return cache ?: getKeyClass(key, klass.java).also { newCache -> cache = newCache } ?: default
    }

    operator fun setValue(self: Any?, property: KProperty<*>, t: T?) {
        cache = t
        if (t == null) removeKey(key)
        else setKeyClass(key, t)
    }
}

data class Editor(val editor: SharedPreferences.Editor) {
    fun <T> setKeyRaw(path: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        if (value is Set<*>) editor.putStringSet(path, value as Set<String>)
        else when (value) {
            is Boolean -> editor.putBoolean(path, value)
            is Int -> editor.putInt(path, value)
            is String -> editor.putString(path, value)
            is Float -> editor.putFloat(path, value)
            is Long -> editor.putLong(path, value)
        }
    }

    fun apply() {
        editor.apply()
        System.gc()
    }
}

object DataStore {
    // keep original mapper name for compatibility
    val mapper: JsonMapper = internalMapper

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getPreferences(this)
    }

    fun getFolderName(folder: String, path: String): String {
        return "$folder/$path"
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
        return getSharedPrefs().all.keys.filter { it.startsWith(folder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun Context.containsKey(path: String): Boolean {
        return getSharedPrefs().contains(path)
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

    // ================================================================
    //           CACHE: repo JSON backup (so plugin list survives kill)
    // ================================================================
    private fun Context.cacheRepo(json: String) {
        try {
            File(filesDir, navigate_this).writeText(json)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun Context.restoreRepoCache(): String? {
        return try {
            val f = File(filesDir, navigate_this)
            if (f.exists()) f.readText() else null
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
    // ================================================================


    // ========================== SET =================================
    fun <T> Context.setKey(path: String, value: T) {
        try {
            val json = mapper.writeValueAsString(value)
            val clean = sanitizeJsonString(json) // Context.sanitizeJsonString via extension below

            getSharedPrefs().edit { putString(path, clean) }

            // cache repo JSON to internal file as fallback (heuristic: path contains "repo")
            if (path.contains("repo", true)) {
                cacheRepo(clean)
            }

        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        val full = getFolderName(folder, path)
        setKey(full, value)
    }

    // ========================== GET =================================
    fun <T> Context.getKey(path: String, valueType: Class<T>): T? {
        return try {
            val raw = getSharedPrefs().getString(path, null)

            // If prefs missing, try file cache fallback; otherwise restore hashes
            val finalJson = when {
                raw != null -> this.restoreRepoUrlsInternal(raw)
                path.contains("repo", true) -> this.restoreRepoCache()
                else -> null
            } ?: return null

            finalJson.toKotlinObject(valueType)
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    fun <T> String.toKotlinObject(valueType: Class<T>): T {
        return mapper.readValue(this, valueType)
    }

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        return try {
            // Delegate to non-inline version (safe to call private helpers)
            getKey(path, T::class.java) ?: defVal
        } catch (e: Exception) {
            logError(e)
            defVal
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