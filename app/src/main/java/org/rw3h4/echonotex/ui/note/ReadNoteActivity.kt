package org.rw3h4.echonotex.ui.note

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.data.local.model.NoteWithCategory

class ReadNoteActivity : AppCompatActivity() {

    private lateinit var currentNote: Note
    private lateinit var currentCategoryName: String

    private val editNoteLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val noteWithCategory: NoteWithCategory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("NOTE_WITH_CATEGORY_EXTRA", NoteWithCategory::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("NOTE_WITH_CATEGORY_EXTRA")
        }

        if (noteWithCategory != null) {
            currentNote = noteWithCategory.note
            currentCategoryName = noteWithCategory.categoryName

            setContent {
                ReadNoteScreen(
                    note = currentNote,
                    categoryName = currentCategoryName,
                    onNavigateUp = { finish() },
                    onEditClick = {
                        val intent = Intent(this, AddEditNoteActivity::class.java)
                        intent.putExtra("note_to_edit", currentNote)
                        editNoteLauncher.launch(intent)
                    }
                )
            }
        } else {
            finish()
        }
    }
}
