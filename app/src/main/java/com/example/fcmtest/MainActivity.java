package com.example.fcmtest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.Manifest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.firebase.messaging.FirebaseMessaging;
import android.content.SharedPreferences;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements LocationManager.LocationUpdateListener {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private LocationManager locationManager;
    private String fcmToken;
    private Button button;
    private WebView webView;
    private SharedPreferences prefs;
    private boolean isLocationServiceRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button);
        webView = findViewById(R.id.webView);
        prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE);

        // SharedPreferences에서 위치 전송 상태 확인
        isLocationServiceRunning = prefs.getBoolean("isLocationServiceRunning", false);
        updateButtonText();

        // LocationManager 초기화
        locationManager = new LocationManager(this, this);

        // 권한 확인 및 요청
        checkLocationPermission();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        fcmToken = task.getResult();
                        getSharedPreferences("FCMPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("fcmToken", fcmToken)
                                .apply();
                        Log.d("FCM", "Device token: " + fcmToken);
                    }
                });
        Log.d("UUID", "Device UUID:" + DeviceUtils.getOrCreateUUID(this));

        webView.setWebViewClient(new WebViewClient());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.loadUrl("http://192.168.35.121:8080/main/");

        // 버튼 클릭 리스너 설정
        button.setOnClickListener(v -> {
            if (isLocationServiceRunning) {
                stopLocationService();
            } else {
                startLocationService();
            }
        });
    }

    private void updateButtonText() {
        button.setText(isLocationServiceRunning ? "위치 전송 중단" : "위치 전송 시작");
    }

    private void startLocationService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
            return;
        }
        Intent serviceIntent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isLocationServiceRunning = true;
        prefs.edit().putBoolean("isLocationServiceRunning", true).apply();
        updateButtonText();
        Toast.makeText(this, "위치 전송 시작", Toast.LENGTH_SHORT).show();
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        stopService(serviceIntent);
        isLocationServiceRunning = false;
        prefs.edit().putBoolean("isLocationServiceRunning", false).apply();
        updateButtonText();
        Toast.makeText(this, "위치 전송 중단", Toast.LENGTH_SHORT).show();
    }

    private void checkLocationPermission() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        // 권한 상태 확인
        boolean needsPermission = false;
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needsPermission = true;
                Log.d("PermissionCheck", "Permission required: " + permission);
            } else {
                Log.d("PermissionCheck", "Permission already granted: " + permission);
            }
        }

        if (needsPermission) {
            Log.d("PermissionCheck", "Requesting permissions");
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        } else if (isLocationServiceRunning) {
            Log.d("PermissionCheck", "Starting location service");
            startLocationService();
        } else {
            Log.d("PermissionCheck", "Permissions granted, but service not started");
        }
    }

    private void sendLocationToServer(String uuid, String deviceName, String fcmToken, double latitude, double longitude) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("deviceId", uuid);
                json.put("deviceName", deviceName);
                json.put("fcmToken", fcmToken);
                json.put("lat", latitude);
                json.put("lng", longitude);

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );
                Request request = new Request.Builder()
                        .url("http://192.168.35.121:8080/main/api/location")
                        .post(body)
                        .build();

                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d("LocationManager", "위치 전송 성공: " + response.body().string());
                    } else {
                        Log.e("LocationManager", "위치 전송 실패: " + response.message());
                    }
                }
            } catch (Exception e) {
                Log.e("LocationManager", "위치 전송 중 오류: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d("PermissionResult", "Permission denied: " + permissions[i]);
                } else {
                    Log.d("PermissionResult", "Permission granted: " + permissions[i]);
                }
            }
            if (allGranted && isLocationServiceRunning) {
                Log.d("PermissionResult", "All permissions granted, starting service");
                startLocationService();
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                Log.d("PermissionResult", "Some permissions denied or service not started");
            }
        }
    }

    @Override
    public void onLocationUpdated(android.location.Location location) {
        String deviceName = Settings.Global.getString(getContentResolver(), "device_name");
        Log.d("device", "name:" + deviceName);
        String message = "위도: " + location.getLatitude() + ", 경도: " + location.getLongitude() + ", 사용자:" + deviceName;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        sendLocationToServer(DeviceUtils.getOrCreateUUID(this), deviceName, fcmToken, location.getLatitude(), location.getLongitude());
    }
}