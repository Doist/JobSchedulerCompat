package com.doist.jobschedulercompat.scheduler.gcm;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.gcm.PendingCallback;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.scheduler.alarm.AlarmScheduler;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.NoopAsyncJobService;
import com.doist.jobschedulercompat.util.ShadowGoogleApiAvailability;
import com.doist.jobschedulercompat.util.ShadowNetworkInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.KITKAT,
        shadows = {ShadowGoogleApiAvailability.class, ShadowNetworkInfo.class})
public class GcmJobServiceTest {
    private static final long THREAD_WAIT_MS = 80;

    private Context context;
    private JobStore jobStore;
    private GcmJobService service;

    @BeforeClass
    public static void enableGcm() {
        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
    }

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobStore = JobStore.get(context);
        service = Robolectric.buildService(GcmJobService.class).create().get();
    }

    @After
    public void teardown() {
        NoopAsyncJobService.stopAll();
        jobStore.clear();
    }

    @Test
    public void testInitializeSendsBroadcastsSchedules() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 1).setRequiresCharging(true).build(), GcmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 2).setRequiresDeviceIdle(true).build(), GcmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 3).setPeriodic(TimeUnit.HOURS.toMillis(1)).build(), GcmScheduler.TAG));

        final AtomicInteger idSum = new AtomicInteger(0);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (GcmScheduler.ACTION_SCHEDULE.equals(intent.getAction())
                        && GcmScheduler.SCHEDULER_ACTION_SCHEDULE_TASK.equals(
                        intent.getStringExtra(GcmScheduler.BUNDLE_PARAM_SCHEDULER_ACTION))) {
                    idSum.addAndGet(Integer.valueOf(intent.getStringExtra(GcmScheduler.PARAM_TAG)));
                }
            }
        }, new IntentFilter(GcmScheduler.ACTION_SCHEDULE));

        assertEquals(0, idSum.get());

        initializeService();

        assertEquals(6, idSum.get());
    }

    @Test
    public void testJobRuns() {
        long delayMs = 2000;
        DeviceTestUtils.setNetworkInfo(context, true, false, true);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, delayMs).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).build(),
                AlarmScheduler.TAG));
        executeService(0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testJobFinishes() throws InterruptedException {
        long delayMs = 5;
        DeviceTestUtils.setNetworkInfo(context, true, false, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, delayMs).setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING).build(),
                AlarmScheduler.TAG));
        executeService(0);

        assertBoundServiceCount(1);

        Thread.sleep(THREAD_WAIT_MS + delayMs);

        assertBoundServiceCount(0);
    }

    @Test
    public void testNotRoamingConstraint() {
        DeviceTestUtils.setNetworkInfo(context, true, true, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING).build(),
                GcmScheduler.TAG));

        assertEquals(1, jobStore.size());

        executeService(0);

        assertEquals(1, jobStore.size());

        DeviceTestUtils.setNetworkInfo(context, true, false, false);
        executeService(0);

        assertEquals(0, jobStore.size());
    }

    @Test
    public void testIdleConstraint() {
        DeviceTestUtils.setDeviceIdle(context, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresDeviceIdle(true).build(), GcmScheduler.TAG));

        assertEquals(1, jobStore.size());

        executeService(0);

        assertEquals(1, jobStore.size());

        DeviceTestUtils.setDeviceIdle(context, true);
        executeService(0);

        assertEquals(0, jobStore.size());
    }

    @Test
    public void testDeadlineConstraint() {
        long latency = TimeUnit.HOURS.toMillis(2);
        DeviceTestUtils.setDeviceIdle(context, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresDeviceIdle(true).setOverrideDeadline(latency).build(),
                GcmScheduler.TAG));

        assertEquals(1, jobStore.size());

        Robolectric.getForegroundThreadScheduler().advanceBy(latency, TimeUnit.MILLISECONDS);
        executeService(0);

        assertEquals(0, jobStore.size());
    }

    private void assertBoundServiceCount(int count) {
        assertEquals(count, ShadowApplication.getInstance().getBoundServiceConnections().size());
    }

    private void initializeService() {
        service.onStartCommand(new Intent(GcmJobService.ACTION_INITIALIZE), 0, 0);
    }

    private void executeService(int jobId) {
        Intent intent = new Intent(GcmJobService.ACTION_EXECUTE);
        intent.putExtra(GcmJobService.EXTRA_TAG, String.valueOf(jobId));
        intent.putExtra(GcmJobService.EXTRA_EXTRAS, Bundle.EMPTY);
        intent.putExtra(GcmJobService.EXTRA_CALLBACK, new PendingCallback(Parcel.obtain()));
        service.onStartCommand(intent, 0, 0);
    }
}
