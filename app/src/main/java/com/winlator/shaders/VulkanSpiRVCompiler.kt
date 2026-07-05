package com.winlator.shaders

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object VulkanSpiRVCompiler {
    
    private const val TAG = "VulkanSpiRVCompiler"
    
    data class SpiRVShader(
        val bytecode: List<Int>,
        val entryPoint: String,
        val reflection: ShaderReflection = ShaderReflection()
    )
    
    data class ShaderReflection(
        val uniforms: Map<String, UniformInfo> = emptyMap(),
        val textures: List<TextureBinding> = emptyList(),
        val samplers: List<SamplerBinding> = emptyList()
    )
    
    data class UniformInfo(
        val name: String,
        val type: String,
        val location: Int
    )
    
    data class TextureBinding(
        val name: String,
        val binding: Int,
        val sampleCount: Int
    )
    
    data class SamplerBinding(
        val name: String,
        val binding: Int
    )
    
    fun compileGlslToSpiRV(
        vertexShader: String?,
        fragmentShader: String,
        entryPoint: String = "main"
    ): Result<SpiRVShader> {
        return try {
            validateGlsl(vertexShader, fragmentShader)
            
            val stubBytecode = generateStubSpiRV(fragmentShader)
            val reflection = parseShaderReflection(fragmentShader)
            
            Result.success(SpiRVShader(
                bytecode = stubBytecode,
                entryPoint = entryPoint,
                reflection = reflection
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun compileFileToSpiRV(glslFile: File, spiRVOutputFile: File): Result<Unit> {
        return try {
            val content = glslFile.readText()
            val isFragment = glslFile.nameWithoutExtension.contains("frag", ignoreCase = true) ||
                           glslFile.extension == "frag" ||
                           glslFile.extension == "glsl"
            
            val vertexShader = if (!isFragment) content else null
            val fragmentShader = if (isFragment) content else null
            
            val result = compileGlslToSpiRV(vertexShader, fragmentShader ?: content)
            
            result.onSuccess { spiRV ->
                val buffer = ByteBuffer.allocate(spiRV.bytecode.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (word in spiRV.bytecode) {
                    buffer.putInt(word)
                }
                spiRVOutputFile.writeBytes(buffer.array())
            }
            
            result.map { Unit }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun loadSpiRV(spiRVFile: File): Result<SpiRVShader> {
        return try {
            val bytes = spiRVFile.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val bytecode = mutableListOf<Int>()
            while (buffer.hasRemaining()) {
                bytecode.add(buffer.int)
            }
            
            Result.success(SpiRVShader(
                bytecode = bytecode,
                entryPoint = "main"
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun generateStubSpiRV(shaderSource: String): List<Int> {
        val bytecode = mutableListOf(
            0x07230203.toInt(),
            0x00010000.toInt(),
            0x00000000.toInt(),
            0x00000000.toInt(),
            0x00000000.toInt()
        )
        
        if (shaderSource.contains("texture")) {
            bytecode.add(0x0B000000.toInt())
        }
        
        return bytecode
    }
    
    private fun validateGlsl(vertexShader: String?, fragmentShader: String?) {
        val shaders = listOfNotNull(vertexShader, fragmentShader)
        for (shader in shaders) {
            if (!shader.contains("void main()") && !shader.contains("void main(void)")) {
                throw IllegalArgumentException("Shader deve conter função main()")
            }
        }
    }
    
    private fun parseShaderReflection(shader: String): ShaderReflection {
        val uniforms = mutableMapOf<String, UniformInfo>()
        
        val uniformRegex = """uniform\s+(\w+)\s+(\w+)\s*;""".toRegex()
        uniformRegex.findAll(shader).forEach { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            uniforms[name] = UniformInfo(name, type, uniforms.size)
        }
        
        return ShaderReflection(uniforms)
    }
}
