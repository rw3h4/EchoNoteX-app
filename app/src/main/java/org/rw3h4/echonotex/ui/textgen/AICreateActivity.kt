package org.rw3h4.echonotex.ui.textgen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.rw3h4.echonotex.ui.theme.EchoNoteTheme
import org.rw3h4.echonotex.ui.note.AddEditNoteActivity

class AICreateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EchoNoteTheme {
                AICreateScreen(
                    onNavigateBack = { finish() },
                    onUseText = { text ->
                        val intent = Intent(this, AddEditNoteActivity::class.java).apply {
                            putExtra("transcribed_text", text)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}
