package uk.co.mishurov.termik2

import android.opengl.GLES20
import android.opengl.GLUtils
import android.graphics.Bitmap
import android.util.Log

import com.google.vr.sdk.base.Eye
import com.google.vr.sdk.base.GvrView
import com.google.vr.sdk.base.HeadTransform
import com.google.vr.sdk.base.Viewport

import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig


class GvrRenderer(private val surface: GvrView) : GvrView.StereoRenderer {
    private var shader: GlShader? = null
    private var textureId = 0
    private var bmp : Bitmap? = null

    init {
        this.surface.setRenderer(this)
    }

    fun setBmp(b : Bitmap) {
        bmp = b
    }

    override fun onNewFrame(headTransform: HeadTransform) {
    }

    override fun onDrawEye(eye: Eye) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        shader!!.useProgram()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        if (bmp != null) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp!!, 0)
        }
        GLES20.glUniform1i(shader!!.getUniformLocation("oes_tex"), 0)
        GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.")
        shader!!.setVertexAttribArray("position", 2, RECT_VERTICES)
        shader!!.setVertexAttribArray("in_tex", 2, RECT_TEX_COORDS)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun onFinishFrame(viewport: Viewport) {

    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Log.d(TAG, "GvrRenderer.onSurfaceChanged")
    }

    override fun onSurfaceCreated(eglConfig: EGLConfig) {
        Log.d(TAG, "GvrRenderer.onSurfaceCreated")
        textureId = GlUtil.createTexture(GLES20.GL_TEXTURE_2D)
        shader = GlShader(VERTEX_SHADER, FRAGMENT_SHADER)
        shader!!.useProgram()

    }

    override fun onRendererShutdown() {
        Log.d(TAG, "GvrRenderer.onRendererShutdown")
        shader!!.release()
    }

    companion object {
        private val TAG = "GvrRenderer"

        private val VERTEX_SHADER = "attribute vec4 position;\n" +
                "attribute vec4 in_tex\n;" +
                "varying vec2 out_tex\n;" +
                "void main() {\n" +
                "	gl_Position = position;\n" +
                "	out_tex = in_tex.xy;\n" +
                "}\n"
        private val FRAGMENT_SHADER = "precision mediump float;\n" +
                "varying vec2 out_tex;\n" +
                "\n" +
                "uniform sampler2D oes_tex;\n" +
                "\n" +
                "void main() {\n" +
                "	gl_FragColor = texture2D(oes_tex, out_tex);\n" +
                "}\n"

        private val RECT_VERTICES = GlUtil.createFloatBuffer(
            floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f)
        )
        private val RECT_TEX_COORDS = GlUtil.createFloatBuffer(
            floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f)
        )

    }

}
