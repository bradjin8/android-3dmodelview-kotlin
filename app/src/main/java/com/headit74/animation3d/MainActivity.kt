package com.headit74.animation3d


import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.headit74.animation3d.activity.ModelActivity
import org.andresoviedo.android_3d_model_engine.services.wavefront.WavefrontLoader
import org.andresoviedo.util.android.AndroidURLStreamHandlerFactory
import org.andresoviedo.util.android.ContentUtils
import java.net.URL


class MainActivity : AppCompatActivity() {
    private val loadModelParameters = mutableMapOf<String, Any>()
    private val REQUEST_CODE_OPEN_FILE = 1101
    private val REQUEST_CODE_OPEN_MATERIAL = 1102

    private val MODEL_TYPE_OBJ = 0
    private val MODEL_TYPE_DAE = 2

    private fun _registerCustomProtocol() {
        // Custom handler: org/andresoviedo/util/android/assets/Handler.class
        System.setProperty("java.protocol.handler.pkgs", "org.andresoviedo.util.android")
        URL.setURLStreamHandlerFactory(AndroidURLStreamHandlerFactory())
    }


    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _registerCustomProtocol()
        launchModelRendererActivity(Uri.parse("assets://$packageName/models/hands-moving.dae"))
        /*AssetUtils.createChooserDialog(
            this,
            "Select a model file",
            null,
            "models",
            "(?i).*\\.(obj|stl|dae)"
        ) { file: String? ->
            if (file != null) {
                ContentUtils.provideAssets(this)
                launchModelRendererActivity(Uri.parse("assets://$packageName/$file"))
            }
        }*/
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        ContentUtils.setThreadActivity(this)
        when (requestCode) {
            REQUEST_CODE_OPEN_FILE -> {
                if (resultCode != RESULT_OK) {
                    return
                }
                val uri: Uri = data?.data ?: return

                // save user selected model
                loadModelParameters["model"] = uri

                // detect model type
                if (uri.toString().toLowerCase().endsWith(".obj")) {
                    askForRelatedFiles(MODEL_TYPE_OBJ)
                } else if (uri.toString().toLowerCase().endsWith(".dae")) {
                    askForRelatedFiles(MODEL_TYPE_DAE)
                } else {
                    ContentUtils.showListDialog(
                        this,
                        "Select type",
                        arrayOf("Wavefront file (*.obj)")
                    ) { dialog: DialogInterface?, which: Int ->
                        askForRelatedFiles(which)
                    }
                }
            }
            else -> return
        }
    }

    private fun askForRelatedFiles(modelType: Int) {
        loadModelParameters["type"] = modelType
        when (modelType) {
            MODEL_TYPE_OBJ -> {
                val materialFile = WavefrontLoader.getMaterialLib(getUserSelectedModel())
                if (materialFile == null) {
                    launchModelRendererActivity(getUserSelectedModel()!!)

                } else {
                    ContentUtils.showDialog(
                        this, "Select material file", "This model references a model file (" +
                                "$materialFile). Please select it.", "OK", "Cancel"
                    )
                    { dialog: DialogInterface?, which: Int ->
                        when (which) {
                            DialogInterface.BUTTON_NEGATIVE -> launchModelRendererActivity(
                                getUserSelectedModel()!!
                            )
                            DialogInterface.BUTTON_POSITIVE -> {
                                loadModelParameters.put("file", materialFile)
                                askForFile(
                                    REQUEST_CODE_OPEN_MATERIAL, "*/*"
                                )
                            }
                        }
                    }
                }
            }
            MODEL_TYPE_DAE -> {
                launchModelRendererActivity(getUserSelectedModel()!!)
            }
        }
    }

    private fun askForFile(requestCode: Int, mimeType: String) {
        val target = ContentUtils.createGetContentIntent(mimeType)
        val intent = Intent.createChooser(target, "Select file")
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Error, Please install a file content provider", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun getUserSelectedModel(): Uri? {
        return loadModelParameters["model"] as Uri?
    }

    private fun launchModelRendererActivity(uri: Uri) {
        Log.i("MainActivity", "Launching renderer for '$uri'")
        val intent: Intent = Intent(this, ModelActivity::class.java)
        intent.putExtra("uri", uri.toString())
        intent.putExtra("immersiveMode", loadModelParameters["type"].toString())

        startActivity(intent)
    }
}
