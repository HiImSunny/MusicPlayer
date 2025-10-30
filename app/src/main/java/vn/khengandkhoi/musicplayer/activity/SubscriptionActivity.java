package vn.khengandkhoi.musicplayer.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import vn.khengandkhoi.musicplayer.R;

/**
 * SubscriptionActivity:
 * - Màn hình quản lý gói Premium (hiển thị thời hạn, cho nút cấp tháng/năm).
 * - Đồng bộ premium từ Firestore về local cache (SharedPreferences).
 * - Tính toán thời gian còn lại (đếm ngược theo phút) và hiển thị.
 * - Sau khi cấp Premium: ghi Firestore -> cache -> thông báo UI -> gửi broadcast để MusicService tắt quảng cáo.
 *
 * Lưu ý:
 * - Đây là "giả lập" cấp Premium nội bộ (grant trực tiếp). Trong thực tế nên tích hợp Billing (Google Play/MoMo/ZaloPay) rồi mới grant.
 * - Dùng SharedPreferences "prefs" để cache "premiumUntil" cục bộ cho nhanh; dữ liệu gốc vẫn nằm ở Firestore.
 * - Handler remainHandler tick mỗi 60 giây để cập nhật UI "còn lại" theo thời gian thực.
 */
public class SubscriptionActivity extends AppCompatActivity {

    // Nút/label UI
    TextView txtReturnToProfile;
    TextView tvPremiumRemain;

    // Handler cập nhật chu kỳ "còn lại" theo phút
    Handler remainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Bật Edge-to-Edge để giao diện full màn (status/navigation bar trong suốt)
        setContentView(R.layout.activity_subscription);

        addViews();   // Ánh xạ view
        addEvents();  // Gán sự kiện nút/quay lại

        // Lấy premium mới nhất từ Firestore (đồng bộ khi mở màn này)
        refreshPremiumFromServer();

        // Ánh xạ hiển thị số ngày/giờ còn lại
        tvPremiumRemain = findViewById(R.id.tvPremiumRemain);

        // Nút mua (giả lập): gọi grantPremium("month"/"year") => ghi Firestore + cache
        // Sau đó updateRemainNow để update giao diện, và broadcast để MusicService biết mà tắt quảng cáo ngay
        findViewById(R.id.btnGrantMonth).setOnClickListener(v -> {
            grantPremium("month");
            updateRemainNow();
            sendBroadcast(new Intent("ACTION_PREMIUM_UPDATED")); // Cho MusicService lắng nghe và tắt ad ngay (nếu có xử lý broadcast)
        });
        findViewById(R.id.btnGrantYear).setOnClickListener(v -> {
            grantPremium("year");
            updateRemainNow();
            sendBroadcast(new Intent("ACTION_PREMIUM_UPDATED"));
        });

        // Cập nhật màn hình "hết hạn" ngay khi vào
        updateRemainNow();
    }

    private void addViews() {
        // Lấy reference các View trong layout
        txtReturnToProfile = findViewById(R.id.returnToProfile);
    }

    private void addEvents() {
        // Nút quay lại hồ sơ (đóng Activity hiện tại)
        txtReturnToProfile.setOnClickListener(v -> finish());
    }

    /**
     * Cấp Premium cho user trong khoảng thời gian period:
     * - "month": +30 ngày
     * - "year" : +365 ngày
     *
     * Quy tắc cộng:
     * - Nếu đang còn hạn (premiumUntil > now): cộng dồn từ premiumUntil
     * - Nếu đã hết hạn: tính từ hiện tại (now)
     *
     * Ghi Firestore fields:
     * - premiumUntil: mốc epoch ms hết hạn
     * - lastGrantAt : thời điểm cấp (now)
     * - plan        : gói ("month"/"year")
     *
     * Sau khi ghi thành công:
     * - cachePremium(newUntil) vào SharedPreferences
     * - Toast thông báo
     * - recreate() để render lại Activity (áp dụng UI mới)
     */
    private void grantPremium(String period) { // "month" hoặc "year"
        long now = System.currentTimeMillis();
        long addMs = "year".equalsIgnoreCase(period)
                ? 365L * 24 * 3600 * 1000
                : 30L  * 24 * 3600 * 1000;

        // Nếu còn hạn, cộng dồn; nếu hết hạn, tính từ hiện tại
        long currentCached = getSharedPreferences("prefs", MODE_PRIVATE)
                .getLong("premiumUntil", 0L);
        long base = Math.max(now, currentCached); // chọn mốc lớn hơn giữa now và cached (để cộng dồn hợp lý)
        long newUntil = base + addMs;

        // Data ghi lên Firestore
        Map<String, Object> data = new HashMap<>();
        data.put("premiumUntil", newUntil);
        data.put("lastGrantAt", now);
        data.put("plan", period);

        // Yêu cầu user đã đăng nhập (uid != null) để lưu theo users/{uid}
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge()) // merge để không ghi đè các field khác
                .addOnSuccessListener(v -> {
                    cachePremium(newUntil); // Lưu cache local để app phản hồi nhanh không cần đợi mạng
                    Toast.makeText(this, "Đã cấp Premium ("+period+")", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi ghi Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );

        // Gọi recreate() để Activity được tạo lại (tái inflate layout, chạy lại onCreate),
        // đảm bảo UI phản ánh ngay dữ liệu mới (ví dụ text "Hết hạn" thay đổi).
        recreate();
    }

    /**
     * Lưu premiumUntil xuống SharedPreferences để truy cập/so sánh nhanh.
     * - Ưu điểm: không cần gọi mạng mỗi lần check isPremium() trong MusicService/UI.
     */
    private void cachePremium(long until) {
        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit().putLong("premiumUntil", until).apply();
    }

    /**
     * Kiểm tra trạng thái Premium từ cache cục bộ.
     * - Trả về true nếu "premiumUntil" trong cache còn lớn hơn thời gian hiện tại.
     * - Dùng cho UI local hoặc logic tắt quảng cáo nhanh (khi Service cũng dùng chung cache).
     */
    private boolean isPremiumCached() {
        long until = getSharedPreferences("prefs", MODE_PRIVATE).getLong("premiumUntil", 0L);
        return System.currentTimeMillis() < until;
    }

    /**
     * Đồng bộ từ Firestore khi mở màn hình:
     * - Lấy users/{uid}.premiumUntil (nếu có) và cache xuống local.
     * - Mục tiêu: nếu user đổi máy/clear data thì cache được "làm ấm" từ server.
     */
    // Đồng bộ từ Firestore khi mở app
    private void refreshPremiumFromServer() {
        var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    Long until = doc.getLong("premiumUntil");
                    if (until != null) {
                        cachePremium(until);
                    }
                });
    }

    // Truy xuất premiumUntil từ cache (epoch milliseconds)
    private long getPremiumUntil() {
        return getSharedPreferences("prefs", MODE_PRIVATE).getLong("premiumUntil", 0L);
    }

    // Format chuỗi "còn lại" đẹp mắt: ngày/giờ/phút
    private String fmtRemain(long ms) {
        if (ms <= 0) return "Hết hạn";
        long sec = ms / 1000;
        long d = sec / 86400; sec %= 86400;
        long h = sec / 3600;  sec %= 3600;
        long m = sec / 60;
        if (d > 0) return d + " ngày " + h + " giờ " + m + " phút";
        if (h > 0) return h + " giờ " + m + " phút";
        return m + " phút";
    }

    // Format thời điểm hết hạn dạng dd/MM/yyyy HH:mm (local timezone)
    private String fmtDate(long epochMs) {
        java.text.SimpleDateFormat df =
                new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
        return df.format(new java.util.Date(epochMs));
    }

    /**
     * Cập nhật UI "Hết hạn: <dd/MM/yyyy HH:mm> (<còn lại>)"
     * - đọc premiumUntil từ cache
     * - tính remain = until - now
     * - setText cho tvPremiumRemain (nếu đã ánh xạ)
     */
    private void updateRemainNow() {
        long until = getPremiumUntil();
        long remain = until - System.currentTimeMillis();
        if (tvPremiumRemain != null) {
            tvPremiumRemain.setText("Hết hạn: " + (until > 0 ? fmtDate(until) : "-")
                    + "   (" + fmtRemain(remain) + ")");
        }
    }

    // Runnable tick mỗi phút để làm mới "còn lại" trên UI
    private final Runnable remainTick = new Runnable() {
        @Override public void run() {
            updateRemainNow(); // cập nhật text mỗi phút
            remainHandler.postDelayed(this, 60_000); // mỗi 60 giây
        }
    };

    // Đăng ký tick khi Activity hiển thị trở lại (đảm bảo không nhân đôi callback)
    @Override protected void onResume() {
        super.onResume();
        remainHandler.removeCallbacks(remainTick);
        remainHandler.post(remainTick);
    }

    // Huỷ tick khi Activity không còn ở foreground để tiết kiệm tài nguyên
    @Override protected void onPause() {
        super.onPause();
        remainHandler.removeCallbacks(remainTick);
    }
}
