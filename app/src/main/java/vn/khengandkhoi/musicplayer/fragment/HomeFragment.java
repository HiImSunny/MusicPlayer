package vn.khengandkhoi.musicplayer.fragment;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.*;
import android.view.*;
import android.widget.Toast;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import java.util.*;

import vn.khengandkhoi.musicplayer.object.Album;
import vn.khengandkhoi.musicplayer.MusicService;
import vn.khengandkhoi.musicplayer.R;
import vn.khengandkhoi.musicplayer.object.SongRemote;
import vn.khengandkhoi.musicplayer.activity.PlayerActivity;
import vn.khengandkhoi.musicplayer.activity.SongListActivity;
import vn.khengandkhoi.musicplayer.adapter.AlbumsAdapter;
import vn.khengandkhoi.musicplayer.adapter.RecentSongsAdapter;

/**
 * HomeFragment hiển thị:
 * 1) Danh sách album đề xuất (Top Picks) dạng RecyclerView ngang.
 * 2) Danh sách 10 bài hát phát gần đây (Recent) dạng RecyclerView dọc.
 *
 * Nguồn dữ liệu:
 * - "albums" (collection gốc) để tải Top Picks.
 * - "users/{uid}/recent" (subcollection) để tải các bài gần đây, sắp xếp theo "lastPlayed".
 *
 * Tương tác:
 * - Click album -> mở SongListActivity với extra "albumId".
 * - Click bài gần đây -> startService(MusicService) để phát danh sách từ vị trí chọn, sau đó mở PlayerActivity.
 *
 * Lưu ý vòng đời:
 * - Khởi tạo adapter & layoutManager trong onViewCreated (sau khi inflate view xong).
 * - Dùng requireContext()/requireActivity() khi chắc chắn fragment đã attach (trong onViewCreated là an toàn).
 * - Các cuộc gọi Firestore .get() là async, trả về trong addOnSuccessListener.
 */
public class HomeFragment extends Fragment {
    // RecyclerView cho Top Picks (album ngang) và Recent (bài gần đây dọc)
    private RecyclerView rvTopPicks, rvRecent;

    // Adapter hiển thị danh sách album và danh sách bài gần đây
    private AlbumsAdapter albumsAdapter;
    private RecentSongsAdapter recentAdapter;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        // Inflate layout XML của fragment (fragment_home) thành cây View.
        // Không nên truy cập view con tại đây (findViewById) vì chưa hoàn tất onViewCreated.
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @OptIn(markerClass = UnstableApi.class) // Chỉ ra có sử dụng API có thể thay đổi (Media3), anotation này yên tâm hơn khi dùng kèm Media3.
    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // Khởi tạo instance Firebase (Firestore & Auth)
        // - Firestore: truy vấn dữ liệu (albums, users/{uid}/recent)
        // - Auth: lấy currentUser để truy cập subcollection "recent" theo uid
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Ánh xạ 2 RecyclerView trong layout fragment_home
        rvTopPicks = v.findViewById(R.id.rvTopPicks);
        rvRecent = v.findViewById(R.id.rvRecent);

        // Thiết lập LayoutManager cho Top Picks: HORIZONTAL để scroll ngang
        rvTopPicks.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));

        // Khởi tạo AlbumsAdapter với callback khi click vào 1 album
        // - Kiểm tra id album rỗng -> báo Toast
        // - Nếu hợp lệ -> mở SongListActivity và truyền "albumId" để activity biết cần load album nào
        albumsAdapter = new AlbumsAdapter(album -> {
            if (album == null || album.getId() == null || album.getId().isEmpty()) {
                Toast.makeText(requireContext(), "Album ID rỗng", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(requireContext(), SongListActivity.class);
            i.putExtra("albumId", album.getId());
            startActivity(i);
        });

        // Gán adapter cho RecyclerView Top Picks
        rvTopPicks.setAdapter(albumsAdapter);

        // Thiết lập LayoutManager cho Recent: dọc (vertical)
        rvRecent.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Khởi tạo RecentSongsAdapter với callback khi click 1 bài:
        // - Tìm index bài trong list (để phát đúng vị trí)
        // - startService(MusicService) với "songs" (toàn bộ list recent) + "index" (vị trí chọn)
        // - Sau đó startActivity(PlayerActivity) để mở màn hình trình phát
        recentAdapter = new RecentSongsAdapter(song -> {
            int index = recentAdapter.getData().indexOf(song); // Tính vị trí bài trong danh sách hiện tại

            // Intent tới Service phát nhạc: truyền danh sách và index
            Intent svc = new Intent(requireContext(), MusicService.class);
            svc.putExtra("songs", new ArrayList<>(recentAdapter.getData())); // Sao chép list để đảm bảo Serializable/Parcelable an toàn
            svc.putExtra("index", index);
            requireActivity().startService(svc); // Bắt đầu Service phát nhạc (Foreground service nếu MusicService triển khai như vậy)

            // Mở PlayerActivity để hiển thị UI điều khiển
            Intent open = new Intent(requireContext(), PlayerActivity.class);
            startActivity(open);
        });

        // Gán adapter cho RecyclerView Recent
        rvRecent.setAdapter(recentAdapter);

        // Gọi tải dữ liệu:
        // - TopPicks: từ collection "albums"
        // - RecentSongs: từ "users/{uid}/recent", orderBy lastPlayed desc, limit 10
        loadTopPicks();
        loadRecentSongs();
    }

    /**
     * Tải danh sách album đề xuất từ collection "albums".
     * - .get(): đọc toàn bộ snapshot một lần (không realtime).
     * - Mapping: DocumentSnapshot -> Album (POJO) qua toObject().
     * - Sau đó set id document vào model (a.setId(d.getId())) để adapter có thể dùng id.
     * - submit(list): cập nhật adapter.
     *
     * Lưu ý:
     * - Nếu muốn realtime, dùng addSnapshotListener thay vì .get().
     * - Có thể thêm .orderBy(...) nếu cần sắp xếp (ví dụ theo lượt nghe).
     */
    private void loadTopPicks() {
        db.collection("albums")
                .get()
                .addOnSuccessListener(snap -> {
                    List<Album> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Album a = d.toObject(Album.class);
                        if (a != null) {
                            a.setId(d.getId()); // 👈 dùng setter để gắn id tài liệu Firestore vào model
                            list.add(a);
                        }
                    }
                    albumsAdapter.submit(list); // Cập nhật dữ liệu cho adapter (nên dùng DiffUtil bên trong adapter để mượt hơn)
                })
                .addOnFailureListener(e -> {
                    // optional: show toast/log
                    // Ví dụ: Toast.makeText(requireContext(), "Lỗi tải albums: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Hoặc ghi log để debug.
                });
    }

    /**
     * Tải danh sách 10 bài hát người dùng đã phát gần đây từ:
     * "users/{uid}/recent"
     * - Yêu cầu user đã đăng nhập (mAuth.getCurrentUser() != null).
     * - Sắp xếp theo "lastPlayed" giảm dần để bài mới nhất lên đầu.
     * - .limit(10) để tránh tải quá nhiều (tối ưu hiệu năng & UI).
     *
     * Mapping:
     * - DocumentSnapshot -> SongRemote (POJO)
     * - recentAdapter.submit(list) để hiển thị.
     *
     * Lưu ý:
     * - Trường "lastPlayed" trong Firestore nên là Timestamp để orderBy chính xác.
     * - Nếu cần realtime, chuyển sang addSnapshotListener.
     * - Nên xử lý addOnFailureListener để thông báo lỗi mạng/quyền truy cập.
     */
    private void loadRecentSongs() {
        FirebaseUser u = mAuth.getCurrentUser();
        if (u == null) return; // Chưa đăng nhập -> không có dữ liệu recent để tải

        db.collection("users").document(u.getUid())
                .collection("recent")
                .orderBy("lastPlayed", Query.Direction.DESCENDING) // Sắp xếp bài phát gần đây nhất lên đầu
                .limit(10)
                .get()
                .addOnSuccessListener(snap -> {
                    List<SongRemote> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        SongRemote s = d.toObject(SongRemote.class);
                        if (s != null) list.add(s);
                    }
                    recentAdapter.submit(list); // Cập nhật danh sách recent
                });
        // (Có thể thêm .addOnFailureListener để hiển thị lỗi nếu cần)
    }

}
