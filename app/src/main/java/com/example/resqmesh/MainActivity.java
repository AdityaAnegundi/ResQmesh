package com.example.resqmesh;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button btnSos, btnLocation, btnNearby, btnHistory, btnHome, btnAlerts, btnProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSos = findViewById(R.id.btnSos);
        btnLocation = findViewById(R.id.btnLocation);
        btnNearby = findViewById(R.id.btnNearby);
        btnHistory = findViewById(R.id.btnHistory);
        btnHome = findViewById(R.id.btnHome);
        btnAlerts = findViewById(R.id.btnAlerts);
        btnProfile = findViewById(R.id.btnProfile);

        btnSos.setOnClickListener(v ->
                Toast.makeText(this, "SOS feature coming next", Toast.LENGTH_SHORT).show());

        btnLocation.setOnClickListener(v ->
                Toast.makeText(this, "Location feature coming next", Toast.LENGTH_SHORT).show());

        btnNearby.setOnClickListener(v ->
                Toast.makeText(this, "Nearby Devices feature coming next", Toast.LENGTH_SHORT).show());

        btnHistory.setOnClickListener(v ->
                Toast.makeText(this, "Emergency History coming next", Toast.LENGTH_SHORT).show());

        btnHome.setOnClickListener(v ->
                Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show());

        btnAlerts.setOnClickListener(v ->
                Toast.makeText(this, "Alerts coming next", Toast.LENGTH_SHORT).show());

        btnProfile.setOnClickListener(v ->
                Toast.makeText(this, "Profile coming next", Toast.LENGTH_SHORT).show());
    }
}