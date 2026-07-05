package com.winlator.shaders

import android.opengl.GLES20
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Gerenciador de preview de shaders em tempo real.
 * Permite compilar e visualizar shaders sem persistir alterações.
 * Usa ShaderManager para preferências e carregamento.
 */
class ShaderPreviewManager(
    private val shaderManager: ShaderManager
) {

    companion object {
        private const val TAG = "ShaderPreviewManager"
        private const val PREF_PREVIEW_SHADER = "preview_shader_relative"

        val DEFAULT_VERTEX_SHADER = """
            attribute vec2 position;
            varying vec2 vUV;
            void main() {
                vUV = position;
                gl_Position = vec4(2.0 * position.x - 1.0, 2.0 * position.y - 1.0, 0.0, 1.0);
            }
        """.trimIndent()
    }

    data class PreviewState(
        val isActive: Boolean = false,
        val shaderPath: String? = null,
        val params: Map<String, Float> = emptyMap(),
        val errorMessage: String? = null
    )

    private val _previewState = MutableStateFlow(PreviewState())
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private var activeProgram: Int = -1
    private var previewShaderPath: String? = null

    private val shaderDir = File(ShaderLoader.DEFAULT_SHADER_DIR)

    /**
     * Inicia um preview de shader.
     * O preview não persiste — é apenas para visualização.
     */
    fun startPreview(shaderEntry: ShaderEntry, initialParams: Map<String, Float> = emptyMap()) {
        try {
            Log.d(TAG, "Starting preview for: ${shaderEntry.relativePath}")

            val shaderFile = File(shaderEntry.path)
            if (!shaderFile.exists()) {
                _previewState.value = PreviewState(
                    isActive = false,
                    errorMessage = "Shader file not found: ${shaderEntry.path}"
                )
                return
            }

            val content = shaderFile.readText()
            val injectedFrag = shaderManager.injectBaseUniforms(content)

            // Compile the shader
            val compiler = ShaderCompiler(DEFAULT_VERTEX_SHADER, injectedFrag)
            activeProgram = compiler.programId

            // Apply initial params
            if (initialParams.isNotEmpty()) {
                shaderManager.applyParamsToProgram(activeProgram, initialParams)
            }

            previewShaderPath = shaderEntry.relativePath

            _previewState.value = PreviewState(
                isActive = true,
                shaderPath = shaderEntry.relativePath,
                params = initialParams
            )
            Log.d(TAG, "Preview started: program $activeProgram")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
            _previewState.value = PreviewState(
                isActive = false,
                errorMessage = "Preview failed: ${e.message}"
            )
        }
    }

    /**
     * Update parameter values for the active preview.
     */
    fun updateParams(params: Map<String, Float>) {
        if (activeProgram <= 0) return
        try {
            shaderManager.applyParamsToProgram(activeProgram, params)
            _previewState.value = _previewState.value.copy(params = params)
            Log.d(TAG, "Updated preview params: ${params.keys}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating preview params", e)
        }
    }

    /**
     * Stops the current preview and frees resources.
     */
    fun stopPreview() {
        if (activeProgram > 0) {
            GLES20.glDeleteProgram(activeProgram)
            activeProgram = -1
        }
        previewShaderPath = null
        _previewState.value = PreviewState()
        Log.d(TAG, "Preview stopped")
    }

    /**
     * Check if a preview is currently active.
     */
    fun isPreviewActive(): Boolean {
        return _previewState.value.isActive && activeProgram > 0
    }

    /**
     * Get the currently active program ID for external rendering.
     */
    fun getActiveProgram(): Int {
        return activeProgram
    }

    /**
     * Get the current preview parameters.
     */
    fun getCurrentParams(): Map<String, Float> {
        return _previewState.value.params
    }

    /**
     * Destroy all resources.
     */
    fun destroy() {
        stopPreview()
    }
}
