package vn.khengandkhoi.musicplayer.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import vn.khengandkhoi.musicplayer.fragment.HomeFragment;
import vn.khengandkhoi.musicplayer.MusicService;
import vn.khengandkhoi.musicplayer.fragment.ProfileFragment;
import vn.khengandkhoi.musicplayer.R;
import vn.khengandkhoi.musicplayer.fragment.SearchFragment;
import vn.khengandkhoi.musicplayer.object.SongRemote;

@UnstableApi
public class MainActivity extends AppCompatActivity implements MusicService.Callback {

    private Fragment homeFrag = new HomeFragment();
    private Fragment searchFrag = new SearchFragment();
    private Fragment profileFrag = new ProfileFragment();
    private Fragment active;

    // Mini Playback UI
    private View miniPlayer;
    private ImageView imgTrack;
    private TextView tvSong, tvArtist;
    private ImageButton btnPrev, btnPlayPause, btnNext;

    private MusicService music;
    private boolean bound = false;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            music = ((MusicService.MusicBinder) b).getService();
            bound = true;
            music.addCallback(MainActivity.this);

            SongRemote current = music.getCurrent();
            if (current != null) {
                onMetaChanged(current, music.getIndex(), music.getDuration());
                // GỌI THỦ CÔNG ĐỂ CẬP NHẬT PROGRESS NGAY
                onPlayStateChanged(music.isPlaying(), music.getPosition(), music.getDuration());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            bound = false;
            music = null;
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottom = findViewById(R.id.bottomNav);

        // --- Setup mini playback ---
        miniPlayer = (View) findViewById(R.id.contentBox).getParent(); // nguyên thanh mini
        imgTrack = findViewById(R.id.imgTrack);
        tvSong = findViewById(R.id.tvSong);
        tvArtist = findViewById(R.id.tvArtist);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);

        miniPlayer.setVisibility(View.GONE); // mặc định chưa có bài → ẩn

        btnPrev.setOnClickListener(v -> {
            if (music != null) music.prev();
        });

        btnPlayPause.setOnClickListener(v -> {
            if (music != null) music.togglePlay();
        });

        btnNext.setOnClickListener(v -> {
            if (music != null) music.next();
        });

        // Bấm mini player → mở full PlayerActivity
        miniPlayer.setOnClickListener(v -> {
            Intent openPlayer = new Intent(this, PlayerActivity.class);
            startActivity(openPlayer);
            overridePendingTransition(R.anim.slide_in_up, R.anim.no_change);
        });


        // Setup fragments
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, profileFrag, "profile").hide(profileFrag).commit();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, searchFrag, "search").hide(searchFrag).commit();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, homeFrag, "home").commit();
        active = homeFrag;

        bottom.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.home) {
                switchTo(homeFrag);
                return true;
            }
            if (id == R.id.search) {
                switchTo(searchFrag);
                return true;
            }
            if (id == R.id.profile) {
                switchTo(profileFrag);
                return true;
            }
            return false;
        });

        bottom.setOnItemReselectedListener(i -> {
        });
    }

    private void switchTo(Fragment target) {
        if (active == target) return;
        getSupportFragmentManager().beginTransaction()
                .hide(active).show(target).commit();
        active = target;
    }

    // ====== Bind service ======
    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), conn, BIND_AUTO_CREATE);
        refreshPremiumFromServer();
        updateUiForPremium();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound && music != null) {
            music.removeCallback(MainActivity.this);
            unbindService(conn);
        }
        bound = false;
    }


    // ====== MusicService callback: update mini UI ======
    @Override
    public void onMetaChanged(SongRemote s, int index, long dur) {
        miniPlayer.setVisibility(View.VISIBLE);
        tvSong.setText(s.getTitle());
        tvArtist.setText(s.getAuthor());

        if (s.getCoverUrl() != null && !s.getCoverUrl().isEmpty()) {
            Glide.with(this).load(s.getCoverUrl()).into(imgTrack);
        } else {
            imgTrack.setImageResource(R.drawable.placeholder_album_light);
        }
    }

    @Override
    public void onPlayStateChanged(boolean playing, long pos, long dur) {
        btnPlayPause.setImageResource(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play
        );
    }

    private void cachePremium(long until) {
        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit().putLong("premiumUntil", until).apply();
    }

    private boolean isPremiumCached() {
        long until = getSharedPreferences("prefs", MODE_PRIVATE).getLong("premiumUntil", 0L);
        return System.currentTimeMillis() < until;
    }

    // Đồng bộ từ Firestore khi mở app
    private void refreshPremiumFromServer() {
        var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    Long until = doc.getLong("premiumUntil");
                    if (until != null) {
                        cachePremium(until);
                        updateUiForPremium();
                    }
                });
    }

    // Ẩn/hiện banner quảng cáo, đổi UI
    private void updateUiForPremium() {
        boolean premium = isPremiumCached();
        View adBanner = findViewById(R.id.adBanner); // nếu bạn có view banner
        if (adBanner != null) adBanner.setVisibility(premium ? View.GONE : View.VISIBLE);
    }
}
