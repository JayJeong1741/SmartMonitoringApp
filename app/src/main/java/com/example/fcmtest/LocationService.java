package com.example.fcmtest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LocationService extends Service {
    private LocationManager locationManager;
    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private String fcmToken;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        // FCM 토큰 비동기적으로 가져오기
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        fcmToken = task.getResult();
                        getSharedPreferences("FCMPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("fcmToken", fcmToken)
                                .apply();
                        Log.d("LocationService", "FCM Token: " + fcmToken);
                    } else {
                        Log.e("LocationService", "FCM Token fetch failed", task.getException());
                    }
                });

        locationManager = new LocationManager(this, location -> {
            String deviceName = DeviceUtils.getDeviceName(this);
            String token = fcmToken != null ? fcmToken : getSharedPreferences("FCMPrefs", MODE_PRIVATE).getString("fcmToken", "");
            if (token.isEmpty()) {
                Log.w("LocationService", "FCM Token not available yet");
            }
            sendLocationToServer(DeviceUtils.getOrCreateUUID(this), deviceName, token, location.getLatitude(), location.getLongitude());
        });
        locationManager.startLocationUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.stopLocationUpdates();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("위치 전송 서비스")
                .setContentText("위치 데이터를 서버로 전송 중입니다.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
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

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d("LocationService", "위치 전송 성공: " + response.body().string());
                    } else {
                        Log.e("LocationService", "위치 전송 실패: " + response.message());
                    }
                }
            } catch (Exception e) {
                Log.e("LocationService", "위치 전송 중 오류: " + e.getMessage());
            }
        }).start();
    }
}