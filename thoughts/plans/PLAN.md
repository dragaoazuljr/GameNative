# Shader Reimplementation - Fase 1: Integração de Shaders Custom

## Objetivo
Integrar shaders GLSL custom carregados do disco no pipeline de renderização do game-native (Winlator fork) através do EffectComposer.

## Status: ✅ CONCLUÍDO

## Arquivos Implementados

### Camada de Dados
- `app/src/nonXr/java/com/winlator/shaders/ShaderEntry.kt` - Data classes (ShaderEntry, ShaderMeta, ShaderParam)
- `app/src/nonXr/java/com/winlator/shaders/ShaderLoader.kt` - Carrega .glsl/.cgp recursivamente de /sdcard/Winlator/shaders/
- `app/src/nonXr/java/com/winlator/shaders/ShaderParser.kt` - Parse de #pragma parameter e #pragma binding

### Compilação e Renderização
- `app/src/nonXr/java/com/winlator/shaders/ShaderCompiler.kt` - Compila GLSL, injeta uniforms base (MVP, OutputSize), cache de uniform locations
- `app/src/nonXr/java/com/winlator/renderer/effects/CustomShaderEffect.kt` - Effect que aplica shader custom no pipeline EffectComposer

### UI
- `app/src/nonXr/java/com/winlator/shaders/ShaderViewModel.kt` - ViewModel com loadShaders, toggleFavorite, toggleShaderEnabled
- `app/src/nonXr/java/com/winlator/shaders/ShaderListAdapter.kt` - RecyclerView adapter com nome, parâmetro, botão favorite
- `app/src/nonXr/java/com/winlator/shaders/ShaderSelectorDialog.kt` - BottomSheet orchestrator com busca e toggle

### Layouts e Recursos
- `app/src/main/res/layout/fragment_shader_selector.xml` - BottomSheet com switch, search, RecyclerView
- `app/src/main/res/layout/item_shader_entry.xml` - Item: nome, parâmetro, favorito
- `app/src/main/res/values/shader_strings.xml` - 13 strings

## Detalhes Técnicos

### ShaderCompiler
- Injeta uniforms base (MVP, OutputSize) se não existirem no shader
- Cache de uniform locations para performance
- Métodos setUniformFloat, setUniformVec2, setUniformVec4, setUniformMatrix4, setUniformInt
- Gerenciamento de lifecycle (compile/destroy)

### CustomShaderEffect
- Extende Effect.java existente para integração com pipeline ping-pong
- Compila shader GLSL sob demanda
- Aplica parâmetros como uniforms
- Integra com ShaderMaterial para vertex/fragment shader

### ShaderViewModel
- Carrega shaders do disco via ShaderLoader
- Gerencia favoritos (persistidos em /sdcard/Winlator/shaders/.favorites)
- Estado de enable/disable (persistido em /sdcard/Winlator/shaders/.enabled)
- Filtro por busca textual

### ShaderSelectorDialog
- BottomSheet com peekHeight de 600dp
- RecyclerView com LazyList
- Campo de busca com filter
- Switch para enable/disable global
- Callbacks para seleção e toggle de favorito

## Próximos Passos (Fase 2)
1. Integrar ShaderSelectorDialog no menu de settings
2. Aplicar shader selecionado no EffectComposer
3. Persistir shader ativo por container
4. Testar em dispositivo

## Build
```
BUILD SUCCESSFUL in 17s
68 actionable tasks: 6 executed, 62 up-to-date
```

APK: app/build/outputs/apk/modern/debug/app-modern-debug.apk (~222MB)
