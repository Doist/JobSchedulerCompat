package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;

import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.os.IBinder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.robolectric.Shadows.shadowOf;

public class JobCreator {
    private static AtomicInteger id = new AtomicInteger();

    // Keep a reference to both services since the binder (obtained below via onBind()) only holds a weak reference.
    private static Service noopService = new NoopJobService();
    private static Service noopAsyncService = new NoopAsyncJobService();

    public static JobInfo.Builder create(Application application) {
        return create(application, 0);
    }

    public static JobInfo.Builder create(Application application, long delay) {
        ComponentName component;
        PersistableBundle extras;
        IBinder binder;
        if (delay > 0) {
            component = new ComponentName(application, NoopAsyncJobService.class);
            extras = new PersistableBundle();
            extras.putLong(NoopAsyncJobService.EXTRA_DELAY, delay);
            binder = noopAsyncService.onBind(null);
        } else {
            component = new ComponentName(application, NoopJobService.class);
            extras = PersistableBundle.EMPTY;
            binder = noopService.onBind(null);
        }
        shadowOf(application).setComponentNameAndServiceForBindService(component, binder);
        return new JobInfo.Builder(id.incrementAndGet(), component).setExtras(extras);
    }

    public static void waitForJob(int id) {
        NoopAsyncJobService.waitForJob(id);
    }

    public static void interruptJobs() {
        NoopAsyncJobService.interruptJobs();
    }
}
