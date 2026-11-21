
package com.example.resqmesh.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EmergencyDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Emergency emergency);

    @Query("SELECT * FROM emergencies WHERE userId = :userId ORDER BY timestamp DESC")
    LiveData<List<Emergency>> observeEmergencies(String userId);

    @Query("SELECT * FROM emergencies WHERE userId = :userId AND syncStatus = 0 ORDER BY timestamp ASC")
    List<Emergency> getPendingSync(String userId);

    @Query("SELECT * FROM emergencies WHERE id = :id LIMIT 1")
    Emergency findById(String id);

    @Query("SELECT * FROM emergencies WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    Emergency getLatestEmergency(String userId);

    @Query("UPDATE emergencies SET syncStatus = 1 WHERE id = :id AND userId = :userId")
    int markAsSynced(String id, String userId);

    @Query("UPDATE emergencies SET status = :status WHERE id = :id AND userId = :userId")
    int updateStatus(String id, String userId, String status);

    @Query("SELECT COUNT(*) FROM emergencies WHERE userId = :userId")
    int getEmergencyCount(String userId);
}
