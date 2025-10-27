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
 * RecentSongsAdapter:
 * - Hiển thị danh sách các bài hát phát gần đây trong RecyclerView (dạng single-row).
 * - Sử dụng pattern Adapter/ViewHolder chuẩn:
 *   + onCreateViewHolder: inflate layout item và tạo ViewHolder.
 *   + onBindViewHolder  : bind dữ liệu SongRemote vào ViewHolder.
 *   + getItemCount      : trả số lượng item.
 *
 * - Tương tác:
 *   + Nhận một callback OnRecentClick để đẩy sự kiện click lên Fragment/Activity.
 *
 * - Dữ liệu:
 *   + Lưu trong 'data' (List<SongRemote>) — submit(list) sẽ thay thế toàn bộ & notifyDataSetChanged().
 *
 * - Hình ảnh:
 *   + Dùng Glide để load coverUrl vào ImageView.
 *
 * Lưu ý/Best practice (tham khảo, KHÔNG sửa code):
 * - Với danh sách lớn, cân nhắc dùng DiffUtil + ListAdapter để update mượt hơn thay vì notifyDataSetChanged().
 * - Có thể set placeholder/error image cho Glide (centerCrop, circleCrop, v.v.) để UX tốt hơn.
 * - getData() đang trả bản sao (copy) để tránh bị sửa trực tiếp từ bên ngoài (giữ bất biến cho 'data').
 */
public class RecentSongsAdapter extends RecyclerView.Adapter<RecentSongsAdapter.VH> {

    /**
     * Interface callback khi người dùng click một bài hát.
     * Fragment/Activity truyền implementation để xử lý (phát nhạc, mở PlayerActivity, v.v.).
     */
    public interface OnRecentClick { void onClick(SongRemote s); }

    // Nguồn dữ liệu hiển thị. Dùng ArrayList để thao tác nhanh đơn giản.
    private final List<SongRemote> data = new ArrayList<>();

    // Callback được inject qua constructor: tuân thủ nguyên tắc "đẩy sự kiện ra ngoài Adapter".
    private final OnRecentClick onClick;

    // Nhận callback khi khởi tạo Adapter
    public RecentSongsAdapter(OnRecentClick click){ this.onClick = click; }

    /**
     * Cập nhật toàn bộ danh sách:
     * - Xoá sạch data cũ, thêm tất cả phần tử mới, rồi notifyDataSetChanged() để RecyclerView vẽ lại toàn bộ.
     * - Đơn giản/nhanh để implement, phù hợp khi danh sách nhỏ (như "recent").
     * - Nếu danh sách lớn hoặc update từng phần, nên dùng DiffUtil để tối ưu.
     */
    public void submit(List<SongRemote> list){
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        // Inflate layout của mỗi item hàng đơn (không hiển thị index/đếm)
        View item = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_single_row_no_count, p, false);
        return new VH(item);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        // Lấy phần tử theo vị trí
        SongRemote s = data.get(i);

        // Bind text: tiêu đề & tác giả
        h.title.setText(s.getTitle());
        h.author.setText(s.getAuthor());

        // Load ảnh cover bằng Glide (mặc định: không placeholder/error; có thể thêm nếu muốn)
        Glide.with(h.img.getContext()).load(s.getCoverUrl()).into(h.img);

        // Gán click listener cho cả itemView: đẩy sự kiện lên thông qua callback đã truyền vào
        h.itemView.setOnClickListener(v -> onClick.onClick(s));
    }

    // Số lượng phần tử muốn hiển thị
    @Override public int getItemCount(){ return data.size(); }

    /**
     * Trả về BẢN SAO của dữ liệu hiện tại.
     * - Tránh việc bên ngoài giữ reference trực tiếp đến 'data' và vô tình sửa đổi nội bộ Adapter.
     * - Phù hợp khi cần gửi "toàn bộ playlist hiện tại" cho Service/Player mà không lo bị mutate.
     */
    public List<SongRemote> getData() {
        return new ArrayList<>(data);
    }

    /**
     * ViewHolder: lưu reference các view con để tái sử dụng khi scroll (tối ưu hiệu năng).
     * - img  : ảnh cover
     * - title: tiêu đề bài hát
     * - author: tên nghệ sĩ/tác giả
     */
    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView title, author;
        VH(View v){
            super(v);
            img = v.findViewById(R.id.imgCover);
            title = v.findViewById(R.id.tvTitle);
            author = v.findViewById(R.id.tvAuthor);
        }
    }
}
