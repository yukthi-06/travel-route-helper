package com.vypeensoft.routehelper;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvAppVersion = findViewById(R.id.tvAppVersion);
        String versionInfo = String.format("Version: %s\nBuild: %s\nGit SHA: %s\nGit SHA Full: %s\nGit Tag: %s",
                BuildConfig.VERSION_NAME,
                BuildConfig.BUILD_TIMESTAMP,
                BuildConfig.GIT_SHA,
                BuildConfig.GIT_SHA_FULL,
                BuildConfig.GIT_TAG);
        tvAppVersion.setText(versionInfo);
    }

    private void applyKeepScreenOn() {
        if (new com.vypeensoft.routehelper.utils.SettingsManager(this).getKeepScreenOn()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyKeepScreenOn();
    }
}
