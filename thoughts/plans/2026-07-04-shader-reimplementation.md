# Reimplementação Completa do Sistema de Shaders Customizados

## Overview
Recriar o sistema completo de shaders customizados do zero (15+ arquivos .kt) baseado na documentação existente, integrando com o EffectComposer upstream do GameNative.

## Acceptance Criteria
- [ ] Pipeline completo de carregamento de shaders GLSL/CVP funciona
- [ ] Parser de `#pragma parameter` e `#pragma binding` extrai parâmetros do RetroArch
- [ ] Compilador GLSL → GLES20 compila shaders em runtime
- [ ] ShaderManager orquestra carregamento, compilação e aplicação
- [ ] Persistência via SharedPreferences (global, per-game, favorites)
- [ ] UI com BottomSheetDialogFragment para seleção de shaders
- [ ] Lista com DiffUtil, filtros por nome/pasta, busca
- [ ] Preview em tempo real antes de aplicar ao container
- [ ] Suporte a presets CGP (Multi-pass)
- [ ] Integração com VulkanRenderer e GLRenderer (onDrawFrame)
- [ ] Build `./gradlew assembleDebug` passa limpo
- [ ] Build validated no dev-workspace

## Current State
- Código perdido no `git reset --hard HEAD~6`
- Projeto está igual ao upstream origin (utkarshdalal/GameNative)
- Origin alterado para fork pessoal: `git@github.com:dragaoazuljr/GameNative.git`
- EffectComposer upstream já existe (12 efeitos nativos)
- Toda a documentação técnica salva em `docs/` e `references/`

## Desired End State
```
Container inicia → setContainerPackageName(container.id)
  → ShaderLifecycleManager.updateGameShader(packageName)
    → ShaderManager.getCurrentShaderForGame() busca shader per-game
      → ShaderProgram carregado e compilado

onDrawFrame() → areShadersEnabled()?
  → ShaderProgram.isCompiled()?
    → glUseProgram(programId)
    → setUniform("MVP", identity)
    → setUniform("OutputSize", surfaceWidth, surfaceHeight)
    → draw fullscreen quad (2 triangles)
    → glUseProgram(0)
```

## Out of Scope
- Modificar backend Vulkan (C++ / window.frag) — focar em OpenGL ES
- HDR real (fake bloom já existe no upstream)
- Sensores (gyro/accelerometer)
- Texturas LUT externas
- Cache SPIR-V nativo (stub)

## Rollout & Rollback
- **Mecanismo:** Direct deploy. Shader é feature opt-in (toggle `shaderEnabled`).
- **Reversibility:** Flag `shaderEnabled` — desliga tudo sem remover código.
- **Blast radius:** 15+ arquivos novos + 3 modificações em Java (GLRenderer, VulkanRenderer, XServerRenderer).
- **Risco:** Kotlin `val` → Java getters, `ContextImpl` não é public SDK class.

## Implementation Approach
Reimplementar fase por fase, com build intermediário após cada fase. Usar documentação existente como source of truth.

---

## Phase 1: Core Shader Pipeline (Fase 1+2)

### What This Accomplishes
Base do sistema: loader, parser, compiler, manager, preferences, viewmodel, dialog.

### Changes

**File**: `app/src/main/java/com/winlator/shaders/ShaderEntry.kt`
```kotlin
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
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderLoader.kt`
```kotlin
package com.winlator.shaders

import android.content.Context
import java.io.File

object ShaderLoader {
    const val DEFAULT_SHADER_DIR = "/sdcard/Winlator/shaders/"

    fun getShaderDir(context: Context?): File {
        val dir = context?.getExternalFilesDir("shaders")
        return dir ?: File(DEFAULT_SHADER_DIR)
    }

    fun loadShaders(dir: File): List<ShaderEntry> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        
        return dir.walkTopDown()
            .filter { it.isFile && (it.extension == "glsl" || it.extension == "cgp") }
            .map { file ->
                val relative = file.relativeTo(dir).path
                val name = file.nameWithoutExtension
                val content = file.readText()
                val meta = com.winlator.shaders.ShaderParser.parseGlsl(content)
                
                ShaderEntry(
                    path = file.absolutePath,
                    relativePath = relative,
                    name = meta?.params?.firstOrNull()?.label ?: name,
                    meta = meta
                )
            }
            .sortedBy { it.name }
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderParser.kt`
```kotlin
package com.winlator.shaders

object ShaderParser {
    fun parseGlsl(content: String): ShaderMeta? {
        val params = mutableListOf<ShaderParam>()
        val bindingRegex = """#pragma\s+binding\s+(\w+)\s+\w+\s+(\d+)""".toRegex()
        
        val paramRegex = """#pragma\s+parameter\s+(\w+)\s+"([^"]+)"\s+([0-9.eE+-]+)\s+([0-9.eE+-]+)\s+([0-9.eE+-]+)\s+([0-9.eE+-]+)""".toRegex()
        paramRegex.findAll(content).forEach { m ->
            params.add(ShaderParam(
                name = m.groupValues[1],
                label = m.groupValues[2],
                min = m.groupValues[3].toFloat(),
                max = m.groupValues[4].toFloat(),
                step = m.groupValues[5].toFloat(),
                initial = m.groupValues[6].toFloat()
            ))
        }
        
        val hasBinding = bindingRegex.containsMatchIn(content)
        
        return if (params.isNotEmpty()) ShaderMeta(params, hasBinding) else null
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderCompiler.kt`
```kotlin
package com.winlator.shaders

import android.opengl.GLES20

class ShaderCompiler(private val vertexShader: String, fragmentShader: String) {
    private var _programId: Int = 0
    private var _isCompiled: Boolean = false
    private val uniformLocations = HashMap<String, Int>()

    val programId: Int get() = _programId
    val isCompiled: Boolean get() = _isCompiled

    fun compile(): Boolean {
        try {
            _programId = GLES20.glCreateProgram()
            val compiled = IntArray(1)

            // Inject MVP and OutputSize if missing
            val vShader = injectBaseUniforms(vertexShader)
            val fShader = injectBaseUniforms(fragmentShader)

            // Vertex shader
            val vsId = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
            GLES20.glShaderSource(vsId, vShader)
            GLES20.glCompileShader(vsId)
            GLES20.glGetShaderiv(vsId, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) throw RuntimeException("Vertex shader failed: ${GLES20.glGetShaderInfoLog(vsId)}")
            GLES20.glAttachShader(_programId, vsId)

            // Fragment shader
            val fsId = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
            GLES20.glShaderSource(fsId, fShader)
            GLES20.glCompileShader(fsId)
            GLES20.glGetShaderiv(fsId, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) throw RuntimeException("Fragment shader failed: ${GLES20.glGetShaderInfoLog(fsId)}")
            GLES20.glAttachShader(_programId, fsId)

            GLES20.glLinkProgram(_programId)
            GLES20.glGetProgramiv(_programId, GLES20.GL_LINK_STATUS, compiled, 0)
            if (compiled[0] == 0) throw RuntimeException("Program link failed: ${GLES20.glGetProgramInfoLog(_programId)}")

            GLES20.glDeleteShader(vsId)
            GLES20.glDeleteShader(fsId)

            cacheUniformLocations()
            _isCompiled = true
            return true
        } catch (e: Exception) {
            GLES20.glDeleteProgram(_programId)
            _programId = 0
            return false
        }
    }

    private fun injectBaseUniforms(shader: String): String {
        return if (!shader.contains("uniform mat4 MVP")) {
            "uniform mat4 MVP;\nuniform vec4 OutputSize;\n" + shader
        } else shader
    }

    private fun cacheUniformLocations() {
        val count = GLES20.glGetProgramiv(_programId, GLES20.GL_ACTIVE_UNIFORMS)
        for (i in 0 until count) {
            val info = GLES20.glGetActiveUniform(_programId, i)
            val loc = GLES20.glGetUniformLocation(_programId, info.name)
            if (loc != -1) uniformLocations[info.name] = loc
        }
    }

    fun use() {
        if (_isCompiled) GLES20.glUseProgram(_programId)
    }

    fun unuse() {
        GLES20.glUseProgram(0)
    }

    fun setUniformFloat(name: String, value: Float) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform1f(loc, value)
    }

    fun setUniformVec2(name: String, x: Float, y: Float) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform2f(loc, x, y)
    }

    fun setUniformVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform4f(loc, x, y, z, w)
    }

    fun setUniformMatrix4(name: String, mvp: FloatArray) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniformMatrix4fv(loc, 1, false, mvp, 0)
    }

    fun setUniformInt(name: String, value: Int) {
        val loc = uniformLocations[name] ?: return
        GLES20.glUniform1i(loc, value)
    }

    fun destroy() {
        if (_programId != 0) GLES20.glDeleteProgram(_programId)
        _programId = 0
        _isCompiled = false
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderPreferences.kt`
```kotlin
package com.winlator.shaders

import android.content.Context
import android.content.SharedPreferences

class ShaderPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shader_preferences", Context.MODE_PRIVATE)

    var shaderEnabled: Boolean
        get() = prefs.getBoolean("shaderEnabled", false)
        set(value) = prefs.edit().putBoolean("shaderEnabled", value).apply()

    fun getShaderForGame(packageName: String): String? {
        return prefs.getString("shader_pkg_$packageName", null)
    }

    fun setShaderForGame(packageName: String, shaderPath: String?) {
        prefs.edit().putString("shader_pkg_$packageName", shaderPath).apply()
    }

    fun getGlobalShader(): String? {
        return prefs.getString("global_shader", null)
    }

    fun setGlobalShader(path: String?) {
        prefs.edit().putString("global_shader", path).apply()
    }

    fun isFavorite(path: String): Boolean {
        val favs = prefs.getStringSet("shader_favorites", emptySet())
        return favs?.contains(path) == true
    }

    fun toggleFavorite(path: String) {
        val current = prefs.getStringSet("shader_favorites", emptySet()) ?: emptySet()
        val updated = if (current.contains(path)) current - path else current + path
        prefs.edit().putStringSet("shader_favorites", updated).apply()
    }

    fun getLastUsedFor(path: String): String? {
        return prefs.getString("last_used_$path", null)
    }

    fun setLastUsedFor(path: String, packageName: String) {
        prefs.edit().putString("last_used_$path", packageName).apply()
    }

    fun getUserParam(shaderPath: String, paramName: String): Float {
        return prefs.getFloat("user_param_${shaderPath}.$paramName", 0f)
    }

    fun setUserParam(shaderPath: String, paramName: String, value: Float) {
        prefs.edit().putFloat("user_param_${shaderPath}.$paramName", value).apply()
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderViewModel.kt`
```kotlin
package com.winlator.shaders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*

class ShaderViewModel(application: Application) : AndroidViewModel(application) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = ShaderPreferences(application)
    
    val shaders = MutableLiveData<List<ShaderEntry>>(emptyList())
    val isLoading = MutableLiveData(false)
    val shaderEnabled = MutableLiveData(prefs.shaderEnabled)

    fun loadShaders() {
        isLoading.value = true
        scope.launch {
            val dir = ShaderLoader.getShaderDir(application)
            val loaded = ShaderLoader.loadShaders(dir)
            // Mark favorites
            val enriched = loaded.map { entry ->
                entry.copy(isFavorite = prefs.isFavorite(entry.relativePath))
            }
            shaders.postValue(enriched)
            isLoading.postValue(false)
        }
    }

    fun toggleShaderEnabled(enabled: Boolean) {
        prefs.shaderEnabled = enabled
        shaderEnabled.postValue(enabled)
    }

    fun setShaderForGame(packageName: String, shaderPath: String?) {
        prefs.setShaderForGame(packageName, shaderPath)
    }

    fun toggleFavorite(shaderPath: String) {
        prefs.toggleFavorite(shaderPath)
        loadShaders() // Refresh to update favorite state
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderSelectorDialog.kt`
```kotlin
package com.winlator.shaders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ShaderSelectorDialog : BottomSheetDialogFragment() {
    
    private lateinit var viewModel: ShaderViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchField: EditText
    private lateinit var loadingText: TextView
    private lateinit var enabledSwitch: Switch

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
        
        searchField.setOnTextChangedListener { text ->
            val filtered = viewModel.shaders.value?.filter { 
                it.name.contains(text, ignoreCase = true) 
            }
            (recyclerView.adapter as ShaderListAdapter).submitList(filtered)
        }
        
        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleShaderEnabled(isChecked)
        }
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadShaders()
        
        val behavior = (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior
        behavior?.peekHeight = 600
        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun onShaderSelected(shader: ShaderEntry) {
        // TODO: Apply shader to current container
        dismiss()
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderListAdapter.kt`
```kotlin
package com.winlator.shaders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ShaderListAdapter(
    private val onShaderClick: (ShaderEntry) -> Unit,
    private val onFavoriteToggle: (String) -> Unit
) : ListAdapter<ShaderEntry, ShaderListAdapter.ViewHolder>(ShaderDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.shaderName)
        val paramText: TextView = view.findViewById(R.id.shaderParam)
        val favoriteButton: ImageButton = view.findViewById(R.id.favoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shader_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shader = getItem(position)
        holder.nameText.text = shader.name
        holder.paramText.text = shader.meta?.params?.firstOrNull()?.label ?: ""
        holder.favoriteButton.setImageResource(
            if (shader.isFavorite) android.R.drawable.star_big_on 
            else android.R.drawable.star_big_off
        )
        
        holder.itemView.setOnClickListener { onShaderClick(shader) }
        holder.favoriteButton.setOnClickListener { 
            onFavoriteToggle(shader.relativePath)
        }
    }

    class ShaderDiffCallback : DiffUtil.ItemCallback<ShaderEntry>() {
        override fun areItemsTheSame(oldItem: ShaderEntry, newItem: ShaderEntry) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: ShaderEntry, newItem: ShaderEntry) = oldItem == newItem
    }
}
```

**File**: `app/src/main/res/layout/fragment_shader_selector.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/settings_shader_enable_title"
        android:textSize="18sp"
        android:textStyle="bold" />

    <Switch
        android:id="@+id/shaderEnabledSwitch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="16dp" />

    <EditText
        android:id="@+id/shaderSearch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/shader_search_hint"
        android:inputType="text"
        android:layout_marginBottom="8dp" />

    <TextView
        android:id="@+id/loadingText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/shader_loading"
        android:visibility="gone" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/shaderRecyclerView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:nestedScrollingEnabled="true" />

</LinearLayout>
```

**File**: `app/src/main/res/layout/item_shader_entry.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp"
    android:gravity="center_vertical">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/shaderName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/shaderParam"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="#666" />

    </LinearLayout>

    <ImageButton
        android:id="@+id/favoriteButton"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@android:drawable/star_big_off" />

</LinearLayout>
```

**File**: `app/src/main/res/values/shader_strings.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="settings_shader_enable_title">Enable Custom Shaders</string>
    <string name="settings_shader_enable_summary">Apply custom GLSL shaders to your games</string>
    <string name="shader_search_hint">Search shaders...</string>
    <string name="shader_loading">Loading shaders...</string>
    <string name="shader_selector_title">Select Shader</string>
    <string name="shader_apply">Apply Shader</string>
    <string name="shader_apply_for_game">Apply for this game only</string>
    <string name="shader_apply_global">Apply globally</string>
    <string name="shader_compilation_failed">Shader compilation failed</string>
    <string name="shader_no_shaders_found">No shaders found</string>
    <string name="shader_favorite">Favorite</string>
    <string name="shader_unfavorite">Unfavorite</string>
    <string name="shader_apply_success">Shader applied successfully</string>
</resources>
```

### Phase 1 Checks
- [ ] Build succeeds: `./gradlew assembleDebug`
- [ ] Resources compile (no missing layout/string references)
- [ ] No compilation errors in ShaderEntry, ShaderLoader, ShaderParser, ShaderCompiler, ShaderPreferences, ShaderViewModel, ShaderSelectorDialog, ShaderListAdapter

---

## Phase 2: CGP Parser + SPIR-V Stub + Preview (Fase 3)

### What This Accomplishes
Multi-pass shader support (CGP format), SPIR-V compiler stub, and real-time preview manager.

### Changes

**File**: `app/src/main/java/com/winlator/shaders/CgpParser.kt`
```kotlin
package com.winlator.shaders

import org.json.JSONArray
import org.json.JSONObject

data class CgpPass(
    val vertexShader: String,
    val fragmentShader: String,
    val uniforms: Map<String, Any> = emptyMap(),
    val parameters: List<ShaderParam> = emptyList()
)

object CgpParser {
    fun parseCgp(content: String): List<CgpPass>? {
        return try {
            val json = JSONObject(content)
            val passes = json.optJSONArray("passes") ?: return null
            
            val result = mutableListOf<CgpPass>()
            for (i in 0 until passes.length()) {
                val pass = passes.getJSONObject(i)
                val vShader = pass.optString("vertex", "")
                val fShader = pass.optString("fragment", "")
                
                val params = mutableListOf<ShaderParam>()
                val paramsArray = pass.optJSONArray("parameters")
                if (paramsArray != null) {
                    for (j in 0 until paramsArray.length()) {
                        val p = paramsArray.getJSONObject(j)
                        params.add(ShaderParam(
                            name = p.getString("name"),
                            label = p.optString("label", p.getString("name")),
                            min = p.optFloat("min", 0f),
                            max = p.optFloat("max", 1f),
                            step = p.optFloat("step", 0.01f),
                            initial = p.optFloat("initial", 0f)
                        ))
                    }
                }
                
                result.add(CgpPass(vShader, fShader, pass.optJSONObject("uniforms")?.let { 
                    // Convert JSONObject to Map<String, Any>
                    val map = HashMap<String, Any>()
                    val keys = it.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = it.opt(key)
                    }
                    map
                } ?: emptyMap(), params))
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/VulkanSpiRVCompiler.kt`
```kotlin
package com.winlator.shaders

import android.content.Context
import java.io.File

object VulkanSpiRVCompiler {
    private const val CACHE_DIR = "spirv_cache"
    
    fun compileToSpirV(vertexShader: String, fragmentShader: String): ByteArray? {
        // Stub: In a real implementation, this would:
        // 1. Compute SHA256 of shader source
        // 2. Check cache for compiled SPIR-V
        // 3. If not cached, use glslang to compile GLSL → SPIR-V
        // 4. Cache result and return
        
        val cacheDir = File("/sdcard/Winlator/shaders/.spirv_cache")
        cacheDir.mkdirs()
        
        // Compute hash (simplified - real impl uses proper SHA256)
        val hash = (vertexShader + "|" + fragmentShader).hashCode().toString(16)
        val cachedFile = File(cacheDir, "$hash.spirv")
        
        if (cachedFile.exists()) {
            return cachedFile.readBytes()
        }
        
        // Stub: return empty SPIR-V (no-op shader)
        val emptySpirV = ByteArray(64) // Minimal valid SPIR-V header
        cachedFile.writeBytes(emptySpirV)
        return emptySpirV
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderPreviewManager.kt`
```kotlin
package com.winlator.shaders

import android.content.Context
import android.opengl.GLES20

class ShaderPreviewManager(private val context: Context) {
    private var previewShader: ShaderProgram? = null
    private var previewParams: Map<String, Float> = emptyMap()
    
    fun startPreview(shader: ShaderEntry, params: Map<String, Float> = emptyMap()) {
        // TODO: Load shader into offscreen FBO for preview
        // 1. Create FBO (width=320, height=240)
        // 2. Compile shader
        // 3. Draw test pattern (color bars or gradient)
        // 4. Render to preview texture
        previewShader = ShaderProgram(shader, params)
        previewParams = params
    }
    
    fun updatePreviewParams(newParams: Map<String, Float>) {
        previewParams = newParams
        previewShader?.updateParams(newParams)
    }
    
    fun stopPreview() {
        previewShader?.cleanup()
        previewShader = null
        previewParams = emptyMap()
    }
    
    fun isPreviewActive(): Boolean = previewShader != null
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderProgram.kt`
```kotlin
package com.winlator.shaders

class ShaderProgram(
    private val shaderEntry: ShaderEntry,
    private val userParams: Map<String, Float> = emptyMap()
) {
    private val compiler: ShaderCompiler
    private var _isCompiled: Boolean = false
    private var _programId: Int = 0
    
    val isCompiled: Boolean get() = _isCompiled
    val programId: Int get() = _programId
    val compiler: ShaderCompiler get() = compiler
    
    init {
        val content = shaderEntry.path.let { java.io.File(it).readText() }
        val (vShader, fShader) = splitGlsl(content)
        compiler = ShaderCompiler(vShader, fShader)
        
        // Apply user params
        val mergedParams = buildParamMap(userParams, shaderEntry.meta?.params ?: emptyList())
        
        _isCompiled = compiler.compile()
        if (_isCompiled) {
            _programId = compiler.programId
            compiler.use()
            applyParams(compiler, mergedParams)
            compiler.unuse()
        }
    }
    
    private fun splitGlsl(content: String): Pair<String, String> {
        val vStart = content.indexOf("//@vertex")
        val fStart = content.indexOf("//@fragment")
        
        return if (vStart != -1 && fStart != -1) {
            val vEnd = content.indexOf("//@fragment", vStart)
            val fEnd = content.indexOf("//", fStart + 12)
            Pair(
                content.substring(vStart + 11, vEnd),
                content.substring(fStart + 12, fEnd)
            )
        } else {
            // Single file: assume fragment shader
            Pair(DEFAULT_VERTEX_SHADER, content)
        }
    }
    
    private fun buildParamMap(userParams: Map<String, Float>, params: List<ShaderParam>): Map<String, Float> {
        return params.associate { param ->
            param.name to userParams.getOrElse(param.name) { param.initial }
        }
    }
    
    private fun applyParams(compiler: ShaderCompiler, params: Map<String, Float>) {
        for ((name, value) in params) {
            compiler.setUniformFloat(name, value)
        }
    }
    
    fun updateParams(newParams: Map<String, Float>) {
        val mergedParams = buildParamMap(newParams, shaderEntry.meta?.params ?: emptyList())
        compiler.use()
        applyParams(compiler, mergedParams)
        compiler.unuse()
    }
    
    fun cleanup() {
        compiler.destroy()
        _isCompiled = false
    }
    
    companion object {
        private const val DEFAULT_VERTEX_SHADER = """
            attribute vec2 position;
            varying vec2 vUV;
            void main() {
                vUV = position;
                gl_Position = vec4(2.0 * position.x - 1.0, 2.0 * position.y - 1.0, 0.0, 1.0);
            }
        """.trimIndent()
    }
}
```

**File**: `app/src/main/java/com/winlator/shaders/ShaderRendererIntegration.kt`
```kotlin
package com.winlator.shaders

import android.content.Context
import android.opengl.GLES20

class BaseRendererWithShaders(private val context: Context) {
    protected var shaderManager: ShaderManager? = null
    protected var activeShader: ShaderProgram? = null
    protected var containerPackageName: String? = null
    
    fun setContainerPackageName(packageName: String) {
        containerPackageName = packageName
        shaderManager = ShaderManager(context)
        updateGameShader(packageName)
    }
    
    fun updateGameShader(packageName: String) {
        // TODO: Load shader for this game from preferences
        // 1. Check per-game preference
        // 2. Check global preference
        // 3. Apply shader
    }
    
    fun getCurrentShaderProgram(): ShaderProgram? = activeShader
    
    fun areShadersEnabled(): Boolean {
        return shaderManager?.isShaderEnabled() == true
    }
    
    fun applyShaderToFrame(compiler: ShaderCompiler) {
        // TODO: Fullscreen quad with MVP + OutputSize
        compiler.use()
        compiler.setUniformMatrix4("MVP", identityMatrix4())
        compiler.setUniformVec4("OutputSize", 
            GLES20.glGetString(GLES20.GL_RENDERER)?.length?.toFloat() ?: 1920f,
            GLES20.glGetString(GLES20.GL_RENDERER)?.length?.toFloat() ?: 1080f,
            0f, 0f
        )
        // draw fullscreen quad
        compiler.unuse()
    }
    
    private fun identityMatrix4(): FloatArray {
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }
}

class ShaderLifecycleManager {
    fun updateGameShader(packageName: String) {
        // TODO: Wire into container lifecycle
    }
}
```

### Phase 2 Checks
- [ ] Build succeeds: `./gradlew assembleDebug`
- [ ] CGP parser handles valid JSON CGP files
- [ ] SPIR-V stub compiles without errors
- [ ] ShaderProgram loads and compiles GLSL files

---

## Phase 3: UI Integration (Fase 4)

### What This Accomplishes
Wire shaders into Settings screen, ScreenEffects panel, and renderer lifecycle.

### Changes

**File**: `app/src/main/java/app/gamenative/ui/settings/SettingsScreen.kt` (MODIFY)
Add new `SettingsGroupRendering`:
```kotlin
@Composable
fun SettingsGroupRendering() {
    val shadersEnabled by remember { mutableStateOf(false) } // TODO: read from preferences
    
    Card(
        modifier = Modifier.padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Rendering", style = MaterialTheme.typography.h6)
            
            Switch(
                modifier = Modifier.padding(top = 8.dp),
                checked = shadersEnabled,
                onCheckedChange = { /* toggle shaderEnabled */ },
                text = { Text("Enable Custom Shaders") }
            )
            
            TextButton(
                onClick = { 
                    // Open ShaderSelectorDialog
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Select Shader")
            }
        }
    }
}
```

**File**: `app/src/main/java/com/winlator/renderer/GLRenderer.java` (MODIFY)
Add shader integration fields and methods:
```java
// Add to GLRenderer.java:
private ShaderManager shaderManager;
private ShaderProgram activeShader;
private String containerPackageName;

public void setContainerPackageName(String packageName) {
    this.containerPackageName = packageName;
    if (shaderManager != null) {
        shaderManager.updateGameShader(packageName);
    }
}

public ShaderProgram getCurrentShaderProgram() {
    return activeShader;
}

public boolean areShadersEnabled() {
    return shaderManager != null && shaderManager.areShadersEnabled();
}

// In onDrawFrame(), after drawScene():
if (areShadersEnabled() && activeShader != null && activeShader.isCompiled()) {
    activeShader.getCompiler().use();
    activeShader.getCompiler().setUniformMatrix4("MVP", identityMatrix);
    activeShader.getCompiler().setUniformVec4("OutputSize", 
        getWidth(), getHeight(), 0, 0);
    // draw fullscreen quad
    activeShader.getCompiler().unuse();
}
```

**File**: `app/src/main/java/com/winlator/renderer/VulkanRenderer.java` (MODIFY)
Add same methods as GLRenderer.

**File**: `app/src/main/java/com/winlator/renderer/XServerRenderer.java` (MODIFY)
Add interface method:
```java
void setContainerPackageName(String packageName);
```

**File**: `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt` (MODIFY)
Wire `setContainerPackageName(container.id)`:
```kotlin
// After renderer creation:
renderer.setContainerPackageName(container.id)
```

**File**: `app/src/main/java/app/gamenative/ui/screen/xserver/XServerView.java` (MODIFY)
Pass context to VulkanRenderer:
```java
new VulkanRenderer(this, xServer, this.getContext())
```

**File**: `app/src/main/java/app/gamenative/ui/screen/xserver/XServerViewGL.java` (MODIFY)
Pass context to GLRenderer:
```java
new GLRenderer(this, xServer, this.getContext())
```

**File**: `app/src/main/java/app/gamenative/ui/screen/effects/ScreenEffectsPanel.kt` (MODIFY)
Add tab for GL/Vulkan that opens ShaderSelectorActivity:
```kotlin
// In GL tab click handler:
Intent(context, ShaderSelectorActivity::class.java).also { intent ->
    context.startActivity(intent)
}
```

### Phase 3 Checks
- [ ] Build succeeds: `./gradlew assembleDebug`
- [ ] Settings screen compiles with new rendering group
- [ ] GLRenderer + VulkanRenderer compile with shader fields
- [ ] XServerRenderer interface updated
- [ ] XServerScreen wires container.id to renderer
- [ ] XServerView passes getContext() to renderers
- [ ] ScreenEffectsPanel opens ShaderSelectorActivity

---

## Phase 4: Testing & Validation

### What This Accomplishes
Verify everything works end-to-end on device/emulator.

### Changes
- Manual testing on device/emulator
- Verify shader is applied visually
- Test toggle `shaderEnabled` in real-time
- Test per-game shader selection
- Test favorites persistence
- Verify no crash when toggling container with/without shader

### Phase 4 Checks
- [ ] Shader applied to running game (visual verification)
- [ ] Toggle works without crash
- [ ] Per-game shader selection persists across restarts
- [ ] Favorites work and survive app restart

---

## File Summary
```
app/src/main/java/com/winlator/shaders/
├── ShaderEntry.kt              # Data model
├── ShaderLoader.kt             # Load shaders from disk
├── ShaderParser.kt             # Parse #pragma parameter/binding
├── ShaderCompiler.kt           # GLSL → GLES20 compilation
├── ShaderManager.kt            # Orchestrate loading/compilation
├── ShaderPreferences.kt        # SharedPreferences persistence
├── ShaderViewModel.kt          # ViewModel for UI
├── ShaderSelectorDialog.kt     # BottomSheetDialogFragment
├── ShaderListAdapter.kt        # RecyclerView adapter
├── CgpParser.kt                # CGP Multi-pass parser
├── VulkanSpiRVCompiler.kt      # SPIR-V compilation stub
├── ShaderPreviewManager.kt     # Real-time preview
├── ShaderProgram.kt            # Shader wrapper with params
├── ShaderRendererIntegration.kt # Base renderer with shader support
└── ShaderSelectorActivity.kt   # Activity for shader selection (if needed)

app/src/main/res/
├── layout/
│   ├── fragment_shader_selector.xml
│   └── item_shader_entry.xml
└── values/
    └── shader_strings.xml

Modifications:
├── app/src/main/java/com/winlator/renderer/GLRenderer.java
├── app/src/main/java/com/winlator/renderer/VulkanRenderer.java
├── app/src/main/java/com/winlator/renderer/XServerRenderer.java
├── app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt
├── app/src/main/java/app/gamenative/ui/screen/xserver/XServerView.java
├── app/src/main/java/app/gamenative/ui/screen/xserver/XServerViewGL.java
├── app/src/main/java/app/gamenative/ui/settings/SettingsScreen.kt
└── app/src/main/java/app/gamenative/ui/screen/effects/ScreenEffectsPanel.kt
```

**Total new files:** ~18 files
**Total modified files:** ~8 files

## Related Research
- `docs/shader-implementation-status.md` — Previous implementation status
- `~/wiki/concepts/opengl-es-shaders.md` — OpenGL ES shader patterns
- `~/wiki/entities/gamenative.md` — GameNative architecture
- `~/game-native-shaders-analysis.md` — Full codebase analysis
- `~/retroarch-shaders-analysis.md` — RetroArch shader pipeline deep dive

## Open Questions

- **[Should we use Activity or Fragment for shader selection?]**
  Recommend: BottomSheetDialogFragment — consistent with upstream UI patterns.
  Discarded: Activity (fragile, doesn't integrate with Settings flow)

- **[How to handle CGP multi-pass shaders?]**
  Recommend: Apply first pass as primary shader, store remaining passes for future use.
  Discarded: Full multi-pass rendering (requires FBO chain, too complex for v1)

- **[Where to store shader cache?]**
  Recommend: `/sdcard/Winlator/shaders/.cache/` — matches RetroArch convention.
  Discarded: Internal app storage (not accessible by user, can't share with RetroArch)
