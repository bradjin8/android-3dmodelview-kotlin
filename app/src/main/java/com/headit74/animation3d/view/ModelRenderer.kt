package com.headit74.animation3d.view

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import org.andresoviedo.android_3d_model_engine.animation.Animator
import org.andresoviedo.android_3d_model_engine.drawer.DrawerFactory
import org.andresoviedo.android_3d_model_engine.model.AnimatedModel
import org.andresoviedo.android_3d_model_engine.model.Object3D
import org.andresoviedo.android_3d_model_engine.model.Object3DData
import org.andresoviedo.android_3d_model_engine.services.Object3DBuilder
import org.andresoviedo.util.android.GLUtil
import java.io.ByteArrayInputStream
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ModelRenderer(// 3D window (parent component)
    private val main: ModelSurfaceView
) : GLSurfaceView.Renderer {

    // width of the screen
    var width = 0
        private set

    // height of the screen
    var height = 0
        private set

    /**
     * Drawer factory to get right renderer/shader based on object attributes
     */
    private val drawer: DrawerFactory

    /**
     * 3D Axis (to show if needed)
     */
    private val axis = Object3DBuilder.buildAxis().setId("axis")

    // The wireframe associated shape (it should be made of lines only)
    private val wireframes: MutableMap<Object3DData?, Object3DData?> =
        HashMap()

    // The loaded textures
    private val textures: MutableMap<Any, Int> =
        HashMap()

    // The corresponding opengl bounding boxes and drawer
    private val boundingBoxes: MutableMap<Object3DData?, Object3DData?> =
        HashMap()

    // The corresponding opengl bounding boxes
    private val normals: MutableMap<Object3DData?, Object3DData> =
        HashMap()
    private val skeleton: MutableMap<Object3DData, Object3DData?> =
        HashMap()

    // 3D matrices to project our 3D world
    private val viewMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    val modelProjectionMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)
    private val lightPosInWorldSpace = FloatArray(4)
    private val cameraPosInWorldSpace = FloatArray(3)

    // 3D stereoscopic matrix (left & right camera)
    private val viewMatrixLeft = FloatArray(16)
    private val projectionMatrixLeft = FloatArray(16)
    private val viewProjectionMatrixLeft = FloatArray(16)
    private val viewMatrixRight = FloatArray(16)
    private val projectionMatrixRight = FloatArray(16)
    private val viewProjectionMatrixRight = FloatArray(16)

    /**
     * Whether the info of the model has been written to console log
     */
    private val infoLogged: MutableMap<Object3DData?, Boolean> =
        HashMap()

    /**
     * Switch to akternate drawing of right and left image
     */
    private var anaglyphSwitch = false

    /**
     * Skeleton Animator
     */
    private val animator =
        Animator()

    /**
     * Did the application explode?
     */
    private var fatalException = false
    val near: Float = 1f

    val far: Float = 100f

    override fun onSurfaceCreated(
        unused: GL10,
        config: EGLConfig
    ) {
        // Set the background frame color
        val backgroundColor = main.modelActivity.backgroundColor
        GLES20.glClearColor(
            backgroundColor[0],
            backgroundColor[1],
            backgroundColor[2],
            backgroundColor[3]
        )

        // Use culling to remove back faces.
        // Don't remove back faces so we can see them
        // GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Enable depth testing for hidden-surface elimination.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Enable not drawing out of view port
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        this.width = width
        this.height = height

        // Adjust the viewport based on geometry changes, such as screen rotation
        GLES20.glViewport(0, 0, width, height)

        // the projection matrix is the 3D virtual space (cube) that we want to project
        val ratio = width.toFloat() / height
        Log.d(
            TAG,
            "projection: [" + -ratio + "," + ratio + ",-1,1]-near/far[1,10]"
        )
        Matrix.frustumM(
            modelProjectionMatrix,
            0,
            -ratio,
            ratio,
            -1f,
            1f,
            near,
            far
        )
        Matrix.frustumM(
            projectionMatrixRight,
            0,
            -ratio,
            ratio,
            -1f,
            1f,
            near,
            far
        )
        Matrix.frustumM(
            projectionMatrixLeft,
            0,
            -ratio,
            ratio,
            -1f,
            1f,
            near,
            far
        )
    }

    override fun onDrawFrame(unused: GL10) {
        if (fatalException) {
            return
        }
        try {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glScissor(0, 0, width, height)

            // Draw background color
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val scene = main.modelActivity.scene
                ?: // scene not ready
                return
            var colorMask: FloatArray? = null
            if (scene.isBlendingEnabled) {
                // Enable blending for combining colors when there is transparency
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                if (scene.isBlendingForced) {
                    colorMask = BLENDING_FORCED_MASK_COLOR
                }
            } else {
                GLES20.glDisable(GLES20.GL_BLEND)
            }

            // animate scene
            scene.onDrawFrame()

            // recalculate mvp matrix according to where we are looking at now
            val camera = scene.camera
            cameraPosInWorldSpace[0] = camera?.xPos ?: 0f
            cameraPosInWorldSpace[1] = camera?.yPos ?: 0f
            cameraPosInWorldSpace[2] = camera?.zPos ?: 0f
            if (camera!!.hasChanged()) {
                // INFO: Set the camera position (View matrix)
                // The camera has 3 vectors (the position, the vector where we are looking at, and the up position (sky)

                // the projection matrix is the 3D virtual space (cube) that we want to project
                val ratio = width.toFloat() / height
                // Log.v(TAG, "Camera changed: projection: [" + -ratio + "," + ratio + ",-1,1]-near/far[1,10], ");
                if (!scene.isStereoscopic) {
                    Matrix.setLookAtM(
                        viewMatrix,
                        0,
                        camera.xPos,
                        camera.yPos,
                        camera.zPos,
                        camera.xView,
                        camera.yView,
                        camera.zView,
                        camera.xUp,
                        camera.yUp,
                        camera.zUp
                    )
                    Matrix.multiplyMM(
                        viewProjectionMatrix,
                        0,
                        modelProjectionMatrix,
                        0,
                        viewMatrix,
                        0
                    )
                } else {
                    val stereoCamera =
                        camera.toStereo(EYE_DISTANCE)
                    val leftCamera =
                        stereoCamera[0]
                    val rightCamera =
                        stereoCamera[1]

                    // camera on the left for the left eye
                    Matrix.setLookAtM(
                        viewMatrixLeft,
                        0,
                        leftCamera.xPos,
                        leftCamera.yPos,
                        leftCamera.zPos,
                        leftCamera.xView,
                        leftCamera.yView,
                        leftCamera.zView,
                        leftCamera.xUp,
                        leftCamera.yUp,
                        leftCamera.zUp
                    )
                    // camera on the right for the right eye
                    Matrix.setLookAtM(
                        viewMatrixRight,
                        0,
                        rightCamera.xPos,
                        rightCamera.yPos,
                        rightCamera.zPos,
                        rightCamera.xView,
                        rightCamera.yView,
                        rightCamera.zView,
                        rightCamera.xUp,
                        rightCamera.yUp,
                        rightCamera.zUp
                    )
                    if (scene.isAnaglyph) {
                        Matrix.frustumM(
                            projectionMatrixRight,
                            0,
                            -ratio,
                            ratio,
                            -1f,
                            1f,
                            near,
                            far
                        )
                        Matrix.frustumM(
                            projectionMatrixLeft,
                            0,
                            -ratio,
                            ratio,
                            -1f,
                            1f,
                            near,
                            far
                        )
                    } else if (scene.isVRGlasses) {
                        val ratio2 = width.toFloat() / 2 / height
                        Matrix.frustumM(
                            projectionMatrixRight,
                            0,
                            -ratio2,
                            ratio2,
                            -1f,
                            1f,
                            near,
                            far
                        )
                        Matrix.frustumM(
                            projectionMatrixLeft,
                            0,
                            -ratio2,
                            ratio2,
                            -1f,
                            1f,
                            near,
                            far
                        )
                    }
                    // Calculate the projection and view transformation
                    Matrix.multiplyMM(
                        viewProjectionMatrixLeft,
                        0,
                        projectionMatrixLeft,
                        0,
                        viewMatrixLeft,
                        0
                    )
                    Matrix.multiplyMM(
                        viewProjectionMatrixRight,
                        0,
                        projectionMatrixRight,
                        0,
                        viewMatrixRight,
                        0
                    )
                }
                camera.setChanged(false)
            }
            if (!scene.isStereoscopic) {
                this.onDrawFrame(
                    viewMatrix,
                    modelProjectionMatrix,
                    viewProjectionMatrix,
                    lightPosInWorldSpace,
                    colorMask,
                    cameraPosInWorldSpace
                )
                return
            }
            if (scene.isAnaglyph) {
                // INFO: switch because blending algorithm doesn't mix colors
                if (anaglyphSwitch) {
                    this.onDrawFrame(
                        viewMatrixLeft,
                        projectionMatrixLeft,
                        viewProjectionMatrixLeft,
                        lightPosInWorldSpace,
                        COLOR_RED,
                        cameraPosInWorldSpace
                    )
                } else {
                    this.onDrawFrame(
                        viewMatrixRight,
                        projectionMatrixRight,
                        viewProjectionMatrixRight,
                        lightPosInWorldSpace,
                        COLOR_BLUE,
                        cameraPosInWorldSpace
                    )
                }
                anaglyphSwitch = !anaglyphSwitch
                return
            }
            if (scene.isVRGlasses) {

                // draw left eye image
                GLES20.glViewport(0, 0, width / 2, height)
                GLES20.glScissor(0, 0, width / 2, height)
                this.onDrawFrame(
                    viewMatrixLeft,
                    projectionMatrixLeft,
                    viewProjectionMatrixLeft,
                    lightPosInWorldSpace,
                    null,
                    cameraPosInWorldSpace
                )

                // draw right eye image
                GLES20.glViewport(width / 2, 0, width / 2, height)
                GLES20.glScissor(width / 2, 0, width / 2, height)
                this.onDrawFrame(
                    viewMatrixRight,
                    projectionMatrixRight,
                    viewProjectionMatrixRight,
                    lightPosInWorldSpace,
                    null,
                    cameraPosInWorldSpace
                )
            }
        } catch (ex: Exception) {
            Log.e("ModelRenderer", "Fatal exception: " + ex.message, ex)
            fatalException = true
        }
    }

    private fun onDrawFrame(
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        viewProjectionMatrix: FloatArray,
        lightPosInWorldSpace: FloatArray,
        colorMask: FloatArray?,
        cameraPosInWorldSpace: FloatArray
    ) {
        val scene = main.modelActivity.scene

        // draw light
        if (scene!!.isDrawLighting) {
            val lightBulbDrawer = drawer.pointDrawer

            // Calculate position of the light in world space to support lighting
            if (scene.isRotatingLight) {
                Matrix.multiplyMV(
                    lightPosInWorldSpace,
                    0,
                    scene.lightBulb.modelMatrix,
                    0,
                    scene.lightPosition,
                    0
                )
                // Draw a point that represents the light bulb
                lightBulbDrawer.draw(
                    scene.lightBulb, projectionMatrix, viewMatrix, -1, lightPosInWorldSpace,
                    colorMask, cameraPosInWorldSpace
                )
            } else {
                lightPosInWorldSpace[0] = scene.camera?.xPos ?: 0f
                lightPosInWorldSpace[1] = scene.camera?.yPos ?: 0f
                lightPosInWorldSpace[2] = scene.camera?.zPos ?: 0f
                lightPosInWorldSpace[3] = 0f
            }

            // FIXME: memory leak
            if (scene.isDrawNormals) {
                lightBulbDrawer.draw(
                    Object3DBuilder.buildLine(
                        floatArrayOf(
                            lightPosInWorldSpace[0],
                            lightPosInWorldSpace[1], lightPosInWorldSpace[2], 0f, 0f, 0f
                        )
                    ), projectionMatrix,
                    viewMatrix, -1,
                    lightPosInWorldSpace,
                    colorMask, cameraPosInWorldSpace
                )
            }
        }

        // draw axis
        if (scene.isDrawAxis) {
            val basicDrawer = drawer.pointDrawer
            basicDrawer.draw(
                axis, projectionMatrix, viewMatrix, axis.drawMode, axis
                    .drawSize, -1, lightPosInWorldSpace, colorMask, cameraPosInWorldSpace
            )
        }


        // is there any object?
        if (scene.objects.isEmpty()) {
            return
        }

        // draw all available objects
        val objects = scene.objects
        for (i in objects.indices) {
            var objData: Object3DData? = null
            try {
                objData = objects[i]
                if (!objData.isVisible) continue
                var drawerObject: Object3D? = drawer.getDrawer(
                    objData, scene.isDrawTextures, scene.isDrawLighting,
                    scene.isDoAnimation, scene.isDrawColors
                )
                    ?: continue
                if (!infoLogged.containsKey(objData)) {
                    Log.v("ModelRenderer", "Drawing model: " + objData.id)
                    infoLogged[objData] = true
                }
                val changed = objData.isChanged

                // load model texture
                var textureId = textures[objData.textureData]
                if (textureId == null && objData.textureData != null) {
                    Log.i(
                        "ModelRenderer",
                        "Loading texture '" + objData.textureFile + "'..."
                    )
                    val textureIs =
                        ByteArrayInputStream(objData.textureData)
                    textureId = GLUtil.loadTexture(textureIs)
                    textureIs.close()
                    textures[objData.textureData] = textureId
                    Log.i("GLUtil", "Loaded texture ok. id: $textureId")
                }
                if (textureId == null) {
                    textureId = -1
                }

                // draw points
                if (objData.drawMode == GLES20.GL_POINTS) {
                    val basicDrawer = drawer.pointDrawer
                    basicDrawer.draw(
                        objData,
                        projectionMatrix,
                        viewMatrix,
                        GLES20.GL_POINTS,
                        lightPosInWorldSpace,
                        cameraPosInWorldSpace
                    )
                } else if (scene.isDrawWireframe && objData.drawMode != GLES20.GL_POINTS && objData.drawMode != GLES20.GL_LINES && objData.drawMode != GLES20.GL_LINE_STRIP && objData.drawMode != GLES20.GL_LINE_LOOP
                ) {
                    // Log.d("ModelRenderer","Drawing wireframe model...");
                    try {
                        // Only draw wireframes for objects having faces (triangles)
                        var wireframe = wireframes[objData]
                        if (wireframe == null || changed) {
                            Log.i("ModelRenderer", "Generating wireframe model...")
                            wireframe = Object3DBuilder.buildWireframe(objData)
                            wireframes[objData] = wireframe
                        }
                        drawerObject?.draw(
                            wireframe, projectionMatrix, viewMatrix, wireframe!!.drawMode,
                            wireframe.drawSize, textureId, lightPosInWorldSpace,
                            colorMask, cameraPosInWorldSpace
                        )
                        animator.update(wireframe, scene.isShowBindPose)
                    } catch (e: Error) {
                        Log.e("ModelRenderer", e.message, e)
                    }
                } else if (scene.isDrawPoints || objData.faces == null || !objData.faces
                        .loaded()
                ) {
                    drawerObject?.draw(
                        objData, projectionMatrix, viewMatrix
                        , GLES20.GL_POINTS, objData.drawSize,
                        textureId, lightPosInWorldSpace, colorMask, cameraPosInWorldSpace
                    )
                } else if (scene.isDrawSkeleton && objData is AnimatedModel && objData
                        .animation != null
                ) {
                    var skeleton = skeleton[objData]
                    if (skeleton == null) {
                        skeleton = Object3DBuilder.buildSkeleton(objData as AnimatedModel?)
                        this.skeleton[objData] = skeleton
                    }
                    animator.update(skeleton, scene.isShowBindPose)
                    drawerObject = drawer.getDrawer(
                        skeleton, false, scene.isDrawLighting, scene
                            .isDoAnimation, scene.isDrawColors
                    )
                    drawerObject.draw(
                        skeleton,
                        projectionMatrix,
                        viewMatrix,
                        -1,
                        lightPosInWorldSpace,
                        colorMask,
                        cameraPosInWorldSpace
                    )
                } else {
                    drawerObject?.draw(
                        objData, projectionMatrix, viewMatrix,
                        textureId, lightPosInWorldSpace, colorMask, cameraPosInWorldSpace
                    )
                }

                // Draw bounding box
                if (scene.isDrawBoundingBox || scene.selectedObject === objData) {
                    var boundingBoxData = boundingBoxes[objData]
                    if (boundingBoxData == null || changed) {
                        boundingBoxData = Object3DBuilder.buildBoundingBox(objData)
                        boundingBoxes[objData] = boundingBoxData
                    }
                    val boundingBoxDrawer = drawer.boundingBoxDrawer
                    boundingBoxDrawer.draw(
                        boundingBoxData, projectionMatrix, viewMatrix, -1,
                        lightPosInWorldSpace, colorMask, cameraPosInWorldSpace
                    )
                }

                // Draw normals
                if (scene.isDrawNormals) {
                    var normalData = normals[objData]
                    if (normalData == null || changed) {
                        normalData = Object3DBuilder.buildFaceNormals(objData)
                        if (normalData != null) {
                            // it can be null if object isnt made of triangles
                            normals[objData] = normalData
                        }
                    }
                    if (normalData != null) {
                        val normalsDrawer = drawer.getDrawer(
                            normalData, false, false, scene.isDoAnimation,
                            false
                        )
                        animator.update(normalData, scene.isShowBindPose)
                        normalsDrawer.draw(
                            normalData, projectionMatrix, viewMatrix, -1, null,
                            lightPosInWorldSpace, cameraPosInWorldSpace
                        )
                    }
                }

                // TODO: enable this only when user wants it
                // obj3D.drawVectorNormals(result, viewMatrix);
            } catch (ex: Exception) {
                Log.e(
                    "ModelRenderer",
                    "There was a problem rendering the object '" + objData!!.id + "':" + ex.message,
                    ex
                )
            }
        }
    }

    fun getModelViewMatrix(): FloatArray {
        return viewMatrix
    }

    companion object {
        private val TAG = ModelRenderer::class.java.name

        /**
         * Add 0.5f to the alpha component to the global shader so we can see through the skin
         */
        private val BLENDING_FORCED_MASK_COLOR =
            floatArrayOf(1.0f, 1.0f, 1.0f, 0.5f)

        // frustrum - nearest pixel
        private const val near = 1f

        // frustrum - fartest pixel
        private const val far = 100f

        // stereoscopic variables
        private const val EYE_DISTANCE = 0.64f
        private val COLOR_RED = floatArrayOf(1.0f, 0.0f, 0.0f, 1f)
        private val COLOR_BLUE = floatArrayOf(0.0f, 1.0f, 0.0f, 1f)
    }

    /**
     * Construct a new renderer for the specified surface view
     *
     * @param modelSurfaceView
     * the 3D window
     */
    init {
        // This component will draw the actual models using OpenGL
        drawer = DrawerFactory(main.context)
    }
}