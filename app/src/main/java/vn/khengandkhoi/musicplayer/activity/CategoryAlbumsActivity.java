package vn.khengandkhoi.musicplayer.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import vn.khengandkhoi.musicplayer.object.Album;
import vn.khengandkhoi.musicplayer.adapter.AlbumsAdapter;
import vn.khengandkhoi.musicplayer.GridSpacingItemDecoration;
import vn.khengandkhoi.musicplayer.R;

/**
 * CategoryAlbumsActivity:
 * - Màn hình hiển thị danh sách Album thuộc một Category cụ thể.
 * - Nhận vào từ Intent: categoryId (bắt buộc), categoryName (optional), coverId (optional).
 * - Luồng chính:
 *   1) Đọc categoryId, hiển thị title (ưu tiên intent, fallback Firestore).
 *   2) Lấy category từ "categories/{categoryId}".
 *   3) Trích các albumId từ nhiều tên field khác nhau (albumIds, albums, albumId, idAlbum).
 *   4) Query "albums" theo lô (chunks) bằng whereIn(FieldPath.documentId(), part) với part ≤ 10 ID
 *      (vì Firestore giới hạn tối đa 10 phần tử cho whereIn).
 *   5) Ghép kết quả theo ĐÚNG THỨ TỰ albumIds ban đầu -> đổ vào AlbumsAdapter.
 *
 * UX:
 * - Back: dùng OnBackPressedDispatcher, sau đó chạy animation slide_out_down để đồng bộ với enter.
 * - Grid 2 cột + spacing giữa các ô qua GridSpacingItemDecoration.
 * - Click 1 album -> mở SongListActivity (truyền albumId).
 *
 * Lưu ý:
 * - Nếu category không tồn tại hoặc thiếu ID -> báo lỗi rồi finish().
 * - Nếu không có albumIds -> submit danh sách rỗng và thông báo.
 */
public class CategoryAlbumsActivity extends AppCompatActivity {

    private String categoryId;
    private String categoryNameFromIntent;
    private String coverId; // nếu cần dùng để hiện cover header
    private RecyclerView rv;
    private AlbumsAdapter albumsAdapter;
    private FirebaseFirestore db;

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_category_albums); // phải có rvAlbums + (optional) tvTitle

        // Đăng ký back bằng OnBackPressedDispatcher:
        // Khi người dùng back (gesture/nút), ta tự finish và áp dụng animation slide xuống cho mượt.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.no_change, R.anim.slide_out_down);
            }
        });

        db = FirebaseFirestore.getInstance();

        // Thiết lập RecyclerView grid 2 cột + khoảng cách
        rv = findViewById(R.id.rvAlbums);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.addItemDecoration(new GridSpacingItemDecoration(2, 12, true));

        // Adapter hiển thị album; click -> đi tới danh sách bài hát của album đó
        albumsAdapter = new AlbumsAdapter(album -> {
            if (album == null || album.getId() == null) return;
            Intent i = new Intent(this, SongListActivity.class);
            i.putExtra("albumId", album.getId());
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_up, R.anim.no_change);
        });
        rv.setAdapter(albumsAdapter);

        // Nhận tham số truyền qua Intent
        categoryId = getIntent().getStringExtra("categoryId");
        categoryNameFromIntent = getIntent().getStringExtra("categoryName");
        coverId = getIntent().getStringExtra("coverId");

        // Nếu layout có tvTitle và intent có name -> đặt luôn
        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null && categoryNameFromIntent != null) {
            tvTitle.setText(categoryNameFromIntent);
        }

        // Bắt buộc phải có categoryId
        if (categoryId == null || categoryId.isEmpty()) {
            Toast.makeText(this, "Thiếu categoryId", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Tải category và các album của category
        loadCategoryAndAlbums();
    }

    /**
     * Gọi Firestore lấy document category:
     * - Nếu title chưa có từ intent -> cập nhật từ field "name" (nếu có).
     * - Từ doc, linh hoạt trích danh sách albumId theo nhiều field khác nhau.
     * - Sau đó fetch album theo danh sách id, đảm bảo giữ nguyên thứ tự gốc.
     */
    private void loadCategoryAndAlbums() {
        db.collection("categories").document(categoryId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Category không tồn tại", Toast.LENGTH_LONG).show();
                        finish(); return;
                    }

                    // Nếu intent không truyền name thì lấy name từ Firestore
                    TextView tvTitle = findViewById(R.id.tvTitle);
                    if (tvTitle != null && (categoryNameFromIntent == null || categoryNameFromIntent.isEmpty())) {
                        String name = doc.getString("name");
                        if (name != null) tvTitle.setText(name);
                    }

                    // Trích danh sách id album theo nhiều khả năng tên field khác nhau
                    List<String> albumIds = extractAlbumIdsFlexible(doc);

                    if (albumIds.isEmpty()) {
                        // Không có album trong category -> hiển thị rỗng + toast gợi ý
                        albumsAdapter.submit(Collections.emptyList());
                        Toast.makeText(this, "Danh mục chưa có album", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Lấy album theo id và GHÉP THEO THỨ TỰ albumIds ban đầu
                    fetchAlbumsByIdsKeepOrder(albumIds);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi tải category: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    /**
     * Cho phép đọc albumIds từ nhiều format khác nhau, phòng khi dữ liệu không đồng nhất:
     * - Mảng: "albumIds": List<String>, "albums": List<String>
     * - Đơn lẻ: "albumId": String, "idAlbum": String
     * => Trả về list đã loại trùng và giữ thứ tự xuất hiện.
     */
    /** Chấp nhận nhiều tên field: albumIds (List<String>), albums (List<String>), albumId/idAlbum (String) */
    private List<String> extractAlbumIdsFlexible(DocumentSnapshot doc) {
        List<String> out = new ArrayList<>();

        // Ưu tiên mảng
        List<String> fromAlbumIds = castStringList(doc.get("albumIds"));
        if (fromAlbumIds != null) out.addAll(fromAlbumIds);

        List<String> fromAlbums = castStringList(doc.get("albums"));
        if (fromAlbums != null) out.addAll(fromAlbums);

        // Fallback: 1 id đơn lẻ
        String single1 = doc.getString("albumId");
        if (single1 != null && !single1.isEmpty()) out.add(single1);

        String single2 = doc.getString("idAlbum");
        if (single2 != null && !single2.isEmpty()) out.add(single2);

        // Loại trùng, giữ thứ tự xuất hiện
        List<String> dedup = new ArrayList<>();
        for (String id : out) {
            if (id != null && !id.isEmpty() && !dedup.contains(id)) dedup.add(id);
        }
        return dedup;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object o) {
        if (o == null) return null;
        if (o instanceof List) {
            List<?> raw = (List<?>) o;
            List<String> r = new ArrayList<>();
            for (Object e : raw) if (e != null) r.add(String.valueOf(e));
            return r;
        }
        return null;
    }

    /**
     * Lấy album theo danh sách id và GIỮ THỨ TỰ ban đầu:
     * - Firestore whereIn(FieldPath.documentId(), part) giới hạn 10 giá trị -> phải chia lô (chunk) tối đa 10 id/lần.
     * - Gom kết quả từng lô vào map<id, Album>.
     * - Khi tất cả lô xong (pending == 0), duyệt lại theo albumIds gốc để build list đúng thứ tự.
     * - Ưu điểm: giữ trật tự như cấu hình category; Nhược: nhiều round-trip nếu danh sách dài.
     */
    /** Query theo từng lô 10 id, giữ đúng thứ tự albumIds ban đầu */
    private void fetchAlbumsByIdsKeepOrder(List<String> albumIds) {
        // Chia lô <= 10 cho whereIn (giới hạn Firestore)
        List<List<String>> chunks = chunk(albumIds, 10);

        // acc: tích luỹ Album đã tải, key = documentId
        Map<String, Album> acc = new HashMap<>();
        // pending: đếm số lô đang chờ hoàn tất; khi về 0 -> tổng hợp và đổ adapter
        AtomicInteger pending = new AtomicInteger(chunks.size());

        for (List<String> part : chunks) {
            db.collection("albums")
                    .whereIn(FieldPath.documentId(), part)
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (DocumentSnapshot d : snap) {
                            Album a = d.toObject(Album.class);
                            if (a != null) {
                                // Set id cho Album (nếu model không tự map field id)
                                try {
                                    java.lang.reflect.Field f = Album.class.getDeclaredField("id");
                                    f.setAccessible(true); f.set(a, d.getId());
                                } catch (Exception ignore){}
                                acc.put(d.getId(), a);
                            }
                        }
                        // Khi lô này hoàn tất, giảm pending; nếu đã xong hết -> build list theo thứ tự gốc
                        if (pending.decrementAndGet() == 0) {
                            // Lắp theo đúng thứ tự albumIds
                            List<Album> ordered = new ArrayList<>();
                            for (String id : albumIds) {
                                Album a = acc.get(id);
                                if (a != null) ordered.add(a);
                            }
                            albumsAdapter.submit(ordered);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Nếu lô lỗi, vẫn cố gắng hoàn tất pending; cuối cùng ghép những gì có được
                        if (pending.decrementAndGet() == 0) {
                            List<Album> ordered = new ArrayList<>();
                            for (String id : albumIds) {
                                Album a = acc.get(id);
                                if (a != null) ordered.add(a);
                            }
                            albumsAdapter.submit(ordered);
                        }
                        Toast.makeText(this, "Lỗi tải album: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    /**
     * Chia danh sách thành các phần (subList) có kích thước tối đa size.
     * - Dùng subList để tránh copy không cần thiết (nhẹ, nhanh).
     * - Nếu cần bản sao độc lập có thể dùng Arrays.copyOfRange(...) thay cho subList.
     */
    private <T> List<List<T>> chunk(List<T> src, int size) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < src.size(); i += size) {
            parts.add(src.subList(i, Math.min(i + size, src.size())));
        }
        return parts;
        // Hoặc dùng Arrays.copyOfRange nếu muốn bản sao
    }
}
