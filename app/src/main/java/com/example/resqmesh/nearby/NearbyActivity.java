
package com.example.resqmesh.nearby;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.resqmesh.LoginActivity;
import com.example.resqmesh.R;
import com.example.resqmesh.data.Emergency;
import com.example.resqmesh.data.EmergencyRepository;
import com.example.resqmesh.emergency.EmergencySyncWorker;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class NearbyActivity extends AppCompatActivity {

    private static final String TAG = "ResQMeshNearby";
    private static final int PERMISSION_REQUEST = 2001;
    private static final String SERVICE_ID = "com.example.resqmesh";
    private static final int MAX_PACKET_SIZE = 30000;

    private ConnectionsClient connectionsClient;
    private EmergencyRepository repository;

    private TextView tvStatus, tvReceived;
    private LinearLayout deviceContainer;
    private Button btnSend;

    private final Map<String, Button> discoveredDevices = new HashMap<>();
    private final Map<String, String> endpointNames = new HashMap<>();
    private final Set<String> connectedEndpoints = new HashSet<>();
    private final Set<String> pendingOutgoingEvents = new HashSet<>();

    private Runnable pendingAction;

    private final ConnectionLifecycleCallback connectionCallback =
            new ConnectionLifecycleCallback() {

                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo info) {

                    Log.d(TAG, "CONNECTION INITIATED: " + endpointId);

                    runOnUiThread(() -> {

                        tvStatus.setText(
                                "Connection request received from " +
                                        info.getEndpointName());

                        new AlertDialog.Builder(NearbyActivity.this)
                                .setTitle("Connection Request")
                                .setMessage(
                                        info.getEndpointName() +
                                                " wants to connect.\n\nAuthentication code: " +
                                                info.getAuthenticationDigits() +
                                                "\n\nCompare this code on both phones before accepting."
                                )
                                .setNegativeButton("Reject", (dialog, which) -> {
                                    Log.d(TAG, "Connection rejected");
                                    connectionsClient.rejectConnection(endpointId);
                                })
                                .setPositiveButton("Accept", (dialog, which) -> {
                                    Log.d(TAG, "Connection accepted");
                                    tvStatus.setText("Accepting connection...");
                                    connectionsClient.acceptConnection(
                                            endpointId, payloadCallback);
                                })
                                .setCancelable(false)
                                .show();
                    });
                }

                @Override
                public void onConnectionResult(
                        String endpointId, ConnectionResolution resolution) {

                    int statusCode =
                            resolution.getStatus().getStatusCode();

                    Log.d(TAG, "CONNECTION RESULT: " + statusCode);

                    runOnUiThread(() -> {

                        if (resolution.getStatus().isSuccess()) {
                            connectedEndpoints.add(endpointId);
                            tvStatus.setText("CONNECTED TO " +
                                    endpointNames.getOrDefault(endpointId, "DEVICE"));
                            updateConnectedUI();
                            showMessage("Device connected successfully.");

                        } else {
                            connectedEndpoints.remove(endpointId);
                            updateConnectedUI();

                            tvStatus.setText(
                                    "Connection failed. Code: " + statusCode);

                            showMessage(
                                    "Connection failed. Error code: " + statusCode);
                        }
                    });
                }

                @Override
                public void onDisconnected(String endpointId) {

                    Log.d(TAG, "DISCONNECTED: " + endpointId);

                    runOnUiThread(() -> {
                        connectedEndpoints.remove(endpointId);
                        updateConnectedUI();
                        showMessage("Nearby device disconnected.");
                    });
                }
            };

    private final EndpointDiscoveryCallback discoveryCallback =
            new EndpointDiscoveryCallback() {

                @Override
                public void onEndpointFound(
                        String endpointId, DiscoveredEndpointInfo info) {

                    Log.d(TAG, "DEVICE FOUND: " +
                            info.getEndpointName() + " / " + endpointId);

                    runOnUiThread(() -> {

                        if (discoveredDevices.containsKey(endpointId)) {
                            return;
                        }

                        endpointNames.put(endpointId, info.getEndpointName());

                        Button device = new Button(NearbyActivity.this);

                        device.setText(
                                info.getEndpointName() + " - CONNECT");

                        device.setEnabled(true);
                        device.setClickable(true);

                        device.setOnClickListener(v -> {
                            Log.d(TAG, "CONNECT BUTTON CLICKED: " + endpointId);
                            showMessage("Connecting to " + info.getEndpointName());
                            requestConnection(endpointId);
                        });

                        discoveredDevices.put(endpointId, device);
                        deviceContainer.addView(device);
                    });
                }

                @Override
                public void onEndpointLost(String endpointId) {

                    Log.d(TAG, "DEVICE LOST: " + endpointId);

                    runOnUiThread(() -> {
                        Button device = discoveredDevices.remove(endpointId);
                        endpointNames.remove(endpointId);

                        if (device != null) {
                            deviceContainer.removeView(device);
                        }
                    });
                }
            };

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {

                @Override
                public void onPayloadReceived(
                        String endpointId, Payload payload) {

                    if (payload.getType() != Payload.Type.BYTES) {
                        return;
                    }

                    byte[] bytes = payload.asBytes();

                    if (bytes == null || bytes.length > MAX_PACKET_SIZE) {
                        runOnUiThread(() ->
                                showMessage("Invalid packet rejected."));
                        return;
                    }

                    String message =
                            new String(bytes, StandardCharsets.UTF_8);

                    runOnUiThread(() ->
                            handleIncomingPacket(endpointId, message));
                }

                @Override
                public void onPayloadTransferUpdate(
                        String endpointId, PayloadTransferUpdate update) {

                    Log.d(TAG, "PAYLOAD TRANSFER STATUS: " +
                            update.getStatus());
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || !user.isEmailVerified()) {
            openLogin();
            return;
        }

        setContentView(R.layout.activity_nearby);

        repository = new EmergencyRepository(this);
        connectionsClient = Nearby.getConnectionsClient(this);

        tvStatus = findViewById(R.id.tvNearbyStatus);
        tvReceived = findViewById(R.id.tvReceivedMessage);
        deviceContainer = findViewById(R.id.deviceContainer);

        Button btnAdvertise = findViewById(R.id.btnAdvertise);
        Button btnDiscover = findViewById(R.id.btnDiscover);

        btnSend = findViewById(R.id.btnSendTestPacket);

        Button btnStop = findViewById(R.id.btnStopNearby);
        Button btnBack = findViewById(R.id.btnNearbyBack);

        btnAdvertise.setOnClickListener(v ->
                runWithPermissions(this::startAdvertising));

        btnDiscover.setOnClickListener(v ->
                runWithPermissions(this::startDiscovery));

        btnSend.setOnClickListener(v ->
                sendLatestEmergency());

        btnStop.setOnClickListener(v ->
                stopNearby());

        btnBack.setOnClickListener(v ->
                finish());

        updateConnectedUI();
    }

    private void runWithPermissions(Runnable action) {

        ArrayList<String> required = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 31) {
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            required.add(Manifest.permission.BLUETOOTH_CONNECT);
            required.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            required.add("android.permission.NEARBY_WIFI_DEVICES");
        }

        if (Build.VERSION.SDK_INT >= 37) {
            required.add("android.permission.ACCESS_LOCAL_NETWORK");
        }

        ArrayList<String> missing = new ArrayList<>();

        for (String permission : required) {
            if (ContextCompat.checkSelfPermission(
                    this, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (missing.isEmpty()) {
            action.run();
        } else {
            pendingAction = action;

            ActivityCompat.requestPermissions(
                    this,
                    missing.toArray(new String[0]),
                    PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST) {

            boolean granted = grantResults.length > 0;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                }
            }

            Runnable action = pendingAction;
            pendingAction = null;

            if (granted && action != null) {
                action.run();
            } else {
                showMessage("Nearby permissions were not granted.");
            }
        }
    }

    private String getDeviceName() {
        return "ResQMesh-" + Build.MODEL;
    }

    private void startAdvertising() {

        tvStatus.setText("Starting advertising...");

        connectionsClient.stopDiscovery();

        AdvertisingOptions options =
                new AdvertisingOptions.Builder()
                        .setStrategy(Strategy.P2P_CLUSTER)
                        .build();

        connectionsClient.startAdvertising(
                getDeviceName(),
                SERVICE_ID,
                connectionCallback,
                options
        ).addOnSuccessListener(unused -> {
            Log.d(TAG, "ADVERTISING STARTED");
            tvStatus.setText("Advertising to nearby devices");
        }).addOnFailureListener(error -> {
            Log.e(TAG, "ADVERTISING ERROR", error);
            displayApiError("Advertising", error);
        });
    }

    private void startDiscovery() {

        tvStatus.setText("Starting discovery...");

        connectionsClient.stopAdvertising();

        discoveredDevices.clear();
        endpointNames.clear();
        deviceContainer.removeAllViews();

        DiscoveryOptions options =
                new DiscoveryOptions.Builder()
                        .setStrategy(Strategy.P2P_CLUSTER)
                        .build();

        connectionsClient.startDiscovery(
                SERVICE_ID,
                discoveryCallback,
                options
        ).addOnSuccessListener(unused -> {
            Log.d(TAG, "DISCOVERY STARTED");
            tvStatus.setText("Searching for ResQMesh devices");
        }).addOnFailureListener(error -> {
            Log.e(TAG, "DISCOVERY ERROR", error);
            displayApiError("Discovery", error);
        });
    }

    private void requestConnection(String endpointId) {

        String name = endpointNames.getOrDefault(
                endpointId, "Unknown device");

        tvStatus.setText("Requesting connection to " + name + "...");

        Log.d(TAG, "REQUESTING CONNECTION: " + endpointId);

        connectionsClient.requestConnection(
                getDeviceName(),
                endpointId,
                connectionCallback
        ).addOnSuccessListener(unused -> {

            Log.d(TAG, "REQUEST TASK ACCEPTED: " + endpointId);

            tvStatus.setText(
                    "Connection request sent to " + name +
                            ". Waiting for both devices to respond.");

            showMessage("Request sent. Check Phone A.");

        }).addOnFailureListener(error -> {

            Log.e(TAG, "REQUEST CONNECTION FAILED", error);
            displayApiError("Connection request", error);
        });
    }

    private void displayApiError(String operation, Exception error) {

        String details = error.getLocalizedMessage();

        if (error instanceof ApiException) {
            ApiException apiError = (ApiException) error;
            details = "API status code: " +
                    apiError.getStatusCode() +
                    "\n" + apiError.getStatus().getStatusMessage();
        }

        String finalDetails = details != null
                ? details : error.toString();

        runOnUiThread(() -> {
            tvStatus.setText(operation + " failed: " + finalDetails);
            showMessage(operation + " failed: " + finalDetails);
        });
    }

    private void updateConnectedUI() {

        tvStatus.setText(
                "Connected devices: " + connectedEndpoints.size());

        btnSend.setEnabled(!connectedEndpoints.isEmpty());
    }

    private void sendLatestEmergency() {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || !user.isEmailVerified()) {
            openLogin();
            return;
        }

        if (connectedEndpoints.isEmpty()) {
            showMessage("Connect to a device first.");
            return;
        }

        btnSend.setEnabled(false);
        btnSend.setText("LOADING SOS...");

        repository.getLatestEmergency(
                user.getUid(),
                new EmergencyRepository.Callback<Emergency>() {

                    @Override
                    public void onSuccess(Emergency emergency) {

                        btnSend.setEnabled(true);
                        btnSend.setText("SEND LATEST SOS");

                        if (emergency == null ||
                                !"ACTIVE".equals(emergency.status)) {
                            showMessage("No active emergency found.");
                            return;
                        }

                        sendEmergencyConfirmation(emergency, user);
                    }

                    @Override
                    public void onError(Exception error) {
                        btnSend.setEnabled(true);
                        btnSend.setText("SEND LATEST SOS");
                        showMessage("Unable to load SOS record.");
                    }
                });
    }

    private void sendEmergencyConfirmation(
            Emergency emergency, FirebaseUser user) {

        new AlertDialog.Builder(this)
                .setTitle("Send Emergency SOS?")
                .setMessage(
                        "Type: " + emergency.type +
                                "\nMessage: " + emergency.message +
                                "\n\nConnected devices: " +
                                connectedEndpoints.size())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("SEND SOS", (dialog, which) ->
                        transmitEmergency(emergency, user))
                .show();
    }

    private void transmitEmergency(
            Emergency emergency, FirebaseUser user) {

        try {
            JSONObject packet = new JSONObject();

            packet.put("app", "ResQMesh");
            packet.put("kind", "EMERGENCY_SOS");
            packet.put("eventId", emergency.id);
            packet.put("senderUid", user.getUid());
            packet.put("senderDevice", getDeviceName());
            packet.put("type", emergency.type);
            packet.put("message", emergency.message);

            packet.put("latitude",
                    emergency.latitude == null
                            ? JSONObject.NULL : emergency.latitude);

            packet.put("longitude",
                    emergency.longitude == null
                            ? JSONObject.NULL : emergency.longitude);

            packet.put("timestamp", emergency.timestamp);

            byte[] bytes = packet.toString()
                    .getBytes(StandardCharsets.UTF_8);

            if (bytes.length > MAX_PACKET_SIZE) {
                showMessage("Packet too large.");
                return;
            }

            for (String endpointId :
                    new HashSet<>(connectedEndpoints)) {

                connectionsClient.sendPayload(
                                endpointId,
                                Payload.fromBytes(bytes)
                        ).addOnSuccessListener(unused ->
                                Log.d(TAG, "SOS PAYLOAD ACCEPTED"))
                        .addOnFailureListener(error ->
                                displayApiError("SOS transfer", error));
            }

            pendingOutgoingEvents.add(emergency.id);

            tvReceived.setText(
                    "Outgoing SOS: " + emergency.type +
                            "\nAwaiting recipient acknowledgement.");

            tvStatus.setText("SOS transmission initiated.");

        } catch (Exception e) {
            showMessage("Could not prepare SOS.");
        }
    }

    private void handleIncomingPacket(
            String endpointId, String rawMessage) {

        try {
            JSONObject packet = new JSONObject(rawMessage);

            if (!"ResQMesh".equals(packet.optString("app"))) {
                return;
            }

            if ("SOS_ACK".equals(packet.optString("kind"))) {
                String eventId = packet.optString("eventId");

                if (pendingOutgoingEvents.remove(eventId)) {
                    String device = packet.optString("receiverDevice");

                    tvReceived.setText(
                            "SOS ACKNOWLEDGED\nReceived by: " + device);

                    showMessage("SOS delivery acknowledged.");
                }

                return;
            }

            if (!"EMERGENCY_SOS".equals(
                    packet.optString("kind"))) {
                return;
            }

            receiveEmergency(endpointId, packet);

        } catch (Exception e) {
            showMessage("Invalid incoming packet.");
        }
    }

    private void receiveEmergency(
            String endpointId, JSONObject packet) throws Exception {

        FirebaseUser receiver =
                FirebaseAuth.getInstance().getCurrentUser();

        if (receiver == null || !receiver.isEmailVerified()) {
            openLogin();
            return;
        }

        final String eventId = packet.getString("eventId");
        final String senderUid = packet.getString("senderUid");
        final String senderDevice = packet.getString("senderDevice");
        final String type = packet.getString("type");
        final String message = packet.getString("message");
        final long timestamp = packet.getLong("timestamp");

        long now = System.currentTimeMillis();

        if (eventId.isEmpty() || eventId.length() > 128 ||
                senderUid.isEmpty() || senderUid.length() > 128 ||
                senderDevice.isEmpty() || senderDevice.length() > 100 ||
                message.length() > 500 ||
                timestamp <= 0 ||
                timestamp > now + 300000 ||
                timestamp < now - TimeUnit.DAYS.toMillis(7)) {
            showMessage("Invalid or expired emergency rejected.");
            return;
        }

        if (senderUid.equals(receiver.getUid())) {
            showMessage("Same-account SOS ignored.");
            return;
        }

        if (!Arrays.asList(
                "Medical", "Accident", "Fire",
                "Harassment", "Other").contains(type)) {
            showMessage("Invalid emergency type.");
            return;
        }

        final Double latitude;
        final Double longitude;

        if (!packet.isNull("latitude") &&
                !packet.isNull("longitude")) {

            double lat = packet.getDouble("latitude");
            double lon = packet.getDouble("longitude");

            if (!Double.isFinite(lat) ||
                    !Double.isFinite(lon) ||
                    lat < -90 || lat > 90 ||
                    lon < -180 || lon > 180) {
                showMessage("Invalid coordinates.");
                return;
            }

            latitude = lat;
            longitude = lon;

        } else if (!packet.isNull("latitude") ||
                !packet.isNull("longitude")) {

            showMessage("Incomplete coordinates.");
            return;

        } else {
            latitude = null;
            longitude = null;
        }

        final String localId = UUID.nameUUIDFromBytes(
                (senderUid + "|" + eventId + "|" + receiver.getUid())
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();

        repository.getEmergencyById(
                localId,
                new EmergencyRepository.Callback<Emergency>() {

                    @Override
                    public void onSuccess(Emergency existing) {

                        if (existing != null) {
                            sendAcknowledgement(endpointId, eventId);
                            showMessage("Duplicate SOS already recorded.");
                            return;
                        }

                        Emergency emergency = new Emergency(
                                localId,
                                receiver.getUid(),
                                type,
                                message,
                                latitude,
                                longitude,
                                timestamp,
                                "ACTIVE",
                                false,
                                "NEARBY_RECEIVED"
                        );

                        repository.insertEmergency(
                                emergency,
                                new EmergencyRepository.Callback<Void>() {

                                    @Override
                                    public void onSuccess(Void result) {

                                        tvReceived.setText(
                                                "INCOMING SOS SAVED\n\n" +
                                                        "From: " + senderDevice +
                                                        "\nType: " + type +
                                                        "\nMessage: " + message);

                                        scheduleSynchronization(receiver.getUid());

                                        sendAcknowledgement(endpointId, eventId);

                                        new AlertDialog.Builder(NearbyActivity.this)
                                                .setTitle("Incoming Emergency SOS")
                                                .setMessage(
                                                        "From: " + senderDevice +
                                                                "\nType: " + type +
                                                                "\nMessage: " + message)
                                                .setPositiveButton("ACKNOWLEDGE", null)
                                                .show();
                                    }

                                    @Override
                                    public void onError(Exception error) {
                                        showMessage("Could not save incoming SOS.");
                                    }
                                });
                    }

                    @Override
                    public void onError(Exception error) {
                        showMessage("Could not check duplicate SOS.");
                    }
                });
    }

    private void sendAcknowledgement(
            String endpointId, String eventId) {

        try {
            JSONObject ack = new JSONObject();

            ack.put("app", "ResQMesh");
            ack.put("kind", "SOS_ACK");
            ack.put("eventId", eventId);
            ack.put("receiverDevice", getDeviceName());

            connectionsClient.sendPayload(
                    endpointId,
                    Payload.fromBytes(
                            ack.toString().getBytes(StandardCharsets.UTF_8))
            ).addOnFailureListener(error ->
                    Log.e(TAG, "ACK SEND FAILED", error));

        } catch (Exception e) {
            Log.e(TAG, "ACK CREATION FAILED", e);
        }
    }

    private void scheduleSynchronization(String userId) {

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data input = new Data.Builder()
                .putString(EmergencySyncWorker.KEY_USER_ID, userId)
                .build();

        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(EmergencySyncWorker.class)
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
                request);
    }

    private void stopNearby() {

        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();

        connectedEndpoints.clear();
        discoveredDevices.clear();
        endpointNames.clear();
        pendingOutgoingEvents.clear();

        deviceContainer.removeAllViews();

        updateConnectedUI();

        tvStatus.setText("Status: Disconnected");
        tvReceived.setText("Communication stopped.");

        showMessage("Nearby communication stopped.");
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {

        if (connectionsClient != null) {
            connectionsClient.stopAdvertising();
            connectionsClient.stopDiscovery();
            connectionsClient.stopAllEndpoints();
        }

        super.onDestroy();
    }
}
