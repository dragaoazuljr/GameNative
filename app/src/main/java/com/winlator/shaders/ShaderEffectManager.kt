package com.winlator.shaders

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.winlator.renderer.GLRenderer
import java.io.File

/**
 * ShaderEffectManager gerencia o ciclo de vida do efeito de shader customizado
 * e o conecta ao EffectComposer existente. Suporta shaders GLSL simples e .cgp multi-pass.
 *
 * Responsabilidades:
 * - Carrega shaders do disco
 * - Aplica shader ao EffectComposer quando selecionado
 * - Remove shader do EffectComposer quando desabilitado
 * - Responde a mudanças de container (packageName)
 * - Suporta shaders .cgp com multi-pass (FBO ping-pong)
 * - Registra-se automaticamente em ShaderEffectManagerHolder para uso pelo dialog
 */
class ShaderEffectManager(
    private val renderer: GLRenderer,
    private val context: Context
) {
    private var shaderManager: ShaderManager? = null
    private var currentPackage: String? = null
    private var activeEffect: CustomShaderEffect? = null
    private var activeCgpEffect: MultiPassShaderEffect? = null
    private var loadedEntries: List<ShaderEntry>? = null

    private val shaderDir = File(ShaderLoader.DEFAULT_SHADER_DIR)
    private val logTag = "ShaderEffectManager"

    init {
        // Register self in static holder for ShaderSelectorDialog
        ShaderEffectManagerHolder.current = this
        Log.d(logTag, "ShaderEffectManager initialized and registered in holder")
    }

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
        
        if (shaderPath.endsWith(".cgp", ignoreCase = true)) {
            applyCgpEffect(shaderEntry)
        } else {
            applyEffect(shaderEntry)
        }
    }

    private fun applyCgpEffect(entry: ShaderEntry) {
        activeEffect?.destroy()
        activeEffect = null
        
        val cgpFile = CgpParser.parseCgpFile(entry.path) ?: return
        activeCgpEffect = MultiPassShaderEffect(cgpFile, renderer)
        
        Log.d(logTag, "Applied CGP multi-pass shader: ${cgpFile.name}")
    }

    private fun applyEffect(entry: ShaderEntry) {
        activeEffect?.destroy()
        activeEffect = CustomShaderEffect(entry, renderer)
        renderer.effectComposer.setEffects(listOf(activeEffect))
    }

    private fun clearEffect() {
        activeEffect?.destroy()
        activeEffect = null
        activeCgpEffect?.destroy()
        activeCgpEffect = null
        renderer.effectComposer.clearEffects()
    }

    fun onShaderSelected(entry: ShaderEntry) {
        val pkg = currentPackage ?: return
        getShaderManager().setShaderForGame(pkg, entry.relativePath)
        getShaderManager().setGlobalShader(entry.relativePath)
        
        if (entry.relativePath.endsWith(".cgp", ignoreCase = true)) {
            applyCgpEffect(entry)
        } else {
            applyEffect(entry)
        }
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
        ShaderEffectManagerHolder.current = null
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
}
