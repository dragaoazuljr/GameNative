# Fase 2: Fix do CustomShaderEffect + ShaderEffectManager + Integração UI

**Status:** ✅ Concluída (build OK)
**Data:** 2026-07-05
**Build:** `BUILD SUCCESSFUL in 10s`

---

## O que foi implementado

### 1. Fix Crítico: `CustomShaderEffect.kt`
- **Bug:** `getFragmentShader()` retornava `shaderEntry.path` (caminho de arquivo) ao invés de GLSL
- **Fix:** Lê conteúdo do arquivo via `File(shaderEntry.path).readText()` com `lazy delegate`
- GLSL pré-carregado no construtor, não no render thread

### 2. Novo: `ShaderEffectManager.kt`
- Gerencia lifecycle do `CustomShaderEffect`
- Chama `effectComposer.setEffects()` quando shader é aplicado
- Chama `effectComposer.clearEffects()` quando removido
- Responde a mudanças de container (`packageName`)
- Cache de `ShaderEntry` para lookup rápido
- Unifica lógica entre shader per-game e global

### 3. `GLRenderer.java` modificado
- Campo `private ShaderEffectManager shaderEffectManager`
- `getShaderEffectManager()` — getter
- `setShaderEffectManager(ShaderEffectManager)` — setter
- `destroyShaderManager()` — cleanup no destroy

### 4. `ShaderSelectorDialog.kt` atualizado
- Removeu `// TODO: Apply shader to current container`
- Agora usa `ShaderEffectManager` via interface `ShaderSelectorHost`
- Switch de enable/disable conectado ao manager
- `enabledSwitch.isChecked = viewModel.getShaderEnabled()` sincronizado

### 5. `ShaderViewModel.kt` unificado
- Delega favoritos e `shaderEnabled` para `ShaderManager`
- Remove duplicação de SharedPreferences

### 6. `ShaderRendererIntegration.kt` deprecado
- Marcado com `@Deprecated`
- Mantido como stub para compatibilidade
- TODOs removidos

---

## Arquivos Modificados
| Arquivo | Tipo |
|---------|------|
| `CustomShaderEffect.kt` | Modificar — fix bug + lazy GLSL |
| `ShaderEffectManager.kt` | Novo — gerencia lifecycle |
| `GLRenderer.java` | Modificar — instanciar + expor manager |
| `ShaderSelectorDialog.kt` | Modificar — integrar com effectManager |
| `ShaderViewModel.kt` | Modificar — unificar com ShaderManager |
| `ShaderRendererIntegration.kt` | Modificar — stubs deprecated |

## Fase 3: FBO Ping-Pong + Multi-pass (pendente)
- Shader multi-pass (.cgp) com FBO ping-pong
- Interface de preview com parâmetros
- Integração com ScreenEffectsPanel
