package vn.khengandkhoi.musicplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import vn.khengandkhoi.musicplayer.object.Album;
import vn.khengandkhoi.musicplayer.R;

/**
 * AlbumsAdapter:
 * ===============================
 * 👉 Adapter cho RecyclerView hiển thị danh sách Album dưới dạng các card hình ảnh.
 *
 * 🧩 Cấu trúc:
 * - Mỗi item (album) gồm:
 *     + Ảnh bìa (imgCover)
 *     + Tên album (tvTitle)
 * - Khi người dùng click vào 1 album, adapter gọi callback `onClick(Album a)`.
 *
 * 🛠️ Công nghệ:
 * - Dùng Glide để tải ảnh cover (ảnh lưu online qua URL hoặc Cloud Storage).
 * - ViewHolder pattern giúp tái sử dụng view, tránh findViewById nhiều lần.
 * - Layout item: `item_album_card.xml` (phải có id `imgCover` và `tvTitle`).
 *
 * ⚙️ Quy trình hoạt động:
 * 1️⃣ `submit(List<Album>)`: cập nhật danh sách album hiển thị.
 * 2️⃣ RecyclerView gọi `onCreateViewHolder()` -> tạo view item.
 * 3️⃣ RecyclerView gọi `onBindViewHolder()` -> gán dữ liệu album tương ứng.
 * 4️⃣ Khi click item -> gọi callback `onClick(album)` truyền về Activity/Fragment.
 *
 * 🔁 Lợi ích:
 * - Dễ dàng kết nối với Firestore, Room hoặc API để nạp danh sách album.
 * - Giao diện có thể mở rộng: thêm tác giả, số bài hát, nút yêu thích...
 */
public class AlbumsAdapter extends RecyclerView.Adapter<AlbumsAdapter.VH> {

    /** Interface callback khi người dùng click vào 1 album. */
    public interface OnAlbumClick { void onClick(Album a); }

    /** Danh sách dữ liệu (các Album) hiển thị trên RecyclerView. */
    private final List<Album> data = new ArrayList<>();

    /** Callback được truyền từ Activity/Fragment để xử lý sự kiện click. */
    private final OnAlbumClick onClick;

    /** Constructor: nhận callback để xử lý khi click vào item album. */
    public AlbumsAdapter(OnAlbumClick onClick) { this.onClick = onClick; }

    /**
     * Cập nhật dữ liệu cho adapter:
     * - Xóa danh sách cũ.
     * - Thêm danh sách mới.
     * - Gọi notifyDataSetChanged() để vẽ lại RecyclerView.
     *
     * ⚠️ Nếu dữ liệu lớn, nên dùng DiffUtil để tránh refresh toàn bộ.
     */
    public void submit(List<Album> items){
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    /**
     * Tạo ViewHolder mới:
     * - Inflate layout từ item_album_card.xml.
     * - Mỗi ViewHolder đại diện cho một item album trong RecyclerView.
     */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        // Inflate layout item_album_card từ XML
        View item = LayoutInflater.from(p.getContext()).inflate(R.layout.item_album_card, p, false);
        return new VH(item);
    }

    /**
     * Gán dữ liệu vào ViewHolder tại vị trí i:
     * - Lấy album tương ứng từ danh sách.
     * - Đặt tiêu đề album (tvTitle).
     * - Tải ảnh bìa album bằng Glide.
     * - Gán sự kiện click → callback OnAlbumClick.
     */
    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        Album a = data.get(i);

        // Hiển thị tiêu đề album
        h.tvTitle.setText(a.getTitle());

        // Tải ảnh bìa album bằng Glide (ảnh có thể từ URL hoặc Firebase Storage)
        Glide.with(h.img.getContext())
                .load(a.getCoverUrl())
                .into(h.img);

        // Gán sự kiện click cho itemView (card album)
        h.itemView.setOnClickListener(v -> onClick.onClick(a));
    }

    /** Trả về tổng số lượng album đang hiển thị. */
    @Override public int getItemCount(){ return data.size(); }

    /**
     * ViewHolder: lớp con giữ tham chiếu đến các view con trong layout item_album_card.
     * - Giúp tăng hiệu năng bằng cách tái sử dụng view cũ khi scroll (RecyclerView tái chế view).
     * - Tránh việc gọi findViewById nhiều lần.
     */
    static class VH extends RecyclerView.ViewHolder {
        ImageView img;  // ảnh bìa album
        TextView tvTitle; // tiêu đề album

        VH(View v){
            super(v);
            img = v.findViewById(R.id.imgCover);
            tvTitle = v.findViewById(R.id.tvTitle);
        }
    }
}
