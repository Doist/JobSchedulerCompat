package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.gcm.PendingCallback;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.ShadowGoogleApiAvailability;
import com.doist.jobschedulercompat.util.ShadowNetworkInfo;
import com.doist.jobschedulercompat.util.ShadowParcel;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.KITKAT,
        shadows = {ShadowGoogleApiAvailability.class, ShadowNetworkInfo.class, ShadowParcel.class})
public class GcmJobServiceTest {
    private Application application;
    private JobStore jobStore;
    private GcmJobService service;

    @BeforeClass
    public static void enableGcm() {
        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
    }

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        jobStore = JobStore.get(application);
        service = Robolectric.buildService(GcmJobService.class).create().get();
    }

    @After
    public void teardown() {
        JobCreator.interruptJobs();
        synchronized (JobStore.LOCK) {
            jobStore.clear();
        }
    }

    @Test
    public void testInitializeSendsBroadcastsSchedules() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application).setRequiresCharging(true).build(), GcmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application).setRequiresDeviceIdle(true).build(), GcmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application).setPeriodic(TimeUnit.HOURS.toMillis(1)).build(), GcmScheduler.TAG));

        final AtomicInteger idCount = new AtomicInteger(0);
        application.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (GcmScheduler.ACTION_SCHEDULE.equals(intent.getAction())
                        && GcmScheduler.SCHEDULER_ACTION_SCHEDULE_TASK.equals(
                        intent.getStringExtra(GcmScheduler.BUNDLE_PARAM_SCHEDULER_ACTION))) {
                    idCount.getAndIncrement();
                }
            }
        }, new IntentFilter(GcmScheduler.ACTION_SCHEDULE));

        assertEquals(0, idCount.get());

        initializeService();

        assertEquals(3, idCount.get());
    }

    @Test
    public void testJobRuns() {
        DeviceTestUtils.setNetworkInfo(application, true, false, true);
        JobInfo job = JobCreator.create(application, 2000).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build();
        jobStore.add(JobStatus.createFromJobInfo(job, GcmScheduler.TAG));
        executeService(job.getId());

        assertBoundServiceCount(1);
    }

    @Test
    public void testJobFinishes() {
        DeviceTestUtils.setNetworkInfo(application, true, false, false);
        JobInfo job = JobCreator.create(application, 50)
                                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                                .build();
        jobStore.add(JobStatus.createFromJobInfo(job, GcmScheduler.TAG));
        executeService(job.getId());

        assertBoundServiceCount(1);

        JobCreator.waitForJob(job.getId());

        assertBoundServiceCount(0);
    }

    @Test
    public void testDeadlineConstraint() {
        long latency = TimeUnit.HOURS.toMillis(2);
        DeviceTestUtils.setCharging(application, false);
        JobInfo job = JobCreator.create(application).setRequiresCharging(true).setOverrideDeadline(latency).build();
        jobStore.add(JobStatus.createFromJobInfo(job, GcmScheduler.TAG));

        assertEquals(1, jobStore.size());

        Robolectric.getForegroundThreadScheduler().advanceBy(latency, TimeUnit.MILLISECONDS);
        executeService(job.getId());

        assertEquals(0, jobStore.size());
    }

    private void assertBoundServiceCount(int count) {
        assertEquals(count, shadowOf(application).getBoundServiceConnections().size());
    }

    private void initializeService() {
        service.onStartCommand(new Intent(GcmJobService.ACTION_INITIALIZE), 0, 0);
    }

    private void executeService(int jobId) {
        Intent intent = new Intent(GcmJobService.ACTION_EXECUTE);
        intent.putExtra(GcmIntentParser.BUNDLE_KEY_TAG, String.valueOf(jobId));
        intent.putExtra(GcmIntentParser.BUNDLE_KEY_EXTRAS, Bundle.EMPTY);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeStrongBinder(new Binder());
            parcel.setDataPosition(0);
            intent.putExtra(GcmIntentParser.BUNDLE_KEY_CALLBACK, new PendingCallback(parcel));
        } finally {
            parcel.recycle();
        }
        service.onStartCommand(intent, 0, 0);
    }
}
