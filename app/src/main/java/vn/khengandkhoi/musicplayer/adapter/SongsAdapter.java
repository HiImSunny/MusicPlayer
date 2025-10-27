package vn.khengandkhoi.musicplayer.adapter;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.*;

import vn.khengandkhoi.musicplayer.R;
import vn.khengandkhoi.musicplayer.object.SongRemote;

/**
 * Adapter cho RecyclerView hiển thị danh sách bài hát
 * Mỗi item gồm: ảnh bìa, số thứ tự, tiêu đề, tác giả
 * Khi bấm vào item → gọi callback để mở Player
 */
public class SongsAdapter extends RecyclerView.Adapter<SongsAdapter.VH> {

    /**
     * Interface callback: khi người dùng bấm vào 1 bài hát
     * Dùng để truyền dữ liệu bài hát về Fragment/Activity
     */
    public interface OnSongClick {
        void onClick(SongRemote s);
    }

    // Danh sách bài hát hiện tại (dữ liệu hiển thị)
    private final List<SongRemote> data = new ArrayList<>();

    // Callback khi bấm bài hát
    private final OnSongClick onClick;

    /**
     * Constructor: nhận vào callback xử lý khi bấm
     */
    public SongsAdapter(OnSongClick onClick) {
        this.onClick = onClick;
    }

    /**
     * Cập nhật toàn bộ danh sách bài hát
     * Xóa cũ → thêm mới → thông báo RecyclerView vẽ lại
     */
    public void submit(List<SongRemote> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged(); // Cập nhật giao diện
    }

    /**
     * Tạo ViewHolder mới khi cần hiển thị item
     * Inflate layout: item_single_row.xml
     */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Tạo view từ layout XML
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_single_row, parent, false);
        return new VH(itemView);
    }

    /**
     * Gán dữ liệu cho từng item (gọi khi RecyclerView cần hiển thị)
     */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        SongRemote song = data.get(position); // Lấy bài hát tại vị trí

        // 1. Hiển thị số thứ tự (1, 2, 3...)
        holder.number.setText(String.valueOf(position + 1));

        // 2. Tiêu đề bài hát
        holder.title.setText(song.getTitle());

        // 3. Tác giả
        holder.author.setText(song.getAuthor());

        // 4. Ảnh bìa: dùng Glide để load từ URL
        Glide.with(holder.img.getContext())
                .load(song.getCoverUrl())           // URL ảnh
                .placeholder(R.drawable.ic_music_note) // Ảnh chờ
                .error(R.drawable.ic_music_note)       // Ảnh lỗi (nếu không load được)
                .into(holder.img);                     // Đổ vào ImageView

        // 5. Xử lý sự kiện bấm vào toàn bộ item
        holder.itemView.setOnClickListener(v -> {
            if (onClick != null) {
                onClick.onClick(song); // Gọi callback → mở Player
            }
        });
    }

    /**
     * Trả về số lượng bài hát hiện có
     */
    @Override
    public int getItemCount() {
        return data.size();
    }

    /**
     * Trả về bản sao của danh sách hiện tại
     * Dùng khi cần truyền playlist vào MusicService
     */
    public List<SongRemote> getCurrentList() {
        return new ArrayList<>(data); // Trả về bản sao để tránh thay đổi ngoài ý muốn
    }

    /**
     * ViewHolder: đại diện cho 1 item trong RecyclerView
     * Lưu trữ các view con để tái sử dụng (tối ưu hiệu năng)
     */
    static class VH extends RecyclerView.ViewHolder {
        ImageView img;      // Ảnh bìa
        TextView title;     // Tiêu đề bài hát
        TextView author;    // Tác giả
        TextView number;    // Số thứ tự

        /**
         * Khởi tạo: tìm các view trong layout item_single_row.xml
         */
        VH(View itemView) {
            super(itemView);
            img    = itemView.findViewById(R.id.imgCover);
            title  = itemView.findViewById(R.id.tvTitle);
            author = itemView.findViewById(R.id.tvAuthor);
            number = itemView.findViewById(R.id.tvNumber); // Số thứ tự
        }
    }
}