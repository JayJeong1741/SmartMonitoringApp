package com.example.fcmtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.UUID;

public class DeviceUtils {
    private static final String PREFS_NAME = "DevicePrefs";
    private static final String UUID_KEY = "device_uuid";

    public static String getOrCreateUUID(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uuid = prefs.getString(UUID_KEY, null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString(UUID_KEY, uuid).apply();
        }
        return uuid;
    }

    public static String getDeviceName(Context context) {
        return Settings.Global.getString(context.getContentResolver(), "device_name");
    }
}