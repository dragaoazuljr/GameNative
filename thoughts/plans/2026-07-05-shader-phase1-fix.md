# Fase 1: Fix do CustomShaderEffect + ShaderEffectManager

**Status:** Planejamento completo
**Data:** 2026-07-05
**Objetivo:** Corrigir crash crítico no `getFragmentShader()` e criar o gerenciador que conecta shader ao EffectComposer

---

## 📊 Contexto

### Bug Crítico Identificado

`CustomShaderEffect.getFragmentShader()` retorna `shaderEntry.path` (String com caminho de arquivo como `/sdcard/Winlator/shaders/myshader.glsl`) ao invés de ler o conteúdo GLSL. O `ShaderMaterial.compileShaders()` passa essa string direto no `glShaderSource()` → **crash com GL error**.

### Gap de Integração

Nenhum código no projeto chama `effectComposer.setEffects()` ou `clearEffects()`. A pipeline render (`GLRenderer.onDrawFrame()`) verifica `effectComposer.hasEffects()` mas nunca popula o composer com `CustomShaderEffect`. Mesmo sem o bug, o shader nunca renderizaria.

### Arquivos com Stubs/TODOs

| Arquivo | Status |
|---------|--------|
| `ShaderRendererIntegration.kt` | TODOs não implementados — `updateGameShader()` vazio |
| `ShaderSelectorDialog.kt` | `onShaderSelected()` tem `// TODO: Apply shader` |
| `ShaderPreviewManager.kt` | TODOs para FBO offscreen |
| `VulkanSpiRVCompiler.kt` | Stub com shader vazio |

---

## 🔧 Fase 1: Fix do CustomShaderEffect + ShaderEffectManager

### What This Accomplishes
- Corrigir bug de crash: `getFragmentShader()` retorna GLSL text, não path
- Pré-carregar GLSL no construtor (lazy delegate) para evitar I/O no render thread
- Criar `ShaderEffectManager` que gerencia lifecycle do shader e conecta ao EffectComposer
- Tornar o shader funcional no pipeline de renderização

### Changes

#### File: `app/src/nonXr/java/com/winlator/shaders/CustomShaderEffect.kt`

```kotlin
package com.winlator.shaders

import android.opengl.GLES20
import com.winlator.renderer.GLRenderer
import com.winlator.renderer.effects.Effect
import com.winlator.renderer.material.ShaderMaterial
import java.io.File

class CustomShaderEffect(
    private val shaderEntry: ShaderEntry,
    private val renderer: GLRenderer
) : Effect() {

    private val vertexShader: String by lazy {
        """
            attribute vec2 position;
            varying vec2 vUV;
            void main() {
                vUV = position;
                gl_Position = vec4(2.0 * position.x - 1.0, 2.0 * position.y - 1.0, 0.0, 1.0);
            }
        """.trimIndent()
    }

    private val fragmentShader: String by lazy {
        File(shaderEntry.path).readText()
    }

    override fun createMaterial(): ShaderMaterial {
        return object : ShaderMaterial() {
            override fun getVertexShader(): String = vertexShader
            override fun getFragmentShader(): String = fragmentShader
        }
    }

    override fun onUse(material: ShaderMaterial, renderer: GLRenderer) {
        shaderEntry.meta?.params?.forEach { param ->
            material.setUniformFloat(param.name, param.initial)
        }
    }

    override fun destroy() {
        super.destroy()
    }
}
```

**Mudanças:**
- `vertexShader` agora é `lazy delegate` — carregado uma vez no primeiro acesso
- `fragmentShader` agora lê o arquivo com `File(shaderEntry.path).readText()` ao invés de retornar o path
- GLSL é pré-carregado no construtor, não no render thread

---

#### File: `app/src/nonXr/java/com/winlator/shaders/ShaderEffectManager.kt` (NOVO)

```kotlin
package com.winlator.shaders

import android.content.Context
import com.winlator.renderer.EffectComposer
import com.winlator.renderer.GLRenderer
import java.io.File

class ShaderEffectManager(
    private val renderer: GLRenderer,
    private val context: Context
) {
    private var shaderManager: ShaderManager? = null
    private var currentPackage: String? = null
    private var activeEffect: CustomShaderEffect? = null
    private var loadedEntries: List<ShaderEntry>? = null

    private val shaderDir = File("/sdcard/Winlator/shaders/")

    fun setContainer(packageName: String) {
        currentPackage = packageName
        applyShaderForContainer()
    }

    private fun applyShaderForContainer() {
        val manager = getShaderManager()
        if (!manager.shaderEnabled) {
            clearEffect()
            return
        }

        val shaderPath = manager.getShaderForGame(currentPackage)
            ?: manager.getGlobalShader()
            ?: return

        val shaderEntry = findShaderEntry(shaderPath) ?: return
        applyEffect(shaderEntry)
    }

    private fun getShaderManager(): ShaderManager {
        if (shaderManager == null) {
            shaderManager = ShaderManager(context)
        }
        return shaderManager!!
    }

    fun loadShaders(): List<ShaderEntry> {
        val entries = ShaderLoader.loadShaders(shaderDir)
        val manager = getShaderManager()
        loadedEntries = entries.map { entry ->
            entry.copy(isFavorite = manager.isFavorite(entry.relativePath))
        }.sortedBy { it.name }
        return loadedEntries!!
    }

    private fun findShaderEntry(path: String): ShaderEntry? {
        val entries = loadedEntries ?: loadShaders()
        return entries.find { it.relativePath == path }
    }

    private fun applyEffect(entry: ShaderEntry) {
        activeEffect?.destroy()
        activeEffect = CustomShaderEffect(entry, renderer)
        renderer.effectComposer.setEffects(listOf(activeEffect))
    }

    private fun clearEffect() {
        activeEffect?.destroy()
        activeEffect = null
        renderer.effectComposer.clearEffects()
    }

    fun onShaderSelected(entry: ShaderEntry) {
        getShaderManager().setShaderForGame(currentPackage, entry.relativePath)
        getShaderManager().setGlobalShader(entry.relativePath)
        applyEffect(entry)
    }

    fun onShaderEnabled(enabled: Boolean) {
        getShaderManager().shaderEnabled = enabled
        if (!enabled) clearEffect()
    }

    fun isShaderEnabled(): Boolean {
        return getShaderManager().shaderEnabled
    }

    fun getCurrentPackage(): String? = currentPackage

    fun destroy() {
        clearEffect()
        shaderManager = null
    }
}
```

**Mudanças:**
- Gerencia lifecycle do `CustomShaderEffect`
- Chama `effectComposer.setEffects()` quando shader é aplicado
- Chama `effectComposer.clearEffects()` quando shader é removido
- Responde a mudanças de container (packageName)
- Cache de `ShaderEntry` para lookup rápido
- Unifica `ShaderManager` para evitar duplicação de prefs

---

#### File: `app/src/main/java/com/winlator/renderer/GLRenderer.java`

```java
// Adicionar import no topo:
import com.winlator.shaders.ShaderEffectManager;

// Adicionar campo privado após effectComposer:
private ShaderEffectManager shaderEffectManager;

// Adicionar setter público:
public void setShaderEffectManager(ShaderEffectManager mgr) {
    this.shaderEffectManager = mgr;
}

// Adicionar destroy no final da classe (ou no método de cleanup existente):
public void destroyShaderManager() {
    if (shaderEffectManager != null) {
        shaderEffectManager.destroy();
        shaderEffectManager = null;
    }
}
```

**Mudanças:**
- Instanciar `ShaderEffectManager` no construtor
- Expor setter para injeção de dependência
- Método `destroyShaderManager()` para cleanup

---

### Phase Checks
- [ ] `CustomShaderEffect.getFragmentShader()` retorna GLSL text (não path)
- [ ] `ShaderEffectManager.applyEffect()` chama `effectComposer.setEffects()`
- [ ] `ShaderEffectManager.clearEffect()` chama `effectComposer.clearEffects()`
- [ ] Build Kotlin: `./gradlew :app:compileModernDebugKotlin` passa
- [ ] Zero warnings de I/O no render thread (lazy delegate)

---

### Open Questions

**Q1: Onde instanciar o `ShaderEffectManager`?**
- Recomendação: No `GLRenderer` (já tem acesso a `EffectComposer`)
- Alternativa: No `MainActivity` ou `ContainerActivity`

**Q2: Como buscar o ShaderEntry pelo path?**
- Recomendação: Manter cache de entries no `ShaderEffectManager` ou usar `ShaderLoader.loadShaders()` com memoization

---

## 📁 Estrutura Final

```
app/src/nonXr/java/com/winlator/shaders/
├── CustomShaderEffect.kt      # Fix bug + lazy GLSL
├── ShaderEffectManager.kt     # Novo: gerencia lifecycle
└── ShaderManager.kt           # Existente (não modifica)

app/src/main/java/com/winlator/renderer/
└── GLRenderer.java            # Instanciar + expor manager
```
