package org.rw3h4.echonotex.ui.textgen

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.rw3h4.echonotex.ui.theme.EchoNoteTheme

class ModelPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "ModelPickerActivity started")
        enableEdgeToEdge()
        setContent {
            EchoNoteTheme {
                ModelPickerScreen(
                    onNavigateBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val TAG = "ModelPicker"
    }
}
