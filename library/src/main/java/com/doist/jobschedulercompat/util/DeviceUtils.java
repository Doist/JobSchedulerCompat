package com.doist.jobschedulercompat.util;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.RestrictTo;

import static android.content.Context.CONNECTIVITY_SERVICE;

@SuppressWarnings("deprecation")
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DeviceUtils {
    public static boolean isCharging(Context context) {
        Bundle extras = getBatteryChangedExtras(context);
        int plugged = extras != null ? extras.getInt(BatteryManager.EXTRA_PLUGGED, 0) : 0;
        return plugged == BatteryManager.BATTERY_PLUGGED_AC
                || plugged == BatteryManager.BATTERY_PLUGGED_USB
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS);
    }

    public static boolean isBatteryNotLow(Context context) {
        Bundle extras = getBatteryChangedExtras(context);
        int percentage = extras != null ? extras.getInt(BatteryManager.EXTRA_LEVEL, -1)
                / extras.getInt(BatteryManager.EXTRA_SCALE, 100) : 0;
        return percentage > 15;
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public static boolean isIdle(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return powerManager.isDeviceIdleMode() || !powerManager.isInteractive();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return !powerManager.isInteractive();
        } else {
            return !powerManager.isScreenOn();
        }
    }

    public static boolean isStorageNotLow(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
        return intent == null;
    }

    public static boolean isConnected(Context context) {
        NetworkInfo info = getActiveNetworkInfo(getConnectivityManager(context));
        return info != null && info.isConnected();
    }

    public static boolean isNotRoaming(Context context) {
        NetworkInfo info = getActiveNetworkInfo(getConnectivityManager(context));
        return info != null && info.isConnected() && !info.isRoaming();
    }

    public static boolean isUnmetered(Context context) {
        ConnectivityManager manager = getConnectivityManager(context);
        NetworkInfo info = getActiveNetworkInfo(manager);
        return info != null && info.isConnected() && !manager.isActiveNetworkMetered();
    }

    public static boolean isMetered(Context context) {
        ConnectivityManager manager = getConnectivityManager(context);
        NetworkInfo info = getActiveNetworkInfo(manager);
        return info != null && info.isConnected() && manager.isActiveNetworkMetered();
    }

    private static Bundle getBatteryChangedExtras(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return intent != null ? intent.getExtras() : null;
    }

    private static NetworkInfo getActiveNetworkInfo(ConnectivityManager manager) {
        return manager.getActiveNetworkInfo();
    }

    private static ConnectivityManager getConnectivityManager(Context context) {
        return (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
    }
}
