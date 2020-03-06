package com.headit74.animation3d.demo

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast
import com.headit74.animation3d.activity.ModelActivity
import org.andresoviedo.util.android.ContentUtils
import java.util.*

/**
 * This class loads a 3D scene as an example of what can be done with the app
 *
 */
class ExampleSceneLoader(modelActivity: ModelActivity?) : SceneLoader(modelActivity!!) {
    // TODO: fix this warning
    @SuppressLint("StaticFieldLeak")
    override fun init() {
        super.init()
        object : AsyncTask<Void?, Void?, Void?>() {
            var dialog = ProgressDialog(parent)
            var errors: MutableList<Exception> =
                ArrayList()

            override fun onPreExecute() {
                super.onPreExecute()
                dialog.setCancelable(false)
                dialog.setMessage("Loading demo...")
                dialog.show()
            }

            protected override fun doInBackground(vararg params: Void?): Void? {
                try {
                    // 3D Axis
                    isDrawAxis = true

                    // Set up ContentUtils so referenced materials and/or textures could be find
                    ContentUtils.setThreadActivity(parent)
                    ContentUtils.provideAssets(parent)
                } catch (ex: Exception) {
                    errors.add(ex)
                } finally {
                    ContentUtils.setThreadActivity(null)
                    ContentUtils.clearDocumentsProvided()
                }
                return null
            }

            override fun onPostExecute(result: Void?) {
                super.onPostExecute(result)
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                if (!errors.isEmpty()) {
                    val msg =
                        StringBuilder("There was a problem loading the data")
                    for (error in errors) {
                        Log.e("Example", error.message, error)
                        msg.append(
                            """

    ${error.message}
    """.trimIndent()
                        )
                    }
                    Toast.makeText(
                        parent.applicationContext,
                        msg,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.execute()
    }
}