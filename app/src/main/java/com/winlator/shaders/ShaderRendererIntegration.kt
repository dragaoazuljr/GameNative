package com.winlator.shaders

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

class ShaderRendererIntegration {
    
    companion object {
        private const val TAG = "ShaderRenderer"
    }
    
    interface GameRenderer {
        fun onCreate()
        fun onResize(width: Int, height: Int)
        fun onDrawFrame(shaderProgram: ShaderProgram?)
        fun onClearColor(r: Float, g: Float, b: Float, a: Float)
    }
    
    open class BaseRendererWithShaders : GameRenderer {
        protected var width = 0
        protected var height = 0
        
        private val vertexBuffer: FloatBuffer by lazy(LazyThreadSafetyMode.NONE) {
            createVertexBuffer()
        }
        
        private fun createVertexBuffer(): FloatBuffer {
            val vertices = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            buffer.put(vertices)
            buffer.position(0)
            return buffer
        }
        
        protected fun setupFullscreenQuad() {
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(0)
        }
        
        protected fun injectMVPUniform(shaderProgram: ShaderProgram?) {
            val mvpMatrix = floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
            shaderProgram?.compiler?.setUniformMatrix4f("MVP", mvpMatrix)
        }
        
        override fun onCreate() {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }

        override fun onResize(width: Int, height: Int) {
            this.width = width
            this.height = height
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(shaderProgram: ShaderProgram?) {
            // Enable vertex attribute for position (layout location 0)
            setupFullscreenQuad()

            if (shaderProgram != null && shaderProgram.isCompiled) {
                // Apply shader program
                shaderProgram.use()

                // Set MVP matrix (identity — can be overridden by subclasses)
                val mvpMatrix = floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f
                )
                shaderProgram.compiler.setUniformMatrix4f("MVP", mvpMatrix)

                // Set OutputSize (resolution) for fragment shader
                val outSize = floatArrayOf(width.toFloat(), height.toFloat(), 0f, 1f)
                shaderProgram.compiler.setUniform("OutputSize", outSize[0], outSize[1], outSize[2], outSize[3])
            }

            // Draw fullscreen quad (2 triangles = 6 vertices)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // Disable vertex attribute array
            GLES20.glDisableVertexAttribArray(0)
        }

        override fun onClearColor(r: Float, g: Float, b: Float, a: Float) {
            GLES20.glClearColor(r, g, b, a)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
    }
    
    class ShaderLifecycleManager(
        private val shaderManager: ShaderManager,
        private val renderer: GameRenderer
    ) {
        private var currentPackageName: String? = null
        private var currentShader: String? = null
        
        fun setupRenderer() {
            Log.d(TAG, "Setting up renderer with shader support")
        }
        
        fun updateGameShader(packageName: String) {
            currentPackageName = packageName
            val newShader = shaderManager.getShaderForGame(packageName)
            if (newShader != currentShader && newShader != null) {
                Log.d(TAG, "Switching shader for game: $packageName")
                currentShader = newShader
            }
        }
        
        fun getActiveShader(): String? = currentShader
        
        fun cleanup() {
            currentShader = null
        }
    }
    
    object ShaderDebug {
        fun logShaderInfo(programId: Int) {
            val maxUniforms = IntArray(1)
            val maxVertexAttrs = IntArray(1)
            
            GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_UNIFORMS, maxUniforms, 0)
            GLES20.glGetProgramiv(programId, GLES20.GL_ACTIVE_ATTRIBUTES, maxVertexAttrs, 0)
            
            Log.d(TAG, "Shader program $programId:")
            Log.d(TAG, "  Uniforms: ${maxUniforms[0]}")
            Log.d(TAG, "  Attributes: ${maxVertexAttrs[0]}")
            
            for (i in 0 until maxUniforms[0]) {
                val sizeBuf = IntArray(1)
                val typeBuf = IntArray(1)
                
                val name = GLES20.glGetActiveUniform(
                    programId, 
                    i, 
                    sizeBuf, 
                    0, 
                    typeBuf, 
                    0
                )
                Log.d(TAG, "  Uniform $i: name=$name size=${sizeBuf[0]} type=0x${typeBuf[0].toString(16)}")
            }
        }
        
        fun checkGLError(operation: String): Boolean {
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "GL Error after $operation: 0x${error.toString(16)}")
                return false
            }
            return true
        }
    }
}

