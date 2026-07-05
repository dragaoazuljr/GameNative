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

/**
 * ViewModel que delega todas as operações para ShaderEffectManagerHolder.
 * Elimina duplicação de instância de ShaderManager (que antes era criada aqui).
 */
class ShaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _shaders = MutableLiveData<List<ShaderEntry>>(emptyList())
    val shaders: LiveData<List<ShaderEntry>> = _shaders

    /**
     * Delega para o ShaderEffectManager singleton (já existente via holder).
     * Evita criar uma nova instância de ShaderManager.
     */
    private val effectManager: ShaderEffectManager
        get() = ShaderEffectManagerHolder.current
            ?: throw IllegalStateException("ShaderEffectManager not initialized. Set ShaderEffectManagerHolder.current from the host.")

    fun loadShaders() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                effectManager.loadShaders()
            }
            _shaders.value = loaded
        }
    }

    fun toggleFavorite(relativePath: String) {
        val current = _shaders.value ?: return
        val entry = current.find { it.relativePath == relativePath } ?: return
        val newFav = !entry.isFavorite

        viewModelScope.launch {
            val manager = effectManager.shaderManagerInstance

            manager.toggleFavorite(relativePath)

            _shaders.value = current.map { e ->
                if (e.relativePath == relativePath) e.copy(isFavorite = newFav) else e
            }
        }
    }

    fun toggleShaderEnabled(enabled: Boolean) {
        effectManager.onShaderEnabled(enabled)
    }

    fun getShaderEnabled(): Boolean {
        return effectManager.isShaderEnabled()
    }
}
