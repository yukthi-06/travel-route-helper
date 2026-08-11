package com.vypeensoft.routehelper;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vypeensoft.routehelper.adapters.PointAdapter;
import com.vypeensoft.routehelper.models.Point;
import com.vypeensoft.routehelper.models.Route;
import com.vypeensoft.routehelper.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.TextView;

public class RouteDetailsActivity extends AppCompatActivity implements PointAdapter.OnPointClickListener {

    private String filePath;
    private Route currentRoute;
    private PointAdapter adapter;
    private RecyclerView recyclerView;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FloatingActionButton fabAddPoint;
    private android.view.Menu optionsMenu;
    private Set<String> selectedFilters = new HashSet<>();
    private TextView tvNoGps;
    private android.content.BroadcastReceiver gpsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_details);

        filePath = getIntent().getStringExtra("FILE_PATH");
        if (filePath == null) {
            finish();
            return;
        }

        tvNoGps = findViewById(R.id.tv_no_gps);
        checkInitialGpsState();
        setupGpsListener();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerViewPoints);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fabAddPoint = findViewById(R.id.fabAddPoint);
        fabAddPoint.setOnClickListener(v -> {
            Intent intent = new Intent(RouteDetailsActivity.this, AddPointActivity.class);
            intent.putExtra("FILE_PATH", filePath);
            if (adapter != null) {
                intent.putExtra("USER_ROW_POSITION", adapter.getUserRowPosition());
            }
            startActivity(intent);
        });

        // Set up ItemTouchHelper for drag and drop reordering
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return adapter != null && adapter.isEditMode();
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (adapter == null) return false;
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                adapter.onItemMove(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Swipe disabled
            }
        };
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (adapter != null && locationResult.getLastLocation() != null) {
                    adapter.updateCurrentLocation(locationResult.getLastLocation());
                }
            }
        };

        loadRouteData();
    }

    private List<Point> displayedPoints = new ArrayList<>();

    private void loadRouteData() {
        try {
            currentRoute = FileUtils.loadRoute(new File(filePath));
            getSupportActionBar().setTitle(currentRoute.getRouteName());
            
            displayedPoints.clear();
            for (Point p : currentRoute.getPoints()) {
                if (!p.isDeleted()) {
                    if (selectedFilters.isEmpty()) {
                        displayedPoints.add(p);
                    } else {
                        boolean match = false;
                        for (String type : p.getTypes()) {
                            if (selectedFilters.contains(type)) {
                                match = true;
                                break;
                            }
                        }
                        if (match) {
                            displayedPoints.add(p);
                        }
                    }
                }
            }

            if (adapter == null) {
                adapter = new PointAdapter(displayedPoints, this);
                recyclerView.setAdapter(adapter);
            } else {
                adapter.updateData(displayedPoints);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load route: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onPointClick(int position) {
        // Find the point in displayedPoints
        Point point = displayedPoints.get(position);
        Intent intent = new Intent(this, AddPointActivity.class);
        intent.putExtra("FILE_PATH", filePath);
        intent.putExtra("POINT_ID", point.getPointId());
        intent.putExtra("POINT_NAME", point.getName());
        intent.putExtra("POINT_LAT", point.getLatitude());
        intent.putExtra("POINT_LNG", point.getLongitude());
        intent.putStringArrayListExtra("POINT_TYPES", new ArrayList<>(point.getTypes()));
        startActivity(intent);
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
        loadRouteData();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Fetch last known location immediately as a baseline
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null && adapter != null) {
                adapter.updateCurrentLocation(location);
            }
        });

        int intervalMs = new com.vypeensoft.routehelper.utils.SettingsManager(this).getGpsRefreshInterval();
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .build();
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void updateEditModeUI(boolean editMode) {
        if (adapter != null) {
            adapter.setEditMode(editMode);
        }
        if (fabAddPoint != null) {
            if (editMode) {
                fabAddPoint.hide();
            } else {
                fabAddPoint.show();
            }
        }
        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_edit).setVisible(!editMode);
            optionsMenu.findItem(R.id.action_reverse).setVisible(!editMode);
            optionsMenu.findItem(R.id.action_filter).setVisible(!editMode);
            optionsMenu.findItem(R.id.action_done).setVisible(editMode);
            optionsMenu.findItem(R.id.action_cancel).setVisible(editMode);
        }
    }

    private void saveReorderedPoints() {
        if (adapter == null || currentRoute == null) return;
        List<Point> reorderedActive = adapter.getPointsList();

        List<Point> newPointsList = new ArrayList<>(reorderedActive);
        for (Point p : currentRoute.getPoints()) {
            if (p.isDeleted()) {
                newPointsList.add(p);
            }
        }

        currentRoute.getPoints().clear();
        currentRoute.getPoints().addAll(newPointsList);

        try {
            FileUtils.saveRoute(this, currentRoute);
            Toast.makeText(this, "Route order saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save route: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_route_details, menu);
        this.optionsMenu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        boolean isEdit = adapter != null && adapter.isEditMode();
        menu.findItem(R.id.action_edit).setVisible(!isEdit);
        menu.findItem(R.id.action_reverse).setVisible(!isEdit);
        android.view.MenuItem filterItem = menu.findItem(R.id.action_filter);
        filterItem.setVisible(!isEdit);
        if (!selectedFilters.isEmpty()) {
            filterItem.setIcon(R.drawable.ic_filter_active);
        } else {
            filterItem.setIcon(R.drawable.ic_filter);
        }
        menu.findItem(R.id.action_done).setVisible(isEdit);
        menu.findItem(R.id.action_cancel).setVisible(isEdit);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            updateEditModeUI(true);
            return true;
        } else if (id == R.id.action_reverse) {
            reverseRouteDirection();
            return true;
        } else if (id == R.id.action_done) {
            saveReorderedPoints();
            updateEditModeUI(false);
            loadRouteData();
            return true;
        } else if (id == R.id.action_cancel) {
            updateEditModeUI(false);
            loadRouteData();
            return true;
        } else if (id == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFilterDialog() {
        String[] filterOptions = {"Food", "Toll", "Petrol", "Toilet"};
        boolean[] checkedItems = new boolean[filterOptions.length];
        
        for (int i = 0; i < filterOptions.length; i++) {
            checkedItems[i] = selectedFilters.contains(filterOptions[i]);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter Points");
        builder.setMultiChoiceItems(filterOptions, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                if (isChecked) {
                    selectedFilters.add(filterOptions[which]);
                } else {
                    selectedFilters.remove(filterOptions[which]);
                }
            }
        });

        builder.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                loadRouteData();
                invalidateOptionsMenu();
            }
        });
        builder.setNegativeButton("Clear", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedFilters.clear();
                loadRouteData();
                invalidateOptionsMenu();
            }
        });
        builder.show();
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isEditMode()) {
            updateEditModeUI(false);
            loadRouteData();
        } else {
            super.onBackPressed();
        }
    }

    private void reverseRouteDirection() {
        if (currentRoute == null) return;

        // 1. Reverse active points
        List<Point> activePoints = new ArrayList<>();
        List<Point> deletedPoints = new ArrayList<>();
        for (Point p : currentRoute.getPoints()) {
            if (p.isDeleted()) {
                deletedPoints.add(p);
            } else {
                activePoints.add(p);
            }
        }
        Collections.reverse(activePoints);
        List<Point> newPointsList = new ArrayList<>(activePoints);
        newPointsList.addAll(deletedPoints);

        currentRoute.getPoints().clear();
        currentRoute.getPoints().addAll(newPointsList);

        // 2. Calculate and set the reversed name
        String oldName = currentRoute.getRouteName();
        String newName = getReversedRouteName(oldName);
        currentRoute.setRouteName(newName);

        // 3. Perform atomic file system rename (folder + file)
        try {
            File oldFile = new File(filePath);
            File oldFolder = oldFile.getParentFile();
            File routesDir = FileUtils.getRoutesDirectory();
            File newFolder = new File(routesDir, newName);

            // Avoid collision: clean up existing destination folder if it already exists
            if (newFolder.exists() && !newFolder.equals(oldFolder)) {
                deleteRecursive(newFolder);
            }

            // Rename old folder to new folder
            if (oldFolder.renameTo(newFolder)) {
                File renamedJsonFile = new File(newFolder, newName + ".json");
                File movedJsonFile = new File(newFolder, oldFile.getName());

                if (movedJsonFile.renameTo(renamedJsonFile)) {
                    // Update filePath and save to update internal routeName GSON field
                    filePath = renamedJsonFile.getAbsolutePath();
                    FileUtils.saveRoute(this, currentRoute);

                    // Refresh Title and layout data
                    getSupportActionBar().setTitle(newName);
                    loadRouteData();

                    Toast.makeText(this, "Route reversed and renamed to: " + newName, Toast.LENGTH_LONG).show();
                } else {
                    saveDirectFallback(newName);
                }
            } else {
                saveDirectFallback(newName);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveDirectFallback(String newName) throws IOException {
        File oldFile = new File(filePath);

        // Save new route (writes to newFolder / newJsonName)
        FileUtils.saveRoute(this, currentRoute);

        // Delete old json file
        if (oldFile.exists()) {
            oldFile.delete();
        }

        // Delete old folder if empty
        File oldFolder = oldFile.getParentFile();
        if (oldFolder != null && oldFolder.exists() && oldFolder.isDirectory()) {
            File[] remainingFiles = oldFolder.listFiles();
            if (remainingFiles == null || remainingFiles.length == 0) {
                oldFolder.delete();
            }
        }

        // Update filePath and refresh UI
        File routesDir = FileUtils.getRoutesDirectory();
        File newFolder = new File(routesDir, newName);
        File newFile = new File(newFolder, newName + ".json");
        filePath = newFile.getAbsolutePath();

        getSupportActionBar().setTitle(newName);
        loadRouteData();

        Toast.makeText(this, "Route reversed and renamed to: " + newName, Toast.LENGTH_LONG).show();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private String getReversedRouteName(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase();

        // 1. " to " pattern (case insensitive, with spaces)
        if (lower.contains(" to ")) {
            int index = lower.indexOf(" to ");
            String part1 = name.substring(0, index).trim();
            String delimiter = name.substring(index, index + 4);
            String part2 = name.substring(index + 4).trim();
            return part2 + delimiter + part1;
        }

        // 2. "to" pattern (case insensitive, word or bounded)
        if (lower.contains("to")) {
            int index = lower.indexOf("to");
            String part1 = name.substring(0, index).trim();
            String part2 = name.substring(index + 2).trim();
            return part2 + " to " + part1;
        }

        // 3. " - " pattern
        if (name.contains(" - ")) {
            String[] parts = name.split(" - ", 2);
            if (parts.length == 2) {
                return parts[1].trim() + " - " + parts[0].trim();
            }
        }

        // 4. "-" pattern
        if (name.contains("-")) {
            String[] parts = name.split("-", 2);
            if (parts.length == 2) {
                return parts[1].trim() + "-" + parts[0].trim();
            }
        }

        return name + " (Reversed)";
    }

    private void checkInitialGpsState() {
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
        if (locationManager != null) {
            boolean isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            tvNoGps.setVisibility((isGpsEnabled || isNetworkEnabled) ? android.view.View.GONE : android.view.View.VISIBLE);
        }
    }

    private void setupGpsListener() {
        gpsReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                if (android.location.LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                    checkInitialGpsState();
                }
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(gpsReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gpsReceiver != null) {
            try {
                unregisterReceiver(gpsReceiver);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
