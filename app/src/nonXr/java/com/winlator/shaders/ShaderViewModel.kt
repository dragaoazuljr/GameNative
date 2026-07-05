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

    private val shaderDir = File(ShaderLoader.DEFAULT_SHADER_DIR)
    private val manager: ShaderManager = ShaderManager(application)

    fun loadShaders() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                manager.loadShaders(shaderDir)
            }
            _shaders.value = loaded
        }
    }

    fun toggleFavorite(relativePath: String) {
        val current = _shaders.value ?: return
        val entry = current.find { it.relativePath == relativePath } ?: return
        val newFav = !entry.isFavorite

        viewModelScope.launch {
            manager.toggleFavorite(relativePath)

            _shaders.value = current.map { e ->
                if (e.relativePath == relativePath) e.copy(isFavorite = newFav) else e
            }
        }
    }

    fun toggleShaderEnabled(enabled: Boolean) {
        manager.shaderEnabled = enabled
    }

    fun getShaderEnabled(): Boolean {
        return manager.shaderEnabled
    }
}

