
package com.example.resqmesh.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmergencyRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    private final EmergencyDao emergencyDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public EmergencyRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        emergencyDao = database.emergencyDao();
    }

    public void insertEmergency(Emergency emergency,
                                Callback<Void> callback) {
        executor.execute(() -> {
            try {
                emergencyDao.insert(emergency);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public LiveData<List<Emergency>> observeEmergencies(String userId) {
        return emergencyDao.observeEmergencies(userId);
    }

    public void getPendingEmergencies(String userId,
                                      Callback<List<Emergency>> callback) {
        executor.execute(() -> {
            try {
                List<Emergency> records =
                        emergencyDao.getPendingSync(userId);
                mainHandler.post(() -> callback.onSuccess(records));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getEmergencyById(String id,
                                 Callback<Emergency> callback) {
        executor.execute(() -> {
            try {
                Emergency record = emergencyDao.findById(id);
                mainHandler.post(() -> callback.onSuccess(record));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getLatestEmergency(String userId,
                                   Callback<Emergency> callback) {
        executor.execute(() -> {
            try {
                Emergency record =
                        emergencyDao.getLatestEmergency(userId);
                mainHandler.post(() -> callback.onSuccess(record));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void markAsSynced(String id, String userId,
                             Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int result = emergencyDao.markAsSynced(id, userId);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void updateStatus(String id, String userId, String status,
                             Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int result =
                        emergencyDao.updateStatus(id, userId, status);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getEmergencyCount(String userId,
                                  Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int count = emergencyDao.getEmergencyCount(userId);
                mainHandler.post(() -> callback.onSuccess(count));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }
}
