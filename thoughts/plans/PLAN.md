# GameNative Shader System - Project Plan

## Status: Phase 4 Complete ✅ | Phase 5 Next

---

## Phase 1: Data Layer + Basic Shader Effect
**Status:** ✅ Concluída
**Data:** 2026-07-04
**Build:** BUILD SUCCESSFUL in 17s

### Arquivos Implementados
- `ShaderEntry.kt` — Data classes (ShaderEntry, ShaderMeta, ShaderParam)
- `ShaderLoader.kt` — Carrega .glsl/.cgp de /sdcard/Winlator/shaders/
- `ShaderParser.kt` — Parse de #pragma parameter e #pragma binding
- `ShaderCompiler.kt` — Compila GLSL, injeta uniforms base, cache
- `CustomShaderEffect.kt` — Effect para pipeline EffectComposer
- `ShaderViewModel.kt` — loadShaders, toggleFavorite, toggleShaderEnabled
- `ShaderListAdapter.kt` — RecyclerView adapter
- `ShaderSelectorDialog.kt` — BottomSheet orchestrator

### Build
```
BUILD SUCCESSFUL in 17s
68 actionable tasks: 6 executed, 62 up-to-date
APK: ~222MB
```

---

## Phase 2: Fix CustomShaderEffect + ShaderEffectManager + Cleanup
**Status:** ✅ Concluída
**Data:** 2026-07-05
**Build:** BUILD SUCCESSFUL in 10s

### O que foi feito
1. **CustomShaderEffect.kt fix** — getFragmentShader() lê conteúdo via File.readText()
2. **ShaderEffectManager.kt** — novo, gerencia lifecycle + EffectComposer
3. **GLRenderer.java** — campo shaderEffectManager + getters/setters
4. **ShaderSelectorDialog.kt** — integrado com ShaderEffectManager via ShaderSelectorHost
5. **ShaderViewModel.kt** — unificado com ShaderManager
6. **ShaderRendererIntegration.kt** — marcado @Deprecated, stubs removidos

### Arquivos Modificados
| Arquivo | Tipo |
|---------|------|
| CustomShaderEffect.kt | Fix bug + lazy GLSL |
| ShaderEffectManager.kt | Novo — lifecycle manager |
| GLRenderer.java | Novo campo + getters/setters |
| ShaderSelectorDialog.kt | Integração effectManager |
| ShaderViewModel.kt | Unificação com ShaderManager |
| ShaderRendererIntegration.kt | Deprecated stub |

---

## Phase 3: Multi-pass FBO Ping-Pong + UI Integration
**Status:** ✅ Concluída
**Data:** 2026-07-05
**Build:** BUILD SUCCESSFUL (kotlin compilation OK)

### O que foi feito
1. **MultiPassShaderEffect.kt** — FBO ping-pong real, multi-pass .cgp
2. **ShaderManager.kt** — injectBaseUniforms() + applyParamsToProgram()
3. **ShaderParser.kt** — escape sequence corrigido
4. **ShaderPreviewManager.kt** — const val → val
5. **ShaderSelectorDialog.kt** — static holder pattern
6. **Migração** — de app/src/nonXr/ para app/src/main/

### Pendentes (Phase 3)
- Slider UI para parâmetros detectados
- Preview offscreen via MultiPassShaderEffect.execute()
- Reutilização de programas compilados (cache)

---

## Phase 4: QuickMenu Integration + Custom Shader Button
**Status:** ✅ Concluída
**Data:** 2026-07-05
**Build:** BUILD SUCCESSFUL in 19s

### O que foi feito
1. **ShaderEffectManager.kt** — reescrito:
   - `shaderManagerInstance` — lazy singleton via ShaderEffectManagerHolder
   - `previewManager` — getter lazy ShaderPreviewManager(shaderManagerInstance)
   - Preview API: startShaderPreview(), updatePreviewParams(), stopShaderPreview(),
     isPreviewActive(), getActivePreviewProgram(), destroyPreview()

2. **ScreenEffectsPanel.kt** — parâmetro + botão:
   - `GLScreenEffectsTabContent()` — `onOpenShaderSelector: (() -> Unit) = {}`
   - `ScreenEffectsTabContent()` — `onOpenShaderSelector: (() -> Unit) = {}`
   - Botão `ScreenEffectActionRow` com ícone Palette e cor accentPink

3. **Chain completa:**
   - XServerScreen → QuickMenu → GL/Vulkan ScreenEffectsTabContent → ShaderSelectorDialog
   - Dialog usa `FragmentActivity.supportFragmentManager` para show()

4. **Strings:** screen_effects_custom_shader + screen_effects_custom_shader_desc

### Chain de Callbacks
```
XServerScreen
  └─ onOpenShaderSelector = { showShaderSelectorDialog = true }
       │
QuickMenu (onOpenShaderSelector param)
  ├─→ GLScreenEffectsTabContent(onOpenShaderSelector)
  │     └─→ ScreenEffectActionRow(onClick = onOpenShaderSelector)
  └─→ ScreenEffectsTabContent(onOpenShaderSelector)
        └─→ ScreenEffectActionRow(onClick = onOpenShaderSelector)
```

### Arquivos Modificados
| Arquivo | Tipo |
|---------|------|
| ScreenEffectsPanel.kt | Parâmetro + botão em ambas as funções |
| XServerScreen.kt | Callback + dialog fragment + import FragmentActivity |
| QuickMenu.kt | Parâmetro onOpenShaderSelector + pass-through |
| ShaderEffectManager.kt | Preview API unificada + singleton manager |
| strings.xml | 2 novas strings |

---

## Phase 5: Preview ao Vivo (Próxima)
**Status:** 📋 Planeada
**Build:** N/A

### O que falta
1. Integrar `startShaderPreview()` com shader selecionado do dialog
2. Atualizar parâmetros em tempo real via `updatePreviewParams()`
3. Mostrar preview offscreen no ScreenEffectsPanel ou dialog dedicado
4. Testar em dispositivo real com shaders .cgp multi-pass

### Dependências
- ShaderSelectorDialog precisa notificar ShaderEffectManager sobre seleção
- ScreenEffectsPanel precisa exibir preview em tempo real
- ShaderPreviewManager precisa renderizar offscreen com FBOs

---

## Status Geral do Sistema de Shaders

| Componente | Status |
|------------|--------|
| Data Layer (ShaderEntry/Loader/Parser) | ✅ Completo |
| ShaderCompiler (GLSL compilation) | ✅ Completo |
| CustomShaderEffect (single-pass) | ✅ Completo |
| MultiPassShaderEffect (ping-pong) | ✅ Completo |
| ShaderEffectManager (lifecycle) | ✅ Completo |
| ShaderViewModel (favorites/search) | ✅ Completo |
| ShaderSelectorDialog (UI) | ✅ Completo |
| ShaderPreviewManager (preview API) | ✅ Completo |
| Custom Shader Button (ScreenEffectsPanel) | ✅ Completo |
| QuickMenu Integration | ✅ Completo |
| Preview ao Vivo (Phase 5) | 📋 Pendente |
| Teste em Dispositivo | 📋 Pendente |
