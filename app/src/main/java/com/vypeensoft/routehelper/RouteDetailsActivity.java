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
import java.util.List;

public class RouteDetailsActivity extends AppCompatActivity implements PointAdapter.OnPointClickListener {

    private String filePath;
    private Route currentRoute;
    private PointAdapter adapter;
    private RecyclerView recyclerView;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_details);

        filePath = getIntent().getStringExtra("FILE_PATH");
        if (filePath == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerViewPoints);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        FloatingActionButton fab = findViewById(R.id.fabAddPoint);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(RouteDetailsActivity.this, AddPointActivity.class);
            intent.putExtra("FILE_PATH", filePath);
            if (adapter != null) {
                intent.putExtra("USER_ROW_POSITION", adapter.getUserRowPosition());
            }
            startActivity(intent);
        });

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
                    displayedPoints.add(p);
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

    @Override
    protected void onResume() {
        super.onResume();
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
}
