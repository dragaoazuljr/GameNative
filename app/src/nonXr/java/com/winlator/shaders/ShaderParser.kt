package com.winlator.shaders

object ShaderParser {
    fun parseGlsl(content: String): ShaderMeta? {
        val params = mutableListOf<ShaderParam>()
        val bindingRegex = """#pragma\s+binding\s+(\w+)\s+\w+\s+(\d+)""".toRegex()
        
        val paramRegex = """#pragma\s+parameter\s+(\w+)\s+"([^"]+)"\s+([0-9.eE+-]+)\s+([0-9.eE+-]+)\s+([0-9.eE+-]+)\s+([0-9.eE+-]+)""".toRegex()
        paramRegex.findAll(content).forEach { m ->
            params.add(ShaderParam(
                name = m.groupValues[1],
                label = m.groupValues[2],
                min = m.groupValues[3].toFloat(),
                max = m.groupValues[4].toFloat(),
                step = m.groupValues[5].toFloat(),
                initial = m.groupValues[6].toFloat()
            ))
        }
        
        val hasBinding = bindingRegex.containsMatchIn(content)
        
        return if (params.isNotEmpty()) ShaderMeta(params, hasBinding) else null
    }
}
