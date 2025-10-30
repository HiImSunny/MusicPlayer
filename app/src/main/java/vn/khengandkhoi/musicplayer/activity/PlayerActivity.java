package vn.khengandkhoi.musicplayer.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

import com.bumptech.glide.Glide;

import vn.khengandkhoi.musicplayer.MusicService;
import vn.khengandkhoi.musicplayer.R;
import vn.khengandkhoi.musicplayer.object.SongRemote;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    // ====== Khai báo view điều khiển phát nhạc ======
    private ImageButton btnBack, btnShare, btnPrev, btnNext, btnRepeat, btnShuffle;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnPlayPause;
    private SeekBar seekBar;
    private TextView tvCur, tvDur, tvTitle, tvAuthor;
    private ImageView cardArtImageView;

    // Trạng thái chế độ lặp & xáo bài (để cập nhật UI và gọi Service)
    private boolean repeatEnabled = false;
    private boolean shuffleEnabled = false;

    // ====== Tham chiếu tới Service phát nhạc ======
    private MusicService service;
    private boolean bound = false;

    // ====== Callback nhận sự kiện từ Service (bài hát đổi, tiến độ phát thay đổi) ======
    private final MusicService.Callback callback = new MusicService.Callback() {

        // Khi metadata (bài hát hiện tại) thay đổi
        @Override
        public void onMetaChanged(SongRemote s, int index, long durationMs) {
            runOnUiThread(() -> {
                // Chia sẻ link bài hát (gắn lại listener mỗi lần meta đổi để dùng đúng URL)
                btnShare.setOnClickListener(v -> shareText(s.getUrl()));

                // Cập nhật tiêu đề / tác giả (fallback nếu null)
                tvTitle.setText(s.getTitle() == null ? "Unknown Title" : s.getTitle());
                // Cho phép marquee nếu TextView bị tràn
                tvTitle.setSelected(true);
                tvAuthor.setText(s.getAuthor() == null ? "Unknown Artist" : s.getAuthor());

                // Ảnh bìa: dùng Glide nếu có URL, nếu không dùng placeholder
                if (s.getCoverUrl() != null && !s.getCoverUrl().isEmpty()) {
                    Glide.with(PlayerActivity.this).load(s.getCoverUrl()).into(cardArtImageView);
                } else {
                    cardArtImageView.setImageResource(R.drawable.placeholder_album_light);
                }

                // Thiết lập tổng thời lượng và thanh SeekBar
                if (durationMs <= 0 || durationMs == C.TIME_UNSET) {
                    // Khi chưa biết thời lượng (ví dụ stream), hiển thị placeholder
                    tvDur.setText("⋯");
                    seekBar.setMax(100);
                    seekBar.setProgress(0);
                    tvCur.setText("0:00");
                } else {
                    tvDur.setText(format(durationMs));
                    // SeekBar dùng int, nên chặn tràn nếu duration lớn
                    seekBar.setMax((int) Math.min(durationMs, Integer.MAX_VALUE));
                    tvCur.setText("0:00");
                }
            });
        }

        // Khi trạng thái phát (đang phát/tạm dừng) hoặc vị trí phát thay đổi
        @Override
        public void onPlayStateChanged(boolean playing, long positionMs, long durationMs) {
            runOnUiThread(() -> {
                // Nếu chưa xác định thời lượng thì bỏ qua cập nhật tiến độ
                if (durationMs <= 0 || durationMs == C.TIME_UNSET) return;

                int max = (int) Math.min(durationMs, Integer.MAX_VALUE);
                int progress = (int) Math.min(positionMs, Integer.MAX_VALUE);

                seekBar.setMax(max);
                seekBar.setProgress(progress);
                tvCur.setText(format(positionMs));
                tvDur.setText(format(durationMs));

                // Đổi icon Play/Pause tương ứng trạng thái
                btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            });
        }
    };

    // ====== Kết nối tới MusicService ======
    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            // Lấy Service thực thông qua Binder do Service trả về
            MusicService.MusicBinder binder = (MusicService.MusicBinder) b;
            service = binder.getService();
            bound = true;

            // Đăng ký callback để nhận sự kiện phát nhạc
            service.addCallback(callback);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Mất kết nối tới Service (hệ thống hủy hoặc crash)
            bound = false;
            service = null;
        }
    };

    // ====== Vòng đời Activity ======
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mapViews();     // Tìm và ánh xạ các view
        bindEvents();   // Gán các listener xử lý tương tác

        updateUiForPremium();

        // Hiệu ứng chuyển màn hình khi mở Player (trượt từ dưới lên)
        overridePendingTransition(R.anim.slide_in_up, 0);
    }

    // Tìm view theo id, có xử lý fallback id khác nhau cho layout khác nhau
    private void mapViews() {
        btnBack = findViewById(R.id.btnBack);
        btnShare = findViewById(R.id.btnShare);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnPlayPause = findViewById(R.id.btnPlayPause);

        seekBar = findViewById(R.id.seek);
        tvCur = findViewById(R.id.tvCur);
        tvDur = findViewById(R.id.tvDur);

        // Một số layout dùng id khác (tvSongName/tvSongAuthor), nên thử lấy theo 2 khả năng
        tvTitle = findViewById(R.id.tvTitle) != null ? findViewById(R.id.tvTitle) : findViewById(R.id.tvSongName);
        tvAuthor = findViewById(R.id.tvAuthor) != null ? findViewById(R.id.tvAuthor) : findViewById(R.id.tvSongAuthor);

        cardArtImageView = findViewById(R.id.cardArtImageView);
    }

    private boolean isPremiumCached() {
        long until = getSharedPreferences("prefs", MODE_PRIVATE).getLong("premiumUntil", 0L);
        return System.currentTimeMillis() < until;
    }

    private void updateUiForPremium() {
        boolean premium = isPremiumCached();
        View adBanner = findViewById(R.id.adBanner); // nếu bạn có view banner
        if (adBanner != null) adBanner.setVisibility(premium ? View.GONE : View.VISIBLE);
    }

    private void bindEvents() {
        // Sử dụng OnBackPressedDispatcher để custom animation khi back (tương thích ngược)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Đóng Activity
                finish();
                // Áp hiệu ứng trượt xuống khi thoát Player
                overridePendingTransition(0, R.anim.slide_out_down);
            }
        });

        // Nút Back trên UI -> gọi onBackPressed (dispatcher sẽ xử lý animation)
        btnBack.setOnClickListener(v -> onBackPressed());

        // Play/Pause: ủy quyền cho Service xử lý
        btnPlayPause.setOnClickListener(v -> {
            if (service != null) service.togglePlay();
        });

        // Chuyển bài kế / trước: ủy quyền cho Service
        btnNext.setOnClickListener(v -> {
            if (service != null) service.next();
        });
        btnPrev.setOnClickListener(v -> {
            if (service != null) service.prev();
        });

        // Bật/tắt Repeat (cập nhật cả Service lẫn icon hiển thị)
        btnRepeat.setOnClickListener(v -> {
            repeatEnabled = !repeatEnabled;
            if (service != null) service.setRepeat(repeatEnabled);
            btnRepeat.setImageResource(repeatEnabled
                    ? R.drawable.ic_repeat_one   // đảm bảo resource tồn tại
                    : R.drawable.ic_repeat);     // đảm bảo resource tồn tại
        });

        // Bật/tắt Shuffle
        btnShuffle.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;
            if (service != null) service.setShuffle(shuffleEnabled);
            // Lưu ý: tên 2 icon phải thống nhất (tránh "shuffle" vs "ic_shuffle" lẫn lộn)
            btnShuffle.setImageResource(shuffleEnabled
                    ? R.drawable.ic_shuffle   // gợi ý đặt tên nhất quán
                    : R.drawable.shuffle);
        });

        // Theo dõi thao tác kéo SeekBar để tua
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) { /* không cần xử lý */ }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Khi người dùng thả tay: tua tới vị trí mới
                if (service != null) service.seekTo(seekBar.getProgress());
            }

            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                // Khi người dùng đang kéo: cập nhật đồng hồ hiện tại tức thời
                if (fromUser) tvCur.setText(format(progress));
            }
        });
    }

    // Intent chia sẻ text đơn giản (link bài hát)
    private void shareText(String text) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ qua"));
    }

    // Định dạng mili-giây thành "m:ss"
    private String format(long ms) {
        if (ms == C.TIME_UNSET || ms < 0) return "0:00";
        long s = ms / 1000;
        long m = s / 60;
        return String.format("%d:%02d", m, s % 60);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind vào Service để nhận callback & điều khiển phát nhạc
        bindService(new Intent(this, MusicService.class), conn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Hủy đăng ký callback và unbind Service để tránh rò rỉ (leak)
        if (bound && service != null) {
            service.removeCallback(callback);
            unbindService(conn);
            bound = false;
        }
    }
}
