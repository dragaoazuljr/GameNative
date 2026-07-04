package com.winlator.shaders

import java.io.File

class ShaderProgram(
    private val shaderEntry: ShaderEntry,
    private val userParams: Map<String, Float> = emptyMap()
) {
    private val shaderCompiler: ShaderCompiler
    private var _isCompiled: Boolean = false

    val isCompiled: Boolean get() = _isCompiled
    val programId: Int get() = shaderCompiler.programId
    val compilerRef: ShaderCompiler get() = shaderCompiler

    init {
        val content = File(shaderEntry.path).readText()
        val (vShader, fShader) = splitGlsl(content)
        shaderCompiler = ShaderCompiler(vShader, fShader)

        val mergedParams = buildParamMap(userParams, shaderEntry.meta?.params ?: emptyList())

        _isCompiled = shaderCompiler.compile()
        if (_isCompiled) {
            shaderCompiler.use()
            applyParams(shaderCompiler, mergedParams)
            shaderCompiler.unuse()
        }
    }

    private fun splitGlsl(content: String): Pair<String, String> {
        val vStart = content.indexOf("//@vertex")
        val fStart = content.indexOf("//@fragment")

        return if (vStart != -1 && fStart != -1) {
            val vEnd = content.indexOf("//@fragment", vStart)
            val fEnd = content.indexOf("//", fStart + 12)
            val fActualEnd = if (fEnd == -1) content.length else fEnd
            Pair(
                content.substring(vStart + 11, vEnd).trim(),
                content.substring(fStart + 12, fActualEnd).trim()
            )
        } else {
            Pair(DEFAULT_VERTEX_SHADER, content)
        }
    }

    private fun buildParamMap(userParams: Map<String, Float>, params: List<ShaderParam>): Map<String, Float> {
        return params.associate { param ->
            param.name to userParams.getOrElse(param.name) { param.initial }
        }
    }

    private fun applyParams(comp: ShaderCompiler, params: Map<String, Float>) {
        for ((name, value) in params) {
            comp.setUniformFloat(name, value)
        }
    }

    fun updateParams(newParams: Map<String, Float>) {
        val mergedParams = buildParamMap(newParams, shaderEntry.meta?.params ?: emptyList())
        shaderCompiler.use()
        applyParams(shaderCompiler, mergedParams)
        shaderCompiler.unuse()
    }

    fun cleanup() {
        shaderCompiler.destroy()
        _isCompiled = false
    }

    companion object {
        private val DEFAULT_VERTEX_SHADER: String = """
            attribute vec2 position;
            varying vec2 vUV;
            void main() {
                vUV = position;
                gl_Position = vec4(2.0 * position.x - 1.0, 2.0 * position.y - 1.0, 0.0, 1.0);
            }
        """.trimIndent()
    }
}
