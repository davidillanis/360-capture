package com.example.viewer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders an equirectangular panorama on the inside of a sphere. The camera sits
 * at the centre; dragging or the device sensors rotate the view.
 */
class EquirectRenderer : android.opengl.GLSurfaceView.Renderer {

    private var program = 0
    private var textureId = 0
    private var hasTexture = false

    private var positionBuffer: FloatBuffer? = null
    private var uvBuffer: FloatBuffer? = null
    private var vertexCount = 0

    private var uMvp = 0
    private var uRotation = 0
    private var uTexture = 0

    private var bitmap: Bitmap? = null
    private var loadedBitmap: Bitmap? = null

    private val projection = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)

    private var sensorYaw = 0f
    private var sensorPitch = 0f
    private var dragYaw = 0f
    private var dragPitch = 0f
    private var sensorEnabled = false

    @Volatile
    var yaw = 0f
        private set

    @Volatile
    var pitch = 0f
        private set

    fun setPanorama(bmp: Bitmap?) {
        bitmap = bmp
    }

    fun setSensor(yawDeg: Float, pitchDeg: Float, enabled: Boolean) {
        if (enabled) {
            sensorYaw = yawDeg * (PI.toFloat() / 180f)
            sensorPitch = (pitchDeg * (PI.toFloat() / 180f)).coerceIn(-1.4f, 1.4f)
        }
        sensorEnabled = enabled
    }

    fun applyDrag(dxPx: Float, dyPx: Float) {
        dragYaw -= dxPx * 0.008f
        dragPitch = (dragPitch + dyPx * 0.008f).coerceIn(-1.4f, 1.4f)
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES20.glClearColor(0.05f, 0.05f, 0.06f, 1f)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uRotation = GLES20.glGetUniformLocation(program, "uRotation")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        hasTexture = false
        loadedBitmap = null
        buildSphere(64, 128)
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height
        Matrix.perspectiveM(projection, 0, 60f, aspect, 0.1f, 20f)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        val targetYaw = sensorYaw + dragYaw
        val targetPitch = (sensorPitch + dragPitch).coerceIn(-1.4f, 1.4f)
        if (sensorEnabled) {
            yaw += (targetYaw - yaw) * 0.25f
            pitch += (targetPitch - pitch) * 0.25f
        } else {
            yaw = targetYaw
            pitch = targetPitch
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val bmp = bitmap
        if (program == 0 || bmp == null) return

        GLES20.glUseProgram(program)

        if (!hasTexture || bmp !== loadedBitmap) {
            if (hasTexture) {
                val ids = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, ids, 0)
            }
            loadTexture(bmp)
            loadedBitmap = bmp
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTexture, 0)

        val rot = FloatArray(9)
        rotationMatrix(rot, yaw, pitch)
        GLES20.glUniformMatrix3fv(uRotation, 1, false, rot, 0)

        Matrix.setIdentityM(modelView, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        val pb = positionBuffer ?: return
        val ub = uvBuffer ?: return
        GLES20.glEnableVertexAttribArray(0)
        pb.position(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, pb)
        GLES20.glEnableVertexAttribArray(1)
        ub.position(0)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, ub)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(0)
        GLES20.glDisableVertexAttribArray(1)
    }

    private fun loadTexture(bmp: Bitmap) {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        hasTexture = true
    }

    private fun rotationMatrix(out: FloatArray, yaw: Float, pitch: Float) {
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        // R = Ry(yaw) * Rx(pitch)
        out[0] = cy; out[1] = 0f; out[2] = sy
        out[3] = 0f; out[4] = 1f; out[5] = 0f
        out[6] = -sy; out[7] = 0f; out[8] = cy
        val rx = floatArrayOf(
            1f, 0f, 0f,
            0f, cp, -sp,
            0f, sp, cp
        )
        val r = FloatArray(9)
        Matrix.multiplyMM(r, 0, out, 0, rx, 0)
        System.arraycopy(r, 0, out, 0, 9)
    }

    private fun buildSphere(latSegments: Int, lonSegments: Int) {
        val positions = ArrayList<Float>(latSegments * lonSegments * 18)
        val uvs = ArrayList<Float>(latSegments * lonSegments * 12)

        for (lat in 0 until latSegments) {
            val v0 = lat.toFloat() / latSegments
            val v1 = (lat + 1).toFloat() / latSegments
            val theta0 = PI.toFloat() * (v0 - 0.5f) // -90..+90
            val theta1 = PI.toFloat() * (v1 - 0.5f)

            for (lon in 0 until lonSegments) {
                val u0 = lon.toFloat() / lonSegments
                val u1 = (lon + 1).toFloat() / lonSegments
                val phi0 = 2f * PI.toFloat() * u0
                val phi1 = 2f * PI.toFloat() * u1

                val p00 = dir(theta0, phi0)
                val p01 = dir(theta0, phi1)
                val p10 = dir(theta1, phi0)
                val p11 = dir(theta1, phi1)

                addVertex(positions, uvs, p00, u0, v0)
                addVertex(positions, uvs, p01, u1, v0)
                addVertex(positions, uvs, p11, u1, v1)

                addVertex(positions, uvs, p00, u0, v0)
                addVertex(positions, uvs, p11, u1, v1)
                addVertex(positions, uvs, p10, u0, v1)
            }
        }

        vertexCount = positions.size / 3
        positionBuffer = toBuffer(positions.toFloatArray())
        uvBuffer = toBuffer(uvs.toFloatArray())
    }

    private fun dir(theta: Float, phi: Float): FloatArray {
        val st = sin(theta); val ct = cos(theta)
        return floatArrayOf(ct * sin(phi), st, ct * cos(phi))
    }

    private fun addVertex(positions: MutableList<Float>, uvs: MutableList<Float>, p: FloatArray, u: Float, v: Float) {
        positions.add(p[0]); positions.add(p[1]); positions.add(p[2])
        uvs.add(u); uvs.add(v)
    }

    private fun toBuffer(data: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(data)
        fb.position(0)
        return fb
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            uniform mat4 uMVP;
            uniform mat3 uRotation;
            varying vec3 vDir;
            void main() {
              vDir = uRotation * aPosition;
              gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec3 vDir;
            void main() {
              vec3 d = normalize(vDir);
              float phi = atan(d.x, -d.z);
              float theta = asin(clamp(d.y, -1.0, 1.0));
              float u = (phi + 3.14159265) / 6.28318531;
              float v = 0.5 - theta / 3.14159265;
              gl_FragColor = texture2D(uTexture, vec2(u, v));
            }
        """
    }
}
