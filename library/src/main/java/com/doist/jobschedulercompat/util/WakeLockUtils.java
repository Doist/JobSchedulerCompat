package com.doist.jobschedulercompat.util;

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.RestrictTo;

import java.util.HashMap;

/**
 * Utility for acquiring and releasing wake locks.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class WakeLockUtils {
    private static final HashMap<String, PowerManager.WakeLock> sActiveWakeLocks = new HashMap<>();

    public static void acquireWakeLock(Context context, String key, long timeout) {
        synchronized (sActiveWakeLocks) {
            PowerManager.WakeLock wakeLock = sActiveWakeLocks.get(key);
            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jobschedulercompat:" + key);
            }

            wakeLock.acquire(timeout);
            sActiveWakeLocks.put(key, wakeLock);
        }
    }

    public static void releaseWakeLock(String key) {
        synchronized (sActiveWakeLocks) {
            PowerManager.WakeLock wakeLock = sActiveWakeLocks.remove(key);
            if (wakeLock != null) {
                wakeLock.release();
            }
        }
    }
}
