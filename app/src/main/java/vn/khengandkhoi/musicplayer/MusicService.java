package vn.khengandkhoi.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.RawResourceDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerNotificationManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import vn.khengandkhoi.musicplayer.activity.PlayerActivity;
import vn.khengandkhoi.musicplayer.object.SongRemote;

@UnstableApi
public class MusicService extends Service implements Player.Listener {

    /**
     * Giao diện callback để Activity/Fragment nhận snapshot trạng thái
     * - onMetaChanged: đổi bài/metadata (tiêu đề/tác giả/duration/index)
     * - onPlayStateChanged: thay đổi state play/pause/seek (để cập nhật UI progress)
     */
    public interface Callback {
        void onMetaChanged(SongRemote song, int index, long durationMs);
        void onPlayStateChanged(boolean playing, long positionMs, long durationMs);
    }

    // Action để tắt Service từ Notification hoặc bên ngoài
    public static final String ACTION_STOP = "vn.khengandkhoi.musicplayer.ACTION_STOP";

    // Thông số kênh thông báo + id notification foreground
    private static final String CH_ID = "music_channel";
    private static final int NOTI_ID = 9001;

    // Binder cho cơ chế bound service: Activity có thể bind để gọi hàm public
    private final IBinder binder = new MusicBinder();
    public class MusicBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    // Firebase để log recent play
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Player chính, MediaSession cho tương tác hệ thống (lockscreen, BT, Assistant),
    // NotificationManager để hiển thị media controls trong status bar/notification
    private ExoPlayer player;
    private MediaSession mediaSession;
    private PlayerNotificationManager notificationManager;

    // Handler chạy vòng lặp update tiến độ phát nhạc cho UI (emitStateAll mỗi 500ms)
    private Handler progressHandler;

    // Danh sách & trạng thái phát
    private ArrayList<SongRemote> playlist = new ArrayList<>();
    private ArrayList<SongRemote> shuffledPlaylist = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private final Random rng = new Random();

    // Danh sách callback thread-safe (UI có thể đăng ký/huỷ)
    private final java.util.concurrent.CopyOnWriteArrayList<Callback> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();

    // ====== Cấu hình chèn quảng cáo (ad injection) ======
    // Handler & lịch ad theo elapsedRealtime để không bị ảnh hưởng bởi time change
    private final Handler adHandler = new Handler(Looper.getMainLooper());
    private long nextAdAtElapsedMs = -1L;    // thời điểm (elapsedRealtime) sẽ phát ad tiếp theo
    private long adRemainingMs = -1L;        // thời gian còn lại đến ad khi bị pause (để resume)
    private boolean isPlayingAd = false;     // cờ đang phát quảng cáo
    private ExoPlayer adPlayer = null;       // Player riêng phát ad, tách khỏi player chính
    private long resumePosMsAfterAd = 0;     // lưu vị trí bài hát để resume sau khi ad kết thúc

    // Chế độ test: 10 giây phát 1 lần. Bật false => random 10–15 phút
    private static final boolean AD_TEST_MODE = true;         // đổi false để dùng 10–15 phút
    private static final long   AD_TEST_INTERVAL_MS = 10_000L; // 10s
    private static final int    AD_MIN_MINUTES = 10;
    private static final int    AD_MAX_MINUTES = 15;

    @Nullable @Override
    public IBinder onBind(Intent intent) { return binder; } // Cho phép Activity bind để điều khiển

    @Override
    public void onCreate() {
        super.onCreate();
        // Khởi tạo Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Tạo notification channel (Android 8+)
        createChannel();
        // Tạo ExoPlayer chính + listener
        initPlayer();
        // Handler cho vòng lặp cập nhật progress
        progressHandler = new Handler(Looper.getMainLooper());
        // MediaSession để hệ thống điều khiển (bluetooth, headset, Assistant)
        setupMediaSession();
        // Notification media controls
        setupNotification();
    }

    private void initPlayer() {
        // Tạo player với AudioAttributes: USAGE_MEDIA + CONTENT_TYPE_MUSIC (để điều khiển audio focus đúng chuẩn)
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .setUsage(C.USAGE_MEDIA)
                                .build(),
                        true // handleAudioFocus
                )
                .build();
        player.addListener(this); // lắng nghe sự kiện player (transition, state, error, ...)
    }

    private void setupMediaSession() {
        // MediaSession từ Media3: đăng ký player để điều khiển từ hệ thống/notification/BT
        mediaSession = new MediaSession.Builder(this, player).build();
    }

    private void setupNotification() {
        // PlayerNotificationManager: UI media trong notification (play/pause/next/prev)
        notificationManager = new PlayerNotificationManager.Builder(this, NOTI_ID, CH_ID)
                .setChannelNameResourceId(R.string.app_name)
                .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                    // Tiêu đề bài hát trong notification
                    @Override public CharSequence getCurrentContentTitle(Player player) {
                        SongRemote s = getCurrent();
                        return s != null ? s.getTitle() : "Unknown";
                    }
                    // Phụ đề (nghệ sĩ)
                    @Override public CharSequence getCurrentContentText(Player player) {
                        SongRemote s = getCurrent();
                        return s != null ? s.getAuthor() : "Artist";
                    }
                    // PendingIntent mở PlayerActivity khi bấm notification
                    @Override public PendingIntent createCurrentContentIntent(Player player) {
                        Intent intent = new Intent(MusicService.this, PlayerActivity.class);
                        return PendingIntent.getActivity(
                                MusicService.this, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT |
                                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
                    }
                    @Override public CharSequence getCurrentSubText(Player player) { return null; }
                    // Có thể trả về album art lớn (Bitmap); hiện để null để đơn giản
                    @Override public android.graphics.Bitmap getCurrentLargeIcon(
                            Player player, PlayerNotificationManager.BitmapCallback cb) { return null; }
                })
                .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                    // Khi notification được hiển thị: đẩy service thành foreground (tránh bị kill)
                    @Override public void onNotificationPosted(int id, Notification n, boolean ongoing) {
                        startForeground(id, n);
                    }
                    // Khi notification bị huỷ: dừng foreground & self-stop service
                    @Override public void onNotificationCancelled(int id, boolean byUser) {
                        stopForeground(true);
                        stopSelf();
                    }
                })
                .build();

        // Kết nối notification với media session & player
        notificationManager.setMediaSessionToken(mediaSession.getSessionCompatToken());
        notificationManager.setPlayer(player);

        // Hiển thị các nút điều khiển cơ bản
        notificationManager.setUsePlayPauseActions(true);
        notificationManager.setUseNextAction(true);
        notificationManager.setUsePreviousAction(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Xử lý action STOP từ notification/khác
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAndQuit();
            return START_NOT_STICKY;
        }

        // Nhận playlist & index từ Intent (khi người dùng chọn phát từ UI)
        if (intent != null && intent.hasExtra("songs")) {
            Object rawList = intent.getSerializableExtra("songs");
            ArrayList<SongRemote> list = parsePlaylist(rawList);
            int index = intent.getIntExtra("index", 0);

            setPlaylist(list, index, isShuffle);
            play();

            // Bắt/tiếp tục lịch quảng cáo (tuỳ premium/test mode)
            resumeAdCountdown();
        }
        // START_STICKY để service tiếp tục khi hệ thống kill tạm thời (nhạc/notification dịch vụ)
        return START_STICKY;
    }

    // Chuyển object từ Intent thành ArrayList<SongRemote> an toàn
    private ArrayList<SongRemote> parsePlaylist(Object raw) {
        if (raw instanceof ArrayList<?>) {
            return (ArrayList<SongRemote>) raw;
        } else if (raw instanceof AtomicReference) {
            Object inner = ((AtomicReference<?>) raw).get();
            if (inner instanceof ArrayList<?>) {
                return (ArrayList<SongRemote>) inner;
            }
        }
        return new ArrayList<>();
    }

    // Thiết lập playlist + trộn (nếu cần) + chuẩn bị media items
    public void setPlaylist(ArrayList<SongRemote> list, int startIndex, boolean shuffle) {
        playlist = new ArrayList<>(list);
        currentIndex = startIndex < playlist.size() ? startIndex : 0;

        if (shuffle) {
            // Tạo bản trộn và xác định index tương ứng bài hiện tại trong list đã trộn
            shuffledPlaylist = new ArrayList<>(playlist);
            Collections.shuffle(shuffledPlaylist, rng);
            currentIndex = shuffledPlaylist.indexOf(playlist.get(startIndex));
        } else {
            shuffledPlaylist = playlist;
        }

        // Đẩy danh sách media items vào ExoPlayer
        buildMediaSource();

        // Ghi log "recent play" ngay bài đầu (phòng khi auto-transition chưa xảy ra)
        SongRemote cur = getCurrent();
        if (cur != null) logRecentPlay(cur);
    }

    // Map playlist -> MediaItem, set vào player và prepare (seek tới currentIndex)
    private void buildMediaSource() {
        player.setMediaItems(shuffledPlaylist.stream()
                .map(song -> new MediaItem.Builder()
                        .setUri(song.getUrl())
                        .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(song.getTitle())
                                .setArtist(song.getAuthor())
                                .build())
                        .build())
                .collect(java.util.stream.Collectors.toList()), currentIndex, 0);
        player.prepare();
    }

    // Bắt đầu phát
    private void play() {
        player.setPlayWhenReady(true);
    }

    // Gửi snapshot đầy đủ cho 1 callback mới đăng ký (metadata + play state)
    private void emitSnapshot(Callback cb) {
        if (cb == null) return;
        SongRemote s = getCurrent();
        if (s != null) cb.onMetaChanged(s, currentIndex, player.getDuration());
        cb.onPlayStateChanged(isPlaying(), player.getCurrentPosition(), player.getDuration());
    }

    // Gửi metadata tới tất cả callback (khi đổi bài)
    private void emitMetaAll() {
        SongRemote s = getCurrent();
        if (s == null) return;
        long dur = player.getDuration();
        for (Callback cb : callbacks) cb.onMetaChanged(s, currentIndex, dur);
    }

    // Gửi trạng thái play/pause/seek tới tất cả callback
    private void emitStateAll() {
        boolean playing = isPlaying();
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        for (Callback cb : callbacks) cb.onPlayStateChanged(playing, pos, dur);
    }

    // Đảm bảo vòng lặp cập nhật progress đang chạy (500ms/lần khi STATE_READY)
    private void ensureProgressLoop() {
        if (player != null && player.getPlaybackState() == Player.STATE_READY) {
            progressHandler.removeCallbacks(progressRunnable);
            progressHandler.post(progressRunnable);
        }
    }

    // Cho UI đăng ký nhận callback
    public void addCallback(Callback cb) {
        if (cb == null) return;
        if (!callbacks.contains(cb)) callbacks.add(cb);
        emitSnapshot(cb);
        ensureProgressLoop();
    }

    // UI huỷ nhận callback
    public void removeCallback(Callback cb) {
        if (cb == null) return;
        callbacks.remove(cb);
    }

    // Lấy bài hiện tại từ danh sách (đã trộn nếu shuffle)
    public SongRemote getCurrent() {
        if (shuffledPlaylist.isEmpty() || currentIndex >= shuffledPlaylist.size()) return null;
        return shuffledPlaylist.get(currentIndex);
    }

    // Một số tiện ích cho UI
    public int getIndex() { return currentIndex; }
    public boolean isPlaying() { return player.getPlaybackState() == Player.STATE_READY && player.getPlayWhenReady(); }
    public long getPosition() { return player.getCurrentPosition(); }
    public long getDuration() { return player.getDuration(); }

    // Toggle play/pause
    public void togglePlay() {
        boolean willPlay = !player.getPlayWhenReady();
        player.setPlayWhenReady(willPlay);
        emitStateAll();
        if (willPlay) resumeAdCountdown(); else pauseAdCountdown();
    }

    // Bài tiếp theo (nếu hết danh sách và repeat bật -> quay về đầu)
    public void next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
        } else if (isRepeat) {
            player.seekTo(0, 0);
        }
        resumeAdCountdown();
    }

    // Bài trước: nếu đang >3s trong bài -> về đầu; nếu không -> lùi bài trước đó
    public void prev() {
        if (player.getCurrentPosition() > 3000) {
            player.seekTo(0);
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
        }
        resumeAdCountdown();
    }

    // Tua đến vị trí ms
    public void seekTo(long ms) {
        player.seekTo(ms);
    }

    // Lặp 1 bài
    public void setRepeat(boolean on) {
        isRepeat = on;
        player.setRepeatMode(on ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    // Bật/tắt shuffle: tái tạo playlist trộn & phát lại tại bài hiện tại
    public void setShuffle(boolean on) {
        isShuffle = on;
        if (playlist.isEmpty()) return;

        int currentSongId = -1;
        SongRemote current = getCurrent();
        if (current != null) {
            currentSongId = playlist.indexOf(current);
        }

        setPlaylist(playlist, currentSongId, on);
        play();
        scheduleNextAdIfNeeded(false);
    }

    // Khi chuyển MediaItem (đổi bài)
    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        currentIndex = player.getCurrentMediaItemIndex();
        emitMetaAll(); // UI cập nhật tiêu đề/duration

        // Log recent play lên Firestore
        SongRemote cur = getCurrent();
        logRecentPlay(cur);
    }

    // Khi trạng thái playWhenReady đổi (play/pause)
    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        emitStateAll();
        if (playWhenReady) resumeAdCountdown(); else pauseAdCountdown();
    }

    // Khi position bị gián đoạn (seek/next/prev) -> cập nhật UI progress
    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPos, Player.PositionInfo newPos, int reason) {
        emitStateAll();
    }

    // Vòng lặp gửi state mỗi 500ms cho tất cả callback (khi READY)
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (player.getPlaybackState() == Player.STATE_READY && !callbacks.isEmpty()) {
                emitStateAll();
            }
            progressHandler.postDelayed(this, 500);
        }
    };

    // Quản lý vòng lặp theo state Player
    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_READY) {
            progressHandler.removeCallbacks(progressRunnable);
            progressHandler.post(progressRunnable);
        } else if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    // Tạo notification channel (Android 8+ bắt buộc)
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "Music", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    // ===================== QUẢNG CÁO: LỊCH & PHÁT =====================

    // Kiểm tra premium (dựa trên shared prefs "premiumUntil")
    private boolean isPremium() {
        long until = getSharedPreferences("prefs", MODE_PRIVATE)
                .getLong("premiumUntil", 0L);
        return System.currentTimeMillis() < until;
    }

    // Lên lịch ad tiếp theo (nếu chưa có) hoặc cưỡng bức đặt lại (force)
    private void scheduleNextAdIfNeeded(boolean force) {
        if (isPremium()) {
            // Premium: không chạy lịch ad
            nextAdAtElapsedMs = -1L;
            adHandler.removeCallbacks(adTick);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (force || nextAdAtElapsedMs <= 0) {
            if (AD_TEST_MODE) {
                nextAdAtElapsedMs = now + AD_TEST_INTERVAL_MS; // 10s
            } else {
                int minutes = AD_MIN_MINUTES + rng.nextInt(AD_MAX_MINUTES - AD_MIN_MINUTES + 1);
                nextAdAtElapsedMs = now + minutes * 60_000L;   // 10–15 phút
            }
        }
        adHandler.removeCallbacks(adTick);
        adHandler.postDelayed(adTick, 1000); // tick mỗi giây để kiểm tra đến hạn ad
    }

    // Tick ad: đến hạn -> phát ad; chưa đến -> hẹn tick tiếp
    private final Runnable adTick = new Runnable() {
        @Override public void run() {
            if (isPremium()) return;
            long now = SystemClock.elapsedRealtime();
            if (!isPlayingAd && nextAdAtElapsedMs > 0 && now >= nextAdAtElapsedMs) {
                playAd();
            } else {
                adHandler.postDelayed(this, 1000);
            }
        }
    };

    // Phát quảng cáo: tạo player ad riêng, pause player chính, nghe END/ERROR để resume
    private void playAd() {
        try {
            // Lưu vị trí hiện tại & tạm dừng player chính để chèn quảng cáo
            if (player != null) {
                resumePosMsAfterAd = player.getCurrentPosition();
                if (player.getPlayWhenReady()) player.pause();
            } else {
                resumePosMsAfterAd = 0;
            }

            isPlayingAd = true;

            // Player riêng cho quảng cáo để không làm xáo trộn queue player chính
            adPlayer = new ExoPlayer.Builder(this)
                    .setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                    .setUsage(C.USAGE_MEDIA)
                                    .build(),
                            true
                    )
                    .build();

            // Quảng cáo lấy từ raw resource (R.raw.ad_clip)
            MediaItem adItem = MediaItem.fromUri(
                    RawResourceDataSource.buildRawResourceUri(R.raw.ad_clip)
            );
            adPlayer.setMediaItem(adItem);
            adPlayer.prepare();

            adPlayer.addListener(new Player.Listener() {
                // Hết quảng cáo -> resume player chính
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED) {
                        stopAdAndResume();
                    }
                }
                // Lỗi quảng cáo -> bỏ qua, resume player chính
                @Override public void onPlayerError(PlaybackException error) {
                    stopAdAndResume();
                }
            });

            adPlayer.setPlayWhenReady(true); // bắt đầu phát ad ngay

        } catch (Exception e) {
            // Nếu lỗi bất kỳ -> bỏ qua ad để không chặn playback
            stopAdAndResume();
        }
    }

    // Dừng ad + resume nhạc tại vị trí cũ + lên lịch ad tiếp theo
    private void stopAdAndResume() {
        isPlayingAd = false;

        if (adPlayer != null) {
            try { adPlayer.stop(); } catch (Exception ignore) {}
            try { adPlayer.release(); } catch (Exception ignore) {}
            adPlayer = null;
        }

        if (player != null) {
            try {
                player.seekTo(resumePosMsAfterAd);
                player.setPlayWhenReady(true);
            } catch (Exception ignore) {}
        }

        // Sau khi phát xong ad -> đặt lại lịch cho lần tiếp theo
        scheduleNextAdIfNeeded(true);
    }

    // Tạm dừng đếm ngược ad (khi pause nhạc) và ghi lại thời gian còn lại
    private void pauseAdCountdown() {
        if (isPremium()) return;
        if (isPlayingAd) return;                       // đang phát ad thì để ad phát xong
        if (nextAdAtElapsedMs > 0) {
            long now = SystemClock.elapsedRealtime();
            adRemainingMs = Math.max(0, nextAdAtElapsedMs - now);
            nextAdAtElapsedMs = -1L;                   // huỷ mốc thời gian cũ
            adHandler.removeCallbacks(adTick);
        }
    }

    // Tiếp tục đếm ngược ad (khi play lại)
    private void resumeAdCountdown() {
        if (isPremium()) return;
        long delay;
        if (adRemainingMs > 0) {
            delay = adRemainingMs;                     // tiếp tục phần còn lại
        } else {
            if (AD_TEST_MODE) {
                delay = AD_TEST_INTERVAL_MS;           // 10 giây
            } else {
                int minutes = AD_MIN_MINUTES + rng.nextInt(AD_MAX_MINUTES - AD_MIN_MINUTES + 1);
                delay = minutes * 60_000L;             // random 10–15 phút
            }
        }
        long now = SystemClock.elapsedRealtime();
        nextAdAtElapsedMs = now + delay;
        adRemainingMs = -1L;
        adHandler.removeCallbacks(adTick);
        adHandler.postDelayed(adTick, 1000);
    }

    // Tạo docId ổn định cho "recent" (ưu tiên id, fallback từ url/title|author -> MD5)
    private String stableSongDocId(SongRemote s){
        if (s == null) return "unknown";
        if (s.getId() != null && !s.getId().isEmpty()) return s.getId();
        String key = s.getUrl() == null ? (s.getTitle() + "|" + s.getAuthor()) : s.getUrl();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());
            BigInteger bi = new BigInteger(1, digest);
            return String.format("%032x", bi);
        } catch (Exception e) {
            return String.valueOf(key.hashCode());
        }
    }

    // Giới hạn tối đa số recent lưu lại
    private static final int RECENT_LIMIT = 50;

    // Ghi "recent play" lên Firestore: users/{uid}/recent/{docId}
    private void logRecentPlay(SongRemote s){
        var user = auth.getCurrentUser();
        if (user == null || s == null) return;
        // Không log nếu đang phát quảng cáo (tránh làm bẩn lịch sử)
        if (isPlayingAd) return;

        String uid = user.getUid();
        String docId = stableSongDocId(s);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("title",     s.getTitle());
        data.put("author",    s.getAuthor());
        data.put("url",       s.getUrl());
        data.put("coverUrl",  s.getCoverUrl());
        data.put("lastPlayed", FieldValue.serverTimestamp()); // server time để orderBy chính xác

        db.collection("users").document(uid)
                .collection("recent").document(docId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> trimRecentIfNeeded(uid)) // sau khi ghi, kiểm tra giới hạn
                .addOnFailureListener(e -> { /* optional log */ });
    }

    // Cắt bớt recent nếu vượt RECENT_LIMIT (giữ mới nhất theo lastPlayed)
    private void trimRecentIfNeeded(String uid){
        db.collection("users").document(uid)
                .collection("recent")
                .orderBy("lastPlayed", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.size() <= RECENT_LIMIT) return;
                    for (int i = RECENT_LIMIT; i < snap.size(); i++){
                        snap.getDocuments().get(i).getReference().delete();
                    }
                });
    }

    // ======================== DỌN SERVICE & TÀI NGUYÊN ========================

    // Dừng toàn bộ & tự huỷ service (được gọi khi action STOP hoặc huỷ notification)
    public void stopAndQuit() {
        // dừng vòng lặp progress & ad
        if (progressHandler != null) progressHandler.removeCallbacks(progressRunnable);
        adHandler.removeCallbacks(adTick);

        // dừng quảng cáo (nếu đang phát)
        if (adPlayer != null) {
            try { adPlayer.stop(); } catch (Exception ignore) {}
            try { adPlayer.release(); } catch (Exception ignore) {}
            adPlayer = null;
            isPlayingAd = false;
        }

        // dừng player chính và xóa media items (giải phóng queue)
        if (player != null) {
            try { player.stop(); } catch (Exception ignore) {}
            player.clearMediaItems();
        }

        // tắt notification & media session
        if (notificationManager != null) notificationManager.setPlayer(null);
        if (mediaSession != null) mediaSession.release();
        stopForeground(true);

        // gửi trạng thái cuối (đang dừng) cho UI để đồng bộ
        emitStateAll();

        // tự hủy service
        stopSelf();
    }

    @Override
    public void onDestroy() {
        // Huỷ các runnable
        if (progressHandler != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        adHandler.removeCallbacks(adTick);

        // Tách player khỏi notification
        if (notificationManager != null) {
            notificationManager.setPlayer(null);
        }
        // Giải phóng media session
        if (mediaSession != null) {
            mediaSession.release();
        }
        // Dừng & thả ad player nếu còn
        if (adPlayer != null) {
            try { adPlayer.stop(); } catch (Exception ignore) {}
            try { adPlayer.release(); } catch (Exception ignore) {}
            adPlayer = null;
        }
        // Thả player chính
        if (player != null) {
            player.release();
        }
        super.onDestroy();
    }
}
