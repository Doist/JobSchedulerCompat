package com.doist.jobschedulercompat.util;

import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowPowerManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.robolectric.Shadows.shadowOf;

public class DeviceTestUtils {
    @SuppressWarnings("deprecation")
    public static void setCharging(Context context, boolean charging) {
        Intent chargingIntent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        if (charging) {
            chargingIntent.putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC);
        }
        context.sendStickyBroadcast(chargingIntent);
    }

    @SuppressWarnings("deprecation")
    public static void setStorageNotLow(Context context, boolean storageNotLow) {
        Intent storageLowIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_LOW);
        if (storageNotLow) {
            context.removeStickyBroadcast(storageLowIntent);
        } else {
            context.sendStickyBroadcast(storageLowIntent);
        }
    }

    public static void setDeviceIdle(Context context, boolean idle) {
        ShadowPowerManager manager = shadowOf((PowerManager) context.getSystemService(Context.POWER_SERVICE));
        manager.setIsInteractive(!idle);
        manager.setIsScreenOn(!idle);
    }

    public static void setNetworkInfo(Context context, boolean isConnected, boolean isRoaming, boolean isWifi) {
        ShadowConnectivityManager manager =
                shadowOf((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        NetworkInfo.DetailedState detailedState =
                isConnected ? NetworkInfo.DetailedState.CONNECTED : NetworkInfo.DetailedState.DISCONNECTED;
        int type = isConnected ? (isWifi ? ConnectivityManager.TYPE_WIFI : ConnectivityManager.TYPE_MOBILE) : -1;
        NetworkInfo networkInfo =
                ShadowNetworkInfo.newInstance(detailedState, type, 0, isConnected, isConnected, isRoaming);
        manager.setActiveNetworkInfo(networkInfo);
    }

    @SuppressWarnings("deprecation")
    public static boolean isComponentEnabled(PackageManager manager, ComponentName component) {
        switch (manager.getComponentEnabledSetting(component)) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                return false;
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            default:
                try {
                    PackageInfo packageInfo = manager.getPackageInfo(
                            component.getPackageName(),
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES
                                    | PackageManager.GET_PROVIDERS | PackageManager.GET_DISABLED_COMPONENTS);
                    List<ComponentInfo> components = new ArrayList<>();
                    if (packageInfo.activities != null) {
                        Collections.addAll(components, packageInfo.activities);
                    }
                    if (packageInfo.services != null) {
                        Collections.addAll(components, packageInfo.services);
                    }
                    if (packageInfo.providers != null) {
                        Collections.addAll(components, packageInfo.providers);
                    }
                    for (ComponentInfo componentInfo : components) {
                        if (componentInfo.name.equals(component.getClassName())) {
                            return componentInfo.isEnabled();
                        }
                    }
                    return false;
                } catch (PackageManager.NameNotFoundException e) {
                    // the package isn't installed on the device
                    return false;
                }
        }
    }

    public static void advanceTime(long timeMs) {
        Robolectric.getForegroundThreadScheduler().advanceBy(timeMs, TimeUnit.MILLISECONDS);
    }
}
