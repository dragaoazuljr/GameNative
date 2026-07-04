package com.winlator.shaders

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix

class ShaderRendererIntegration(private val context: Context) {
    private var shaderManager: ShaderManager? = null
    private var activeShader: ShaderProgram? = null
    private var containerPackageName: String? = null

    fun setContainerPackageName(packageName: String) {
        containerPackageName = packageName
        shaderManager = ShaderManager(context)
        updateGameShader(packageName)
    }

    fun updateGameShader(packageName: String) {
        // TODO: Load shader for this game from preferences
        // 1. Check per-game preference
        // 2. Check global preference
        // 3. Apply shader
    }

    fun getCurrentShaderProgram(): ShaderProgram? = activeShader

    fun areShadersEnabled(): Boolean {
        return shaderManager?.shaderEnabled == true
    }

    fun applyShaderToFrame(compiler: ShaderCompiler) {
        compiler.use()
        compiler.setUniformMatrix4("MVP", identityMatrix4())
        compiler.setUniformVec4(
            "OutputSize",
            GLES20.glGetString(GLES20.GL_RENDERER)?.length?.toFloat() ?: 1920f,
            GLES20.glGetString(GLES20.GL_RENDERER)?.length?.toFloat() ?: 1080f,
            0f,
            0f
        )
        // draw fullscreen quad
        compiler.unuse()
    }

    private fun identityMatrix4(): FloatArray {
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }
}

class ShaderLifecycleManager {
    fun updateGameShader(packageName: String) {
        // TODO: Wire into container lifecycle
    }
}
