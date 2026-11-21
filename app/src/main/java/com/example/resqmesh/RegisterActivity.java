
package com.example.resqmesh;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button btnRegister;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);

        TextView tvGoToLogin = findViewById(R.id.tvGoToLogin);

        btnRegister.setOnClickListener(v -> registerUser());
        tvGoToLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (name.length() < 2 || name.length() > 80) {
            etName.setError("Enter a valid name");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must contain at least 6 characters");
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("CREATING ACCOUNT...");

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        btnRegister.setEnabled(true);
                        btnRegister.setText("CREATE ACCOUNT");
                        Toast.makeText(this, "Registration failed",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();

                    if (user == null) {
                        auth.signOut();
                        btnRegister.setEnabled(true);
                        btnRegister.setText("CREATE ACCOUNT");
                        return;
                    }

                    saveProfile(user, name, email);
                });
    }

    private void saveProfile(FirebaseUser user, String name, String email) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("name", name);
        profile.put("email", email);
        profile.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(user.getUid())
                .set(profile)
                .addOnCompleteListener(profileTask -> {

                    user.sendEmailVerification()
                            .addOnCompleteListener(verificationTask -> {

                                auth.signOut();
                                btnRegister.setEnabled(true);
                                btnRegister.setText("CREATE ACCOUNT");

                                if (verificationTask.isSuccessful()) {
                                    Toast.makeText(this,
                                            "Verification email sent. Please verify your email.",
                                            Toast.LENGTH_LONG).show();

                                    startActivity(new Intent(
                                            this, LoginActivity.class));
                                    finish();

                                } else {
                                    Toast.makeText(this,
                                            "Account created, but verification email failed.",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                });
    }
}
