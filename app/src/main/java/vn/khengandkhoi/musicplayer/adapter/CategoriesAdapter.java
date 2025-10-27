package vn.khengandkhoi.musicplayer.adapter;

import android.graphics.Color;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.*;

import vn.khengandkhoi.musicplayer.object.Category;
import vn.khengandkhoi.musicplayer.R;

/**
 * CategoriesAdapter:
 * - Adapter hiển thị danh sách thể loại (Category) dưới dạng các thẻ (MaterialCardView).
 * - Dùng RecyclerView + ViewHolder pattern để tái sử dụng view hiệu quả.
 *
 * 📦 Chức năng chính:
 *  - Nhận danh sách Category và hiển thị tên + màu nền (từ mã hex).
 *  - Khi click vào một category, gọi callback (OnClick.onClick) để Activity/Fragment xử lý.
 *
 * 🔧 Đặc điểm:
 *  - Dữ liệu lưu trong `data` (List<Category>).
 *  - Dễ dàng cập nhật danh sách mới bằng `submit(List<Category>)`.
 *  - Mỗi item layout: `item_category_card.xml` (phải có TextView id = tvCatName, root là MaterialCardView).
 *
 * ⚙️ UI/UX:
 *  - Card background màu lấy từ Category.colorHex (nếu null → mặc định xám đậm "#FF444444").
 *  - Dùng try/catch để tránh crash nếu colorHex không hợp lệ.
 */
public class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.VH> {

    /** Callback interface khi người dùng click 1 Category. */
    public interface OnClick { void onClick(Category c); }

    /** Danh sách dữ liệu hiển thị trên RecyclerView */
    private final List<Category> data = new ArrayList<>();

    /** Callback truyền từ Activity/Fragment để xử lý sự kiện click */
    private final OnClick onClick;

    /** Constructor: nhận callback click */
    public CategoriesAdapter(OnClick onClick) { this.onClick = onClick; }

    /**
     * Cập nhật dữ liệu hiển thị:
     *  - Xóa danh sách cũ
     *  - Thêm danh sách mới
     *  - Gọi notifyDataSetChanged() để RecyclerView vẽ lại
     *
     * 👉 Dễ dùng, nhưng nếu dữ liệu lớn, nên thay bằng DiffUtil/ListAdapter để tránh nháy toàn bộ danh sách.
     */
    public void submit(List<Category> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    /**
     * Tạo mới ViewHolder khi RecyclerView cần (inflate layout item_category_card)
     */
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View item = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_category_card, p, false);
        return new VH(item);
    }

    /**
     * Gán dữ liệu Category vào ViewHolder
     * - Set tên thể loại (TextView)
     * - Set màu nền (MaterialCardView)
     * - Gán sự kiện click → gọi callback onClick(Category)
     */
    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        Category c = data.get(i);

        // Gán tên thể loại
        h.tv.setText(c.getName());

        // Đặt màu nền cho card: dùng colorHex nếu hợp lệ, fallback màu mặc định
        try {
            ((MaterialCardView) h.itemView).setCardBackgroundColor(
                    Color.parseColor(c.getColorHex() != null ? c.getColorHex() : "#FF444444")
            );
        } catch (Exception ignore) {
            ((MaterialCardView) h.itemView).setCardBackgroundColor(Color.parseColor("#FF444444"));
        }

        // Gán listener click cho item
        h.itemView.setOnClickListener(v -> onClick.onClick(c));
    }

    /** Trả về số lượng item */
    @Override public int getItemCount() { return data.size(); }

    /**
     * ViewHolder giữ tham chiếu đến các view con của mỗi item để tránh gọi findViewById nhiều lần.
     *  - tv: TextView hiển thị tên Category.
     *  - itemView: chính là thẻ MaterialCardView gốc của layout.
     */
    static class VH extends RecyclerView.ViewHolder {
        TextView tv;
        VH(@NonNull View v) {
            super(v);
            tv = v.findViewById(R.id.tvCatName);
        }
    }
}
