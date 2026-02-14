package org.rw3h4.echonotex.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.rw3h4.echonotex.data.local.model.Note;

import java.util.Objects;
public class NoteFirestore {
    @Nullable
    private String firestoreId;
    @NonNull
    private String title;
    @Nullable
    private String content;

    private int categoryId;
    private long timestamp;
    private long lastEdited;
    private boolean isPinned;
    @NonNull
    private String noteType;
    @Nullable
    private String filePath;
    private long duration;

    @Nullable
    private String userId;

    public NoteFirestore() {
        this.title = "";
        this.noteType = Note.NOTE_TYPE_TEXT;
        this.content = null;
        this.filePath = null;
    }

    public NoteFirestore(@Nullable String firestoreId, @NonNull String title,
                         @Nullable String content, int categoryId, long timestamp, long lastEdited,
                         boolean isPinned, @NonNull String noteType, @Nullable String filePath,
                         long duration, @Nullable String userId) {
        this.firestoreId = firestoreId;
        this.title = title;
        this.content = content;
        this.categoryId = categoryId;
        this.timestamp = timestamp;
        this.lastEdited = lastEdited;
        this.isPinned = isPinned;
        this.noteType = noteType;
        this.filePath = filePath;
        this.duration = duration;
        this.userId = userId;
    }

    @Nullable
    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(@Nullable String firestoreId) { this.firestoreId = firestoreId; }

    @NonNull
    public String getTitle() { return title; }
    public void setTitle(@Nullable String title) { this.title = title; }

    @Nullable
    public String getContent() { return content; }
    public void setContent(@Nullable String content) { this.content = content; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getLastEdited() { return lastEdited; }
    public void setLastEdited(long lastEdited) { this.lastEdited = lastEdited; }

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    @NonNull
    public String getNoteType() { return noteType; }
    public void setNoteType(@NonNull String noteType) { this.noteType = noteType; }

    @Nullable
    public String getFilePath() { return filePath; }
    public void setFilePath(@Nullable String filePath) { this.filePath = filePath; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    @Nullable
    public String getUserId() { return userId; }
    public void setUserId(@Nullable String userId) { this.userId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NoteFirestore that = (NoteFirestore) o;
        return categoryId == that.categoryId &&
                timestamp == that.timestamp &&
                lastEdited == that.lastEdited &&
                isPinned == that.isPinned &&
                duration == that.duration &&
                title.equals(that.title) &&
                Objects.equals(content, that.content) &&
                noteType.equals(that.noteType) &&
                Objects.equals(filePath, that.filePath) &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, content, categoryId, timestamp,
                lastEdited, isPinned, noteType, filePath, duration, userId);
    }
}
