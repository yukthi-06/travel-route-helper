package com.vypeensoft.routehelper;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.vypeensoft.routehelper.models.Point;
import com.vypeensoft.routehelper.models.Route;
import com.vypeensoft.routehelper.utils.DateUtils;
import com.vypeensoft.routehelper.utils.FileUtils;
import android.widget.CheckBox;
import android.view.View;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AddPointActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_LOCATION = 1001;
    
    private String filePath;
    private String pointId = null;
    private MaterialButton buttonDelete;
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLat = 0;
    private double currentLng = 0;
    
    private TextInputEditText editTextPointName;
    private TextView textViewLocation;
    private CheckBox checkboxPetrol, checkboxFood, checkboxToll, checkboxToilet;
    private CancellationTokenSource cancellationTokenSource;

    private Route currentRoute;
    private android.widget.RadioGroup radioGroupPosition;
    private android.widget.RadioButton radioPositionEnd, radioPositionStart, radioPositionAuto;
    private int userRowPosition = -1;
    private Point prevPoint = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_point);

        filePath = getIntent().getStringExtra("FILE_PATH");
        if (filePath == null) {
            finish();
            return;
        }

        try {
            currentRoute = FileUtils.loadRoute(new File(filePath));
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load route: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        editTextPointName = findViewById(R.id.editTextPointName);
        textViewLocation = findViewById(R.id.textViewLocation);
        checkboxPetrol = findViewById(R.id.checkboxPetrol);
        checkboxFood = findViewById(R.id.checkboxFood);
        checkboxToll = findViewById(R.id.checkboxToll);
        checkboxToilet = findViewById(R.id.checkboxToilet);
        MaterialButton buttonSave = findViewById(R.id.buttonSave);
        MaterialButton buttonRefresh = findViewById(R.id.buttonRefreshLocation);
        buttonDelete = findViewById(R.id.buttonDelete);

        radioGroupPosition = findViewById(R.id.radioGroupPosition);
        radioPositionEnd = findViewById(R.id.radioPositionEnd);
        radioPositionStart = findViewById(R.id.radioPositionStart);
        radioPositionAuto = findViewById(R.id.radioPositionAuto);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        pointId = getIntent().getStringExtra("POINT_ID");
        if (pointId != null) {
            getSupportActionBar().setTitle("Edit Point");
            String name = getIntent().getStringExtra("POINT_NAME");
            currentLat = getIntent().getDoubleExtra("POINT_LAT", 0);
            currentLng = getIntent().getDoubleExtra("POINT_LNG", 0);
            ArrayList<String> types = getIntent().getStringArrayListExtra("POINT_TYPES");
            
            editTextPointName.setText(name);
            textViewLocation.setText(String.format("Lat: %.6f\nLng: %.6f", currentLat, currentLng));
            
            if (types != null) {
                if (types.contains("Petrol")) checkboxPetrol.setChecked(true);
                if (types.contains("Food")) checkboxFood.setChecked(true);
                if (types.contains("Toll")) checkboxToll.setChecked(true);
                if (types.contains("Toilet")) checkboxToilet.setChecked(true);
            }
            
            buttonRefresh.setEnabled(false);
            buttonRefresh.setAlpha(0.5f);
            
            buttonDelete.setVisibility(View.VISIBLE);
            buttonDelete.setOnClickListener(v -> confirmDeletePoint());

            findViewById(R.id.textViewPositionHeader).setVisibility(View.GONE);
            radioGroupPosition.setVisibility(View.GONE);
        } else {
            requestLocation();
            
            userRowPosition = getIntent().getIntExtra("USER_ROW_POSITION", -1);
            List<Point> activePoints = new ArrayList<>();
            if (currentRoute != null && currentRoute.getPoints() != null) {
                for (Point p : currentRoute.getPoints()) {
                    if (!p.isDeleted()) {
                        activePoints.add(p);
                    }
                }
            }

            if (userRowPosition > 0 && userRowPosition < activePoints.size()) {
                prevPoint = activePoints.get(userRowPosition - 1);
                Point nextPoint = activePoints.get(userRowPosition);
                radioPositionAuto.setText("Automatically between " + prevPoint.getName() + " and " + nextPoint.getName());
                radioPositionAuto.setVisibility(View.VISIBLE);
                radioPositionAuto.setChecked(true);
            }
        }

        buttonRefresh.setOnClickListener(v -> requestLocation());
        buttonSave.setOnClickListener(v -> savePoint());
    }

    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
            return;
        }

        textViewLocation.setText("Requesting fresh location...");
        
        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
        }
        cancellationTokenSource = new com.google.android.gms.tasks.CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
            .addOnSuccessListener(this, location -> {
                if (location != null) {
                    currentLat = location.getLatitude();
                    currentLng = location.getLongitude();
                    textViewLocation.setText(String.format("Lat: %.6f\nLng: %.6f", currentLat, currentLng));
                } else {
                    textViewLocation.setText("Location unavailable. Try refreshing.");
                }
            })
            .addOnFailureListener(e -> {
                textViewLocation.setText("Failed to get location: " + e.getMessage());
            });
    }

    private void confirmDeletePoint() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_msg)
            .setPositiveButton(R.string.btn_delete, (dialog, which) -> deletePoint())
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void deletePoint() {
        if (currentRoute == null) return;
        try {
            for (Point p : currentRoute.getPoints()) {
                if (p.getPointId().equals(pointId)) {
                    p.setDeleted(true);
                    break;
                }
            }
            
            FileUtils.saveRoute(this, currentRoute);
            Toast.makeText(this, R.string.point_deleted, Toast.LENGTH_SHORT).show();
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to delete point: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void savePoint() {
        if (currentRoute == null) return;
        String name = editTextPointName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0 && currentLng == 0) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> selectedTypes = new ArrayList<>();
        if (checkboxPetrol.isChecked()) selectedTypes.add("Petrol");
        if (checkboxFood.isChecked()) selectedTypes.add("Food");
        if (checkboxToll.isChecked()) selectedTypes.add("Toll");
        if (checkboxToilet.isChecked()) selectedTypes.add("Toilet");

        try {
            if (pointId != null) {
                // Edit Mode: find point by ID
                for (Point p : currentRoute.getPoints()) {
                    if (p.getPointId().equals(pointId)) {
                        int index = currentRoute.getPoints().indexOf(p);
                        Point updatedPoint = new Point(p.getPointId(), name, p.getLatitude(), p.getLongitude(), p.getTimestamp(), selectedTypes);
                        updatedPoint.setDeleted(false);
                        currentRoute.getPoints().set(index, updatedPoint);
                        break;
                    }
                }
            } else {
                // Add Mode
                Point newPoint = new Point(name, currentLat, currentLng, DateUtils.getCurrentTimestampISO(), selectedTypes);
                if (radioPositionStart.isChecked()) {
                    currentRoute.getPoints().add(0, newPoint);
                } else if (radioPositionAuto.isChecked() && prevPoint != null) {
                    int mainIndex = currentRoute.getPoints().indexOf(prevPoint);
                    if (mainIndex != -1) {
                        currentRoute.getPoints().add(mainIndex + 1, newPoint);
                    } else {
                        currentRoute.addPoint(newPoint);
                    }
                } else {
                    currentRoute.addPoint(newPoint);
                }
            }
            
            FileUtils.saveRoute(this, currentRoute);
            
            Toast.makeText(this, pointId != null ? "Point updated" : "Point saved", Toast.LENGTH_SHORT).show();
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save point: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                textViewLocation.setText("Permission denied.");
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
        }
    }
}
