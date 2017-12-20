package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;

import org.robolectric.shadows.ShadowApplication;

import android.content.ComponentName;
import android.content.Context;

public class JobCreator {
    public static JobInfo.Builder create(Context context, int id) {
        return create(context, id, 0);
    }

    public static JobInfo.Builder create(Context context, int id, long delay) {
        JobInfo.Builder builder;
        if (delay > 0) {
            ComponentName component = new ComponentName(context, NoopAsyncJobService.class);
            PersistableBundle extras = new PersistableBundle();
            extras.putLong(NoopAsyncJobService.EXTRA_DELAY, delay);
            builder = new JobInfo.Builder(id, component).setExtras(extras);
            ShadowApplication.getInstance().setComponentNameAndServiceForBindService(
                    component, new NoopAsyncJobService().onBind(null));
        } else {
            ComponentName component = new ComponentName(context, NoopJobService.class);
            builder = new JobInfo.Builder(id, component);
            ShadowApplication.getInstance().setComponentNameAndServiceForBindService(
                    component, new NoopJobService().onBind(null));
        }
        return builder;
    }

    public static void waitForJob(int id) {
        NoopAsyncJobService.waitForJob(id);
    }

    public static void interruptJobs() {
        NoopAsyncJobService.interruptJobs();
    }
}
