# Fase 3: FBO Ping-Pong + Multi-pass + Integração UI

**Status:** Planejamento
**Data:** 2026-07-05

---

## O que falta para a fase 3

### O que já foi feito (fases 1 e 2)
- ✅ Pipeline básico de renderização (shader de cor sólida)
- ✅ CustomShaderEffect lê GLSL do disco corretamente
- ✅ ShaderEffectManager gerencia lifecycle e conecta ao EffectComposer
- ✅ GLRenderer expõe shaderEffectManager
- ✅ ShaderSelectorDialog integrado com ShaderEffectManager
- ✅ ShaderViewModel unificado com ShaderManager
- ✅ ShaderRendererIntegration deprecated e limpo

### Pendente para Fase 3

#### 1. Multi-pass com FBO Ping-Pong (.cgp)
- Suportar shaders .cgp com múltiplos passes
- Dois FBOs alternados (ping-pong) para passagem entre passes
- Cada passagem: sceneBuffer → FBO_A, FBO_A → FBO_B, FBO_B → output

#### 2. Interface de Preview com Parâmetros
- Renderizar preview offscreen para shaders selecionados
- Slider UI para cada parâmetro detectado no GLSL
- Atualização em tempo real dos uniformes

#### 3. Integração com ScreenEffectsPanel
- Adicionar botão "Select Shader" no menu de efeitos
- Conectar toggle de enable/disable ao ShaderEffectManager

---

## Próximos passos
1. Adicionar suporte a .cgp (parser de multi-pass)
2. Implementar FBO ping-pong no EffectComposer
3. Criar UI de parâmetros para cada shader
4. Integrar com ScreenEffectsPanel
