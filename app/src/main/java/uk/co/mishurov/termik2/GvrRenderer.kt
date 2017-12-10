package uk.co.mishurov.termik2

import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.graphics.Bitmap

import com.google.vr.sdk.base.Eye
import com.google.vr.sdk.base.GvrView
import com.google.vr.sdk.base.HeadTransform
import com.google.vr.sdk.base.Viewport

import javax.microedition.khronos.egl.EGLConfig

import android.util.Log


class GvrRenderer(private val surface: GvrView) : GvrView.StereoRenderer
{
    private var shader: GlShader? = null
    private var shaderOverlay: GlShader? = null
    private var textureId = 0
    private var bmp : Bitmap? = null
    private var vis : Bitmap? = null

    private var headView = FloatArray(16)
    private var invHeadView = FloatArray(16)
    private var view = FloatArray(16)
    private var model = FloatArray(16)
    private var modelViewProjection = FloatArray(16)
    private var modelView = FloatArray(16)
    private var camera = FloatArray(16)
    private var width = 0
    private var height = 0


    init {
        this.surface.setRenderer(this)
    }

    fun setBmp(b : Bitmap)
    {
        bmp = b
    }

    fun setVis(b : Bitmap) {
        vis = b
    }

    override fun onNewFrame(headTransform: HeadTransform)
    {
        headTransform.getHeadView(headView, 0)
        Matrix.invertM(invHeadView, 0, headView, 0)
    }

    override fun onDrawEye(eye: Eye)
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        shader!!.useProgram()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        // Draw the image from a camera
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        if (bmp != null) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp!!, 0)
        }
        GLES20.glUniform1i(shader!!.getUniformLocation("img_tex"), 0)
        GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.")
        shader!!.setVertexAttribArray("position", 2, RECT_VERTICES)
        shader!!.setVertexAttribArray("in_tex", 2, RECT_TEX_COORDS)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Draw the text overlay
        shaderOverlay!!.useProgram()
        if (vis != null) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, vis!!, 0)
        }
        GLES20.glUniform1i(shaderOverlay!!.getUniformLocation("img_tex"), 0)
        GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.")
        shaderOverlay!!.setVertexAttribArray("position", 3, OVERLAY_VERTICES)
        shaderOverlay!!.setVertexAttribArray("in_tex", 2, OVERLAY_TEX_COORDS)

        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, invHeadView, 0)
        Matrix.multiplyMM(view, 0, view, 0, camera, 0)

        val perspective = FloatArray(16)
        val fov = eye.getFov().getTop() + eye.getFov().getBottom()
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(perspective, 0, fov, aspect, Z_NEAR, Z_FAR)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0);
        Matrix.multiplyMM(
            modelViewProjection, 0, perspective, 0, modelView, 0
        )

        GLES20.glUniformMatrix4fv(
            shaderOverlay!!.getUniformLocation("mvpMatrix"),
            1,
            false,
            modelViewProjection,
            0
        )

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisable(GLES20.GL_BLEND)

    }

    override fun onFinishFrame(viewport: Viewport) {}

    override fun onSurfaceChanged(w: Int, h: Int)
    {
        Log.d(TAG, "GvrRenderer.onSurfaceChanged")
        width = w
        height = h
    }

    override fun onSurfaceCreated(eglConfig: EGLConfig)
    {
        Log.d(TAG, "GvrRenderer.onSurfaceCreated")
        textureId = GlUtil.createTexture(GLES20.GL_TEXTURE_2D)
        shader = GlShader(VERTEX_SHADER, FRAGMENT_SHADER)
        shader!!.useProgram()
        shaderOverlay = GlShader(VERTEX_OVERLAY_SHADER, FRAGMENT_SHADER)
        shaderOverlay!!.useProgram()

        Matrix.setIdentityM(model, 0)
        Matrix.setIdentityM(view, 0)
        Matrix.setLookAtM(camera, 0,
            0.0f, 0.0f, CAMERA_Z,
            0.0f, 0.0f, -1.0f,
            0.0f, 1.0f, 0.0f
        )
    }

    override fun onRendererShutdown()
    {
        Log.d(TAG, "GvrRenderer.onRendererShutdown")
        shader!!.release()
    }

    companion object
    {
        private val TAG = "GvrRenderer"

        private val VERTEX_OVERLAY_SHADER = "attribute vec4 position;\n" +
                "attribute vec4 in_tex;\n" +
                "varying vec2 out_tex;\n" +
                "uniform mat4 mvpMatrix;\n" +
                "uniform mat4 texMatrix;\n" +
                "void main() {\n" +
                "	gl_Position = mvpMatrix * position;\n" +
                "	out_tex = in_tex.xy;\n" +
                "}\n"

        private val VERTEX_SHADER = "attribute vec4 position;\n" +
                "attribute vec4 in_tex;\n" +
                "varying vec2 out_tex;\n" +
                "void main() {\n" +
                "	gl_Position = position;\n" +
                "	out_tex = in_tex.xy;\n" +
                "}\n"
        private val FRAGMENT_SHADER = "precision mediump float;\n" +
                "varying vec2 out_tex;\n" +
                "\n" +
                "uniform sampler2D img_tex;\n" +
                "\n" +
                "void main() {\n" +
                "	gl_FragColor = texture2D(img_tex, out_tex);\n" +
                "}\n"

        private val RECT_VERTICES = GlUtil.createFloatBuffer(
            floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f)
        )
        private val OVERLAY_VERTICES = GlUtil.createFloatBuffer(
            floatArrayOf(
                1.0f, 1.0f, 0.3f,
                1.0f, -1.0f, 0.3f,
                -1.0f, 1.0f, 0.5f,
                -1.0f, -1.0f, 0.5f
            )
        )
        private val OVERLAY_TEX_COORDS = GlUtil.createFloatBuffer(
            floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f)
        )
        private val RECT_TEX_COORDS = GlUtil.createFloatBuffer(
            floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f)
        )

        private val Z_NEAR = 0.01f
        private val Z_FAR = 100.0f
        private val CAMERA_Z = -1.4f
    }
}
