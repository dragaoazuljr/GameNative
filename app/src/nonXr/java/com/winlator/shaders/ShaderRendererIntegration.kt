package com.winlator.shaders

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix

/**
 * Stub kept for API compatibility. Use [ShaderEffectManager] directly instead.
 * This class will be removed once all call sites are updated.
 */
@Deprecated("Use ShaderEffectManager directly", ReplaceWith("ShaderEffectManager"))
class ShaderRendererIntegration(private val context: Context) {
    fun setContainerPackageName(packageName: String) {
        // Intentionally empty — use ShaderEffectManager.setContainer()
    }

    fun getCurrentShaderProgram(): Any? = null

    fun areShadersEnabled(): Boolean = false

    fun applyShaderToFrame(compiler: ShaderCompiler) {
        compiler.unuse()
    }

    private fun identityMatrix4(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}

/**
 * Stub kept for API compatibility. Use ShaderEffectManager.setContainer() directly.
 * This class will be removed once all call sites are updated.
 */
@Deprecated("Use ShaderEffectManager directly", ReplaceWith("ShaderEffectManager"))
class ShaderLifecycleManager {
    fun updateGameShader(packageName: String) {
        // Intentionally empty — use ShaderEffectManager.setContainer()
    }
}

