package com.vypeensoft.routehelper.utils;

import android.content.Context;
import android.os.Environment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsManager {
    // Default interval is 60000ms (1 minute)
    public static final int DEFAULT_INTERVAL_MS = 60000;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static class SettingsData {
        int gpsRefreshInterval = DEFAULT_INTERVAL_MS;
    }

    private final File settingsFile;
    private SettingsData settingsData;

    public SettingsManager(Context context) {
        File sdcard = Environment.getExternalStorageDirectory();
        File vypeensoft = new File(sdcard, "Vypeensoft");
        File helper = new File(vypeensoft, "Travel_Route_Helper");
        File settingsDir = new File(helper, "settings");
        this.settingsFile = new File(settingsDir, "setings.json");
        loadSettings();
    }

    private void loadSettings() {
        if (settingsFile.exists()) {
            try (FileReader reader = new FileReader(settingsFile)) {
                settingsData = gson.fromJson(reader, SettingsData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (settingsData == null) {
            settingsData = new SettingsData();
            saveSettings();
        }
    }

    private void saveSettings() {
        File parent = settingsFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(settingsFile)) {
            gson.toJson(settingsData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getGpsRefreshInterval() {
        if (settingsData == null) {
            loadSettings();
        }
        return settingsData.gpsRefreshInterval;
    }

    public void setGpsRefreshInterval(int intervalMs) {
        if (settingsData == null) {
            settingsData = new SettingsData();
        }
        settingsData.gpsRefreshInterval = intervalMs;
        saveSettings();
    }
}
