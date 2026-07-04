package com.winlator.shaders

class ShaderPreviewManager {
    private var previewShader: ShaderProgram? = null
    private var previewParams: Map<String, Float> = emptyMap()

    fun startPreview(shader: ShaderEntry, params: Map<String, Float> = emptyMap()) {
        // TODO: Load shader into offscreen FBO for preview
        // 1. Create FBO (width=320, height=240)
        // 2. Compile shader
        // 3. Draw test pattern (color bars or gradient)
        // 4. Render to preview texture
        previewShader = ShaderProgram(shader, params)
        previewParams = params
    }

    fun updatePreviewParams(newParams: Map<String, Float>) {
        previewParams = newParams
        previewShader?.updateParams(newParams)
    }

    fun stopPreview() {
        previewShader?.cleanup()
        previewShader = null
        previewParams = emptyMap()
    }

    fun isPreviewActive(): Boolean = previewShader != null
}
