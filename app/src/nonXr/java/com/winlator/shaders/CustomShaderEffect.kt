package com.winlator.shaders

import android.opengl.GLES20
import com.winlator.renderer.GLRenderer
import com.winlator.renderer.effects.Effect
import com.winlator.renderer.material.ShaderMaterial

class CustomShaderEffect(
    private val shaderEntry: ShaderEntry,
    private val renderer: GLRenderer
) : Effect() {

    private val vertexShader = """
        attribute vec2 position;
        varying vec2 vUV;
        void main() {
            vUV = position;
            gl_Position = vec4(2.0 * position.x - 1.0, 2.0 * position.y - 1.0, 0.0, 1.0);
        }
    """.trimIndent()

    override fun createMaterial(): ShaderMaterial {
        return object : ShaderMaterial() {
            override fun getVertexShader(): String = vertexShader
            override fun getFragmentShader(): String = shaderEntry.path
        }
    }

    override fun onUse(material: ShaderMaterial, renderer: GLRenderer) {
        // Apply shader parameters as uniforms
        shaderEntry.meta?.params?.forEach { param ->
            material.setUniformFloat(param.name, param.initial)
        }
    }

    override fun destroy() {
        super.destroy()
    }
}
