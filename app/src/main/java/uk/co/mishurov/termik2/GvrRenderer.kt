package uk.co.mishurov.termik2

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.HandlerThread
import android.util.Log

import com.google.vr.sdk.base.Eye
import com.google.vr.sdk.base.GvrView
import com.google.vr.sdk.base.HeadTransform
import com.google.vr.sdk.base.Viewport

import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig


class GvrRenderer(private val surface: GvrView,
                internal var events: GvrRendererEvents) : GvrView.StereoRenderer {
    private var shader: GlShader? = null
    var surfaceTexture: SurfaceTexture? = null
        private set
    private val drawThread: HandlerThread? = null

    private var textureId = 0

    // Position the eye in front of the origin.
    internal val eyeX = 0.0f
    internal val eyeY = 0.0f
    internal val eyeZ = 0.0f

    // We are looking toward the distance
    internal val lookX = 0.0f
    internal val lookY = 0.0f
    internal val lookZ = -100.0f

    // Set our up vector. This is where our head would be pointing were we holding the camera.
    internal val upX = 0.0f
    internal val upY = 1.0f
    internal val upZ = 0.0f

    private val cameraViewMatrix: FloatArray
    private val viewMatrix: FloatArray

    private val model: FloatArray
    private val modelView: FloatArray
    private val modelViewProjection: FloatArray

    private val transformMatrix: FloatArray


    interface GvrRendererEvents {
        fun onSurfaceTextureCreated(tex: SurfaceTexture)
    }

    init {
        this.surface.setRenderer(this)

        cameraViewMatrix = FloatArray(16)
        viewMatrix = FloatArray(16)

        model = FloatArray(16)
        modelView = FloatArray(16)
        modelViewProjection = FloatArray(16)
        transformMatrix = FloatArray(16)
    }

    override fun onNewFrame(headTransform: HeadTransform) {
        surfaceTexture!!.updateTexImage()
        surfaceTexture!!.getTransformMatrix(transformMatrix)
        Matrix.rotateM(transformMatrix, 0, 270f, 0f, 0f, 1f)
        Matrix.translateM(transformMatrix, 0, -1f, 0f, 0f)
    }

    override fun onDrawEye(eye: Eye) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        Matrix.multiplyMM(viewMatrix, 0, eye.getEyeView(), 0, cameraViewMatrix, 0)

        val perspective = eye.getPerspective(Z_NEAR, Z_FAR)

        Matrix.multiplyMM(modelView, 0, viewMatrix, 0, model, 0)
        Matrix.setIdentityM(modelView, 0)
        Matrix.translateM(modelView, 0, 0f, 0f, -2.0f)
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0)


        shader!!.useProgram()
        GLES20.glUniformMatrix4fv(shader!!.getUniformLocation("mvpMatrix"), 1, false, modelViewProjection, 0)
        GLES20.glUniform1i(shader!!.getUniformLocation("oes_tex"), 0)
        GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.")

        shader!!.setVertexAttribArray("position", 2, RECT_VERTICES)
        shader!!.setVertexAttribArray("in_tex", 2, RECT_TEX_COORDS)

        GLES20.glUniformMatrix4fv(shader!!.getUniformLocation("texMatrix"), 1, false, transformMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    override fun onFinishFrame(viewport: Viewport) {

    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Log.d(TAG, "GvrRenderer.onSurfaceChanged")
        surfaceTexture!!.setDefaultBufferSize(width, height)
    }

    override fun onSurfaceCreated(eglConfig: EGLConfig) {
        Log.d(TAG, "GvrRenderer.onSurfaceCreated")

        textureId = GlUtil.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)

        surfaceTexture = SurfaceTexture(textureId)

        events.onSurfaceTextureCreated(surfaceTexture!!)

        shader = GlShader(VERTEX_SHADER, FRAGMENT_SHADER)
        shader!!.useProgram()


        Matrix.setLookAtM(cameraViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, 0f, -2.0f)
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
                "uniform mat4 mvpMatrix;\n" +
                "uniform mat4 texMatrix;\n" +
                "void main() {\n" +
                "	gl_Position = mvpMatrix * position;\n" +
                "	out_tex = (texMatrix * in_tex).xy;\n" +
                "}\n"
        private val FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 out_tex;\n" +
                "\n" +
                "uniform samplerExternalOES oes_tex;\n" +
                "\n" +
                "void main() {\n" +
                "	gl_FragColor = texture2D(oes_tex, out_tex);\n" +
                "}\n"

        private val Z_NEAR = 0.1f
        private val Z_FAR = 1000.0f

        private val RECT_VERTICES = GlUtil.createFloatBuffer(floatArrayOf(-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f))

        private val RECT_TEX_COORDS = GlUtil.createFloatBuffer(floatArrayOf(0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f))

        private val verticalFlipMatrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
    }

}
