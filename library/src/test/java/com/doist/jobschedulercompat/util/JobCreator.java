package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;

import android.app.Application;
import android.content.ComponentName;
import android.os.IBinder;

import static org.robolectric.Shadows.shadowOf;

public class JobCreator {
    public static JobInfo.Builder create(Application application, int id) {
        return create(application, id, 0);
    }

    public static JobInfo.Builder create(Application application, int id, long delay) {
        ComponentName component;
        PersistableBundle extras;
        IBinder service;
        if (delay > 0) {
            component = new ComponentName(application, NoopAsyncJobService.class);
            extras = new PersistableBundle();
            extras.putLong(NoopAsyncJobService.EXTRA_DELAY, delay);
            service = new NoopAsyncJobService().onBind(null);
        } else {
            component = new ComponentName(application, NoopJobService.class);
            extras = PersistableBundle.EMPTY;
            service = new NoopJobService().onBind(null);
        }
        JobInfo.Builder builder = new JobInfo.Builder(id, component).setExtras(extras);
        shadowOf(application).setComponentNameAndServiceForBindService(component, service);
        return builder;
    }

    public static void waitForJob(int id) {
        NoopAsyncJobService.waitForJob(id);
    }

    public static void interruptJobs() {
        NoopAsyncJobService.interruptJobs();
    }
}
