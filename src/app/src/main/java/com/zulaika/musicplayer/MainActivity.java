package com.zulaika.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.zulaika.musicplayer.activities.PlayerDialog;
import com.zulaika.musicplayer.activities.QueueDialog;
import com.zulaika.musicplayer.adapter.MainPagerAdapter;
import com.zulaika.musicplayer.dialogs.SleepTimerDialog;
import com.zulaika.musicplayer.dialogs.SleepTimerDisplayDialog;
import com.zulaika.musicplayer.helper.MusicLibraryHelper;
import com.zulaika.musicplayer.helper.ThemeHelper;
import com.zulaika.musicplayer.listener.MusicSelectListener;
import com.zulaika.musicplayer.listener.PlayerDialogListener;
import com.zulaika.musicplayer.listener.SleepTimerSetListener;
import com.zulaika.musicplayer.model.Music;
import com.zulaika.musicplayer.player.PlayerBuilder;
import com.zulaika.musicplayer.player.PlayerListener;
import com.zulaika.musicplayer.player.PlayerManager;
import com.zulaika.musicplayer.viewmodel.MainViewModel;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements MusicSelectListener, PlayerListener, View.OnClickListener, SleepTimerSetListener, PlayerDialogListener {

    public static boolean isSleepTimerRunning;
    public static MutableLiveData<Long> sleepTimerTick;
    private static CountDownTimer sleepTimer;
    private RelativeLayout playerView;
    private ImageView albumArt;
    private TextView songName;
    private TextView songDetails;
    private ImageButton play_pause;
    private LinearProgressIndicator progressIndicator;
    private PlayerDialog playerDialog;
    private QueueDialog queueDialog;
    private PlayerBuilder playerBuilder;
    private PlayerManager playerManager;
    private boolean albumState;
    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeHelper.getTheme(MPPreferences.getTheme(MainActivity.this)));
        AppCompatDelegate.setDefaultNightMode(MPPreferences.getThemeMode(MainActivity.this));
        setContentView(R.layout.activity_main);
        MPConstants.musicSelectListener = this;

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        if (hasReadStoragePermission(MainActivity.this)) {
            fetchMusicList();
            setUpUiElements();
        } else
            manageStoragePermission(MainActivity.this);

        albumState = MPPreferences.getAlbumRequest(this);

        MaterialCardView playerLayout = findViewById(R.id.player_layout);
        albumArt = findViewById(R.id.albumArt);
        progressIndicator = findViewById(R.id.song_progress);
        playerView = findViewById(R.id.player_view);
        songName = findViewById(R.id.song_title);
        songDetails = findViewById(R.id.song_details);
        play_pause = findViewById(R.id.control_play_pause);
        ImageButton queue = findViewById(R.id.control_queue);

        play_pause.setOnClickListener(this);
        playerLayout.setOnClickListener(this);
        queue.setOnClickListener(this);
    }

    private void setPlayerView() {
        if (playerManager != null && playerManager.isPlaying()) {
            playerView.setVisibility(View.VISIBLE);
            onMusicSet(playerManager.getCurrentMusic());
        }
    }

    private void fetchMusicList() {
        new Handler().post(() -> {
            List<Music> musicList = MusicLibraryHelper.fetchMusicLibrary(MainActivity.this);
            viewModel.setSongsList(musicList);
            viewModel.parseFolderList(musicList);
        });
    }

    public void setUpUiElements() {
        playerBuilder = new PlayerBuilder(MainActivity.this, this);
        MainPagerAdapter sectionsPagerAdapter = new MainPagerAdapter(
                getSupportFragmentManager(), this);

        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        viewPager.setOffscreenPageLimit(3);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        for (int i = 0; i < tabs.getTabCount(); i++) {
            TabLayout.Tab tab = tabs.getTabAt(i);
            if (tab != null) {
                tab.setIcon(MPConstants.TAB_ICONS[i]);
            }
        }
    }

    public void manageStoragePermission(Activity context) {
        if (!hasReadStoragePermission(context)) {
            // required a dialog?
            if (ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Requesting permission")
                        .setMessage("Enable storage permission for accessing the media files.")
                        .setPositiveButton("Accept", (dialog, which) -> askReadStoragePermission(context)).show();
            } else
                askReadStoragePermission(context);
        }
    }

    public boolean hasReadStoragePermission(Activity context) {
        return (
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        );
    }

    public void askReadStoragePermission(Activity context) {
        ActivityCompat.requestPermissions(
                context,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                MPConstants.PERMISSION_READ_STORAGE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ThemeHelper.applySettings(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playerBuilder != null)
            playerBuilder.unBindService();

        if (playerDialog != null)
            playerDialog.dismiss();

        if (queueDialog != null)
            queueDialog.dismiss();
    }

    @Override
    public void playQueue(List<Music> musicList, boolean shuffle) {
        if (shuffle) {
            Collections.shuffle(musicList);
        }

        if (musicList.size() > 0) {
            playerManager.setMusicList(musicList);
            setPlayerView();
        }
    }

    @Override
    public void addToQueue(List<Music> musicList) {
        if (musicList.size() > 0) {
            if (playerManager != null && playerManager.isPlaying())
                playerManager.addMusicQueue(musicList);
            else if (playerManager != null)
                playerManager.setMusicList(musicList);

            setPlayerView();
        }
    }

    @Override
    public void refreshMediaLibrary() {
        fetchMusicList();
    }

    @Override
    public void onPrepared() {
        playerManager = playerBuilder.getPlayerManager();
        setPlayerView();
    }

    @Override
    public void onStateChanged(int state) {
        if (state == State.PLAYING)
            play_pause.setImageResource(R.drawable.ic_controls_pause);
        else
            play_pause.setImageResource(R.drawable.ic_controls_play);
    }

    @Override
    public void onPositionChanged(int position) {
        progressIndicator.setProgress(position);
    }

    @Override
    public void onMusicSet(Music music) {
        songName.setText(music.title);
        songDetails.setText(
                String.format(Locale.getDefault(), "%s • %s",
                        music.artist, music.album));
        playerView.setVisibility(View.VISIBLE);

        if (albumState)
            Glide.with(getApplicationContext())
                    .load(music.albumArt)
                    .centerCrop()
                    .into(albumArt);

        if (playerManager != null && playerManager.isPlaying())
            play_pause.setImageResource(R.drawable.ic_controls_pause);
        else
            play_pause.setImageResource(R.drawable.ic_controls_play);
    }

    @Override
    public void onPlaybackCompleted() {
    }

    @Override
    public void onRelease() {
        playerView.setVisibility(View.GONE);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.control_play_pause)
            playerManager.playPause();

        else if (id == R.id.control_queue)
            setUpQueueDialogHeadless();

        else if (id == R.id.player_layout)
            setUpPlayerDialog();
    }

    private void setUpPlayerDialog() {
        playerDialog = new PlayerDialog(this, playerManager, this);
        playerDialog.show();
    }

    @Override
    public void setTimer(int minutes) {
        if (!isSleepTimerRunning) {
            isSleepTimerRunning = true;
            sleepTimer = new CountDownTimer(minutes * 60 * 1000L, 1000) {
                @Override
                public void onTick(long l) {
                    if (sleepTimerTick == null) sleepTimerTick = new MutableLiveData<>();
                    sleepTimerTick.postValue(l);
                }

                @Override
                public void onFinish() {
                    isSleepTimerRunning = false;
                    playerManager.pauseMediaPlayer();
                }
            }.start();
        }
    }

    @Override
    public void cancelTimer() {
        if (isSleepTimerRunning && sleepTimer != null) {
            isSleepTimerRunning = false;
            sleepTimer.cancel();
        }
    }

    @Override
    public MutableLiveData<Long> getTick() {
        return sleepTimerTick;
    }


    @Override
    public void queueOptionSelect() {
        setUpQueueDialog();
    }

    @Override
    public void sleepTimerOptionSelect() {
        setUpSleepTimerDialog();
    }

    private void setUpQueueDialog() {
        queueDialog = new QueueDialog(MainActivity.this, playerManager.getPlayerQueue());
        queueDialog.setOnDismissListener(v -> {
            if(!this.isDestroyed()) {
                playerDialog.show();
            }
        });

        playerDialog.dismiss();
        queueDialog.show();
    }

    private void setUpQueueDialogHeadless() {
        queueDialog = new QueueDialog(MainActivity.this, playerManager.getPlayerQueue());
        queueDialog.show();
    }

    private void setUpSleepTimerDialog() {
        if (MainActivity.isSleepTimerRunning) {
            setUpSleepTimerDisplayDialog();
            return;
        }
        SleepTimerDialog sleepTimerDialog = new SleepTimerDialog(MainActivity.this, this);
        sleepTimerDialog.setOnDismissListener(v -> {
            if(!this.isDestroyed()) playerDialog.show();
        });

        playerDialog.dismiss();
        sleepTimerDialog.show();
    }

    private void setUpSleepTimerDisplayDialog() {
        SleepTimerDisplayDialog sleepTimerDisplayDialog = new SleepTimerDisplayDialog(MainActivity.this, this);
        sleepTimerDisplayDialog.setOnDismissListener(v -> {
            if(!this.isDestroyed()) playerDialog.show();
        });

        playerDialog.dismiss();
        sleepTimerDisplayDialog.show();
    }
}