
package com.example.resqmesh.emergency;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.resqmesh.data.AppDatabase;
import com.example.resqmesh.data.Emergency;
import com.example.resqmesh.data.EmergencyDao;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EmergencySyncWorker extends Worker {

    private static final String TAG = "EmergencySyncWorker";
    public static final String KEY_USER_ID = "userId";

    public EmergencySyncWorker(@NonNull Context context,
                               @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {

        String userId = getInputData().getString(KEY_USER_ID);

        if (userId == null || userId.isEmpty()) {
            return Result.failure();
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || !user.getUid().equals(userId)
                || !user.isEmailVerified()) {
            return Result.retry();
        }

        EmergencyDao dao = AppDatabase.getInstance(
                getApplicationContext()).emergencyDao();

        FirebaseFirestore firestore =
                FirebaseFirestore.getInstance();

        try {
            List<Emergency> records = dao.getPendingSync(userId);

            for (Emergency emergency : records) {

                Map<String, Object> data = new HashMap<>();

                data.put("id", emergency.id);
                data.put("userId", emergency.userId);
                data.put("type", emergency.type);
                data.put("message", emergency.message);
                data.put("latitude", emergency.latitude);
                data.put("longitude", emergency.longitude);
                data.put("timestamp", emergency.timestamp);
                data.put("status", emergency.status);
                data.put("source", emergency.source);

                Tasks.await(
                        firestore.collection("emergencies")
                                .document(emergency.id)
                                .set(data),
                        30,
                        TimeUnit.SECONDS
                );

                dao.markAsSynced(emergency.id, userId);

                Log.d(TAG, "Synced emergency: " + emergency.id);
            }

            return Result.success();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();

        } catch (Exception e) {
            Log.e(TAG, "Emergency synchronization failed", e);
            return Result.retry();
        }
    }
}
