package com.winlator.shaders

import android.opengl.GLES20

/**
 * Wrapper do programa OpenGL ES compilado.
 * Encapsula o program ID e expõe métodos de uniformes.
 */
class ShaderProgram internal constructor(
    val compiler: ShaderCompiler,
    val entry: ShaderEntry,
) {
    val programId: Int = compiler.programId
    val isCompiled: Boolean = compiler.isCompiled

    fun use() {
        if (isCompiled) {
            GLES20.glUseProgram(programId)
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

    fun destroy() {
        if (isCompiled) {
            GLES20.glDeleteProgram(programId)
        }
    }
}
