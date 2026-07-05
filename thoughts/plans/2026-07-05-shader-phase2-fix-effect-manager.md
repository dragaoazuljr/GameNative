# Fase 2: Fix do CustomShaderEffect + ShaderEffectManager

**Status:** Planejamento
**Data:** 2026-07-05

---

## Contexto

### Bug Crítico
`CustomShaderEffect.getFragmentShader()` retorna `shaderEntry.path` (caminho de arquivo como `/sdcard/Winlator/shaders/xxx.glsl`) ao invés de ler o conteúdo do GLSL. Isso vai crashar no `glShaderSource` porque ele espera texto GLSL, não um path.

### Gap de Integração
- `ShaderSelectorDialog.onShaderSelected()` tem `// TODO: Apply shader to current container`
- Nenhum código chama `effectComposer.setEffects()` com `CustomShaderEffect`
- `ShaderRendererIntegration.kt` é um stub com TODOs
- Sem integração, o shader nunca será aplicado ao pipeline

### Como o pipeline funciona
1. `GLRenderer.onDrawFrame()` → `if (effectComposer.hasEffects()) effectComposer.render()`
2. `EffectComposer.render()` → desenha a cena no sceneBuffer, depois aplica cada effect via ping-pong
3. Cada effect chama `effect.use()` → `ShaderMaterial.use()` → `compileShaders(vertex, fragment)` → `glShaderSource`

---

## Fase 2: Fix do CustomShaderEffect + ShaderEffectManager

### What This Accomplishes
1. Fix bug: `getFragmentShader()` lê o arquivo GLSL ao invés de retornar path
2. Criar `ShaderEffectManager` que gerencia lifecycle e conecta ao EffectComposer
3. Tornar o shader funcional no pipeline de renderização

### Changes

#### File: `app/src/nonXr/java/com/winlator/shaders/CustomShaderEffect.kt`

**Bug:** `getFragmentShader()` retorna `shaderEntry.path` (caminho de arquivo)
**Fix:** Ler o conteúdo do arquivo e retornar o GLSL text

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
- `vertexShader` e `fragmentShader` agora são `lazy delegates` — carregam uma vez
- `fragmentShader` lê o arquivo com `File(shaderEntry.path).readText()` ao invés de retornar o path
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

**Responsabilidades:**
- Gerencia lifecycle do `CustomShaderEffect`
- Chama `effectComposer.setEffects()` quando shader é aplicado
- Chama `effectComposer.clearEffects()` quando shader é removido
- Responde a mudanças de container (packageName)
- Cache de `ShaderEntry` para lookup rápido

---

#### File: `app/src/main/java/com/winlator/renderer/GLRenderer.java`

```java
// Adicionar import no topo:
import com.winlator.shaders.ShaderEffectManager;

// Adicionar campo privado:
private ShaderEffectManager shaderEffectManager;

// Adicionar setter público:
public void setShaderEffectManager(ShaderEffectManager mgr) {
    this.shaderEffectManager = mgr;
}

// Adicionar destroy:
public void destroyShaderManager() {
    if (shaderEffectManager != null) {
        shaderEffectManager.destroy();
        shaderEffectManager = null;
    }
}
```

---

### Phase Checks
- [ ] `CustomShaderEffect.getFragmentShader()` retorna GLSL text (não path)
- [ ] `ShaderEffectManager.applyEffect()` chama `effectComposer.setEffects()`
- [ ] `ShaderEffectManager.clearEffect()` chama `effectComposer.clearEffects()`
- [ ] Build Kotlin: `./gradlew :app:compileModernDebugKotlin` passa
- [ ] Zero warnings de I/O no render thread (lazy delegate)

---

### Arquivos Modificados
| Arquivo | Tipo |
|---------|------|
| `CustomShaderEffect.kt` | Modificar — fix bug + lazy GLSL |
| `ShaderEffectManager.kt` | Novo — gerencia lifecycle |
| `GLRenderer.java` | Modificar — instanciar + expor manager |
