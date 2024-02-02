package com.example.markerediting30;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private Handler handler;
    private List<LatLng> markerPositions = new ArrayList<>();
    private int currentIndex = 0;
    private List<Marker> markers = new ArrayList<>();
    private int totalCoordinates;
    private Marker selectedMarker;
    private String selectedFileName="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this); // Initialize Firebase


        showFilePickerDialog();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);

        Button btnNext = findViewById(R.id.btnNext);
        btnNext.setOnClickListener(view -> selectNextMarker());

        Button btnPrevious = findViewById(R.id.btnPrevious);
        btnPrevious.setOnClickListener(view -> selectPreviousMarker());

        Button btnFinish = findViewById(R.id.btnFinish);
        btnFinish.setOnClickListener(view -> saveMarkersToExcel());



    }

    private void showFilePickerDialog() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference().child("AbhishekT");


        storageRef.listAll()
                .addOnSuccessListener(listResult -> {
                    List<StorageReference> filesInFolder = listResult.getItems();

                    if (!filesInFolder.isEmpty()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Select a Different File");

                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
                        for (StorageReference file : filesInFolder) {
                            adapter.add(file.getName());
                        }

                        builder.setAdapter(adapter, (dialog, which) -> {
                            StorageReference selectedFile = filesInFolder.get(which);
                            selectedFileName=selectedFile.getName();
                            showToast("Selected File: " + selectedFile.getName());
                            parseCSVFile(selectedFile);

                        });

                        builder.show();
                    }

                    else {
                        Toast.makeText(this, "No files found in the folder.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseError", "Error listing files: " + e.getMessage());
                });
    }

    private void parseCSVFile(StorageReference selectedFile) {

        selectedFile.getBytes(1024 * 1024)
                .addOnSuccessListener(bytes -> {
                    String csvData = new String(bytes);

                    String[] lines = csvData.split("\n");
                    for (int i = 1; i < lines.length; i++) { // Start from index 1 to skip header
                        String[] columns = lines[i].split(",");
                        if (columns.length >= 3) { // Ensure there are at least three columns (serial number, latitude, longitude)
                            double latitude = Double.parseDouble(columns[1]);
                            double longitude = Double.parseDouble(columns[2]);

                            LatLng location = new LatLng(latitude, longitude);
                            markerPositions.add(location);
                        }
                    }

                    addMarker();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error reading CSV file: " + e.getMessage());
                });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;

        LatLng defaultLocation = new LatLng(19.076090, 72.877426);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10));
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
    }

    private void addMarker() {
        handler = new Handler();
        handler.postDelayed(() -> {
            if (currentIndex < markerPositions.size() && currentIndex < 500) {
                LatLng currentLocation = markerPositions.get(currentIndex);

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(currentLocation)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));

                Marker marker = googleMap.addMarker(markerOptions);
                markers.add(marker);

                animateCameraToMarker(currentLocation);
                currentIndex++;
                addMarker();
            }
        }, 10);
    }

    private void animateCameraToMarker(LatLng markerPosition) {
        float zoomLevel = 15.0f;
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(markerPosition, zoomLevel);
        googleMap.animateCamera(cameraUpdate);
    }
// Modify the import statements if needed

// Existing code...

    private void saveMarkersToFirebaseStorage(String selectedFileName) {
        if(selectedFileName.isEmpty()){
            showToast("No selected file.please select a file first.");
        }
        String folderName = "AbhishekT/" + selectedFileName + "_" + getCurrentDateTime() + ".csv";
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference().child(folderName);

        // Set metadata for CSV file
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("text/csv")
                .build();

        StringBuilder csvContent = new StringBuilder("Serial,Latitude,Longitude\n");

        for (int i = 0; i < markers.size(); i++) {
            LatLng location = markers.get(i).getPosition();
            csvContent.append(i + 1).append(",").append(location.latitude).append(",").append(location.longitude).append("\n");
        }

        byte[] csvBytes = csvContent.toString().getBytes();

        storageRef.putBytes(csvBytes, metadata)
                .addOnSuccessListener(taskSnapshot -> showToast("Markers saved to Firebase Storage"))
                .addOnFailureListener(e -> Log.e("FirebaseError", "Error saving markers to Firebase Storage: " + e.getMessage()));
    }

    private void saveMarkersToCSV(String selectedFileName) {
        StringBuilder csvContent = new StringBuilder("Serial,Latitude,Longitude\n");

        for (int i = 0; i < markers.size(); i++) {
            LatLng location = markers.get(i).getPosition();
            csvContent.append(i + 1).append(",").append(location.latitude).append(",").append(location.longitude).append("\n");
        }

        try {
            String fileName = selectedFileName + "_" + getCurrentDateTime();
            File file = new File(getExternalFilesDir(null), fileName + ".csv");

            FileOutputStream fileOut = new FileOutputStream(file);
            fileOut.write(csvContent.toString().getBytes());
            fileOut.close();

            showToast("Markers stored in CSV file...");
            saveMarkersToFirebaseStorage(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

// Existing code...

    private String getCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        return dateFormat.format(Calendar.getInstance().getTime());
    }

    private void saveMarkersToExcel() {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("Marker Coordinates");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Latitude");
        headerRow.createCell(1).setCellValue("Longitude");

        for (int i = 0; i < markers.size(); i++) {
            LatLng location = markers.get(i).getPosition();
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(location.latitude);
            row.createCell(1).setCellValue(location.longitude);
        }

        try {
            String fileName = "Edited_coordinates_" + getCurrentDateTime() ;
            File file = new File(getExternalFilesDir(null), fileName);

            FileOutputStream fileOut = new FileOutputStream(file);
            workbook.write(fileOut);
            fileOut.close();
            showToast("Edited markers stored...");
            saveMarkersToFirebaseStorage(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void selectNextMarker() {
        if (!markers.isEmpty()) {
            if (selectedMarker != null) {
                selectedMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                selectedMarker.setDraggable(false);
            }

            currentIndex++;

            if (currentIndex >= markers.size()) {
                currentIndex = 0;
            }

            selectedMarker = markers.get(currentIndex);
            selectedMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
            selectedMarker.setDraggable(true);

            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (Marker marker : markers) {
                builder.include(marker.getPosition());
            }
            LatLngBounds bounds = builder.build();

            int padding = 100;
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            googleMap.animateCamera(cameraUpdate);
        }
    }

    private void selectPreviousMarker() {
        if (!markers.isEmpty()) {
            if (selectedMarker != null) {
                selectedMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                selectedMarker.setDraggable(false);
            }

            currentIndex--;

            if (currentIndex < 0) {
                currentIndex = markers.size() - 1;
            }

            selectedMarker = markers.get(currentIndex);
            selectedMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
            selectedMarker.setDraggable(true);

            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (Marker marker : markers) {
                builder.include(marker.getPosition());
            }
            LatLngBounds bounds = builder.build();

            int padding = 100;
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            googleMap.animateCamera(cameraUpdate);
        }
    }
}