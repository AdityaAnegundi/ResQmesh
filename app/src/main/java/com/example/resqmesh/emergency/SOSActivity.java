
package com.example.resqmesh.emergency;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;

import com.example.resqmesh.LoginActivity;
import com.example.resqmesh.R;
import com.example.resqmesh.data.Emergency;
import com.example.resqmesh.data.EmergencyRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SOSActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private Spinner spEmergencyType;
    private EditText etEmergencyMessage;
    private Button btnSendSOS, btnCancelSOS;
    private TextView tvLocationStatus;

    private EmergencyRepository repository;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseAuth auth;

    private boolean isSubmitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        if (user == null || !user.isEmailVerified()) {
            openLogin();
            return;
        }

        setContentView(R.layout.activity_sos);

        repository = new EmergencyRepository(this);

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        spEmergencyType = findViewById(R.id.spEmergencyType);
        etEmergencyMessage = findViewById(R.id.etEmergencyMessage);
        btnSendSOS = findViewById(R.id.btnSendSOS);
        btnCancelSOS = findViewById(R.id.btnCancelSOS);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);

        String[] emergencyTypes = {
                "Medical",
                "Accident",
                "Fire",
                "Harassment",
                "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                emergencyTypes
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        spEmergencyType.setAdapter(adapter);

        btnSendSOS.setOnClickListener(v -> confirmSOS());

        btnCancelSOS.setOnClickListener(v -> finish());
    }

    private void confirmSOS() {

        if (isSubmitting) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Emergency SOS")
                .setMessage("Do you want to create an emergency record?")
                .setNegativeButton("Cancel", (dialog, which) ->
                        dialog.dismiss())
                .setPositiveButton("SEND SOS", (dialog, which) ->
                        requestLocation())
                .show();
    }

    private void requestLocation() {

        isSubmitting = true;
        btnSendSOS.setEnabled(false);
        btnSendSOS.setText("PROCESSING SOS...");

        boolean fine = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarse = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            getCurrentLocation();
        } else {
            tvLocationStatus.setText(
                    "Requesting location permission...");

            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {

            boolean granted = false;

            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                getCurrentLocation();
            } else {
                tvLocationStatus.setText(
                        "Location permission denied. Saving SOS without coordinates.");

                saveEmergency(null, null);
            }
        }
    }

    private void getCurrentLocation() {

        tvLocationStatus.setText("Obtaining current location...");

        try {
            CancellationTokenSource cancellationToken =
                    new CancellationTokenSource();

            fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.getToken()
            ).addOnCompleteListener(task -> {

                Location location = null;

                if (task.isSuccessful()) {
                    location = task.getResult();
                }

                if (location != null) {
                    tvLocationStatus.setText(
                            "Location captured successfully.");
                    saveEmergency(
                            location.getLatitude(),
                            location.getLongitude()
                    );
                } else {
                    getLastKnownLocation();
                }
            });

        } catch (SecurityException e) {
            tvLocationStatus.setText(
                    "Location unavailable. Saving SOS without coordinates.");
            saveEmergency(null, null);
        }
    }

    private void getLastKnownLocation() {

        try {
            fusedLocationClient.getLastLocation()
                    .addOnCompleteListener(task -> {

                        Location location = null;

                        if (task.isSuccessful()) {
                            location = task.getResult();
                        }

                        if (location != null) {
                            tvLocationStatus.setText(
                                    "Using last known device location.");
                            saveEmergency(
                                    location.getLatitude(),
                                    location.getLongitude()
                            );
                        } else {
                            tvLocationStatus.setText(
                                    "Location unavailable. Saving SOS without coordinates.");
                            saveEmergency(null, null);
                        }
                    });

        } catch (SecurityException e) {
            saveEmergency(null, null);
        }
    }

    private void saveEmergency(Double latitude, Double longitude) {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null || !user.isEmailVerified()) {
            isSubmitting = false;
            openLogin();
            return;
        }

        String type = spEmergencyType.getSelectedItem().toString();

        String message = etEmergencyMessage.getText()
                .toString().trim();

        if (message.isEmpty()) {
            message = "Emergency assistance requested.";
        }

        Emergency emergency = new Emergency(
                UUID.randomUUID().toString(),
                user.getUid(),
                type,
                message,
                latitude,
                longitude,
                System.currentTimeMillis(),
                "ACTIVE",
                false,
                "ANDROID_APP"
        );

        tvLocationStatus.setText(
                "Saving emergency securely on this device...");

        repository.insertEmergency(emergency,
                new EmergencyRepository.Callback<Void>() {

                    @Override
                    public void onSuccess(Void result) {

                        scheduleSynchronization(user.getUid());

                        isSubmitting = false;
                        btnSendSOS.setEnabled(true);
                        btnSendSOS.setText("SEND EMERGENCY SOS");

                        tvLocationStatus.setText(
                                "Emergency saved locally. Cloud synchronization has been queued.");

                        Toast.makeText(
                                SOSActivity.this,
                                "Emergency record created successfully.",
                                Toast.LENGTH_LONG
                        ).show();

                        new AlertDialog.Builder(SOSActivity.this)
                                .setTitle("SOS Recorded")
                                .setMessage(
                                        "Your emergency record has been saved on this device. Cloud synchronization is queued."
                                )
                                .setPositiveButton("OK", (dialog, which) ->
                                        finish())
                                .show();
                    }

                    @Override
                    public void onError(Exception error) {

                        isSubmitting = false;
                        btnSendSOS.setEnabled(true);
                        btnSendSOS.setText("SEND EMERGENCY SOS");

                        Toast.makeText(
                                SOSActivity.this,
                                "Could not save emergency: " +
                                        error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void scheduleSynchronization(String userId) {

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data input = new Data.Builder()
                .putString(EmergencySyncWorker.KEY_USER_ID, userId)
                .build();

        OneTimeWorkRequest syncRequest =
                new OneTimeWorkRequest.Builder(
                        EmergencySyncWorker.class)
                        .setConstraints(constraints)
                        .setInputData(input)
                        .setBackoffCriteria(
                                androidx.work.BackoffPolicy.EXPONENTIAL,
                                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                                TimeUnit.MILLISECONDS)
                        .build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                "resqmesh_emergency_sync_" + userId,
                ExistingWorkPolicy.REPLACE,
                syncRequest
        );
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
