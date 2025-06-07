package com.example.fcmtest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class LocationManager {
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationRequest locationRequest;
    private final Handler handler;
    private LocationCallback locationCallback;
    private static final long UPDATE_INTERVAL = 30 * 1000; // 30초
    private final LocationUpdateListener listener;

    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
    }

    public LocationManager(Context context, LocationUpdateListener listener) {
        this.context = context;
        this.listener = listener;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        handler = new Handler(Looper.getMainLooper());

        locationRequest = LocationRequest.create()
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(UPDATE_INTERVAL / 2)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null && listener != null) {
                    listener.onLocationUpdated(location);
                }
            }
        };

        Runnable locationRunnable = new Runnable() {
            @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
            @Override
            public void run() {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(locationRunnable);
    }

    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        handler.removeCallbacksAndMessages(null);
    }
}