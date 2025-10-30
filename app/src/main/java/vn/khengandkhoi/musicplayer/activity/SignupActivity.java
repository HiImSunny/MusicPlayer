package vn.khengandkhoi.musicplayer.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.regex.Pattern;

import vn.khengandkhoi.musicplayer.R;

/**
 * SignupActivity:
 * - Màn hình đăng ký tài khoản bằng email/password sử dụng Firebase Authentication.
 * - Tính năng:
 *   + Validate email theo Patterns.EMAIL_ADDRESS (chuẩn Android).
 *   + Validate password theo regex (ít nhất 8 ký tự, có chữ hoa/thường/số).
 *   + Ẩn bàn phím khi nhấn IME_ACTION_DONE ở ô mật khẩu.
 *   + Điều hướng tới LoginActivity (đã có tài khoản) hoặc MainActivity sau khi đăng ký thành công.
 * - Lưu ý UX:
 *   + Dùng TextInputLayout.setError(...) để hiển thị lỗi đúng chuẩn Material.
 *   + Cân nhắc hiển thị loading/disable nút trong khi đăng ký để tránh double-click.
 * - Bảo mật:
 *   + Nên bật App Check/Email verification tuỳ yêu cầu.
 */
public class SignupActivity extends AppCompatActivity {

    // Entry point Firebase Authentication
    private FirebaseAuth mAuth;

    // View tham chiếu tới các ô nhập + layout lỗi Material
    TextInputLayout edtEmailLayout, edtPassLayout, edtConfirmPassLayout;
    TextInputEditText edtEmail, edtPass, edtConfirmPass;
    Button btnSignUp;
    TextView txtLogin;

    // Mẫu kiểm tra độ mạnh của mật khẩu:
    // - Ít nhất 1 chữ số, 1 chữ thường, 1 chữ hoa, và tối thiểu 8 ký tự.
    // - LƯU Ý: thông báo có nhắc "ký tự đặc biệt" nhưng regex hiện KHÔNG bắt buộc ký tự đặc biệt.
    //   Nếu muốn khớp thông điệp, thêm (?=.*[!@#$%^&*()_+=-]) vào regex.
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^" +
                    "(?=.*[0-9])" +         // ít nhất 1 chữ số
                    "(?=.*[a-z])" +         // ít nhất 1 chữ thường
                    "(?=.*[A-Z])" +         // ít nhất 1 chữ hoa
                    ".{8,}" +               // ít nhất 8 ký tự
                    "$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Gắn layout cho màn hình đăng ký
        setContentView(R.layout.activity_signup);
        // Lấy instance FirebaseAuth để thao tác đăng ký
        mAuth = FirebaseAuth.getInstance();

        // Ánh xạ view từ XML
        addViews();

        // Gán sự kiện cho các thành phần UI
        addEvents();
    }

    private void addViews() {
        // Lấy reference tới các TextInputLayout (để hiển thị error dưới dạng Material)
        edtEmailLayout = findViewById(R.id.EmailInputLayout);
        edtPassLayout = findViewById(R.id.MatKhauInputLayout);
        edtConfirmPassLayout = findViewById(R.id.NhapLaiMatKhauInputLayout);

        // Lấy reference tới các TextInputEditText (ô nhập liệu)
        edtEmail = findViewById(R.id.EmailInputEditText);
        edtPass = findViewById(R.id.MatKhauInputEditText);
        edtConfirmPass = findViewById(R.id.NhapLaiMatKhauInputEditText);

        // Nút đăng ký và link chuyển qua màn hình đăng nhập
        btnSignUp = findViewById(R.id.btnSignup);
        txtLogin = findViewById(R.id.txtLogin);
    }

    private void addEvents() {
        // Xử lý khi người dùng nhấn nút Done (IME_ACTION_DONE) ở bàn phím tại ô mật khẩu:
        // - Ẩn bàn phím để UI gọn.
        // - Clear focus để caret không nhấp nháy.
        // - Chuyển focus sang nút Đăng ký (tùy UX).
        edtPass.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // 1) Ẩn bàn phím
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                // 2) Clear focus để không còn caret nhấp nháy ở password
                v.clearFocus();

                // 3) Tuỳ chọn: chuyển focus sang nút Đăng nhập
                btnSignUp.requestFocus();

                return true; // đã xử lý
            }
            return false;
        });

        // Theo dõi thay đổi email để validate realtime:
        // - Patterns.EMAIL_ADDRESS kiểm tra format email hợp lệ (a@b.c).
        // - Đặt lỗi tại TextInputLayout để hiển thị đúng Material guideline.
        edtEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {} // không dùng

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {} // không dùng

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!Patterns.EMAIL_ADDRESS.matcher(s).matches()) {
                    edtEmailLayout.setError("Email không hợp lệ");
                } else
                    edtEmailLayout.setError(null); // Xoá lỗi khi hợp lệ
            }
        });

        // Theo dõi thay đổi mật khẩu để validate theo regex:
        // - Lưu ý: thông báo yêu cầu "ký tự đặc biệt" nhưng regex hiện tại chưa bắt buộc.
        // - Có thể chuyển sang edtPassLayout.setError(...) để thống nhất cách hiển thị lỗi.
        edtPass.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {} // không dùng

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {} // không dùng

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!PASSWORD_PATTERN.matcher(s).matches()) {
                    edtPass.setError("Mật khẩu cần có chữ hoa, số, ký tự đặc biệt, ≥8 ký tự");
                } else
                    edtPass.setError(null);
            }
        });

        // Nút Đăng ký:
        // - Lấy dữ liệu từ 3 ô.
        // - Kiểm tra rỗng từng trường để feedback ngay (UX tốt).
        // - (Khuyến nghị) Kiểm tra confirmPass trùng pass trước khi gọi signUp để tránh call API lỗi.
        btnSignUp.setOnClickListener(v -> {
            String email = edtEmail.getText().toString();
            String pass = edtPass.getText().toString();
            String confirmPass = edtConfirmPass.getText().toString();

            if (email.isEmpty()) {
                edtEmailLayout.setError("Vui lòng nhập email");
                return;
            }

            if (pass.isEmpty()) {
                edtPassLayout.setError("Vui lòng nhập mật khẩu");
                return;
            }

            if (confirmPass.isEmpty()) {
                edtConfirmPassLayout.setError("Vui lòng nhập lại mật khẩu");
                return;
            }

            // LƯU Ý (UX/logic): Nên kiểm tra 2 mật khẩu có khớp hay không trước khi gọi Firebase:
            // if (!pass.equals(confirmPass)) { edtConfirmPassLayout.setError("Mật khẩu nhập lại không khớp"); return; }
            // Ở đây giữ nguyên code theo yêu cầu (không sửa), nên chưa check trùng khớp.

            signUp(email, pass);
        });

        // Chuyển sang màn hình Login (xoá back stack)
        txtLogin.setOnClickListener(v -> goToLoginAndFinish());
    }

    private void signUp(String email, String pass) {
        // Gọi Firebase Auth để tạo user bằng email & password (async).
        // - Thành công: điều hướng sang MainActivity (xoá stack).
        // - Thất bại: hiển thị e.getMessage() (tiếng Anh); có thể map sang TV để thân thiện hơn.
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> goToMainAndFinish())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @OptIn(markerClass = UnstableApi.class) // annotation chỉ ra có thể dùng API không ổn định (nếu project có Media3)
    private void goToMainAndFinish() {
        // Điều hướng sang MainActivity và xoá toàn bộ back stack
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToLoginAndFinish() {
        // Điều hướng sang LoginActivity và xoá toàn bộ back stack
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

}
