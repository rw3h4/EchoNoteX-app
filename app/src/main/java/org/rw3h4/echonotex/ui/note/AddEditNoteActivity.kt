package org.rw3h4.echonotex.ui.note

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.viewmodel.AddEditNoteViewModel
import java.io.File

class AddEditNoteActivity : AppCompatActivity() {

    private lateinit var addEditNoteViewModel: AddEditNoteViewModel
    private var existingNote: Note? = null
    private var cameraImageUri: Uri? = null

    private val galleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { selectedImageUri ->
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(selectedImageUri, takeFlags)
                addEditNoteViewModel.onImageSelected(selectedImageUri.toString())
            }
        }
    }

    private val cameraLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            cameraImageUri?.let {
                addEditNoteViewModel.onImageSelected(it.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addEditNoteViewModel = ViewModelProvider(this)[AddEditNoteViewModel::class.java]

        existingNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("note_to_edit", Note::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("note_to_edit")
        }

        val transcribedText = intent.getStringExtra("transcribed_text")

        setContent {
            AddEditNoteScreen(
                viewModel = addEditNoteViewModel,
                existingNote = existingNote,
                initialContent = transcribedText,
                onSave = { title, content, category ->
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        addEditNoteViewModel.saveNote(existingNote, title, content, category, user.uid)
                    } else {
                        Toast.makeText(this, "Error: No user logged in.", Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigateUp = { finish() },
                onLaunchGallery = ::openGallery,
                onLaunchCamera = ::openCamera
            )
        }

        addEditNoteViewModel.saveFinished.observe(this) { isFinished ->
            if (isFinished == true) {
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                finish()
                addEditNoteViewModel.onSaveComplete()
            }
        }
    }

    private fun openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 200)
                return
            }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        galleryLauncher.launch(intent)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 201)
            return
        }

        val imageFile = File(cacheDir, "camera_image_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cameraLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 200 || requestCode == 201) && grantResults.isNotEmpty() && grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (requestCode == 200) openGallery() else openCamera()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
