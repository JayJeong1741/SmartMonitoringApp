package com.example.fcmtest;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;



public class DeviceUtils {
    private static final String PREFS_FILE = "app_prefs";
    private static final String KEY_UUID = "device_uuid";

    public static String getOrCreateUUID(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        String uuid = prefs.getString(KEY_UUID, null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_UUID, uuid).apply();
        }
        return uuid;
    }
}