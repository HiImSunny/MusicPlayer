package vn.khengandkhoi.musicplayer.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import vn.khengandkhoi.musicplayer.MusicService;
import vn.khengandkhoi.musicplayer.R;
import vn.khengandkhoi.musicplayer.activity.ChangePasswordActivity;
import vn.khengandkhoi.musicplayer.activity.LoginActivity;
import vn.khengandkhoi.musicplayer.activity.SubscriptionActivity;

/**
 * ProfileFragment:
 * - Quản lý thông tin cá nhân: avatar, họ tên, ngày sinh, email (chỉ hiển thị), đổi mật khẩu, gói premium.
 * - Tích hợp:
 *   + FirebaseAuth: displayName, photoUrl (avatar).
 *   + FirebaseStorage: upload avatar -> lấy downloadUrl.
 *   + Firestore: lưu fullName, birthday, photoUrl, updatedAt.
 * - UX:
 *   + Nhấn IME Done/Enter để lưu, validate định dạng ngày sinh dd/MM/yyyy.
 *   + Ẩn bàn phím & clear focus sau khi lưu.
 *   + Click nền (root view) để bỏ focus field.
 * - Logout:
 *   + Dừng MusicService, signOut, xoá SharedPreferences cache premium, broadcast cho service biết.
 * - Premium badge:
 *   + Đọc cache "premiumUntil" trong SharedPreferences -> hiện/ẩn tvPremiumBadge ở Activity.
 */
public class ProfileFragment extends Fragment {

    // Nút/field UI
    Button btnLogout;
    TextInputLayout edtHoTenLayout, edtNgaySinhLayout, edtEmailLayout;
    TextInputEditText edtHoTen, edtNgaySinh, edtEmail;
    LinearLayout rowSubscriptions;        // đi tới màn Subscription (mua/cấp premium)
    ImageView imgAvatar;                  // ảnh đại diện
    TextView txtSubscriptions, txtChangePassword; // label & link đổi mật khẩu

    // Firebase
    FirebaseAuth auth;
    FirebaseStorage storage;
    FirebaseFirestore db;

    // Activity Result API: chọn ảnh từ thiết bị
    ActivityResultLauncher<String> pickImageLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();

        // Đăng ký launcher để pick ảnh (MIME: image/*)
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    // Khi user chọn ảnh: show tạm lên UI và upload lên Storage
                    if (uri != null && imgAvatar != null) {
                        imgAvatar.setImageURI(uri);
                        uploadAvatar(uri);
                    }
                }
        );

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate layout của fragment
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @OptIn(markerClass = UnstableApi.class) // annotation theo project (Media3), không ảnh hưởng logic ở đây
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Ánh xạ view
        imgAvatar  = view.findViewById(R.id.imgAvatar);

        edtHoTenLayout = view.findViewById(R.id.hoTenLayout);
        edtNgaySinhLayout = view.findViewById(R.id.ngaySinhLayout);
        edtEmailLayout = view.findViewById(R.id.EmailInputLayout);

        edtHoTen   = view.findViewById(R.id.hoTenEditText);
        edtNgaySinh = view.findViewById(R.id.ngaySinhEditText);
        edtEmail = view.findViewById(R.id.EmailEditText);

        rowSubscriptions = view.findViewById(R.id.rowSubscriptions);
        txtChangePassword = view.findViewById(R.id.txtChangePassword);

        btnLogout  = view.findViewById(R.id.btnLogout);

        // Nạp sẵn dữ liệu profile vào UI (Auth + Firestore)
        preloadProfile();

        // Đổi avatar: mở picker lấy ảnh
        imgAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Gán xử lý IME Done/Enter cho 2 field: họ tên & ngày sinh
        setupProfileFieldAction(edtHoTen, "hoTen");
        setupProfileFieldAction(edtNgaySinh, "ngaySinh");

        // Bắt chạm nền để clear focus và đóng bàn phím (tạo cảm giác "tap outside to dismiss")
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (edtHoTen.isFocused()) {
                    edtHoTen.clearFocus();
                }

                if (edtNgaySinh.isFocused()) {
                    edtNgaySinh.clearFocus();
                }
                v.performClick(); // cho accessibility
            }
            return false; // cho phép sự kiện tiếp tục bubble
        });

        // Mở màn Subscription (quản lý premium)
        rowSubscriptions.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), SubscriptionActivity.class);
            startActivity(i);
        });

        // Mở màn đổi mật khẩu
        txtChangePassword.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), ChangePasswordActivity.class);
            startActivity(i);
        });

        // Đăng xuất:
        // - Dừng MusicService (gửi ACTION_STOP)
        // - FirebaseAuth.signOut()
        // - Xoá SharedPreferences cache premium (cả prefs_userId và prefs chung)
        // - Broadcast ACTION_LOGOUT cho các thành phần khác
        // - Điều hướng về Login và clear back stack
        btnLogout.setOnClickListener(v -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            String prevUid = (u != null ? u.getUid() : null);

            Intent stop = new Intent(requireContext(), MusicService.class)
                    .setAction(MusicService.ACTION_STOP);
            requireContext().startService(stop);

            FirebaseAuth.getInstance().signOut();

            if (prevUid != null) {
                this.getContext().getSharedPreferences("prefs_" + prevUid, MODE_PRIVATE).edit().clear().apply();
            }
            // Phòng khi code cũ còn dùng file "prefs" chung:
            this.getContext().getSharedPreferences("prefs", MODE_PRIVATE).edit().clear().apply();

            // Báo cho MusicService reset lịch quảng cáo
            this.getContext().sendBroadcast(new Intent("ACTION_LOGOUT"));

            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        // Hiển thị/hide badge Premium dựa vào cache "premiumUntil"
        bindProfilePremiumUI(this.requireActivity());
    }

    /**
     * Nạp dữ liệu ban đầu cho UI từ Firebase:
     * - Auth: displayName (-> họ tên), email, photoUrl (avatar)
     * - Firestore: users/{uid}.birthday
     */
    private void preloadProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Họ tên từ FirebaseAuth (displayName)
        if (user.getDisplayName() != null) {
            edtHoTen.setText(user.getDisplayName());
        }

        // Ngày sinh từ Firestore
        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String ngaySinh = doc.getString("birthday");
                        if (ngaySinh != null && !ngaySinh.isEmpty()) {
                            edtNgaySinh.setText(ngaySinh);
                        }
                    }
                })
                .addOnFailureListener(e -> toast("Lỗi tải ngày sinh: " + e.getMessage()));

        // Email từ FirebaseAuth
        edtEmail.setText(user.getEmail());

        // Avatar: nếu đã có photoUrl thì load bằng Glide
        Uri photo = user.getPhotoUrl();
        if (photo != null && !photo.toString().trim().isEmpty()) {
            Glide.with(this).load(photo).into(imgAvatar);
        }
    }

    /**
     * Upload avatar lên Firebase Storage, sau đó:
     * - Lấy downloadUrl
     * - Cập nhật photoUrl trong FirebaseAuth (user profile)
     * - Lưu photoUrl + updatedAt lên Firestore (merge)
     * - Thông báo thành công/thất bại
     */
    private void uploadAvatar(Uri localUri) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { toast("Chưa đăng nhập"); return; }

        String uid = user.getUid();
        StorageReference ref = storage.getReference("avatars/" + uid + "/avatar.jpg");

        ref.putFile(localUri)
                .addOnSuccessListener(t -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    user.updateProfile(new UserProfileChangeRequest.Builder()
                                    .setPhotoUri(downloadUri)
                                    .build())
                            .addOnSuccessListener(u -> {
                                Map<String, Object> data = new HashMap<>();
                                data.put("photoUrl", downloadUri.toString());
                                data.put("updatedAt", FieldValue.serverTimestamp());
                                mergeUser(uid, data, "Đã cập nhật avatar");
                            })
                            .addOnFailureListener(e -> toast("Lỗi cập nhật Auth: " + e.getMessage()));
                }))
                .addOnFailureListener(e -> toast("Upload lỗi: " + e.getMessage()));
    }

    /**
     * Gán hành vi cho TextInputEditText:
     * - Nhấn IME Done hoặc Enter -> validate & lưu:
     *   + fieldType="ngaySinh": check dd/MM/yyyy (strict) -> updateNgaySinh
     *   + fieldType="hoTen"   : updateHoTen
     * - Sau khi lưu: clear focus & ẩn bàn phím.
     */
    private void setupProfileFieldAction(TextInputEditText edt, String fieldType) {
        edt.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;

            if (!(isDone || isEnter)) return false;

            String value = String.valueOf(edt.getText()).trim();
            if (value.isEmpty()) {
                edt.setError(fieldType.equals("hoTen")
                        ? "Họ tên không được để trống"
                        : "Ngày sinh không được để trống");
                return true;
            }

            if (fieldType.equals("ngaySinh")) {
                try {
                    // Validate ngày sinh dd/MM/yyyy (không cho 31/02,...)
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    sdf.setLenient(false);
                    sdf.parse(value);
                } catch (Exception e) {
                    edt.setError("Định dạng phải là dd/MM/yyyy (ví dụ 28/06/2004)");
                    return true;
                }
                updateNgaySinh(value);
            } else if (fieldType.equals("hoTen")) {
                updateHoTen(value);
            }

            // Dọn UI: clear focus & ẩn bàn phím
            edt.clearFocus();
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(edt.getWindowToken(), 0);
            return true;
        });
    }

    /**
     * Cập nhật họ tên:
     * - FirebaseAuth: displayName
     * - Firestore: fullName + updatedAt
     */
    private void updateHoTen(String name) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { toast("Chưa đăng nhập"); return; }

        user.updateProfile(new UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build())
                .addOnSuccessListener(u -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("fullName", name);
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    mergeUser(user.getUid(), data, "Đã cập nhật tên");
                })
                .addOnFailureListener(e -> toast("Lỗi cập nhật Auth: " + e.getMessage()));
    }

    /**
     * Cập nhật ngày sinh lên Firestore (users/{uid}.birthday)
     * - Dùng serverTimestamp cho updatedAt để đồng bộ server time.
     */
    private void updateNgaySinh(String ngaySinh) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Chưa đăng nhập");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("birthday", ngaySinh);
        data.put("updatedAt", FieldValue.serverTimestamp());

        mergeUser(user.getUid(), data, "Đã cập nhật ngày sinh");
    }

    /**
     * Kiểm tra premium từ cache cục bộ:
     * - SharedPreferences "prefs": đọc premiumUntil và so sánh với currentTimeMillis().
     */
    private boolean isPremiumCached(Context ctx) {
        long until = ctx.getSharedPreferences("prefs", MODE_PRIVATE)
                .getLong("premiumUntil", 0L);
        return System.currentTimeMillis() < until;
    }

    /**
     * Hiển thị badge Premium trên Activity chứa fragment:
     * - Tìm TextView tvPremiumBadge trong Activity
     * - Ẩn/hiện theo isPremiumCached()
     */
    private void bindProfilePremiumUI(Activity act) {
        TextView badge = act.findViewById(R.id.tvPremiumBadge);
        if (badge == null) return;
        if (isPremiumCached(act)) {
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    /**
     * Gộp dữ liệu user vào Firestore (merge):
     * - Không ghi đè toàn bộ document, chỉ cập nhật các field có trong 'data'.
     * - successString: message hiển thị khi thành công.
     */
    private void mergeUser(String uid, Map<String, Object> data, String successString) {
        db.collection("users").document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(e -> toast(successString))
                .addOnFailureListener(e -> toast("Lỗi Firestore: " + e.getMessage()));
    }

    // Helper hiển thị Toast an toàn (check context null)
    private void toast(String msg) {
        Context c = getContext();
        if (c != null) Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }
}
