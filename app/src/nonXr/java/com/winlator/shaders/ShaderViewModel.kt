package com.winlator.shaders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _shaders = MutableLiveData<List<ShaderEntry>>(emptyList())
    val shaders: LiveData<List<ShaderEntry>> = _shaders

    private val shaderDir = File("/sdcard/Winlator/shaders/")
    private val favoritesFile = File("/sdcard/Winlator/shaders/.favorites")

    fun loadShaders() {
        viewModelScope.launch {
            val favorites = loadFavorites()
            val loaded = withContext(Dispatchers.IO) {
                ShaderLoader.loadShaders(shaderDir)
            }
            _shaders.value = loaded.map { entry ->
                entry.copy(isFavorite = favorites.contains(entry.relativePath))
            }.sortedBy { it.name }
        }
    }

    fun toggleFavorite(relativePath: String) {
        val current = _shaders.value ?: return
        val entry = current.find { it.relativePath == relativePath } ?: return
        val newFav = !entry.isFavorite

        viewModelScope.launch {
            if (newFav) {
                saveFavorite(relativePath)
            } else {
                removeFavorite(relativePath)
            }

            _shaders.value = current.map { e ->
                if (e.relativePath == relativePath) e.copy(isFavorite = newFav) else e
            }
        }
    }

    fun toggleShaderEnabled(enabled: Boolean) {
        val pref = getApplication<Application>().getSharedPreferences("shader_prefs", 0)
        pref.edit().putBoolean("shader_enabled", enabled).apply()
    }

    fun getShaderEnabled(): Boolean {
        val pref = getApplication<Application>().getSharedPreferences("shader_prefs", 0)
        return pref.getBoolean("shader_enabled", false)
    }

    private suspend fun loadFavorites(): List<String> = withContext(Dispatchers.IO) {
        if (!favoritesFile.exists()) return@withContext emptyList()
        favoritesFile.readLines()
    }

    private suspend fun saveFavorite(path: String) = withContext(Dispatchers.IO) {
        val lines = loadFavorites()
        if (!lines.contains(path)) {
            val mutableLines = lines.toMutableList()
            mutableLines.add(path)
            favoritesFile.writeText(mutableLines.joinToString("\n"))
        }
    }

    private suspend fun removeFavorite(path: String) = withContext(Dispatchers.IO) {
        val lines = loadFavorites()
        if (lines.contains(path)) {
            val mutableLines = lines.toMutableList()
            mutableLines.remove(path)
            if (mutableLines.isEmpty()) {
                favoritesFile.delete()
            } else {
                favoritesFile.writeText(mutableLines.joinToString("\n"))
            }
        }
    }
}
