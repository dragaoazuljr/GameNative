# Fase 4: Integrar ShaderSelectorDialog no Menu QuickMenu + Botão Custom Shader no ScreenEffectsPanel

**Status:** ✅ Concluída (build OK)
**Data:** 2026-07-05
**Build:** `BUILD SUCCESSFUL in 19s`

---

## O que foi feito

### 1. ShaderPreviewManager unificado com ShaderEffectManager
- **ShaderEffectManager.kt** reescrito:
  - `shaderManagerInstance` — lazy singleton via `ShaderEffectManagerHolder`, expõe `ShaderManager` unificado
  - `previewManager` — getter lazy `ShaderPreviewManager(shaderManagerInstance)`
  - Métodos de preview API:
    - `startShaderPreview()` — inicia preview com shader selecionado
    - `updatePreviewParams()` — atualiza parâmetros do shader ativo
    - `stopShaderPreview()` — para o preview
    - `isPreviewActive()` — verifica se preview está rodando
    - `getActivePreviewProgram()` — retorna programa GL ativo
    - `destroyPreview()` — cleanup de recursos de preview

### 2. Botão Custom Shader no ScreenEffectsPanel
- **GLScreenEffectsTabContent()** — adicionado parâmetro `onOpenShaderSelector: (() -> Unit) = {}`
- **ScreenEffectsTabContent()** (Vulkan) — adicionado mesmo parâmetro `onOpenShaderSelector: (() -> Unit) = {}`
- **Botão** `ScreenEffectActionRow` adicionado antes do Reset:
  - `title = R.string.screen_effects_custom_shader` ("Custom Shader")
  - `icon = Icons.Default.Palette`
  - `accentColor = PluviaTheme.colors.accentPink`
  - `onClick = onOpenShaderSelector`

### 3. Chain completa de callbacks
- **XServerScreen** → `onOpenShaderSelector = { showShaderSelectorDialog = true }`
- **QuickMenu** → `onOpenShaderSelector: () -> Unit = {}` como parâmetro
- **QuickMenu** → passa callback para `GLScreenEffectsTabContent` e `ScreenEffectsTabContent`
- **Dialog** → `ShaderSelectorDialog().show(fragmentActivity.supportFragmentManager, "shader_selector")`

### 4. Fix do Dialog Fragment
- `showShaderSelectorDialog` usa `context as? FragmentActivity` (não `Activity`)
- Import `androidx.fragment.app.FragmentActivity` adicionado ao XServerScreen

### 5. Strings resources
- `screen_effects_custom_shader` = "Custom Shader"
- `screen_effects_custom_shader_desc` = "Open shader selector to apply custom GLSL shaders."

---

## Arquivos Modificados
| Arquivo | Tipo |
|---------|------|
| `ScreenEffectsPanel.kt` | Modificar — parâmetro + botão em ambas as funções |
| `XServerScreen.kt` | Modificar — callback + dialog fragment + import FragmentActivity |
| `QuickMenu.kt` | Modificar — parâmetro onOpenShaderSelector + pass-through |
| `ShaderEffectManager.kt` | Modificar — preview API unificada + singleton manager |
| `strings.xml` | Modificar — 2 novas strings |

---

## Chain de Callbacks
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

## Fase 5: Preview ao vivo (pendente)
- Integrar `startShaderPreview()` com o shader selecionado no dialog
- Atualizar parâmetros em tempo real via `updatePreviewParams()`
- Mostrar preview offscreen no ScreenEffectsPanel ou dialog dedicado
- Testar em dispositivo real com shaders .cgp multi-pass
