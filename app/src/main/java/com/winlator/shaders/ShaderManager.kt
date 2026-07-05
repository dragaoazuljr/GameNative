package com.winlator.shaders

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class ShaderManager(private val context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("shader_preferences", Context.MODE_PRIVATE)

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

    // ─── Methods for ShaderPreviewManager ───

    /**
     * Injects base uniforms (MVP + OutputSize) into fragment shader source if not already present.
     * Called by ShaderPreviewManager for standalone shader compilation.
     */
    fun injectBaseUniforms(fragmentShader: String): String {
        return if (!fragmentShader.contains("uniform mat4 MVP")) {
            "uniform mat4 MVP;\nuniform vec4 OutputSize;\n" + fragmentShader
        } else {
            fragmentShader
        }
    }

    /**
     * Applies parameter values to a compiled shader program.
     * Called by ShaderPreviewManager to update uniforms at runtime.
     */
    fun applyParamsToProgram(programId: Int, params: Map<String, Float>) {
        if (programId <= 0) return
        android.opengl.GLES20.glUseProgram(programId)
        for ((name, value) in params.entries) {
            val loc = android.opengl.GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) {
                android.opengl.GLES20.glUniform1f(loc, value)
            }
        }
        // Restore previous program so the caller's state is preserved
        if (android.opengl.GLES20.glGetError() != android.opengl.GLES20.GL_NO_ERROR) {
            android.util.Log.e("ShaderManager", "Error applying params to program $programId")
        }
    }
}
