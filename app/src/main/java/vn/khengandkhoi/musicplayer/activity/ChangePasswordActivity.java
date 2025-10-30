package vn.khengandkhoi.musicplayer.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.regex.Pattern;

import vn.khengandkhoi.musicplayer.R;

public class ChangePasswordActivity extends AppCompatActivity {

    TextInputLayout oldPasswordLayout, newPasswordLayout, confirmPasswordLayout;
    EditText oldPasswordEditText, newPasswordEditText, confirmPasswordEditText;
    TextView txtReturnToProfile;
    Button btnChangePassword;

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
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);

        addViews();

        addEvents();
    }

    private void addViews() {
        txtReturnToProfile = findViewById(R.id.returnToProfile);

        oldPasswordLayout = findViewById(R.id.matkhauHientaiLayout);
        newPasswordLayout = findViewById(R.id.matkhauMoiLayout);
        confirmPasswordLayout = findViewById(R.id.NhapLaiMatKhauInputLayout);

        oldPasswordEditText = findViewById(R.id.matkhauHientaiEditText);
        newPasswordEditText = findViewById(R.id.matkhauMoiEditText);
        confirmPasswordEditText = findViewById(R.id.NhapLaiMatKhauInputEditText);

        btnChangePassword = findViewById(R.id.btnDoiMatKhau);
    }

    private void addEvents() {
        View view = findViewById(android.R.id.content);

        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (oldPasswordEditText.isFocused()) {
                    oldPasswordEditText.clearFocus();
                }

                if (newPasswordEditText.isFocused()) {
                    newPasswordEditText.clearFocus();
                }

                if (confirmPasswordEditText.isFocused()) {
                    confirmPasswordEditText.clearFocus();
                }

                v.performClick();
            }
            return false;
        });

        txtReturnToProfile.setOnClickListener(v -> finish());

        newPasswordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String oldPassword = oldPasswordEditText.getText().toString();
                String newPassword = newPasswordEditText.getText().toString();

                if (oldPassword.equals(newPassword)) {
                    newPasswordLayout.setError("Mật khẩu mới không được trùng với mật khẩu cũ");
                } else {
                    newPasswordLayout.setError(null);
                }
            }
        });

        confirmPasswordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String newPassword = newPasswordEditText.getText().toString();
                String confirmPassword = confirmPasswordEditText.getText().toString();

                if (!newPassword.equals(confirmPassword)) {
                    confirmPasswordLayout.setError("Mật khẩu không trùng khớp");
                } else {
                    confirmPasswordLayout.setError(null);
                }
            }
        });

        btnChangePassword.setOnClickListener(v -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) return;

            String oldPassword = oldPasswordEditText.getText().toString();
            String newPassword = newPasswordEditText.getText().toString();
            String confirmPassword = confirmPasswordEditText.getText().toString();

            if (oldPassword.isEmpty()) {
                oldPasswordLayout.setError("Vui lòng nhập mật khẩu cũ");
                return;
            }
            if (newPassword.isEmpty()) {
                newPasswordLayout.setError("Vui lòng nhập mật khẩu mới");
                return;
            }
            if (confirmPassword.isEmpty()) {
                confirmPasswordLayout.setError("Vui lòng nhập lại mật khẩu");
                return;
            }

            if (oldPassword.equals(newPassword)) {
                newPasswordLayout.setError("Mật khẩu mới không được trùng với mật khẩu cũ");
                return;
            } else {
                newPasswordLayout.setError(null);
            }

            if (!newPassword.equals(confirmPassword)) {
                confirmPasswordLayout.setError("Mật khẩu không trùng khớp");
            } else {
                confirmPasswordLayout.setError(null);
            }

            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);
            user.reauthenticate(credential).addOnSuccessListener(eSuccess -> {
                user.updatePassword(newPassword).addOnSuccessListener(e -> toast("Đổi mật khẩu thành công"))
                        .addOnFailureListener(e -> {
                            toast("Đổi mật khẩu thất bại: " + e);
                            Log.d("TAG", "Đổi mật khẩu thất bại: " + e);
                        });
            }).addOnFailureListener(e -> {
                toast("Mật khẩu cũ không đúng");
                oldPasswordLayout.setError("Mật khẩu cũ không đúng");
            });
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

}