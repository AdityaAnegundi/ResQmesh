
package com.example.resqmesh.emergency;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.resqmesh.LoginActivity;
import com.example.resqmesh.R;
import com.example.resqmesh.data.Emergency;
import com.example.resqmesh.data.EmergencyRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmergencyHistoryActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private TextView tvHistoryCount, tvEmptyHistory;
    private EmergencyRepository repository;
    private LiveData<List<Emergency>> historyLiveData;

    private final Observer<List<Emergency>> historyObserver =
            this::displayHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || !user.isEmailVerified()) {
            openLogin();
            return;
        }

        setContentView(R.layout.activity_history);

        historyContainer = findViewById(R.id.historyContainer);
        tvHistoryCount = findViewById(R.id.tvHistoryCount);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);

        Button btnBack = findViewById(R.id.btnHistoryBack);

        btnBack.setOnClickListener(v -> finish());

        repository = new EmergencyRepository(this);

        historyLiveData = repository.observeEmergencies(user.getUid());

        historyLiveData.observe(this, historyObserver);
    }

    private void displayHistory(List<Emergency> emergencies) {

        historyContainer.removeAllViews();

        if (emergencies == null || emergencies.isEmpty()) {
            tvHistoryCount.setText("0 emergency records");
            tvEmptyHistory.setVisibility(TextView.VISIBLE);
            return;
        }

        tvEmptyHistory.setVisibility(TextView.GONE);

        tvHistoryCount.setText(
                emergencies.size() + " emergency records found"
        );

        for (Emergency emergency : emergencies) {
            historyContainer.addView(createEmergencyCard(emergency));
        }
    }

    private LinearLayout createEmergencyCard(Emergency emergency) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(23, 33, 46));
        background.setCornerRadius(dp(15));

        card.setBackground(background);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(15);
        card.setLayoutParams(cardParams);

        TextView title = new TextView(this);
        title.setText(emergency.type.toUpperCase(Locale.ROOT));
        title.setTextColor(Color.rgb(240, 68, 82));
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);

        card.addView(title);

        TextView message = new TextView(this);
        message.setText(emergency.message);
        message.setTextColor(Color.WHITE);
        message.setTextSize(14);
        message.setPadding(0, dp(10), 0, dp(10));

        card.addView(message);

        TextView date = new TextView(this);

        String formattedDate =
                new SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        Locale.getDefault()
                ).format(new Date(emergency.timestamp));

        date.setText("Date: " + formattedDate);
        date.setTextColor(Color.LTGRAY);
        date.setTextSize(12);

        card.addView(date);

        TextView location = new TextView(this);

        if (emergency.latitude != null &&
                emergency.longitude != null) {

            location.setText(String.format(
                    Locale.US,
                    "Location: %.5f, %.5f",
                    emergency.latitude,
                    emergency.longitude
            ));

        } else {
            location.setText("Location: Not available");
        }

        location.setTextColor(Color.LTGRAY);
        location.setTextSize(12);
        location.setPadding(0, dp(8), 0, dp(8));

        card.addView(location);

        TextView status = new TextView(this);

        status.setText(
                "Status: " + emergency.status +
                        "\nCloud Sync: " +
                        (emergency.syncStatus ? "Synced" : "Pending")
        );

        status.setTextColor(
                emergency.syncStatus
                        ? Color.rgb(45, 210, 145)
                        : Color.rgb(255, 190, 75)
        );

        status.setTextSize(12);

        card.addView(status);

        return card;
    }

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
