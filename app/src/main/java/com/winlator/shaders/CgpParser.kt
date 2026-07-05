package com.winlator.shaders

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CgpParser {
    
    data class CgpPass(
        val name: String,
        val fragmentShader: String,
        val vertexShader: String,
        val parameters: List<ShaderParam>
    )
    
    data class CgpFile(
        val name: String,
        val description: String,
        val passes: List<CgpPass>
    )
    
    private val DEFAULT_VERTEX_SHADER = """
        attribute vec2 a_position;
        varying vec2 v_texCoord;
        void main() {
            v_texCoord = a_position * 0.5 + 0.5;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
    """.trimIndent()
    
    fun parseCgpFile(filePath: String): CgpFile? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.path.endsWith(".cgp")) return null
            
            val json = JSONObject(file.readText())
            
            val name = json.optString("name", file.nameWithoutExtension)
            val description = json.optString("description", "")
            
            val fragmentShaders = parseShaderFragments(json.optJSONObject("fragment_shader"))
            val vertexShaders = parseShaderFragments(json.optJSONObject("vertex_shader"))
            
            val passes = mutableListOf<CgpPass>()
            val passArray = json.optJSONArray("passes")
            if (passArray != null) {
                for (i in 0 until passArray.length()) {
                    val passObj = passArray.getJSONObject(i)
                    val passName = passObj.optString("name", "Pass $i")
                    
                    val fragSource = resolveShaderSource(
                        passObj.optString("fragment_shader", ""),
                        fragmentShaders
                    )
                    val vertSource = resolveShaderSource(
                        passObj.optString("vertex_shader", ""),
                        vertexShaders
                    )
                    
                    val params = parseCgpParameters(passObj.optJSONArray("parameters"))
                    
                    passes.add(CgpPass(
                        name = passName,
                        fragmentShader = fragSource,
                        vertexShader = vertSource,
                        parameters = params
                    ))
                }
            }
            
            CgpFile(name, description, passes)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseShaderFragments(jsonObj: JSONObject?): Map<String, String> {
        val shaders = mutableMapOf<String, String>()
        if (jsonObj == null) return shaders
        
        jsonObj.keys().forEachRemaining { name ->
            shaders[name] = jsonObj.getString(name)
        }
        return shaders
    }
    
    private fun resolveShaderSource(source: String, fragments: Map<String, String>): String {
        var resolved = source
        for ((name, content) in fragments) {
            resolved = resolved.replace("#include \"$name\"", content)
                .replace("#include '$name'", content)
        }
        return resolved
    }
    
    private fun parseCgpParameters(jsonArray: JSONArray?): List<ShaderParam> {
        val params = mutableListOf<ShaderParam>()
        if (jsonArray == null) return params
        
        for (i in 0 until jsonArray.length()) {
            try {
                val paramObj = jsonArray.getJSONObject(i)
                val name = paramObj.optString("name", "param$i")
                val label = paramObj.optString("label", name)
                val min = paramObj.optDouble("min", 0.0).toFloat()
                val max = paramObj.optDouble("max", 1.0).toFloat()
                val step = paramObj.optDouble("step", 0.01).toFloat()
                val initial = paramObj.optDouble("default", 0.5).toFloat()
                
                params.add(ShaderParam(name, label, min, max, step, initial))
            } catch (e: Exception) {
                // Skip malformed parameters
            }
        }
        
        return params
    }
    
    fun generateCgpFile(cgpFile: CgpFile, outputPath: String): Boolean {
        return try {
            val json = JSONObject()
            json.put("name", cgpFile.name)
            json.put("description", cgpFile.description)
            
            val passArray = JSONArray()
            for (pass in cgpFile.passes) {
                val passObj = JSONObject()
                passObj.put("name", pass.name)
                passObj.put("fragment_shader", pass.fragmentShader)
                passObj.put("vertex_shader", pass.vertexShader)
                
                if (pass.parameters.isNotEmpty()) {
                    val paramArray = JSONArray()
                    for (param in pass.parameters) {
                        val p = JSONObject()
                        p.put("name", param.name)
                        p.put("label", param.label)
                        p.put("min", param.min)
                        p.put("max", param.max)
                        p.put("step", param.step)
                        p.put("default", param.initial)
                        paramArray.put(p)
                    }
                    passObj.put("parameters", paramArray)
                }
                
                passArray.put(passObj)
            }
            json.put("passes", passArray)
            
            File(outputPath).writeText(json.toString(2))
            true
        } catch (e: Exception) {
            false
        }
    }
}
