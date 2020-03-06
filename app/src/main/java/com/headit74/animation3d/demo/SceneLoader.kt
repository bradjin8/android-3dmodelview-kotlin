package com.headit74.animation3d.demo

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.headit74.animation3d.activity.ModelActivity
import org.andresoviedo.android_3d_model_engine.animation.Animator
import org.andresoviedo.android_3d_model_engine.collision.CollisionDetection
import org.andresoviedo.android_3d_model_engine.model.Camera
import org.andresoviedo.android_3d_model_engine.model.Object3DData
import org.andresoviedo.android_3d_model_engine.services.LoaderTask
import org.andresoviedo.android_3d_model_engine.services.Object3DBuilder
import org.andresoviedo.android_3d_model_engine.services.collada.ColladaLoaderTask
import org.andresoviedo.android_3d_model_engine.services.stl.STLLoaderTask
import org.andresoviedo.android_3d_model_engine.services.wavefront.WavefrontLoaderTask
import org.andresoviedo.util.android.ContentUtils
import org.andresoviedo.util.io.IOUtils
import java.io.IOException
import java.util.*

/**
 * This class loads a 3D scena as an example of what can be done with the app
 *
 * @author andresoviedo
 */
open class SceneLoader(
    /**
     * Parent component
     */
    protected val parent: ModelActivity
) : LoaderTask.Callback {

    /**
     * List of data objects containing info for building the opengl objects
     */
    @get:Synchronized
    var objects: List<Object3DData> = ArrayList()
        private set

    /**
     * Show axis or not
     */
    var isDrawAxis = false

    /**
     * Point of view camera
     */
    var camera: Camera? = null
        private set

    /**
     * Enable or disable blending (transparency)
     */
    var isBlendingEnabled = true
        private set

    /**
     * Force transparency
     */
    var isBlendingForced = false
        private set

    /**
     * Whether to draw objects as wireframes
     */
    var isDrawWireframe = false
        private set

    /**
     * Whether to draw using points
     */
    var isDrawPoints = false
        private set

    /**
     * Whether to draw bounding boxes around objects
     */
    var isDrawBoundingBox = false
        private set

    /**
     * Whether to draw face normals. Normally used to debug models
     */
    // TODO: toggle feature this
    val isDrawNormals = false

    /**
     * Whether to draw using textures
     */
    var isDrawTextures = true
        private set

    /**
     * Whether to draw using colors or use default white color
     */
    var isDrawColors = true
        private set

    /**
     * Light toggle feature: we have 3 states: no light, light, light + rotation
     */
    var isRotatingLight = true
        private set

    /**
     * Light toggle feature: whether to draw using lights
     */
    var isDrawLighting = true
        private set

    /**
     * Animate model (dae only) or not
     */
    var isDoAnimation = true
        private set

    /**
     * show bind pose only
     */
    var isShowBindPose = false
        private set

    /**
     * Draw skeleton or not
     */
    var isDrawSkeleton = false
        private set

    /**
     * Toggle collision detection
     */
    var isCollision = false
        private set

    /**
     * Toggle 3d
     */
    var isStereoscopic = false
        private set

    /**
     * Toggle 3d anaglyph (red, blue glasses)
     */
    var isAnaglyph = false
        private set

    /**
     * Toggle 3d VR glasses
     */
    var isVRGlasses = false
        private set

    /**
     * Object selected by the user
     */
    var selectedObject: Object3DData? = null
        private set

    /**
     * Initial light position
     */
    //    private final float[] lightPosition = new float[]{0, 0, 6, 1};
    val lightPosition = floatArrayOf(0f, -12f, -1f, 1f)

    /**
     * Light bulb 3d data
     */
    val lightBulb = Object3DBuilder.buildPoint(lightPosition).setId("light")

    /**
     * Animator
     */
    private val animator =
        Animator()

    /**
     * Did the user touched the model for the first time?
     */
    private var userHasInteracted = false

    /**
     * time when model loading has started (for stats)
     */
    private var startTime: Long = 0
    open fun init() {

        // Camera to show a point of view
        camera = Camera(
            0f,
            -12f,
            -0.5f,
            0f,
            0f,
            0.5f,
            0f,
            0f,
            1f
        )
        camera!!.setChanged(true) // force first draw
        if (parent.paramUri == null) {
            return
        }
        startTime = SystemClock.uptimeMillis()
        val uri = parent.paramUri
        Log.i("Object3DBuilder", "Loading model $uri. async and parallel..")
        if (uri.toString().toLowerCase().endsWith(".obj") || parent.paramType == 0) {
            WavefrontLoaderTask(parent, uri, this).execute()
        } else if (uri.toString().toLowerCase().endsWith(".stl") || parent.paramType == 1) {
            Log.i("Object3DBuilder", "Loading STL object from: $uri")
            STLLoaderTask(parent, uri, this).execute()
        } else if (uri.toString().toLowerCase().endsWith(".dae") || parent.paramType == 2) {
            Log.i("Object3DBuilder", "Loading Collada object from: $uri")
            ColladaLoaderTask(parent, uri, this).execute()
        }
    }

    private fun makeToastText(text: String, toastDuration: Int) {
        parent.runOnUiThread {
            Toast.makeText(
                parent.applicationContext,
                text,
                toastDuration
            ).show()
        }
    }

    /**
     * Hook for animating the objects before the rendering
     */
    fun onDrawFrame() {

        //animateLight();

        // smooth camera transition
        //camera.animate();

        // initial camera animation. animate if user didn't touch the screen
        if (!userHasInteracted) {
            //animateCamera();
        }
        if (objects.isEmpty()) return
        if (isDoAnimation) {
            for (i in objects.indices) {
                val obj = objects[i]
                animator.update(obj, isShowBindPose)
            }
        }
    }

    private fun animateLight() {
        if (!isRotatingLight) return

        // animate light - Do a complete rotation every 5 seconds.
        val time = SystemClock.uptimeMillis() % 5000L
        val angleInDegrees = 360.0f / 5000.0f * time.toInt()
        lightBulb.rotationY = angleInDegrees
    }

    private fun animateCamera() {
        camera!!.translateCamera(0.0025f, 0f)
    }

    @Synchronized
    fun addObject(obj: Object3DData) {
        val newList: MutableList<Object3DData> =
            ArrayList(objects)
        newList.add(obj)
        objects = newList
        requestRender()
    }

    private fun requestRender() {
        // request render only if GL view is already initialized
        if (parent.gLView != null) {
            parent.gLView!!.requestRender()
        }
    }

    fun toggleWireframe() {
        if (!isDrawWireframe && !isDrawPoints && !isDrawSkeleton) {
            isDrawWireframe = true
            makeToastText("Wireframe", Toast.LENGTH_SHORT)
        } else if (!isDrawPoints && !isDrawSkeleton) {
            isDrawWireframe = false
            isDrawPoints = true
            makeToastText("Points", Toast.LENGTH_SHORT)
        } else if (!isDrawSkeleton) {
            isDrawPoints = false
            isDrawSkeleton = true
            makeToastText("Skeleton", Toast.LENGTH_SHORT)
        } else {
            isDrawSkeleton = false
            makeToastText("Faces", Toast.LENGTH_SHORT)
        }
        requestRender()
    }

    fun toggleBoundingBox() {
        isDrawBoundingBox = !isDrawBoundingBox
        requestRender()
    }

    fun toggleTextures() {
        if (isDrawTextures && isDrawColors) {
            isDrawTextures = false
            isDrawColors = true
            makeToastText("Texture off", Toast.LENGTH_SHORT)
        } else if (isDrawColors) {
            isDrawTextures = false
            isDrawColors = false
            makeToastText("Colors off", Toast.LENGTH_SHORT)
        } else {
            isDrawTextures = true
            isDrawColors = true
            makeToastText("Textures on", Toast.LENGTH_SHORT)
        }
    }

    fun toggleLighting() {
        if (isDrawLighting && isRotatingLight) {
            isRotatingLight = false
            makeToastText("Light stopped", Toast.LENGTH_SHORT)
        } else if (isDrawLighting && !isRotatingLight) {
            isDrawLighting = false
            makeToastText("Lights off", Toast.LENGTH_SHORT)
        } else {
            isDrawLighting = true
            isRotatingLight = true
            makeToastText("Light on", Toast.LENGTH_SHORT)
        }
        requestRender()
    }

    fun toggleAnimation() {
        if (!isDoAnimation) {
            isDoAnimation = true
            isShowBindPose = false
            makeToastText("Animation on", Toast.LENGTH_SHORT)
        } else {
            isDoAnimation = false
            isShowBindPose = true
            makeToastText("Bind pose", Toast.LENGTH_SHORT)
        }
    }

    fun toggleCollision() {
        isCollision = !isCollision
        makeToastText("Collisions: $isCollision", Toast.LENGTH_SHORT)
    }

    fun toggleStereoscopic() {
        if (!isStereoscopic) {
            isStereoscopic = true
            isAnaglyph = true
            isVRGlasses = false
            makeToastText("Stereoscopic Anaplygh", Toast.LENGTH_SHORT)
        } else if (isAnaglyph) {
            isAnaglyph = false
            isVRGlasses = true
            // move object automatically cause with VR glasses we still have no way of moving object
            userHasInteracted = false
            makeToastText("Stereoscopic VR Glasses", Toast.LENGTH_SHORT)
        } else {
            isStereoscopic = false
            isAnaglyph = false
            isVRGlasses = false
            makeToastText("Stereoscopic disabled", Toast.LENGTH_SHORT)
        }
        // recalculate camera
        camera!!.setChanged(true)
    }

    fun toggleBlending() {
        if (isBlendingEnabled && !isBlendingForced) {
            makeToastText("Blending forced", Toast.LENGTH_SHORT)
            isBlendingEnabled = true
            isBlendingForced = true
        } else if (isBlendingForced) {
            makeToastText("Blending disabled", Toast.LENGTH_SHORT)
            isBlendingEnabled = false
            isBlendingForced = false
        } else {
            makeToastText("Blending enabled", Toast.LENGTH_SHORT)
            isBlendingEnabled = true
            isBlendingForced = false
        }
    }

    override fun onStart() {
        ContentUtils.setThreadActivity(parent)
    }

    override fun onLoadComplete(datas: List<Object3DData>) {
        // TODO: move texture load to LoaderTask
        for (data in datas) {
            if (data.textureData == null && data.textureFile != null) {
                Log.i("LoaderTask", "Loading texture... " + data.textureFile)
                try {
                    ContentUtils.getInputStream(data.textureFile).use { stream ->
                        if (stream != null) {
                            data.textureData = IOUtils.read(stream)
                        }
                    }
                } catch (ex: IOException) {
                    data.addError("Problem loading texture " + data.textureFile)
                }
            }
        }

        // TODO: move error alert to LoaderTask
        val allErrors: MutableList<String> =
            ArrayList()
        for (data in datas) {
            addObject(data)
            allErrors.addAll(data.errors)
        }
        if (!allErrors.isEmpty()) {
            makeToastText(allErrors.toString(), Toast.LENGTH_LONG)
        }
        val elapsed: String =
            ((SystemClock.uptimeMillis() - startTime) / 1000f).toString() + " secs"
        makeToastText("Build complete ($elapsed)", Toast.LENGTH_LONG)
        ContentUtils.setThreadActivity(null)
    }

    override fun onLoadError(ex: Exception) {
        Log.e("SceneLoader", ex.message, ex)
        makeToastText(
            "There was a problem building the model: " + ex.message,
            Toast.LENGTH_LONG
        )
        ContentUtils.setThreadActivity(null)
    }

    @Throws(IOException::class)
    fun loadTexture(obj: Object3DData?, uri: Uri?) {
        var obj = obj
        if (obj == null && objects.size != 1) {
            makeToastText("Unavailable", Toast.LENGTH_SHORT)
            return
        }
        obj = obj ?: objects[0]
        obj.textureData = IOUtils.read(ContentUtils.getInputStream(uri))
        isDrawTextures = true
    }

    fun processTouch(x: Float, y: Float) {
        val mr = parent.gLView!!.modelRenderer
        val objectToSelect = CollisionDetection.getBoxIntersection(
            objects,
            mr.width,
            mr.height,
            mr.getModelViewMatrix(),
            mr.modelProjectionMatrix,
            x,
            y
        )
        if (objectToSelect != null) {
            selectedObject = if (selectedObject === objectToSelect) {
                Log.i("SceneLoader", "Unselected object " + objectToSelect.id)
                null
            } else {
                Log.i("SceneLoader", "Selected object " + objectToSelect.id)
                objectToSelect
            }
            if (isCollision) {
                Log.d("SceneLoader", "Detecting collision...")
                val point = CollisionDetection.getTriangleIntersection(
                    objects,
                    mr.width,
                    mr.height,
                    mr.getModelViewMatrix(),
                    mr.modelProjectionMatrix,
                    x,
                    y
                )
                if (point != null) {
                    Log.i(
                        "SceneLoader",
                        "Drawing intersection point: " + Arrays.toString(point)
                    )
                    addObject(
                        Object3DBuilder.buildPoint(point).setColor(floatArrayOf(1.0f, 0f, 0f, 1f))
                    )
                }
            }
        }
    }

    fun processMove(dx1: Float, dy1: Float) {
        userHasInteracted = true
    }

    companion object {
        /**
         * Default model color: yellow
         */
        private val DEFAULT_COLOR = floatArrayOf(1.0f, 1.0f, 0f, 1.0f)
    }

}