# Fase 3: FBO Ping-Pong + Multi-pass + Integração UI

**Status: Concluída**
**Data: 2026-07-05**

---

## O que foi feito

### 1. Multi-pass com FBO Ping-Pong (.cgp)
- **MultiPassShaderEffect.kt** - Reescrito do zero com:
  - FBO ping-pong real: dois buffers (A/B) alternados via currentIdx
  - Compilação de cada pass do CGP via ShaderCompiler (standalone)
  - createMaterial() implementado (contrato Effect)
  - destroy() override com cleanup de FBOs/programas
  - Parâmetros por pass: setPassParam(), setPassParams(), getPassParams()
  - Renderização fullscreen via glDrawArrays(GL_TRIANGLE_STRIP)
  - Bind de textura via glActiveTexture(GL_TEXTURE0) + uniforms tex, source, SceneTexture
  - Import correto de CgpFile e CgpPass do object CgpParser
- FBO allocation com glCheckFramebufferStatus verificação
- Cleanup de todos os recursos (FBOs, texturas, programas)

### 2. Correções de Compilação Kotlin
- **ShaderManager.kt**: injectBaseUniforms() e applyParamsToProgram() adicionados; forEach corrigido
- **ShaderParser.kt**: Escape sequence \s+ corrigido
- **ShaderPreviewManager.kt**: const val -> val (trimIndent não é compile-time constant)
- **ShaderSelectorDialog.kt**: static holder pattern substituindo getParcelable
- **ShaderEffectManager.kt**: init registra no holder, destroy limpa
- **ShaderRendererIntegration.kt**: currentShader String?, getShaderForGame

### 3. Migração de Localização
- Arquivos movidos de app/src/nonXr/ para app/src/main/ (todas as builds)

---

## Status de Compilação
- ./gradlew :app:compileModernDebugKotlin -> 0 erros
- ./gradlew :app:assembleModernDebug -> BUILD SUCCESSFUL

---

## Pendentes (próximas fases)

### UI de Parâmetros
- Slider UI para cada parâmetro detectado no GLSL
- Atualização em tempo real dos uniformes via applyParamsToProgram()
- Preview offscreen via MultiPassShaderEffect.execute()

### Integração com ScreenEffectsPanel
- Botão Select Shader no menu de efeitos
- Toggle enable/disable conectado ao ShaderEffectManager

### Otimizações
- Reutilização de programas compilados (cache)
- Pool de FBOs para evitar alocação frequente
- Suporte a mais de 2 passes (pool circular)
