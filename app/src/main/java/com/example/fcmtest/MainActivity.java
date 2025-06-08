package com.example.fcmtest;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements LocationManager.LocationUpdateListener {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private LocationManager locationManager;
    private String fcmToken;
    private Button button;
    private WebView webView;
    private SharedPreferences prefs;
    private boolean isLocationServiceRunning;
    private String deviceName;
    private String deviceId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceName = DeviceUtils.getDeviceName(this);
        deviceId = DeviceUtils.getOrCreateUUID(this);

        button = findViewById(R.id.button);
        webView = findViewById(R.id.webView);
        prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE);

        // SharedPreferences에서 위치 전송 상태 확인
        isLocationServiceRunning = prefs.getBoolean("isLocationServiceRunning", false);
        updateButtonText();

        // LocationManager 초기화
        locationManager = new LocationManager(this, this);

        // 권한 요청
        requestPermissions();

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

        webView.setWebViewClient(new WebViewClient());
        WebSettings webSettings = webView.getSettings();
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
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
        button.setText(isLocationServiceRunning ? "현장근무 중단" : "현장근무 시작");
    }

    private void startLocationService() {
        if (isServiceRunning()) {
            Log.d("LocationService", "Service is already running");
            isLocationServiceRunning = true;
            prefs.edit().putBoolean("isLocationServiceRunning", true).apply();
            updateButtonText();
            return;
        }

        // 위치 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            requestPermissions();
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
        sendStateUpdate(deviceId, deviceName, fcmToken, 1);
        Toast.makeText(this, "위치 전송 시작", Toast.LENGTH_SHORT).show();
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        stopService(serviceIntent);
        isLocationServiceRunning = false;
        prefs.edit().putBoolean("isLocationServiceRunning", false).apply();
        updateButtonText();
        sendStateUpdate(deviceId, deviceName, fcmToken, 0);
        Toast.makeText(this, "위치 전송 중단", Toast.LENGTH_SHORT).show();
    }

    private void requestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // 알림 권한 (Android 13, API 33 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // 위치 권한
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // 백그라운드 위치 권한 (Android 10, API 29 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        }

        // 포그라운드 서비스 위치 권한 (Android 14, API 34 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION);
            }
        }

        // 권한 요청이 필요한 경우
        if (!permissionsNeeded.isEmpty()) {
            // 권한 필요성 설명 다이얼로그 표시
            showPermissionRationaleDialog(permissionsNeeded);
        } else if (isLocationServiceRunning) {
            // 모든 권한이 이미 부여된 경우, 위치 서비스 시작
            startLocationService();
        }
    }

    private void showPermissionRationaleDialog(List<String> permissions) {
        String rationaleMessage = "앱을 정상적으로 사용하려면 다음 권한이 필요합니다:\n";
        for (String permission : permissions) {
            if (permission.equals(Manifest.permission.POST_NOTIFICATIONS)) {
                rationaleMessage += "- 알림: 푸시 알림을 받기 위해 필요합니다.\n";
            } else if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION) || permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                rationaleMessage += "- 위치: 정확한 위치 정보를 제공하기 위해 필요합니다.\n";
            } else if (permission.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                rationaleMessage += "- 백그라운드 위치: 앱이 백그라운드에서도 위치를 추적하기 위해 필요합니다.\n";
            } else if (permission.equals(Manifest.permission.FOREGROUND_SERVICE_LOCATION)) {
                rationaleMessage += "- 포그라운드 서비스 위치: 위치 기반 서비스를 실행하기 위해 필요합니다.\n";
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("권한 요청")
                .setMessage(rationaleMessage)
                .setPositiveButton("허용", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("거부", (dialog, which) -> {
                    Toast.makeText(this, "권한이 거부되어 일부 기능이 제한될 수 있습니다.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d("PermissionResult", "Permission denied: " + permissions[i]);
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        // 영구적 거부 시 설정 화면으로 안내
                        new AlertDialog.Builder(this)
                                .setTitle("권한 필요")
                                .setMessage(permissions[i] + " 권한이 필요합니다. 설정에서 허용해주세요.")
                                .setPositiveButton("설정", (dialog, which) -> {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    startActivity(intent);
                                })
                                .setNegativeButton("취소", (dialog, which) -> {
                                    Toast.makeText(this, "권한이 거부되어 일부 기능이 제한될 수 있습니다.", Toast.LENGTH_SHORT).show();
                                })
                                .setCancelable(false)
                                .show();
                    }
                } else {
                    Log.d("PermissionResult", "Permission granted: " + permissions[i]);
                }
            }
            if (allGranted && isLocationServiceRunning) {
                Log.d("PermissionResult", "All permissions granted, starting service");
                startLocationService();
            }
        }
    }

    @Override
    public void onLocationUpdated(android.location.Location location) {
        String deviceName = Settings.Global.getString(getContentResolver(), "device_name");
        Log.d("device", "name:" + deviceName);
        String message = "위도: " + location.getLatitude() + ", 경도: " + location.getLongitude() + ", 사용자:" + deviceName;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void sendStateUpdate(String uuid, String deviceName, String fcmToken, int state) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("deviceId", uuid);
                json.put("state", state);
                json.put("deviceName", deviceName);
                json.put("fcmToken", fcmToken);

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("http://192.168.35.121:8080/main/api/updateState")
                        .post(body)
                        .build();

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d("updateState", "상태 정보 전송 성공: " + response.body().string());
                    } else {
                        Log.e("updateState", "상태 정보 전송 실패: " + response.message());
                    }
                }
            } catch (Exception e) {
                Log.e("updateState", "상태 정보 전송 중 오류", e);
            }
        }).start(); // 👉 반드시 start() 해줘야 백그라운드 스레드 실행됨
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (LocationService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}