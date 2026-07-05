package com.winlator.shaders

import android.opengl.GLES20
import android.util.Log
import com.winlator.renderer.GLRenderer
import com.winlator.renderer.effects.Effect
import com.winlator.renderer.material.ShaderMaterial
import com.winlator.shaders.CgpParser.CgpFile
import com.winlator.shaders.CgpParser.CgpPass

/**
 * Multi-pass shader effect using FBO ping-pong.
 * Extends Effect so it can be used with EffectComposer.
 * Each pass in the CGP file is rendered sequentially,
 * ping-ponging between two FBOs for intermediate passes.
 */
class MultiPassShaderEffect(
    private val cgpFile: CgpFile,
    private val renderer: GLRenderer
) : Effect() {

    private companion object {
        const val TAG = "MultiPassShader"
        /**
         * Vertex shader used for all multi-pass rendering.
         * Takes a fullscreen quad and passes UV coords to fragment.
         */
        val DEFAULT_VS: String = """
            attribute vec2 position;
            varying vec2 vUV;
            void main() {
                vUV = position;
                gl_Position = vec4(2.0 * position.x - 1.0, 2.0 * position.y - 1.0, 0.0, 1.0);
            }
        """.trimIndent()
    }

    // ─── FBO ping-pong buffers ───
    private var fboA = IntArray(1)
    private var fboB = IntArray(1)
    private var texA = IntArray(1)
    private var texB = IntArray(1)
    private var fboAllocated = false

    // ─── Compiled programs per pass ───
    private val passPrograms: MutableList<Int> = mutableListOf()
    private var compiled = false

    // ─── Parameter maps per pass ───
    private val passParams: MutableList<MutableMap<String, Float>> = mutableListOf()

    // ─── Ping-pong index ───
    private var currentIdx = 0

    // ─── Allocation tracking ───
    private var allocW = 0
    private var allocH = 0

    init {
        // Initialize parameter maps from CGP passes
        for (pass in cgpFile.passes) {
            val map = mutableMapOf<String, Float>()
            for (p in pass.parameters) {
                map[p.name] = p.initial
            }
            passParams.add(map)
        }
    }

    // ─── Effect contract ───
    // Required by Effect abstract class, but we handle rendering manually via execute().
    override fun createMaterial(): ShaderMaterial {
        return object : ShaderMaterial() {
            override fun getVertexShader(): String = DEFAULT_VS
            override fun getFragmentShader(): String = cgpFile.name
        }
    }

    override fun destroy() {
        cleanup()
        super.destroy()
    }

    // ─── FBO allocation ───

    private fun cleanup() {
        if (fboA[0] != 0) GLES20.glDeleteFramebuffers(1, fboA, 0)
        if (fboB[0] != 0) GLES20.glDeleteFramebuffers(1, fboB, 0)
        if (texA[0] != 0) GLES20.glDeleteTextures(1, texA, 0)
        if (texB[0] != 0) GLES20.glDeleteTextures(1, texB, 0)
        for (prog in passPrograms) {
            if (prog > 0) GLES20.glDeleteProgram(prog)
        }
        fboA[0] = 0; fboB[0] = 0; texA[0] = 0; texB[0] = 0
        passPrograms.clear()
        compiled = false
        fboAllocated = false
    }

    private fun allocFBOs(w: Int, h: Int) {
        if (fboAllocated && allocW == w && allocH == h) return
        cleanup()
        _allocFBOs(w, h)
    }

    private fun _allocFBOs(w: Int, h: Int) {
        // FBO A + texture
        GLES20.glGenFramebuffers(1, fboA, 0)
        GLES20.glGenTextures(1, texA, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texA[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        _setupTexParams(texA[0])
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboA[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texA[0], 0
        )

        // FBO B + texture
        GLES20.glGenFramebuffers(1, fboB, 0)
        GLES20.glGenTextures(1, texB, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texB[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        _setupTexParams(texB[0])
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboB[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texB[0], 0
        )

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO incomplete: $status")
            cleanup()
            return
        }

        fboAllocated = true
        allocW = w
        allocH = h
        Log.d(TAG, "Allocated ping-pong FBOs: $w x $h")
    }

    private fun _setupTexParams(texId: Int) {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    // ─── Ping-pong helpers ───

    private fun getFbo(idx: Int): IntArray = if (idx == 0) fboA else fboB
    private fun getTex(idx: Int): IntArray = if (idx == 0) texA else texB

    // ─── Public API ───

    fun setPassParam(idx: Int, name: String, value: Float) {
        if (idx in passParams.indices) passParams[idx][name] = value
    }

    fun setPassParams(idx: Int, params: Map<String, Float>) {
        if (idx in passParams.indices) {
            for ((k, v) in params) passParams[idx][k] = v
        }
    }

    fun getPassParams(idx: Int): Map<String, Float> {
        return if (idx in passParams.indices) passParams[idx].toMap() else emptyMap()
    }

    /**
     * Execute all CGP passes with FBO ping-pong.
     * @param sourceTex Texture ID of input (scene or previous pass output)
     * @param w Output width
     * @param h Output height
     * @param outFbo Output framebuffer (0 = screen)
     */
    fun execute(sourceTex: Int, w: Int, h: Int, outFbo: Int = 0) {
        if (!fboAllocated || allocW != w || allocH != h) {
            allocFBOs(w, h)
            if (!fboAllocated) {
                Log.e(TAG, "FBO alloc failed, skipping multi-pass")
                return
            }
        }
        _compileAll()

        var inTex = sourceTex
        val n = cgpFile.passes.size

        for (i in 0 until n) {
            if (i >= passPrograms.size) break
            val prog = passPrograms[i]
            if (prog <= 0) {
                Log.w(TAG, "Pass $i program invalid, skipping")
                continue
            }

            GLES20.glUseProgram(prog)

            // Bind input texture to unit 0
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inTex)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "tex"), 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "source"), 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "SceneTexture"), 0)

            // Set per-pass parameters
            val pMap = passParams.getOrElse(i) { emptyMap<String, Float>() }
            for ((k, v) in pMap.entries) {
                val loc = GLES20.glGetUniformLocation(prog, k)
                if (loc >= 0) GLES20.glUniform1f(loc, v)
            }

            // Draw fullscreen quad
            if (i == n - 1) {
                // Last pass -> output framebuffer
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo)
                GLES20.glViewport(0, 0, w, h)
                _drawQuad(prog)
            } else {
                // Intermediate -> next FBO in ping-pong
                val nextIdx = 1 - currentIdx
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getFbo(nextIdx)[0])
                GLES20.glViewport(0, 0, w, h)
                _drawQuad(prog)
                currentIdx = nextIdx
                inTex = getTex(nextIdx)[0]
            }
        }
        Log.d(TAG, "Multi-pass done: ${cgpFile.name} ($n passes)")
    }

    private fun _drawQuad(prog: Int) {
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buf = java.nio.ByteBuffer.allocateDirect(verts.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verts)
            .position(0)

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)
    }

    // ─── Compilation ───

    private fun _compileAll() {
        if (compiled) return
        for (i in cgpFile.passes.indices) {
            val pass = cgpFile.passes[i]
            try {
                var frag = pass.fragmentShader
                // Strip #include directives (inline refs)
                frag = frag.replace("#include \"\"", "")
                val compiler = ShaderCompiler(DEFAULT_VS, frag)
                if (compiler.isCompiled) {
                    passPrograms.add(compiler.programId)
                    Log.d(TAG, "Pass '${pass.name}' compiled -> ${compiler.programId}")
                } else {
                    passPrograms.add(0)
                    Log.e(TAG, "Failed to compile pass '${pass.name}'")
                }
            } catch (e: Exception) {
                passPrograms.add(0)
                Log.e(TAG, "Error compiling pass '${pass.name}': ${e.message}")
            }
        }
        compiled = true
    }

    // ─── Getters ───

    fun getWidth() = allocW
    fun getHeight() = allocH
}
