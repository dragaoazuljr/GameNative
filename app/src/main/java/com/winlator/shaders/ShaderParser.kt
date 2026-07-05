package com.winlator.shaders

object ShaderParser {
    fun parseGlsl(content: String): ShaderMeta? {
        val params = mutableListOf<ShaderParam>()
        var hasTextureBinding = false

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#pragma parameter") || trimmed.startsWith("#pragma PARAMETER")) {
                val parts = trimmed.substringAfter("#pragma", "").trim().split("\\s+".toRegex())
                if (parts.size >= 6) {
                    try {
                        val param = ShaderParam(
                            name = parts[0],
                            label = parts[1],
                            min = parts[2].toFloat(),
                            max = parts[3].toFloat(),
                            step = parts[4].toFloat(),
                            initial = parts[5].toFloat()
                        )
                        params.add(param)
                    } catch (e: NumberFormatException) {
                        // Skip invalid parameter line
                    }
                }
            }
            if (trimmed.startsWith("#pragma binding") || trimmed.startsWith("#pragma BINDING")) {
                hasTextureBinding = true
            }
        }
        return if (params.isNotEmpty() || hasTextureBinding) {
            ShaderMeta(params, hasTextureBinding)
        } else null
    }
}
