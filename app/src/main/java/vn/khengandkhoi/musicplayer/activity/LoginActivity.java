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
import com.google.firebase.auth.FirebaseUser;

import java.util.regex.Pattern;

import vn.khengandkhoi.musicplayer.R;

/**
 * LoginActivity:
 * - Màn hình đăng nhập bằng Email/Password sử dụng FirebaseAuth.
 * - Hỗ trợ:
 *   + Validate email theo Patterns.EMAIL_ADDRESS (theo chuẩn Android).
 *   + Validate password bằng regex PASSWORD_PATTERN (ít nhất 8 ký tự, có chữ hoa/thường/số).
 *   + Ẩn bàn phím khi nhấn IME_ACTION_DONE ở ô mật khẩu.
 *   + Điều hướng tới MainActivity sau khi đăng nhập thành công hoặc đã đăng nhập (onStart).
 *   + Điều hướng tới SignupActivity và ForgotPasswordActivity.
 *   + Hiển thị lỗi tiếng Việt có phân loại theo Exception Firebase.
 *
 * Lưu ý UX:
 * - TextInputLayout.setError() dùng cho hiển thị lỗi gọn gàng dưới ô nhập.
 * - Với TextInputEditText, khi setError() trực tiếp thì lỗi hiển thị ở ô; nhưng ưu tiên đặt ở Layout để đồng bộ Material.
 * - onStart() tự động chuyển thẳng vào Main nếu user đã đăng nhập -> tránh bắt nhập lại.
 */
public class LoginActivity extends AppCompatActivity {

    // Firebase Auth: entry point xác thực người dùng
    private FirebaseAuth mAuth;

    // Material components: layout chứa error + edit text cho email & password
    TextInputLayout edtEmailLayout, edtPassLayout;
    TextInputEditText edtEmail, edtPass;
    Button btnLogin, btnSignup;
    TextView txtForgotPass;


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
        // Gắn layout activity_login.xml cho màn hình đăng nhập
        setContentView(R.layout.activity_login);
        // Lấy instance FirebaseAuth dùng chung cho Activity
        mAuth = FirebaseAuth.getInstance();

        // Ánh xạ View từ layout
        addViews();

        // Gán listeners (TextWatcher, OnClick, OnEditorAction...)
        addEvents();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Vòng đời: onStart gọi sau onCreate mỗi lần Activity hiện ra (kể cả back từ Activity khác).
        // Kiểm tra user đã đăng nhập -> nếu có, chuyển thẳng vào Main để bỏ qua màn login.
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null) {
            goToMainAndFinish();
        }
    }

    private void addViews() {
        // Lấy references tới các View trong layout
        edtEmailLayout = findViewById(R.id.EmailInputLayout);
        edtPassLayout = findViewById(R.id.MatKhauInputLayout);
        edtEmail = findViewById(R.id.EmailInputEditText);
        edtPass = findViewById(R.id.MatKhauInputEditText);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);
        txtForgotPass = findViewById(R.id.txtForgotPassword);
    }

    private void addEvents() {
        // Xử lý khi nhấn nút "Done" trên bàn phím ở ô mật khẩu:
        // - Ẩn bàn phím, clear focus để UI gọn gàng, rồi focus sang nút Đăng nhập (hỗ trợ accessibility/keyboard flow).
        edtPass.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // 1) Ẩn bàn phím
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                // 2) Clear focus để không còn caret nhấp nháy ở password
                v.clearFocus();

                // 3) Tuỳ chọn: chuyển focus sang nút Đăng nhập
                btnLogin.requestFocus();

                return true; // đã xử lý
            }
            return false;
        });

        // Validate email realtime khi người dùng gõ:
        // - Dùng Patterns.EMAIL_ADDRESS để kiểm tra format email.
        // - Đặt lỗi trên TextInputLayout để hiển thị dạng Material.
        edtEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {} // Không dùng: không cần xử lý sau khi text thay đổi

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {} // Không dùng

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!Patterns.EMAIL_ADDRESS.matcher(s).matches()) {
                    edtEmailLayout.setError("Email không hợp lệ"); // Hiện thông báo lỗi dưới TextInputLayout
                } else
                    edtEmailLayout.setError(null); // Xoá lỗi khi hợp lệ
            }
        });

        // Validate password realtime theo regex:
        // - Ở đây setError trên chính TextInputEditText (vẫn OK). Có thể chuyển sang edtPassLayout.setError(...) nếu muốn đồng bộ Material.
        edtPass.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {} // Không dùng

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {} // Không dùng

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!PASSWORD_PATTERN.matcher(s).matches()) {
                    edtPass.setError("Mật khẩu cần có chữ hoa, số, ký tự đặc biệt, ≥8 ký tự");
                    // Lưu ý: Thông báo đang nói "ký tự đặc biệt" nhưng regex hiện không bắt buộc ký tự đặc biệt.
                    // Nếu muốn khớp thông điệp -> thêm điều kiện (?=.*[!@#$%^&*()_+=-]) trong PASSWORD_PATTERN.
                } else
                    edtPass.setError(null);
            }
        });

        // Xử lý nút Đăng nhập:
        // - Lấy text email/pass
        // - Kiểm tra rỗng để feedback sớm (UX tốt)
        // - Gọi signIn() -> Firebase Auth xử lý async
        btnLogin.setOnClickListener(v -> {
            String email = edtEmail.getText().toString();
            String pass = edtPass.getText().toString();

            if (email.isEmpty()) {
                edtEmailLayout.setError("Vui lòng nhập email");
                return;
            }

            if (pass.isEmpty()) {
                edtPassLayout.setError("Vui lòng nhập mật khẩu");
                return;
            }

            signIn(email, pass);
        });

        // Chuyển sang màn hình đăng ký (xoá back stack để không quay về login)
        btnSignup.setOnClickListener(v -> goToSignupAndFinish());

        // Chuyển sang màn hình quên mật khẩu (xoá back stack)
        txtForgotPass.setOnClickListener(v -> goToForgotPasswordAndFinish());
    }

    @OptIn(markerClass = UnstableApi.class)
    private void goToMainAndFinish() {
        // Điều hướng sang MainActivity
        // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK: xoá toàn bộ back stack, Main thành root -> không back về Login
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToSignupAndFinish() {
        // Điều hướng sang SignupActivity và clear stack
        Intent i = new Intent(this, SignupActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToForgotPasswordAndFinish() {
        // Điều hướng sang ForgotPasswordActivity và clear stack
        Intent i = new Intent(this, ForgotPasswordActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void signIn(String email, String pass) {
        // Gọi FirebaseAuth.signInWithEmailAndPassword (async):
        // - addOnSuccessListener: đăng nhập ok -> vào Main
        // - addOnFailureListener: hiển thị thông điệp lỗi tiếng Việt hoá qua viAuthError()
        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> goToMainAndFinish())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, viAuthError(e), Toast.LENGTH_SHORT).show();
                });
    }

    private String viAuthError(Exception e) {
        // Chuẩn hoá thông báo lỗi từ Exception Firebase sang tiếng Việt, giúp UX thân thiện hơn.
        // Th thứ tự if dưới đây dựa trên các dạng Exception thường gặp của FirebaseAuth.

        // Sai định dạng email, mật khẩu không khớp, hoặc credential không hợp lệ
        if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            return "Email hoặc mật khẩu không đúng.";
        }
        // Tài khoản không tồn tại / bị vô hiệu hoá / bị xoá
        if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            String code = ((com.google.firebase.auth.FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code))  return "Tài khoản đã bị vô hiệu hóa.";
            return "Tài khoản không tồn tại hoặc đã bị xóa.";
        }
        // Mạng lỗi: không có kết nối Internet / timeout
        if (e instanceof com.google.firebase.FirebaseNetworkException) {
            return "Không có kết nối mạng. Vui lòng kiểm tra Internet.";
        }
        // Email đã được dùng (thường gặp ở lúc đăng ký hơn đăng nhập)
        if (e instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            return "Email này đã được sử dụng.";
        }
        // Quota/Rate-limited: quá nhiều yêu cầu trong thời gian ngắn
        if (e instanceof com.google.firebase.FirebaseTooManyRequestsException) {
            return "Bạn thử quá nhiều lần. Hãy thử lại sau.";
        }
        // Fallback: giữ thông điệp gốc (tiếng Anh) để developer dễ debug khi gặp lỗi lạ
        return "Đăng nhập thất bại: " + (e != null ? e.getMessage() : "Không rõ lỗi");
    }


}