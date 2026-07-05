package com.winlator.shaders

import android.content.Context
import com.winlator.renderer.GLRenderer
import java.io.File

class ShaderEffectManager(
    private val renderer: GLRenderer,
    private val context: Context
) {
    private var shaderManager: ShaderManager? = null
    private var currentPackage: String? = null
    private var activeEffect: CustomShaderEffect? = null
    private var loadedEntries: List<ShaderEntry>? = null

    private val shaderDir = File(ShaderLoader.DEFAULT_SHADER_DIR)

    fun setContainer(packageName: String) {
        currentPackage = packageName
        applyShaderForContainer()
    }

    private fun applyShaderForContainer() {
        val manager = getShaderManager()
        if (!manager.shaderEnabled) {
            clearEffect()
            return
        }

        val pkg = currentPackage ?: return
        val shaderPath: String = manager.getShaderForGame(pkg)
            ?: manager.getGlobalShader()
            ?: return

        val shaderEntry = findShaderEntry(shaderPath) ?: return
        applyEffect(shaderEntry)
    }

    private fun getShaderManager(): ShaderManager {
        if (shaderManager == null) {
            shaderManager = ShaderManager(context)
        }
        return shaderManager!!
    }

    fun loadShaders(): List<ShaderEntry> {
        val entries = ShaderLoader.loadShaders(shaderDir)
        val manager = getShaderManager()
        loadedEntries = entries.map { entry ->
            entry.copy(isFavorite = manager.isFavorite(entry.relativePath))
        }.sortedBy { it.name }
        return loadedEntries!!
    }

    private fun findShaderEntry(path: String): ShaderEntry? {
        val entries = loadedEntries ?: loadShaders()
        return entries.find { it.relativePath == path }
    }

    private fun applyEffect(entry: ShaderEntry) {
        activeEffect?.destroy()
        activeEffect = CustomShaderEffect(entry, renderer)
        renderer.effectComposer.setEffects(listOf(activeEffect))
    }

    private fun clearEffect() {
        activeEffect?.destroy()
        activeEffect = null
        renderer.effectComposer.clearEffects()
    }

    fun onShaderSelected(entry: ShaderEntry) {
        val pkg = currentPackage ?: return
        getShaderManager().setShaderForGame(pkg, entry.relativePath)
        getShaderManager().setGlobalShader(entry.relativePath)
        applyEffect(entry)
    }

    fun onShaderEnabled(enabled: Boolean) {
        getShaderManager().shaderEnabled = enabled
        if (!enabled) clearEffect()
    }

    fun isShaderEnabled(): Boolean {
        return getShaderManager().shaderEnabled
    }

    fun getCurrentPackage(): String? = currentPackage

    fun destroy() {
        clearEffect()
        shaderManager = null
    }
}

