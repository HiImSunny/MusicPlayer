package vn.khengandkhoi.musicplayer.fragment;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.*;
import java.util.*;

import vn.khengandkhoi.musicplayer.object.Category;
import vn.khengandkhoi.musicplayer.MusicService;
import vn.khengandkhoi.musicplayer.R;
import vn.khengandkhoi.musicplayer.object.SongRemote;
import vn.khengandkhoi.musicplayer.activity.CategoryAlbumsActivity;
import vn.khengandkhoi.musicplayer.activity.PlayerActivity;
import vn.khengandkhoi.musicplayer.adapter.CategoriesAdapter;
import vn.khengandkhoi.musicplayer.adapter.SongsAdapter;

/**
 * SearchFragment:
 * - Màn hình tìm kiếm có 2 chế độ hiển thị:
 *    (1) Lưới Categories (rvCategoriesGrid) khi ô tìm kiếm rỗng
 *    (2) Danh sách bài hát (rvSongsList) khi có từ khoá
 *
 * - Debounce tìm kiếm: chờ 300ms sau khi người dùng gõ xong mới query.
 * - Tải bài hát bằng Firestore collectionGroup("songs"), sau đó LỌC CỤC BỘ theo từ khoá đã chuẩn hoá tiếng Việt (bỏ dấu).
 * - Có ProgressBar khi đang tải, và TextView "trống" khi không có kết quả.
 * - Có cơ chế "load-more" đơn giản (dựa trên scroll), hiện đang gọi lại querySongs (placeholder—vì chưa dùng startAfter cho phân trang thực).
 *
 * Lưu ý:
 * - Để phân trang thực sự, cần query Firestore với .orderBy(...).limit(PAGE_SIZE).startAfter(lastDoc).
 * - Ở đây vì dùng collectionGroup().get() (lấy tất cả rồi lọc cục bộ) -> dễ demo nhưng không tối ưu khi dữ liệu lớn.
 */
public class SearchFragment extends Fragment {

    // Ô nhập từ khoá tìm kiếm
    private EditText edtSearch;
    // RecyclerView lưới danh mục và danh sách bài hát
    private RecyclerView rvCategoriesGrid, rvSongsList;
    // Tiến trình & thông báo trống
    private ProgressBar progress;
    private TextView tvEmpty;

    // Firestore & adapters
    private FirebaseFirestore db;
    private CategoriesAdapter catAdapter;
    private SongsAdapter songsAdapter;

    // Debounce: handler UI và runnable trì hoãn
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable pending;

    // Trạng thái/paging (hiện dùng làm placeholder vì querySongs đang load-all)
    private static final int PAGE_SIZE = 24;
    private DocumentSnapshot lastDoc; // sẽ dùng khi chuyển sang query phân trang thực
    private boolean isLoading = false;
    private String currentQuery = "";
    private final List<SongRemote> currentPage = new ArrayList<>(); // dữ liệu hiện hiển thị

    // Danh sách tất cả categories (để đổ vào grid)
    private final List<Category> allCats = new ArrayList<>();


    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        // Inflate layout cho fragment
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @OptIn(markerClass = UnstableApi.class) // Dùng để chú thích có liên quan Media3 (nếu cần)
    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        db = FirebaseFirestore.getInstance(); // Khởi tạo Firestore

        // Ánh xạ view
        edtSearch       = v.findViewById(R.id.edtSearch);
        rvCategoriesGrid= v.findViewById(R.id.rvCategoriesGrid);
        rvSongsList     = v.findViewById(R.id.rvSongsList);
        progress        = v.findViewById(R.id.progress);
        tvEmpty         = v.findViewById(R.id.tvEmpty);

        // ============== CATEGORIES GRID ==============
        // 2 cột, hiển thị danh mục; click -> mở CategoryAlbumsActivity
        rvCategoriesGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        catAdapter = new CategoriesAdapter(c -> {
            Intent i = new Intent(requireContext(), CategoryAlbumsActivity.class);
            i.putExtra("categoryId", c.getId());   // truyền id để màn albums biết query
            i.putExtra("categoryName", c.getName());
            startActivity(i);
            // animation chuyển màn
            requireActivity().overridePendingTransition(R.anim.slide_in_up, R.anim.no_change);
        });
        rvCategoriesGrid.setAdapter(catAdapter);
        loadCategories(); // tải danh mục từ collection "categories"

        // ============== SONGS LIST ==============
        // Hiển thị danh sách bài hát theo kết quả search
        rvSongsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        songsAdapter = new SongsAdapter(song -> {
            // Khi click 1 bài: tạo playlist = danh sách hiện có (currentPage)
            List<SongRemote> playlist = new ArrayList<>(currentPage);
            int index = playlist.indexOf(song); // vị trí bài click

            // Gửi danh sách & index sang MusicService để phát nhạc
            Intent serviceIntent = new Intent(requireContext(), MusicService.class);
            serviceIntent.putExtra("songs", new ArrayList<>(playlist));
            serviceIntent.putExtra("index", index);

            // Android O+ cần startForegroundService cho media playback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(serviceIntent);
            } else {
                requireContext().startService(serviceIntent);
            }

            // Mở PlayerActivity để điều khiển UI phát nhạc
            Intent playerIntent = new Intent(requireContext(), PlayerActivity.class);
            startActivity(playerIntent);
            requireActivity().overridePendingTransition(R.anim.slide_in_up, 0);
        });
        rvSongsList.setAdapter(songsAdapter);

        // Mặc định: chưa gõ gì -> hiển thị categories
        showCategories(true);

        // ============== DEBOUNCE SEARCH ==============
        // Chỉ chạy query sau 300ms kể từ lần gõ cuối -> giảm số lần gọi Firestore
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (pending != null) ui.removeCallbacks(pending); // huỷ runnable cũ nếu user tiếp tục gõ
                pending = () -> {
                    String q = s.toString().trim();
                    if (q.isEmpty()) {
                        // Nếu rỗng -> quay lại màn categories
                        showCategories(true);
                    } else {
                        // Có từ khoá -> chuyển sang danh sách bài hát và query
                        showCategories(false);
                        querySongs(q, true);
                    }
                };
                ui.postDelayed(pending, 300); // trì hoãn 300ms
            }
        });

        // ============== LOAD MORE CHO SONGS LIST ==============
        // Khi kéo gần cuối danh sách -> gọi queryNextPage()
        // Hiện tại queryNextPage chỉ gọi lại querySongs (vì chưa có startAfter/limit),
        // để phân trang thực sự, cần thay đổi query Firestore tương ứng.
        rvSongsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return; // chỉ quan tâm scroll xuống
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                int visible = lm.getChildCount();
                int total   = lm.getItemCount();
                int first   = lm.findFirstVisibleItemPosition();
                // Nếu còn <6 item là chạm đáy -> load thêm
                if (!isLoading && total - (visible + first) < 6) {
                    queryNextPage();
                }
            }
        });
    }

    /**
     * Bật/tắt chế độ hiển thị categories.
     * - show=true: hiện lưới categories và ẩn danh sách bài hát, đồng thời xoá kết quả cũ.
     * - show=false: ẩn categories, hiện danh sách bài hát.
     */
    private void showCategories(boolean show) {
        rvCategoriesGrid.setVisibility(show ? View.VISIBLE : View.GONE);
        rvSongsList.setVisibility(show ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        if (show) {
            // clear kết quả cũ khi quay lại categories
            songsAdapter.submit(Collections.emptyList());
            currentPage.clear();
            currentQuery = "";
        }
    }

    // Set cờ loading + hiện/ẩn ProgressBar
    private void setLoading(boolean loading) {
        isLoading = loading;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // Hiện/ẩn TextView "Không có kết quả"
    private void showEmpty(boolean show) { tvEmpty.setVisibility(show ? View.VISIBLE : View.GONE); }

    private static final String TAG = "SearchQuery";

    // Chuẩn hoá tiếng Việt: lower-case, bỏ dấu, đổi 'đ'->'d', gom khoảng trắng
    private static String vnNorm(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.US).trim();
        String norm = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        norm = norm.replace('đ', 'd');
        return norm.replaceAll("\\s+", " ");
    }

    /**
     * Thực hiện tìm kiếm bài hát theo từ khoá q.
     * - reset=true: xoá trang hiện tại & UI trước khi tải
     * - Chuẩn hoá query (vnNorm)
     * - Dùng collectionGroup("songs") để lấy tất cả tài liệu trong mọi subcollection "songs" thuộc các album.
     * - Sau khi lấy, lọc cục bộ theo tiêu đề/tác giả đã chuẩn hoá.
     *
     * Lưu ý:
     * - Cách này dễ triển khai, nhưng tốn băng thông khi dữ liệu lớn. Để tối ưu, có thể index hoá/truy vấn theo trường "normTitle"/"tokens".
     */
    private void querySongs(String q, boolean reset) {
        Log.d(TAG, "=== TÌM KIẾM: \"" + q + "\" ===");

        if (reset) {
            currentPage.clear();
            songsAdapter.submit(Collections.emptyList());
            showEmpty(false);
        }

        String queryNorm = vnNorm(q);
        Log.d(TAG, "Query chuẩn hóa: \"" + queryNorm + "\"");

        if (queryNorm.isEmpty()) {
            Log.d(TAG, "Query rỗng");
            songsAdapter.submit(Collections.emptyList());
            showEmpty(false);
            return;
        }

        setLoading(true);

        // LẤY TOÀN BỘ "songs" QUA collectionGroup:
        // Ưu: gom dữ liệu từ mọi albums/*/songs/*; Nhược: không có filter ở server-side -> phải lọc local
        db.collectionGroup("songs").get()
                .addOnSuccessListener(snap -> {
                    setLoading(false);

                    int total = snap.size();
                    Log.d(TAG, "LẤY ĐƯỢC: " + total + " bài hát từ albums/*/songs/*");

                    if (total == 0) {
                        Log.w(TAG, "KHÔNG CÓ BÀI HÁT NÀO TRONG collectionGroup('songs')");
                        showEmpty(true);
                        return;
                    }

                    // Chuyển snapshot -> list SongRemote
                    List<SongRemote> allSongs = new ArrayList<>();
                    for (DocumentSnapshot d : snap) {
                        SongRemote s = d.toObject(SongRemote.class);
                        if (s != null) {
                            allSongs.add(s);
                            // Log sample vài phần tử đầu để debug
                            if (allSongs.size() <= 3) {
                                Log.d(TAG, "Sample: \"" + s.getTitle() + "\" - " + s.getAuthor());
                            }
                        }
                    }

                    // LỌC LOCAL THEO VN-NORM (tiêu đề + tác giả)
                    List<SongRemote> filtered = new ArrayList<>();
                    for (SongRemote s : allSongs) {
                        String haystack = vnNorm(s.getTitle()) + " " + vnNorm(s.getAuthor());
                        if (haystack.contains(queryNorm)) {
                            filtered.add(s);
                        }
                    }

                    Log.d(TAG, "SAU LỌC: " + filtered.size() + " bài");
                    for (int i = 0; i < Math.min(3, filtered.size()); i++) {
                        SongRemote s2 = filtered.get(i);
                        Log.d(TAG, "→ \"" + s2.getTitle() + "\" - " + s2.getAuthor());
                    }

                    // Cập nhật dữ liệu hiển thị
                    currentPage.clear();
                    currentPage.addAll(filtered);
                    songsAdapter.submit(new ArrayList<>(currentPage));
                    showEmpty(filtered.isEmpty());

                    Log.d(TAG, "=== HOÀN TẤT ===");
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "LỖI LẤY DỮ LIỆU", e);
                    Toast.makeText(requireContext(), "Lỗi kết nối!", Toast.LENGTH_SHORT).show();
                    showEmpty(true);
                });
    }

    /**
     * Gọi khi cuộn gần cuối danh sách:
     * - Hiện tại: nếu đã có currentQuery + lastDoc -> gọi lại querySongs(currentQuery, false)
     * - Để phân trang thực: cần thay querySongs bằng query có .orderBy().limit().startAfter(lastDoc)
     */
    private void queryNextPage() {
        if (isLoading || lastDoc == null || currentQuery.isEmpty()) return;
        querySongs(currentQuery, false);
    }

    /**
     * Tải danh mục (categories) từ collection "categories".
     * - Map DocumentSnapshot -> Category (POJO), set id từ d.getId()
     * - Đổ vào adapter grid.
     */
    private void loadCategories() {
        db.collection("categories").get()
                .addOnSuccessListener(snap -> {
                    List<Category> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap) {
                        Category c = d.toObject(Category.class);
                        if (c != null) { c.setId(d.getId()); list.add(c); }
                    }

                    allCats.clear();
                    allCats.addAll(list);
                    catAdapter.submit(allCats);
                });
    }

}
