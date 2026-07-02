package com.vypeensoft.routehelper;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vypeensoft.routehelper.adapters.RouteAdapter;
import com.vypeensoft.routehelper.models.Route;
import com.vypeensoft.routehelper.utils.DateUtils;
import com.vypeensoft.routehelper.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity implements RouteAdapter.OnRouteClickListener, RouteAdapter.OnRouteLongClickListener {

    private static final int PERMISSION_REQUEST_CODE = 2001;
    private static final int PERMISSION_REQUEST_MANAGE_STORAGE = 2002;

    private RecyclerView recyclerView;
    private RouteAdapter adapter;
    private TextView textViewEmptyState;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView tvNoGps;
    private android.content.BroadcastReceiver gpsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerViewRoutes);
        textViewEmptyState = findViewById(R.id.textViewEmptyState);
        tvNoGps = findViewById(R.id.tv_no_gps);
        checkInitialGpsState();
        setupGpsListener();
        FloatingActionButton fab = findViewById(R.id.fabAddRoute);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_help) {
                Toast.makeText(this, "Help center coming soon!", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_about) {
                startActivity(new Intent(this, AboutActivity.class));
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        if (checkStandardPermissions()) {
            if (checkManageStoragePermission()) {
                loadRoutes();
            } else {
                requestManageStoragePermission();
            }
        } else {
            requestStandardPermissions();
        }

        fab.setOnClickListener(view -> {
            if (checkStandardPermissions() && checkManageStoragePermission()) {
                showCreateRouteDialog();
            } else {
                if (!checkStandardPermissions()) {
                    requestStandardPermissions();
                } else {
                    requestManageStoragePermission();
                }
            }
        });
    }

    private boolean checkStandardPermissions() {
        int fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        
        boolean storageOk = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            int write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            storageOk = (write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED);
        }
        
        return fineLoc == PackageManager.PERMISSION_GRANTED && 
               coarseLoc == PackageManager.PERMISSION_GRANTED && 
               storageOk;
    }

    private boolean checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private void requestStandardPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }

    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, PERMISSION_REQUEST_MANAGE_STORAGE);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_REQUEST_MANAGE_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                if (checkManageStoragePermission()) {
                    loadRoutes();
                } else {
                    requestManageStoragePermission();
                }
            } else {
                Toast.makeText(this, "Location and Storage permissions are required for the app to function properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_MANAGE_STORAGE) {
            if (checkManageStoragePermission()) {
                loadRoutes();
            } else {
                Toast.makeText(this, "All Files Access is required to save route data.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadRoutes() {
        List<File> routeFiles = FileUtils.listRouteFiles(this);
        if (routeFiles.isEmpty()) {
            textViewEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            textViewEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        if (adapter == null) {
            adapter = new RouteAdapter(routeFiles, this, this);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(routeFiles);
        }
    }

    private void showCreateRouteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Route");

        final EditText input = new EditText(this);
        input.setHint("Enter route name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String routeName = input.getText().toString().trim();
            if (!routeName.isEmpty()) {
                createNewRoute(routeName);
            } else {
                Toast.makeText(MainActivity.this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void createNewRoute(String name) {
        try {
            Route newRoute = new Route(name, DateUtils.getCurrentTimestampISO());
            FileUtils.saveRoute(this, newRoute);
            loadRoutes();
            Toast.makeText(this, "Route created", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to create route: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRouteLongClick(File file) {
        String currentName = file.getName().replace(".json", "");
        showEditRouteDialog(file, currentName);
    }

    private void showEditRouteDialog(File routeFile, String currentName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Route");

        final EditText input = new EditText(this);
        input.setText(currentName);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(currentName)) {
                renameRoute(routeFile, newName);
            } else if (newName.isEmpty()) {
                Toast.makeText(MainActivity.this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void renameRoute(File oldJsonFile, String newName) {
        try {
            File oldFolder = oldJsonFile.getParentFile();
            File routesDir = FileUtils.getRoutesDirectory();
            File newFolder = new File(routesDir, newName);

            if (newFolder.exists()) {
                Toast.makeText(this, "A route with this name already exists", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. Rename the folder
            if (oldFolder.renameTo(newFolder)) {
                // 2. Rename the JSON file inside the new folder
                File renamedJsonFile = new File(newFolder, newName + ".json");
                File movedJsonFile = new File(newFolder, oldJsonFile.getName());
                
                if (movedJsonFile.renameTo(renamedJsonFile)) {
                    // 3. Update the routeName inside the JSON
                    Route route = FileUtils.loadRoute(renamedJsonFile);
                    // We need a setter or a way to change the name
                    // Since Route has private fields and no setter, I'll create a new one or use reflection.
                    // Better: recreate the route object with same points.
                    Route updatedRoute = new Route(newName, route.getCreatedAt());
                    for (com.vypeensoft.routehelper.models.Point p : route.getPoints()) {
                        updatedRoute.addPoint(p);
                    }
                    FileUtils.saveRoute(this, updatedRoute);
                    
                    loadRoutes();
                    Toast.makeText(this, "Route renamed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to rename data file", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to rename folder", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRouteClick(File file) {
        Intent intent = new Intent(this, RouteDetailsActivity.class);
        intent.putExtra("FILE_PATH", file.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
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
        loadRoutes();
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
