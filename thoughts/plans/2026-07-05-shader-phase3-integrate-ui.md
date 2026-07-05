# Fase 3: Integração do ShaderSelectorDialog + Cleanup de Stubs

**Status:** Planejamento
**Data:** 2026-07-05

---

## Contexto

### Dependências
- **Fase 2 obrigatória primeiro:** `ShaderEffectManager` e fix do `CustomShaderEffect` precisam existir
- Sem Fase 2, o shader crasha ou não renderiza

### O que falta
1. `ShaderSelectorDialog.onShaderSelected()` — tem `// TODO: Apply shader`
2. Nenhum menu chama o dialog — precisa adicionar entry no menu do container
3. `ShaderRendererIntegration.kt` — stubs não implementados
4. `ShaderPreviewManager.kt` — TODOs para FBO offscreen
5. `VulkanSpiRVCompiler.kt` — stub com shader vazio

---

## Fase 3: Integração UI + Cleanup

### What This Accomplishes
1. Conectar `ShaderSelectorDialog` ao `ShaderEffectManager`
2. Adicionar entry "Shaders" no menu do container
3. Implementar `onShaderSelected()` no dialog
4. Completar stubs de `ShaderRendererIntegration`
5. Implementar preview básico com FBO offscreen

### Changes

#### File: `app/src/nonXr/java/com/winlator/shaders/ShaderSelectorDialog.kt`

```kotlin
package com.winlator.shaders

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.gamenative.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior

class ShaderSelectorDialog(
    private val effectManager: ShaderEffectManager
) : BottomSheetDialogFragment() {

    private lateinit var viewModel: ShaderViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchField: EditText
    private lateinit var loadingText: TextView
    private lateinit var enabledSwitch: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shader_selector, container, false)

        recyclerView = view.findViewById(R.id.shaderRecyclerView)
        searchField = view.findViewById(R.id.shaderSearch)
        loadingText = view.findViewById(R.id.loadingText)
        enabledSwitch = view.findViewById(R.id.shaderEnabledSwitch)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ShaderListAdapter(
            onShaderClick = { shader -> onShaderSelected(shader) },
            onFavoriteToggle = { path -> viewModel.toggleFavorite(path) }
        )

        viewModel = ViewModelProvider(this)[ShaderViewModel::class.java]
        viewModel.shaders.observe(viewLifecycleOwner) { shaders ->
            (recyclerView.adapter as ShaderListAdapter).submitList(shaders)
            loadingText.visibility = if (shaders.isNullOrEmpty()) View.VISIBLE else View.GONE
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val filtered = viewModel.shaders.value?.filter {
                    it.name.contains(text, ignoreCase = true)
                }
                (recyclerView.adapter as ShaderListAdapter).submitList(filtered)
            }
        })

        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            effectManager.onShaderEnabled(isChecked)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        effectManager.loadShaders()

        val behavior = (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior
        behavior?.peekHeight = 600
        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun onShaderSelected(shader: ShaderEntry) {
        effectManager.onShaderSelected(shader)
        dismiss()
    }
}
```

**Mudanças:**
- Adicionar `ShaderEffectManager` como parâmetro no construtor
- Passar `effectManager` para callbacks no adapter
- Implementar `onShaderSelected()` — chama `effectManager.onShaderSelected()`
- `enabledSwitch` agora chama `effectManager.onShaderEnabled()`
- Carregar shaders via `effectManager.loadShaders()`

---

#### File: `app/src/nonXr/java/com/winlator/shaders/ShaderViewModel.kt`

```kotlin
package com.winlator.shaders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShaderViewModel(
    application: Application,
    private val effectManager: ShaderEffectManager
) : AndroidViewModel(application) {

    private val _shaders = MutableLiveData<List<ShaderEntry>>(emptyList())
    val shaders: LiveData<List<ShaderEntry>> = _shaders

    fun loadShaders() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                effectManager.loadShaders()
            }
            _shaders.value = loaded.sortedBy { it.name }
        }
    }

    fun toggleFavorite(relativePath: String) {
        val current = _shaders.value ?: return
        val entry = current.find { it.relativePath == relativePath } ?: return
        val newFav = !entry.isFavorite

        viewModelScope.launch {
            val manager = ShaderManager(getApplication<Application>())
            if (newFav) {
                manager.toggleFavorite(relativePath)
            } else {
                manager.toggleFavorite(relativePath)
            }

            _shaders.value = current.map { e ->
                if (e.relativePath == relativePath) e.copy(isFavorite = newFav) else e
            }
        }
    }
}
```

**Mudanças:**
- Adicionar `ShaderEffectManager` como parâmetro
- `loadShaders()` agora usa `effectManager.loadShaders()`
- Unificar com `ShaderManager` para favoritos

---

#### File: `app/src/main/java/com/winlator/ui/component/ScreenEffectsPanel.kt`

```kotlin
// Adicionar após a seção de shader toggles:

OptionSectionHeader(text = stringResource(R.string.screen_effects_custom_shader))

ScreenEffectActionRow(
    title = stringResource(R.string.screen_effects_select_shader),
    icon = Icons.Default.PhotoFilter,
    accentColor = PluviaTheme.colors.accentPurple,
    onClick = {
        val dialog = ShaderSelectorDialog(shaderEffectManager)
        dialog.show(parentFragmentManager, "shader_selector")
    },
)
```

---

#### File: `app/src/nonXr/java/com/winlator/shaders/ShaderRendererIntegration.kt`

```kotlin
package com.winlator.shaders

import android.content.Context

class ShaderRendererIntegration(private val context: Context) {
    private var shaderManager: ShaderManager? = null
    private var containerPackageName: String? = null

    fun setContainerPackageName(packageName: String) {
        containerPackageName = packageName
        shaderManager = ShaderManager(context)
    }

    fun getCurrentShaderPackage(): String? = containerPackageName

    fun areShadersEnabled(): Boolean {
        return shaderManager?.shaderEnabled == true
    }
}

class ShaderLifecycleManager {
    // TODO: Wire into container lifecycle
}
```

**Mudanças:**
- Implementar `setContainerPackageName()` — salva packageName
- Remover TODOs de `updateGameShader()`
- Simplificar — não precisa de `ShaderProgram` ainda

---

#### File: `app/src/nonXr/java/com/winlator/shaders/ShaderPreviewManager.kt`

```kotlin
package com.winlator.shaders

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ShaderPreviewManager : GLSurfaceView.Renderer {
    private var previewShader: ShaderProgram? = null
    private var previewParams: Map<String, Float> = emptyMap()
    private var fboId = 0
    private var textureId = 0

    fun startPreview(shader: ShaderEntry, params: Map<String, Float> = emptyMap()) {
        previewShader = ShaderProgram(shader, params)
        previewParams = params
        createFBO()
    }

    fun updatePreviewParams(newParams: Map<String, Float>) {
        previewParams = newParams
        previewShader?.updateParams(newParams)
    }

    fun stopPreview() {
        deleteFBO()
        previewShader?.cleanup()
        previewShader = null
        previewParams = emptyMap()
    }

    fun isPreviewActive(): Boolean = previewShader != null

    fun getPreviewTextureId(): Int = textureId

    private fun createFBO() {
        val fboIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fboIds, 0)
        fboId = fboIds[0]

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            320, 240, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId, 0
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun deleteFBO() {
        if (fboId != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = 0
        }
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        if (!isPreviewActive()) return

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, 320, 240)

        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }
}
```

**Mudanças:**
- Implementar FBO offscreen (320x240)
- Criar textura para captura
- Implementar `GLSurfaceView.Renderer` para preview
- `startPreview()`, `updatePreviewParams()`, `stopPreview()` funcionais
- `getPreviewTextureId()` para acesso externo

---

#### File: `app/src/nonXr/java/com/winlator/shaders/VulkanSpiRVCompiler.kt`

```kotlin
package com.winlator.shaders

import java.io.File
import java.security.MessageDigest

object VulkanSpiRVCompiler {
    private const val CACHE_DIR = "/sdcard/Winlator/shaders/.spirv_cache"

    fun compileToSpirV(vertexShader: String, fragmentShader: String): ByteArray? {
        val cacheDir = File(CACHE_DIR)
        cacheDir.mkdirs()

        val hash = computeHash(vertexShader, fragmentShader)
        val cachedFile = File(cacheDir, "$hash.spirv")

        if (cachedFile.exists()) {
            return cachedFile.readBytes()
        }

        // TODO: Implementar com glslangValidator
        // 1. Escrever shaders em temp files
        // 2. Executar: glslangValidator -V -o output.spv vs.vert fs.frag
        // 3. Ler output.spv
        // 4. Cachear em cachedFile
        // Por enquanto, retornar shader vazio (no-op)
        val emptySpirV = ByteArray(64)
        emptySpirV[0] = 0x07230203.toByte() // SPIR-V magic number
        cachedFile.writeBytes(emptySpirV)
        return emptySpirV
    }

    fun invalidateCache() {
        val cacheDir = File(CACHE_DIR)
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun computeHash(vertexShader: String, fragmentShader: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$vertexShader|$fragmentShader"
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
```

**Mudanças:**
- Implementar hash de cache (SHA-256)
- Cache de SPIR-V em `/sdcard/Winlator/shaders/.spirv_cache/`
- Retornar shader vazio como stub (comentário TODO para glslangValidator)
- `invalidateCache()` limpa cache

---

### Phase Checks
- [ ] `ShaderSelectorDialog` abre e lista shaders
- [ ] Click em shader aplica ao container
- [ ] Toggle de enable/disable funciona
- [ ] `ShaderPreviewManager` cria FBO e textura
- [ ] `VulkanSpiRVCompiler` cacheia shaders
- [ ] Build Kotlin: `./gradlew :app:compileModernDebugKotlin` passa

---

### Arquivos Modificados
| Arquivo | Tipo |
|---------|------|
| `ShaderSelectorDialog.kt` | Modificar — integrar com effectManager |
| `ShaderViewModel.kt` | Modificar — unificar com ShaderManager |
| `ShaderRendererIntegration.kt` | Modificar — stubs implementados |
| `ShaderPreviewManager.kt` | Modificar — FBO offscreen implementado |
| `VulkanSpiRVCompiler.kt` | Modificar — cache implementado |
| `ScreenEffectsPanel.kt` | Modificar — botão "Select Shader" |
