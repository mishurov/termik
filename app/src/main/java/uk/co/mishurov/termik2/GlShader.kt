package uk.co.mishurov.termik2

import android.opengl.GLES20
import android.util.Log
import java.nio.FloatBuffer


class GlShader(vertexSource: String, fragmentSource: String)
{
    private var program: Int = 0

    init {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(
            GLES20.GL_FRAGMENT_SHADER, fragmentSource
        )
        program = GLES20.glCreateProgram()
        if (program == 0) {
            throw RuntimeException(
                "glCreateProgram() failed. GLES20 error: " +
                GLES20.glGetError()
            )
        }
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = intArrayOf(GLES20.GL_FALSE)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(
                TAG,
                "Could not link program: " +
                GLES20.glGetProgramInfoLog(program)
            )
            throw RuntimeException(GLES20.glGetProgramInfoLog(program))
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        GlUtil.checkNoGLES2Error("Creating GlShader")
    }

    fun getAttribLocation(label: String): Int
    {
        if (program == -1) {
            throw RuntimeException("The program has been released")
        }
        val location = GLES20.glGetAttribLocation(program, label)
        if (location < 0) {
            throw RuntimeException("Could not locate '$label' in program")
        }
        return location
    }

    fun setVertexAttribArray(label: String, dimension: Int, buffer: FloatBuffer)
    {
        if (program == -1) {
            throw RuntimeException("The program has been released")
        }
        val location = getAttribLocation(label)
        GLES20.glEnableVertexAttribArray(location)
        GLES20.glVertexAttribPointer(
            location, dimension, GLES20.GL_FLOAT, false, 0, buffer
        )
        GlUtil.checkNoGLES2Error("setVertexAttribArray")
    }

    fun getUniformLocation(label: String): Int
    {
        if (program == -1) {
            throw RuntimeException("The program has been released")
        }
        val location = GLES20.glGetUniformLocation(program, label)
        if (location < 0) {
            throw RuntimeException(
                "Could not locate uniform '$label' in program"
            )
        }
        return location
    }

    fun useProgram()
    {
        if (program == -1) {
            throw RuntimeException("The program has been released")
        }
        GLES20.glUseProgram(program)
        GlUtil.checkNoGLES2Error("glUseProgram")
    }

    fun release()
    {
        Log.d(TAG, "Deleting shader.")
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
        }
    }

    companion object
    {
        private val TAG = "GlShader"
        private fun compileShader(shaderType: Int, source: String): Int {
            val shader = GLES20.glCreateShader(shaderType)
            if (shader == 0) {
                throw RuntimeException(
                    "glCreateShader() failed. GLES20 error: " +
                    GLES20.glGetError()
                )
            }
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compileStatus = intArrayOf(GLES20.GL_FALSE)
            GLES20.glGetShaderiv(
                shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0
            )
            if (compileStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":" +
                        GLES20.glGetShaderInfoLog(shader))
                throw RuntimeException(GLES20.glGetShaderInfoLog(shader))
            }
            GlUtil.checkNoGLES2Error("compileShader")
            return shader
        }
    }
}
