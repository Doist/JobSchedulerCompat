package com.doist.jobschedulercompat.scheduler.alarm;

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
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import android.app.Application;
import android.net.Uri;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowNetworkInfo.class})
public class AlarmJobServiceTest {
    private static long DELAY_MS = 100;
    private static long LATENCY_MS = TimeUnit.HOURS.toMillis(1);

    private Application application;
    private JobStore jobStore;
    private ServiceController<AlarmJobService> service;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        jobStore = JobStore.get(application);
        service = Robolectric.buildService(AlarmJobService.class).create();
    }

    @After
    public void teardown() {
        JobCreator.interruptJobs();
        jobStore.clear();
    }

    @Test
    public void testJobRuns() {
        DeviceTestUtils.setDeviceIdle(application, true);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS).setRequiresDeviceIdle(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testJobFinishes() {
        long delayMs = 5;
        DeviceTestUtils.setCharging(application, true);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, delayMs).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(1);

        JobCreator.waitForJob(0);

        assertBoundServiceCount(0);
    }

    @Test
    public void testChargingConstraint() {
        DeviceTestUtils.setCharging(application, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setCharging(application, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testIdleConstraint() {
        DeviceTestUtils.setDeviceIdle(application, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS).setRequiresDeviceIdle(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setDeviceIdle(application, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testConnectivityConstraint() {
        DeviceTestUtils.setNetworkInfo(application, false, false, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(application, true, false, false);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testNotRoamingConstraint() {
        DeviceTestUtils.setNetworkInfo(application, true, true, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(application, true, false, false);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testUnmeteredConstraint() {
        DeviceTestUtils.setNetworkInfo(application, true, false, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(application, true, false, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testContentTriggerConstraint() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS)
                          .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("com.doist"), 0))
                          .build(),
                AlarmScheduler.TAG));

        service.startCommand(0, 0);

        assertEquals(ContentObserverService.class.getCanonicalName(),
                     shadowOf(application).getNextStartedService().getComponent().getClassName());
    }

    @Test
    public void testLatencyConstraint() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS).setMinimumLatency(LATENCY_MS).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.advanceTime(LATENCY_MS);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testDeadlineConstraint() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS)
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
        DeviceTestUtils.setCharging(application, false);
        DeviceTestUtils.setDeviceIdle(application, false);
        DeviceTestUtils.setNetworkInfo(application, false, false, false);

        PersistableBundle extras = new PersistableBundle();
        extras.putLong(NoopAsyncJobService.EXTRA_DELAY, 2000);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS)
                          .setRequiresCharging(true)
                          .setRequiresDeviceIdle(true)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                          .setMinimumLatency(LATENCY_MS)
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setCharging(application, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setDeviceIdle(application, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.setNetworkInfo(application, true, false, true);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);

        DeviceTestUtils.advanceTime(LATENCY_MS);
        service.startCommand(0, 0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testRemoveRunningJob() {
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 1, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
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
                JobCreator.create(application, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 1, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(2);

        jobStore.clear();
        service.startCommand(0, 0);

        assertBoundServiceCount(0);
    }

    @Test
    public void testConstraintUnmetWhileRunningJob() {
        DeviceTestUtils.setCharging(application, true);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, DELAY_MS).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertBoundServiceCount(1);

        DeviceTestUtils.setCharging(application, false);
        service.startCommand(0, 0);

        assertBoundServiceCount(0);
    }

    private void assertBoundServiceCount(int count) {
        assertEquals(count, shadowOf(application).getBoundServiceConnections().size());
    }
}
