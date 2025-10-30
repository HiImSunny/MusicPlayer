package vn.khengandkhoi.musicplayer.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import vn.khengandkhoi.musicplayer.R;

/**
 * ForgotPasswordActivity:
 * - Màn hình "Quên mật khẩu" cho phép người dùng nhập email đã đăng ký.
 * - Xác thực format email realtime bằng Patterns.EMAIL_ADDRESS.
 * - Khi bấm "Tiếp tục", gọi FirebaseAuth.sendPasswordResetEmail(email) để Firebase gửi
 *   đường link đặt lại mật khẩu vào email người dùng.
 * - Thành công: thông báo Toast và điều hướng về Login.
 * - Thất bại: hiển thị Toast ngắn "Thử lại sau." (giữ nguyên code), có thể log lỗi khi debug.

 */
public class ForgotPasswordActivity extends AppCompatActivity {

    // TextInputLayout dùng để hiển thị lỗi Material dưới ô nhập
    TextInputLayout edtEmailLayout;
    // Ô nhập email (Material TextInputEditText)
    TextInputEditText edtEmail;
    // Nút gửi yêu cầu reset
    Button btnNext;
    // Text dẫn về màn hình Đăng nhập
    TextView txtLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Bật Edge-to-Edge để nội dung có thể vẽ tràn viền màn hình (status/navigation bar)
        // Yêu cầu layout xử lý padding/insets phù hợp (XML đã thiết kế tương thích).
        EdgeToEdge.enable(this);
        // Gắn layout UI cho màn hình
        setContentView(R.layout.activity_forgot_password);

        // Ánh xạ view
        addViews();

        // Gán sự kiện (TextWatcher validate & click listeners)
        addEvents();
    }

    private void addViews() {
        // Lấy references tới các view trong layout
        edtEmailLayout = findViewById(R.id.EmailInputLayout);
        edtEmail = findViewById(R.id.EmailInputEditText);
        btnNext = findViewById(R.id.btnNext);
        txtLogin = findViewById(R.id.txtLogin);
    }

    private void addEvents() {
        // TextWatcher để validate format email theo thời gian thực khi người dùng gõ
        edtEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                // Không dùng: validate đã xử lý trong onTextChanged
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Không dùng
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Kiểm tra format email: hợp lệ -> clear error, không hợp lệ -> hiển thị lỗi Material
                if (!Patterns.EMAIL_ADDRESS.matcher(s).matches()) {
                    edtEmailLayout.setError("Email không hợp lệ");
                } else {
                    edtEmailLayout.setError(null);
                }
            }
        });

        // Lấy instance FirebaseAuth (theo scope method; có thể dùng field nếu muốn tái sử dụng nhiều nơi)
        FirebaseAuth auth = FirebaseAuth.getInstance();
        btnNext.setOnClickListener(v -> {
            // Lấy email, trim khoảng trắng 2 đầu để tránh lỗi đánh máy
            String email = edtEmail.getText().toString().trim();
            // Kiểm tra rỗng sớm (UX tốt): nếu trống -> setError ngay trên ô nhập
            if (email.isEmpty()) { edtEmail.setError("Nhập email"); return; }

            // Gửi email reset password qua Firebase
            // - Firebase sẽ gửi link reset đến email nếu tồn tại trong hệ thống và được phép
            // - API chạy async: kết quả trả về trong OnCompleteListener
            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Thành công: thông báo và điều hướng về Login
                            Toast.makeText(this, "Link đã được gửi đến email của bạn.", Toast.LENGTH_SHORT).show();

                            goToLoginAndFinish();
                        } else {
                            // Thất bại: (giữ nguyên code) báo chung chung "Thử lại sau."
                            // Có thể lấy task.getException() để map lỗi chi tiết (ví dụ email không tồn tại),
                            // nhưng để tránh lộ thông tin người dùng, thông điệp chung là an toàn hơn.
                            Exception e = task.getException();
                            Toast.makeText(this, "Thử lại sau.", Toast.LENGTH_SHORT).show();
                            // Log.e("Forgot", "reset failed", e); // Gợi ý: bật log khi debug
                        }
                    });
        });

        // Cho phép quay lại màn hình đăng nhập
        txtLogin.setOnClickListener(v -> goToLoginAndFinish());
    }

    private void goToLoginAndFinish() {
        // Điều hướng về LoginActivity và xóa toàn bộ back stack
        // => người dùng không back lại màn quên mật khẩu sau khi rời đi
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

}
