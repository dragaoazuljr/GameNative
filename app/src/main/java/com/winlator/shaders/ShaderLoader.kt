package com.winlator.shaders

import android.content.Context
import java.io.File

object ShaderLoader {
    const val DEFAULT_SHADER_DIR = "/sdcard/Winlator/shaders/"

    fun getShaderDir(context: Context?): File {
        val dir = context?.getExternalFilesDir("shaders")
        return dir ?: File(DEFAULT_SHADER_DIR)
    }

    fun loadShaders(dir: File): List<ShaderEntry> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.walkTopDown()
            .filter { it.isFile && (it.extension == "glsl" || it.extension == "cg" || it.extension == "frag" || it.extension == "vert") }
            .map { file ->
                val relative = file.relativeTo(dir).path
                val name = file.nameWithoutExtension
                val content = file.readText()
                val meta = ShaderParser.parseGlsl(content)

                ShaderEntry(
                    path = file.absolutePath,
                    relativePath = relative,
                    name = meta?.params?.firstOrNull()?.label ?: name,
                    meta = meta
                )
            }
            .toList()
            .sortedBy { it.name }
    }
}
