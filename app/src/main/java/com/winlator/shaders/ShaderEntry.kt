package com.winlator.shaders

data class ShaderEntry(
    val path: String,
    val relativePath: String,
    val name: String,
    val isFavorite: Boolean = false,
    val lastUsedFor: String? = null,
    val meta: ShaderMeta? = null
)

data class ShaderMeta(
    val params: List<ShaderParam> = emptyList(),
    val hasTextureBinding: Boolean = false
)

data class ShaderParam(
    val name: String,
    val label: String,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0.01f,
    val initial: Float = 0f
)
