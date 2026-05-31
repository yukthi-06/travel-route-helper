package com.vypeensoft.routehelper.utils;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vypeensoft.routehelper.models.Route;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.os.Environment;

public class FileUtils {
    private static final String APP_DIR_NAME = "Travel_Route_Helper";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static File getRoutesDirectory() {
        File sdcard = Environment.getExternalStorageDirectory();
        File vypeensoftDir = new File(sdcard, "Vypeensoft");
        File appDir = new File(vypeensoftDir, APP_DIR_NAME);
        File routesDir = new File(appDir, "routes");
        if (!routesDir.exists()) {
            routesDir.mkdirs();
        }
        return routesDir;
    }

    public static void saveRoute(Context context, Route route) throws IOException {
        if (route != null) {
            route.updateLinkedPointers();
        }
        File routesDir = getRoutesDirectory();
        File routeFolder = new File(routesDir, route.getRouteName());
        if (!routeFolder.exists()) {
            routeFolder.mkdirs();
        }
        File file = new File(routeFolder, route.getRouteName() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(route, writer);
        }
    }

    public static Route loadRoute(File file) throws IOException {
        Route route;
        try (FileReader reader = new FileReader(file)) {
            route = gson.fromJson(reader, Route.class);
        }
        if (route != null) {
            boolean changed = route.updateLinkedPointers();
            if (changed) {
                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(route, writer);
                }
            }
        }
        return route;
    }

    public static List<File> listRouteFiles(Context context) {
        File routesDir = getRoutesDirectory();
        List<File> routeFiles = new ArrayList<>();
        findJsonFiles(routesDir, routeFiles);
        return routeFiles;
    }

    private static void findJsonFiles(File dir, List<File> resultList) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findJsonFiles(f, resultList);
            } else if (f.getName().endsWith(".json")) {
                resultList.add(f);
            }
        }
    }
}
