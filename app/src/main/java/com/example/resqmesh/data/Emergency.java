
package com.example.resqmesh.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "emergencies",
        indices = {
                @Index("userId"),
                @Index("syncStatus")
        }
)
public class Emergency {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String userId;

    @NonNull
    public String type;

    @NonNull
    public String message;

    @Nullable
    public Double latitude;

    @Nullable
    public Double longitude;

    public long timestamp;

    @NonNull
    public String status;

    public boolean syncStatus;

    @NonNull
    public String source;

    public Emergency(
            @NonNull String id,
            @NonNull String userId,
            @NonNull String type,
            @NonNull String message,
            @Nullable Double latitude,
            @Nullable Double longitude,
            long timestamp,
            @NonNull String status,
            boolean syncStatus,
            @NonNull String source) {

        this.id = id;
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.status = status;
        this.syncStatus = syncStatus;
        this.source = source;
    }
}
