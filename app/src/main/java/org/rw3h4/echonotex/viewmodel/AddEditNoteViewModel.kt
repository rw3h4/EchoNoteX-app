package org.rw3h4.echonotex.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.rw3h4.echonotex.data.local.model.Category
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.repository.NoteRepository

class AddEditNoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository = NoteRepository(application)

    val allCategories: LiveData<List<Category>> = repository.allCategories

    private val _saveFinished = MutableLiveData<Boolean>()
    val saveFinished: LiveData<Boolean> = _saveFinished

    private val _imageToInsert = MutableSharedFlow<String>()
    val imageToInsert = _imageToInsert.asSharedFlow()

    fun saveNote(
        existingNote: Note?,
        title: String,
        content: String,
        categoryName: String,
        userId: String
    ) {
        if (title.isBlank()) {
            return
        }

        val finalCategoryName = if (categoryName.isBlank()) "None" else categoryName

        val noteToSave = if (existingNote == null) {
            Note(title, content, 0, userId)
        } else {
            Note(
                existingNote.id,
                title,
                content,
                existingNote.categoryId,
                existingNote.timestamp,
                System.currentTimeMillis(),
                existingNote.isPinned,
                Note.NOTE_TYPE_TEXT,
                null,
                0,
                userId
            )
        }

        viewModelScope.launch {
            repository.saveNoteWithCategory(noteToSave, finalCategoryName)
            _saveFinished.postValue(true)
        }
    }

    fun onSaveComplete() {
        _saveFinished.value = false
    }

    fun onImageSelected(uri: String) {
        viewModelScope.launch {
            _imageToInsert.emit(uri)
        }
    }
}
