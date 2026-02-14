package org.rw3h4.echonotex.ui.voice;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import org.rw3h4.echonotex.R;

public class VoiceOptionsBottomSheetFragment extends BottomSheetDialogFragment {

    public interface VoiceOptionsListener {
        void onRecordVoiceNoteClicked();
        void onDictateNoteClicked();
    }

    private VoiceOptionsListener mListener;

    public static VoiceOptionsBottomSheetFragment newInstance() {
        return new VoiceOptionsBottomSheetFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof VoiceOptionsListener) {
            mListener = (VoiceOptionsListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement VoiceOptionsListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_voice_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialCardView recordOption = view.findViewById(R.id.option_record_audio);
        MaterialCardView dictateOption = view.findViewById(R.id.option_dictate_note);

        recordOption.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onRecordVoiceNoteClicked();
            }
            dismiss();
        });

        dictateOption.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onDictateNoteClicked();
            }
            dismiss();
        });
    }
}
