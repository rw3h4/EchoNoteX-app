package org.rw3h4.echonotex.adapter;

import android.text.Html;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.rw3h4.echonotex.R;
import org.rw3h4.echonotex.data.local.model.Note;
import org.rw3h4.echonotex.data.local.model.NoteWithCategory;
import org.rw3h4.echonotex.util.note.CoilImageGetter;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// Moved to ListAdapter (from RecyclerView.Adapter) to simplify code when using DiffUtil
public class NoteAdapter extends ListAdapter<NoteWithCategory, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_TEXT = 1;
    private static final int VIEW_TYPE_VOICE = 2;
    private final OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
        void onNoteLongClick(Note note);
        void onPlayVoiceNoteClick(Note note, ImageButton playButton);
    }

    public NoteAdapter(OnNoteClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    //Changed the DiffUtil Callback to a static final field
    private static final DiffUtil.ItemCallback<NoteWithCategory> DIFF_CALLBACK = new DiffUtil.ItemCallback<NoteWithCategory>() {
        @Override
        public boolean areItemsTheSame(@NonNull NoteWithCategory oldItem, @NonNull NoteWithCategory newItem) {
            return oldItem.getNote().getId() == newItem.getNote().getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull NoteWithCategory oldItem, @NonNull NoteWithCategory newItem) {
            return oldItem.getNote().equals(newItem.getNote()) &&
                    oldItem.getCategoryName().equals(newItem.getCategoryName());
        }
    };

    @Override
    public int getItemViewType(int position) {
        Note note = getItem(position).getNote();
        if (Note.NOTE_TYPE_VOICE.equals(note.getNoteType())) {
            return VIEW_TYPE_VOICE;
        } else {
            return VIEW_TYPE_TEXT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_VOICE) {
            View view = inflater.inflate(R.layout.note_item_voice, parent, false);
            return new VoiceNoteViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.note_item_text, parent, false);
            return new TextNoteViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NoteWithCategory currentItem = getItem(position);
        if (getItemViewType(position) == VIEW_TYPE_VOICE) {
            ((VoiceNoteViewHolder) holder).bind(currentItem, listener);
        } else {
            ((TextNoteViewHolder) holder).bind(currentItem, listener);
        }
    }

    private String formatDuration(long millis) {
        return String.format(Locale.getDefault(), "%01d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }

    class TextNoteViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, contentTextView, categoryTextView, timestampTextView;
        ImageView pinIcon;

        TextNoteViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView =  itemView.findViewById(R.id.note_title);
            categoryTextView = itemView.findViewById(R.id.note_category);
            contentTextView = itemView.findViewById(R.id.note_content);
            timestampTextView = itemView.findViewById(R.id.note_timestamp);
            pinIcon = itemView.findViewById(R.id.pin_icon_imageView);
        }

        void bind(final NoteWithCategory item, final OnNoteClickListener listener) {
            Note note = item.getNote();
            titleTextView.setText(note.getTitle());
            categoryTextView.setText(item.getCategoryName());
            pinIcon.setActivated(note.isPinned());

            CoilImageGetter imageGetter = new CoilImageGetter(itemView.getContext(), contentTextView);
            if (note.getContent() != null) {
                CharSequence spannedText = Html.fromHtml(note.getContent(),
                        Html.FROM_HTML_MODE_COMPACT, imageGetter, null);
                contentTextView.setText(spannedText);
                contentTextView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            long timeToUse = note.getLastEdited() > 0 ? note.getLastEdited() : note.getTimestamp();
            String formattedTime = DateFormat.format("dd MMMM, hh:mm a",
                    new Date(timeToUse)).toString();
            timestampTextView.setText(formattedTime);

            itemView.setOnClickListener(v -> listener.onNoteClick(note));
            itemView.setOnLongClickListener(v -> {
                listener.onNoteLongClick(note);
                return true;
            });
        }
    }

    class VoiceNoteViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, categoryTextView, durationTextView, timestampTextView;
        ImageView pinIcon;
        ImageButton playPauseButton;

        VoiceNoteViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView =  itemView.findViewById(R.id.note_title);
            categoryTextView = itemView.findViewById(R.id.note_category);
            durationTextView = itemView.findViewById(R.id.text_voice_duration);
            pinIcon = itemView.findViewById(R.id.pin_icon_imageView);
            playPauseButton = itemView.findViewById(R.id.button_play_pause);
            timestampTextView = itemView.findViewById(R.id.voice_note_timestamp);
        }

        void bind(final NoteWithCategory  item, final OnNoteClickListener  listener) {
            Note note = item.getNote();
            titleTextView.setText(note.getTitle());
            categoryTextView.setText(item.getCategoryName());
            durationTextView.setText(formatDuration(note.getDuration()));

            pinIcon.setActivated(note.isPinned());

            long timeToUse = note.getLastEdited() > 0  ? note.getLastEdited() : note.getTimestamp();
            String formattedTime = DateFormat.format("dd MMM", new Date(timeToUse)).toString();
            timestampTextView.setText(formattedTime);

            itemView.setOnClickListener(v -> listener.onNoteClick(note));
            itemView.setOnLongClickListener(v -> {
                listener.onNoteLongClick(note);
                return true;
            });

            playPauseButton.setOnClickListener(v -> listener.onPlayVoiceNoteClick(note, playPauseButton));
        }
    }
}
