package com.winlator.shaders

import org.json.JSONArray
import org.json.JSONObject

data class CgpPass(
    val vertexShader: String,
    val fragmentShader: String,
    val uniforms: Map<String, Any> = emptyMap(),
    val parameters: List<ShaderParam> = emptyList()
)

object CgpParser {
    fun parseCgp(content: String): List<CgpPass>? {
        return try {
            val json = JSONObject(content)
            val passes = json.optJSONArray("passes") ?: return null

            val result = mutableListOf<CgpPass>()
            for (i in 0 until passes.length()) {
                val pass = passes.getJSONObject(i)
                val vShader = pass.optString("vertex", "")
                val fShader = pass.optString("fragment", "")

                val params = mutableListOf<ShaderParam>()
                val paramsArray = pass.optJSONArray("parameters")
                if (paramsArray != null) {
                    for (j in 0 until paramsArray.length()) {
                        val p = paramsArray.getJSONObject(j)
                        params.add(ShaderParam(
                            name = p.getString("name"),
                            label = p.optString("label", p.getString("name")),
                            min = p.optDouble("min", 0.0).toFloat(),
                            max = p.optDouble("max", 1.0).toFloat(),
                            step = p.optDouble("step", 0.01).toFloat(),
                            initial = p.optDouble("initial", 0.0).toFloat()
                        ))
                    }
                }

                val uniformsMap = HashMap<String, Any>()
                val uniformsJson = pass.optJSONObject("uniforms")
                if (uniformsJson != null) {
                    val keys = uniformsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        uniformsMap[key] = uniformsJson.opt(key) as Any
                    }
                }

                result.add(CgpPass(vShader, fShader, uniformsMap, params))
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
