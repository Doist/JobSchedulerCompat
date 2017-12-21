package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.NoopAsyncJobService;
import com.doist.jobschedulercompat.util.ShadowNetworkInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.KITKAT,
        shadows = {ShadowNetworkInfo.class})
public class AlarmJobServiceTest {
    private static long DELAY_MS = 5000;
    private static long LATENCY_MS = TimeUnit.HOURS.toMillis(1);

    private Context context;
    private JobStore jobStore;
    private ServiceController<AlarmJobService> service;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobStore = JobStore.get(context);
        service = Robolectric.buildService(AlarmJobService.class).create();
    }

    @After
    public void teardown() {
        JobCreator.interruptJobs();
        jobStore.clear();
    }

    @Test
    public void testJobRuns() {
        DeviceTestUtils.setDeviceIdle(context, true);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setRequiresDeviceIdle(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testJobFinishes() throws InterruptedException {
        long delayMs = 5;
        DeviceTestUtils.setCharging(context, true);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, delayMs).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(1);

        JobCreator.waitForJob(0);

        assertBoundServiceCount(0);
    }

    @Test
    public void testChargingConstraint() {
        DeviceTestUtils.setCharging(context, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setCharging(context, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testIdleConstraint() {
        DeviceTestUtils.setDeviceIdle(context, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setRequiresDeviceIdle(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setDeviceIdle(context, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testConnectivityConstraint() {
        DeviceTestUtils.setNetworkInfo(context, false, false, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(context, true, false, false);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testNotRoamingConstraint() {
        DeviceTestUtils.setNetworkInfo(context, true, true, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(context, true, false, false);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testUnmeteredConstraint() {
        DeviceTestUtils.setNetworkInfo(context, true, false, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(context, true, false, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testContentTriggerConstraint() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS)
                          .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("com.doist"), 0))
                          .build(),
                AlarmScheduler.TAG));

        service.startCommand(0, 0);

        assertEquals(ContentObserverService.class.getCanonicalName(),
                     ShadowApplication.getInstance().getNextStartedService().getComponent().getClassName());
    }

    @Test
    public void testLatencyConstraint() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setMinimumLatency(LATENCY_MS).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.advanceTime(LATENCY_MS);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testDeadlineConstraint() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS)
                          .setRequiresCharging(true)
                          .setOverrideDeadline(LATENCY_MS)
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.advanceTime(LATENCY_MS);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testMixedConstraints() {
        DeviceTestUtils.setCharging(context, false);
        DeviceTestUtils.setDeviceIdle(context, false);
        DeviceTestUtils.setNetworkInfo(context, false, false, false);

        PersistableBundle extras = new PersistableBundle();
        extras.putLong(NoopAsyncJobService.EXTRA_DELAY, 2000);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS)
                          .setRequiresCharging(true)
                          .setRequiresDeviceIdle(true)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                          .setMinimumLatency(LATENCY_MS)
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setCharging(context, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setDeviceIdle(context, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(context, true, false, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.advanceTime(LATENCY_MS);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testRemoveRunningJob() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 1, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(2);

        jobStore.remove(0);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testRemoveAllRunningJobs() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 1, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(2);

        jobStore.clear();
        service.startCommand(0, 0);

        assertBoundServiceCount(0);
    }

    @Test
    public void testConstraintUnmetWhileRunningJob() {
        DeviceTestUtils.setCharging(context, true);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, DELAY_MS).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(1);

        DeviceTestUtils.setCharging(context, false);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);
    }

    private void assertBoundServiceCount(int count) {
        assertEquals(count, ShadowApplication.getInstance().getBoundServiceConnections().size());
    }
}
