package org.rw3h4.echonotex.ui.note;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.Menu;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import coil3.ImageLoader;
import coil3.request.ImageRequest;
import coil3.target.ImageViewTarget;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.rw3h4.echonotex.R;
import org.rw3h4.echonotex.adapter.NoteAdapter;
import org.rw3h4.echonotex.data.local.model.Category;
import org.rw3h4.echonotex.data.local.model.Note;
import org.rw3h4.echonotex.data.local.model.NoteWithCategory;
import org.rw3h4.echonotex.databinding.ActivityNotesBinding;
import org.rw3h4.echonotex.databinding.MiniPlayerBinding;
import org.rw3h4.echonotex.ui.auth.LoginActivity;

import org.rw3h4.echonotex.ui.voice.record.RecordVoiceNoteActivity;
import org.rw3h4.echonotex.ui.voice.speech2text.DictateNoteActivity;
import org.rw3h4.echonotex.viewmodel.MediaPlayerViewModel;
import org.rw3h4.echonotex.viewmodel.NotesViewModel;
import org.rw3h4.echonotex.ui.voice.VoiceOptionsBottomSheetFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class NotesActivity extends AppCompatActivity implements VoiceOptionsBottomSheetFragment.VoiceOptionsListener {

    private ActivityNotesBinding binding;
    private MiniPlayerBinding miniPlayerBinding;

    private NotesViewModel notesViewModel;
    private NoteAdapter adapter;
    private MediaPlayerViewModel mediaPlayerViewModel;

    private LiveData<List<Note>> searchResultsLiveData;
    private Observer<List<Note>> searchObserver;

    private final ActivityResultLauncher<Intent> dictateNoteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() !=  null) {
                    String transcribedText = result.getData().getStringExtra("transcribed_text");
                    if (transcribedText != null) {
                        Intent intent = new Intent(NotesActivity.this, AddEditNoteActivity.class);
                        intent.putExtra("transcribed_text", transcribedText);
                        startActivity(intent);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityNotesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        miniPlayerBinding = binding.miniPlayerLayoutContainer;

        notesViewModel = new ViewModelProvider(this).get(NotesViewModel.class);
        mediaPlayerViewModel = new ViewModelProvider(this).get(MediaPlayerViewModel.class);
        notesViewModel.loadNotesForCurrentUser();

        setupNavigationDrawer();
        setupRecyclerView();
        setupCategoryTabs();
        setupSearchBar();
        observeViewModel();
        observeMediaPlayer();

        binding.addNoteBtn.setOnClickListener(v -> {
            Intent intent = new Intent(NotesActivity.this, AddEditNoteActivity.class);
            startActivity(intent);
        });

        binding.voiceRecordButton.setOnClickListener(v -> {
            VoiceOptionsBottomSheetFragment bottomSheet = VoiceOptionsBottomSheetFragment.newInstance();
            bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
        });
    }

    private void observeMediaPlayer() {
        mediaPlayerViewModel.getCurrentNote().observe(this, note -> {
            if (note != null) {
                miniPlayerBinding.miniPlayerTitle.setText(note.getTitle());
                if (miniPlayerBinding.miniPlayerContainer.getVisibility() == View.GONE) {
                    Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
                    miniPlayerBinding.miniPlayerContainer.startAnimation(slideUp);
                    miniPlayerBinding.miniPlayerContainer.setVisibility(View.VISIBLE);
                }
            } else {
                if (miniPlayerBinding.miniPlayerContainer.getVisibility() == View.VISIBLE) {
                    Animation slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down);
                    slideDown.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {}

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            miniPlayerBinding.miniPlayerContainer.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {}
                    });
                    miniPlayerBinding.miniPlayerContainer.startAnimation(slideDown);
                }
            }
        });

        mediaPlayerViewModel.isPlaying().observe(this, isPlaying -> {
            if (isPlaying) {
                miniPlayerBinding.miniPlayerPlayPauseButton.setImageResource(R.drawable.ic_pause_circle);
            } else {
                miniPlayerBinding.miniPlayerPlayPauseButton.setImageResource(R.drawable.ic_play_circle);
            }
        });

        mediaPlayerViewModel.getTotalDuration().observe(this, duration -> {
            if (duration != null && duration > 0) {
                miniPlayerBinding.miniPlayerSeekBar.setMax(duration.intValue());
            }
        });

        mediaPlayerViewModel.getCurrentPosition().observe(this, position -> {
            if (position == null) return;

            miniPlayerBinding.miniPlayerSeekBar.setProgress(position.intValue());

            Long totalDuration = mediaPlayerViewModel.getTotalDuration().getValue();
            if (totalDuration != null && totalDuration > 0) {
                miniPlayerBinding.miniPlayerElapsedTime.setText(formatTime(position));
                miniPlayerBinding.miniPlayerRemainingTime.setText("-" + formatTime(totalDuration - position));
            } else {
                miniPlayerBinding.miniPlayerElapsedTime.setText("0:00");
                miniPlayerBinding.miniPlayerRemainingTime.setText("-0:00");
            }
        });

        miniPlayerBinding.miniPlayerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaPlayerViewModel.seekTo((long) progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        miniPlayerBinding.miniPlayerPlayPauseButton.setOnClickListener(v -> {
            Boolean isPlaying = mediaPlayerViewModel.isPlaying().getValue();
            if (isPlaying != null && isPlaying) {
                mediaPlayerViewModel.pause();
            } else {
                mediaPlayerViewModel.resume();
            }
        });

        miniPlayerBinding.miniPlayerCloseButton.setOnClickListener(v -> {
            mediaPlayerViewModel.stop();
        });
        
    }


    private String formatTime(Long millis) {
        if (millis == null || millis < 0) return "0:00";

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
    }

    private void setupNavigationDrawer() {
        // Use the binding object to access views
        binding.menuButton.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));
        updateNavHeader();
        binding.navigationView.setNavigationItemSelectedListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(NotesActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void updateNavHeader() {
        // Use the binding object to access views
        View headerView = binding.navigationView.getHeaderView(0);
        TextView headerUserName = headerView.findViewById(R.id.nav_header_user_name);
        TextView headerUserEmail = headerView.findViewById(R.id.nav_header_user_email);
        ImageView navHeaderAvatar = headerView.findViewById(R.id.nav_header_user_avatar);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Use the binding object here for the NavigationView
        Menu navMenu = binding.navigationView.getMenu();

        if (user != null) {
            if (user.isAnonymous()) {
                headerUserName.setText("Guest User");
                headerUserEmail.setVisibility(View.GONE);
                navMenu.findItem(R.id.nav_login).setVisible(true);
                navMenu.findItem(R.id.nav_logout).setVisible(false);
            } else {
                String name = user.getDisplayName();
                String email = user.getEmail();
                Uri photoUrl = user.getPhotoUrl();
                headerUserName.setText(name != null && !name.isEmpty() ? name : "User");
                headerUserEmail.setText(email);
                headerUserEmail.setVisibility(View.VISIBLE);
                if (photoUrl != null) {
                    ImageLoader imageLoader = new ImageLoader.Builder(this).build();
                    imageLoader.enqueue(new ImageRequest.Builder(this)
                            .data(photoUrl)
                            .target(new ImageViewTarget(binding.userAvatarImageView))
                            .build());
                    imageLoader.enqueue(new ImageRequest.Builder(this)
                            .data(photoUrl)
                            .target(new ImageViewTarget(navHeaderAvatar))
                            .build());
                }
                navMenu.findItem(R.id.nav_login).setVisible(false);
                navMenu.findItem(R.id.nav_logout).setVisible(true);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new NoteAdapter(new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Note note) {
                if (note.getNoteType().equals(Note.NOTE_TYPE_VOICE)) {
                    mediaPlayerViewModel.play(note);
                } else {
                    NoteWithCategory item = findNoteWithCategoryById(note.getId());
                    if (item == null) return;
                    Intent intent = new Intent(NotesActivity.this, ReadNoteActivity.class);
                    intent.putExtra("NOTE_WITH_CATEGORY_EXTRA", item);
                    startActivity(intent);
                }
            }

            @Override
            public void onNoteLongClick(Note note) {
                notesViewModel.updatePinStatus(note.getId(), !note.isPinned());
            }

            @Override
            public void onPlayVoiceNoteClick(Note note, ImageButton playButton) {
                mediaPlayerViewModel.play(note);
            }
        });

        // Use the binding object to access the RecyclerView
        binding.noteRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.noteRecyclerView.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove( @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped( @NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    NoteWithCategory itemToDelete = adapter.getCurrentList().get(position);
                    notesViewModel.delete(itemToDelete.getNote());
                }
            }
        }).attachToRecyclerView(binding.noteRecyclerView);
    }

    private NoteWithCategory findNoteWithCategoryById(int noteId) {
        for (NoteWithCategory item : adapter.getCurrentList()) {
            if (item.getNote().getId() == noteId) {
                return item;
            }
        }
        return null;
    }

    private void setupCategoryTabs() {
        // Use the binding object to access the TabLayout
        binding.categoryTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getTag() != null) {
                    int categoryId = (int) tab.getTag();
                    notesViewModel.setCategoryFilter(categoryId);
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSearchBar() {
        searchObserver = notes -> {
            List<Category> allCategories = notesViewModel.allCategories.getValue();
            if (notes != null && allCategories != null) {
                List<NoteWithCategory> displayList = convertToNoteWithCategory(notes, allCategories);
                adapter.submitList(displayList);
                binding.emptyPlaceholder.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
            }
        };

        // Use the binding object to access the search bar
        binding.searchBarEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchResultsLiveData != null) {
                    searchResultsLiveData.removeObserver(searchObserver);
                }
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    observeViewModel();
                } else {
                    notesViewModel.getNotesWithCategories().removeObservers(NotesActivity.this);
                    searchResultsLiveData = notesViewModel.searchNotes(query);
                    searchResultsLiveData.observe(NotesActivity.this, searchObserver);
                }
            }
        });
    }

    private void observeViewModel() {
        notesViewModel.getNotesWithCategories().observe(this, notesWithCategories -> {
            adapter.submitList(notesWithCategories);
            binding.emptyPlaceholder.setVisibility(notesWithCategories.isEmpty() ? View.VISIBLE : View.GONE);
        });
        notesViewModel.allCategories.observe(this, this::updateCategoryTabs);

        miniPlayerBinding.miniPlayerCloseButton.setOnClickListener(v -> {
            mediaPlayerViewModel.stop();
        });

        
    }

    private void updateCategoryTabs(List<Category> categories) {
        // Use the binding object to access the TabLayout
        TabLayout.Tab selectedTab = (binding.categoryTabs.getTabAt(binding.categoryTabs.getSelectedTabPosition()));
        Object selectedTag = selectedTab != null ? selectedTab.getTag() : -1;

        binding.categoryTabs.removeAllTabs();

        TabLayout.Tab allTab = binding.categoryTabs.newTab().setText("All");
        allTab.setTag(-1);
        binding.categoryTabs.addTab(allTab);

        for (Category category : categories) {
            if (!category.getName().equalsIgnoreCase("None")) {
                TabLayout.Tab tab = binding.categoryTabs.newTab().setText(category.getName());
                tab.setTag(category.getId());
                binding.categoryTabs.addTab(tab);
            }
        }

        for (int i = 0; i < binding.categoryTabs.getTabCount(); i++) {
            TabLayout.Tab tabAtIndex = binding.categoryTabs.getTabAt(i);
            if (tabAtIndex != null && Objects.equals(tabAtIndex.getTag(), selectedTag)) {
                tabAtIndex.select();
                break;
            }
        }
    }

    private List<NoteWithCategory> convertToNoteWithCategory(List<Note> notes, List<Category> categories) {
        Map<Integer, String> categoryMap = new HashMap<>();
        for (Category category : categories) {
            categoryMap.put(category.getId(), category.getName());
        }
        List<NoteWithCategory> result = new ArrayList<>();
        for (Note note : notes) {
            String categoryName = categoryMap.getOrDefault(note.getCategoryId(), "None");
            result.add(new NoteWithCategory(note, Objects.requireNonNull(categoryName)));
        }
        return result;
    }

    @Override
    public void onRecordVoiceNoteClicked() {
        Intent intent = new Intent(this, RecordVoiceNoteActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDictateNoteClicked() {
        Intent intent = new Intent(this, DictateNoteActivity.class);
        dictateNoteLauncher.launch(intent);
    }
}