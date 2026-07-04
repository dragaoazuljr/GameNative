package com.winlator.shaders

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class ShaderManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shader_preferences", Context.MODE_PRIVATE)

    var shaderEnabled: Boolean
        get() = prefs.getBoolean("shaderEnabled", false)
        set(value) = prefs.edit().putBoolean("shaderEnabled", value).apply()

    fun getShaderForGame(packageName: String): String? {
        return prefs.getString("shader_pkg_$packageName", null)
    }

    fun setShaderForGame(packageName: String, shaderPath: String?) {
        prefs.edit().putString("shader_pkg_$packageName", shaderPath).apply()
    }

    fun getGlobalShader(): String? {
        return prefs.getString("global_shader", null)
    }

    fun setGlobalShader(path: String?) {
        prefs.edit().putString("global_shader", path).apply()
    }

    fun isFavorite(path: String): Boolean {
        val favs = prefs.getStringSet("shader_favorites", emptySet())
        return favs?.contains(path) == true
    }

    fun toggleFavorite(path: String) {
        val current = prefs.getStringSet("shader_favorites", emptySet()) ?: emptySet()
        val updated = if (current.contains(path)) current - path else current + path
        prefs.edit().putStringSet("shader_favorites", updated).apply()
    }

    fun getLastUsedFor(path: String): String? {
        return prefs.getString("last_used_$path", null)
    }

    fun setLastUsedFor(path: String, packageName: String) {
        prefs.edit().putString("last_used_$path", packageName).apply()
    }

    fun getUserParam(shaderPath: String, paramName: String): Float {
        return prefs.getFloat("user_param_${shaderPath}.$paramName", 0f)
    }

    fun setUserParam(shaderPath: String, paramName: String, value: Float) {
        prefs.edit().putFloat("user_param_${shaderPath}.$paramName", value).apply()
    }

    fun loadShaders(dir: File): List<ShaderEntry> {
        return ShaderLoader.loadShaders(dir).map { entry ->
            entry.copy(isFavorite = isFavorite(entry.relativePath))
        }
    }
}
