package com.winlator.shaders

import android.opengl.GLES20
import kotlin.math.roundToInt

class ShaderCompiler(private val vertexShaderSrc: String, private val fragmentShaderSrc: String) {
    var programId = -1
    val isCompiled: Boolean
        get() = programId != -1

    init {
        compile()
    }

    private fun compile() {
        val vertexId = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc)
        val fragmentId = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc)

        if (vertexId == -1 || fragmentId == -1) return

        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertexId)
        GLES20.glAttachShader(programId, fragmentId)
        GLES20.glLinkProgram(programId)

        // Clean up
        GLES20.glDeleteShader(vertexId)
        GLES20.glDeleteShader(fragmentId)

        if (!isLinked) {
            programId = -1
        }
    }

    private val isLinked: Boolean
        get() {
            val results = IntArray(1)
            GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, results, 0)
            return results[0] == GLES20.GL_TRUE
        }

    fun use() {
        if (programId != -1) {
            GLES20.glUseProgram(programId)
        }
    }

    fun setUniform(name: String, value: Float) {
        if (!isCompiled) return
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc != -1) {
            GLES20.glUniform1f(loc, value)
        }
    }

    fun setUniform(name: String, x: Float, y: Float) {
        if (!isCompiled) return
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc != -1) {
            GLES20.glUniform2f(loc, x, y)
        }
    }
    
    fun setUniform(name: String, x: Float, y: Float, z: Float, w: Float) {
        if (!isCompiled) return
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc != -1) {
            GLES20.glUniform4f(loc, x, y, z, w)
        }
    }

    fun setUniform1i(name: String, value: Int) {
        if (!isCompiled) return
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc != -1) {
            GLES20.glUniform1i(loc, value)
        }
    }

    fun setUniformMatrix4f(name: String, matrix: FloatArray) {
        if (!isCompiled) return
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc != -1) {
            GLES20.glUniformMatrix4fv(loc, 1, false, matrix, 0)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)

        val result = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, result, 0)
        if (result[0] == GLES20.GL_FALSE) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw RuntimeException("Shader compilation failed: $log")
        }
        return id
    }
}
