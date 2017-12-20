package com.doist.jobschedulercompat.scheduler.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.RestrictTo;

/**
 * Updates alarm-based jobs by starting {@link AlarmJobService}.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmJobService.start(context);
    }

    public static class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            AlarmJobService.start(context);
        }
    }

    public static class StorageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            AlarmJobService.start(context);
        }
    }

    public static class ConnectivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            AlarmJobService.start(context);
        }
    }
}
