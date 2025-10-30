package vn.khengandkhoi.musicplayer.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.*;
import java.util.*;

import vn.khengandkhoi.musicplayer.MusicService;
import vn.khengandkhoi.musicplayer.R;
import vn.khengandkhoi.musicplayer.object.SongRemote;
import vn.khengandkhoi.musicplayer.adapter.SongsAdapter;

/**
 * Activity hiển thị danh sách bài hát trong 1 album cụ thể
 * - Hiển thị: ảnh bìa, tên album, tác giả
 * - Danh sách bài hát (RecyclerView)
 * - Bấm bài hát → phát nhạc trong PlayerActivity
 */
public class SongListActivity extends AppCompatActivity {

    // ID của album (lấy từ Intent)
    private String albumId;

    // RecyclerView hiển thị danh sách bài hát
    private RecyclerView rv;

    // Adapter cho RecyclerView
    private SongsAdapter adapter;

    // Firestore instance
    private FirebaseFirestore db;

    // === CÁC VIEW TRÊN HEADER (ảnh bìa, tên album, tác giả, nút back) ===
    private TextView tvReturnBack;   // Nút quay lại
    private TextView tvSongName;     // Tên album
    private TextView tvAuthor;       // Tác giả album
    private ImageView imgCover;      // Ảnh bìa album

    /**
     * onCreate: khởi tạo layout, ánh xạ view, lấy albumId, load dữ liệu
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_list); // Layout chính

        // === ÁNH XẠ CÁC VIEW ===
        tvReturnBack = findViewById(R.id.returnBack);
        imgCover     = findViewById(R.id.imgCover);
        tvSongName   = findViewById(R.id.tvSongName);
        tvAuthor     = findViewById(R.id.tvAuthor);

        rv = findViewById(R.id.rvSongs);
        rv.setLayoutManager(new LinearLayoutManager(this)); // Danh sách dọc

        db = FirebaseFirestore.getInstance(); // Khởi tạo Firestore

        // === LẤY albumId TỪ INTENT ===
        albumId = getIntent().getStringExtra("albumId");
        if (albumId == null || albumId.isEmpty()) {
            Toast.makeText(this, "Lỗi: Không có albumId", Toast.LENGTH_LONG).show();
            finish(); // Thoát nếu không có ID
            return;
        }

        // === GÁN SỰ KIỆN CHO NÚT QUAY LẠI ===
        tvReturnBack.setOnClickListener(v -> finish());

        // === LOAD DỮ LIỆU ===
        loadAlbumInfo(); // Tải thông tin album (tên, ảnh, tác giả)
        loadSongs();     // Tải danh sách bài hát
    }

    /**
     * Tải thông tin album (tên, tác giả, ảnh bìa)
     * Từ collection: albums/{albumId}
     */
    private void loadAlbumInfo() {
        db.collection("albums").document(albumId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Lấy dữ liệu từ Firestore document
                        String name = doc.getString("title");
                        String author = doc.getString("author");
                        String coverUrl = doc.getString("coverUrl");

                        // Cập nhật UI
                        tvSongName.setText(name != null ? name : "Unknown Album");
                        tvAuthor.setText(author != null ? author : "Unknown Artist");

                        // Load ảnh bìa bằng Glide
                        if (coverUrl != null && !coverUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(coverUrl)
                                    .placeholder(R.drawable.ic_music_note) // Ảnh chờ
                                    .error(R.drawable.ic_music_note)       // Ảnh lỗi
                                    .into(imgCover);
                        } else {
                            imgCover.setImageResource(R.drawable.ic_music_note);
                        }
                    } else {
                        Toast.makeText(this, "Album không tồn tại", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi tải album: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Tải danh sách bài hát trong album
     * Từ: albums/{albumId}/songs/{songId}
     * Sắp xếp theo trackNum (thứ tự bài hát)
     */
    @OptIn(markerClass = UnstableApi.class)
    private void loadSongs() {
        db.collection("albums").document(albumId)
                .collection("songs")
                .orderBy("trackNum", Query.Direction.ASCENDING) // Sắp xếp tăng dần
                .get()
                .addOnSuccessListener(snap -> {
                    // Danh sách bài hát
                    List<SongRemote> songList = new ArrayList<>();

                    // Duyệt từng document → chuyển thành SongRemote
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        SongRemote song = d.toObject(SongRemote.class);
                        if (song != null) {
                            songList.add(song);
                        }
                    }

                    // === KIỂM TRA RỖNG ===
                    if (songList.isEmpty()) {
                        Toast.makeText(this, "Album chưa có bài hát", Toast.LENGTH_SHORT).show();
                    }

                    // === TẠO ADAPTER ===
                    adapter = new SongsAdapter(song -> {
                        // === KHI BẤM VÀO BÀI HÁT ===
                        // 1. Tạo playlist từ danh sách hiện tại
                        List<SongRemote> playlist = new ArrayList<>(adapter.getCurrentList());
                        int index = playlist.indexOf(song);

                        // 2. Gửi dữ liệu vào MusicService
                        Intent serviceIntent = new Intent(this, MusicService.class);
                        serviceIntent.putExtra("songs", new ArrayList<>(playlist)); // Phải là ArrayList
                        serviceIntent.putExtra("index", index);

                        // 3. Khởi động Service (Foreground nếu Android 8+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }

                        // 4. Mở PlayerActivity sau 200ms (đợi Service khởi tạo)
                        new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent playerIntent = new Intent(this, PlayerActivity.class);
                            startActivity(playerIntent);
                            overridePendingTransition(R.anim.slide_in_up, 0); // Hiệu ứng trượt lên
                        }, 200);
                    });

                    // === GÁN ADAPTER VÀO RECYCLERVIEW ===
                    rv.setAdapter(adapter);
                    adapter.submit(songList); // Cập nhật dữ liệu
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi tải bài hát: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}