
package com.example.resqmesh;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.resqmesh.emergency.EmergencyHistoryActivity;
import com.example.resqmesh.emergency.SOSActivity;
import com.example.resqmesh.nearby.NearbyActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        if (user == null || !user.isEmailVerified()) {
            openLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        Button btnSos = findViewById(R.id.btnSos);
        Button btnLocation = findViewById(R.id.btnLocation);
        Button btnNearby = findViewById(R.id.btnNearby);
        Button btnHistory = findViewById(R.id.btnHistory);
        Button btnHome = findViewById(R.id.btnHome);
        Button btnAlerts = findViewById(R.id.btnAlerts);
        Button btnProfile = findViewById(R.id.btnProfile);

        btnSos.setOnClickListener(v ->
                startActivity(new Intent(this, SOSActivity.class)));

        btnNearby.setOnClickListener(v ->
                startActivity(new Intent(this, NearbyActivity.class)));

        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, EmergencyHistoryActivity.class)));

        btnLocation.setOnClickListener(v ->
                showMessage("Location screen coming next."));

        btnHome.setOnClickListener(v ->
                showMessage("You are already on Home."));

        btnAlerts.setOnClickListener(v ->
                showMessage("Emergency alerts coming next."));

        btnProfile.setOnClickListener(v -> showProfile());
    }

    private void showProfile() {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            openLogin();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("My Profile")
                .setMessage(
                        "Email: " + user.getEmail() +
                                "\nEmail verified: " + user.isEmailVerified() +
                                "\n\nDo you want to sign out?"
                )
                .setNegativeButton("Cancel", (dialog, which) ->
                        dialog.dismiss())
                .setPositiveButton("Log Out", (dialog, which) -> {
                    auth.signOut();
                    openLogin();
                })
                .show();
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
