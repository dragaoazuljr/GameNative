package com.winlator.shaders

import android.opengl.GLES20
import java.nio.IntBuffer
import com.winlator.renderer.material.ShaderMaterial

class ShaderCompiler(
    private val vertexShader: String,
    private val fragmentShader: String
) {
    private var _programId: Int = 0
    private var _isCompiled: Boolean = false
    private val uniformLocations = HashMap<String, Int>()

    val programId: Int get() = _programId
    val isCompiled: Boolean get() = _isCompiled

    fun compile(): Boolean {
        try {
            _programId = GLES20.glCreateProgram()
            val compiled = IntArray(1)

            val vShader = injectBaseUniforms(vertexShader)
            val fShader = injectBaseUniforms(fragmentShader)

            val vsId = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
            GLES20.glShaderSource(vsId, vShader)
            GLES20.glCompileShader(vsId)
            GLES20.glGetShaderiv(vsId, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                throw RuntimeException("Vertex shader failed: " + GLES20.glGetShaderInfoLog(vsId))
            }
            GLES20.glAttachShader(_programId, vsId)

            val fsId = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
            GLES20.glShaderSource(fsId, fShader)
            GLES20.glCompileShader(fsId)
            GLES20.glGetShaderiv(fsId, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                throw RuntimeException("Fragment shader failed: " + GLES20.glGetShaderInfoLog(fsId))
            }
            GLES20.glAttachShader(_programId, fsId)

            GLES20.glLinkProgram(_programId)
            GLES20.glGetProgramiv(_programId, GLES20.GL_LINK_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                throw RuntimeException("Program link failed: " + GLES20.glGetProgramInfoLog(_programId))
            }

            GLES20.glDeleteShader(vsId)
            GLES20.glDeleteShader(fsId)

            cacheUniformLocations()
            _isCompiled = true
            return true
        } catch (e: Exception) {
            GLES20.glDeleteProgram(_programId)
            _programId = 0
            return false
        }
    }

    private fun injectBaseUniforms(shader: String): String {
        return if (!shader.contains("uniform mat4 MVP")) {
            "uniform mat4 MVP;\nuniform vec4 OutputSize;\n" + shader
        } else shader
    }

    private fun cacheUniformLocations() {
        val count = IntArray(1)
        GLES20.glGetProgramiv(_programId, GLES20.GL_ACTIVE_UNIFORMS, count, 0)
        for (i in 0 until count[0]) {
            val sizeBuf = IntBuffer.wrap(IntArray(1))
            val typeBuf = IntBuffer.wrap(IntArray(1))
            val name = GLES20.glGetActiveUniform(_programId, i, sizeBuf, typeBuf) ?: continue
            val loc = GLES20.glGetUniformLocation(_programId, name)
            if (loc != -1) uniformLocations[name] = loc
        }
    }

    fun use() {
        if (_isCompiled) GLES20.glUseProgram(_programId)
    }

    fun unuse() {
        GLES20.glUseProgram(0)
    }

    fun setUniformFloat(name: String, value: Float) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform1f(loc, value)
    }

    fun setUniformVec2(name: String, x: Float, y: Float) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform2f(loc, x, y)
    }

    fun setUniformVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform4f(loc, x, y, z, w)
    }

    fun setUniformMatrix4(name: String, mvp: FloatArray) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniformMatrix4fv(loc, 1, false, mvp, 0)
    }

    fun setUniformInt(name: String, value: Int) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform1i(loc, value)
    }

    fun destroy() {
        if (_programId != 0) GLES20.glDeleteProgram(_programId)
        _programId = 0
        _isCompiled = false
    }
}
