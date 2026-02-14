package org.rw3h4.echonotex.ui.voice.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.rw3h4.echonotex.ui.theme.EchoNoteTheme

class RecordVoiceNoteActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            setComposeContent()
        } else {
            Toast.makeText(this, "Permission denied. Cannot record audio.",
                Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                setComposeContent()
            } else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setComposeContent() {
        setContent {
            EchoNoteTheme {
                RecordVoiceNoteScreen(
                    onClose = { finish() },
                    onSaveFinished = {
                        Toast.makeText(this, "Voice Note Saved.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}
