package com.example.fcmtest;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.Manifest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements LocationManager.LocationUpdateListener{
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private LocationManager locationManager;
    private String fcmToken;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                        String token = task.getResult();
                        Log.d("FCM", "Device token: " + token);
                    }
                });
        Log.d("UUID", "Device UUID:" + DeviceUtils.getOrCreateUUID(this));

        webView = findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.loadUrl("http://172.171.251.7:8080/main/");

    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            locationManager.startLocationUpdates();
        }
    }

    private void sendLocationToServer(String uuid,String deviceName, String fcmToken ,double latitude, double longitude ) {
        new Thread(() -> {
            try {
                // JSON 데이터 생성
                JSONObject json = new JSONObject();
                json.put("deviceId", uuid);
                json.put("deviceName", deviceName);
                json.put("fcmToken", fcmToken);
                json.put("lat", latitude);
                json.put("lng", longitude);

                // POST 요청 생성
                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );
                Request request = new Request.Builder()
                        .url("http://172.171.251.7:8080/main/api/location") // 에뮬레이터용 URL
                        .post(body)
                        .build();

                // 요청 실행
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
        if (requestCode == 1001) {
        }
    }

    @Override
    public void onLocationUpdated(Location location) {

        String deviceName = Settings.Global.getString(getContentResolver(), "device_name");
        Log.d("device", "name:" + deviceName);


        // 위치 데이터 처리
        String message = "위도: " + location.getLatitude() + ", 경도: " + location.getLongitude() + ",사용자:" + deviceName;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        // 여기서 서버로 전송하거나 데이터 저장 가능
        sendLocationToServer(DeviceUtils.getOrCreateUUID(this), deviceName, fcmToken,location.getLatitude(), location.getLongitude());
    }
}
