package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.NoopAsyncJobService;
import com.doist.jobschedulercompat.util.ShadowJobParameters;
import com.doist.jobschedulercompat.util.ShadowNetworkInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import android.content.Context;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, shadows = {ShadowJobParameters.class, ShadowNetworkInfo.class})
public class JobSchedulerJobServiceTest {
    private static final long THREAD_WAIT_MS = 80;
    private static final long LATENCY_MS = TimeUnit.HOURS.toMillis(1);

    private Context context;
    private JobStore jobStore;
    private JobSchedulerJobService service;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobStore = JobStore.get(context);
        service = Robolectric.buildService(JobSchedulerJobService.class).create().get();
    }

    @After
    public void teardown() {
        NoopAsyncJobService.stopAll();
        jobStore.clear();
    }

    @Test
    public void testJobRuns() {
        long delayMs = 2000;
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, delayMs).setMinimumLatency(LATENCY_MS).build(),
                JobSchedulerScheduler.TAG));
        DeviceTestUtils.advanceTime(LATENCY_MS);
        executeService(0, false);

        assertBoundServiceCount(1);
    }

    @Test
    public void testJobFinishes() throws InterruptedException {
        long delayMs = 5;
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, delayMs).setMinimumLatency(LATENCY_MS).build(),
                JobSchedulerScheduler.TAG));
        DeviceTestUtils.advanceTime(LATENCY_MS);
        executeService(0, false);

        assertBoundServiceCount(1);

        Thread.sleep(THREAD_WAIT_MS + delayMs);

        assertBoundServiceCount(0);
    }

    @Test
    public void testStopJobStopsJob() {
        DeviceTestUtils.setCharging(context, true);
        long delayMs = 2000;
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0, delayMs).setRequiresCharging(true).build(),
                JobSchedulerScheduler.TAG);
        jobStore.add(jobStatus);
        executeService(0, false);

        assertBoundServiceCount(1);

        service.onStopJob(ShadowJobParameters.newInstance(jobStore.getJob(0), false));

        assertBoundServiceCount(0);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void testNotRoamingConstraintOnLollipop() {
        DeviceTestUtils.setNetworkInfo(context, true, true, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING).build(),
                JobSchedulerScheduler.TAG));

        assertEquals(1, jobStore.size());

        executeService(0, false);

        assertEquals(1, jobStore.size());

        DeviceTestUtils.setNetworkInfo(context, true, false, false);
        executeService(0, false);

        assertEquals(0, jobStore.size());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void testIdleConstraintOnLollipop() {
        DeviceTestUtils.setDeviceIdle(context, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresDeviceIdle(true).build(), JobSchedulerScheduler.TAG));

        assertEquals(1, jobStore.size());

        executeService(0, false);

        assertEquals(1, jobStore.size());

        DeviceTestUtils.setDeviceIdle(context, true);
        executeService(0, false);

        assertEquals(0, jobStore.size());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void testDeadlineConstraintOnLollipop() {
        DeviceTestUtils.setDeviceIdle(context, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresDeviceIdle(true).build(), JobSchedulerScheduler.TAG));

        assertEquals(1, jobStore.size());

        executeService(0, true);

        assertEquals(0, jobStore.size());
    }

    private void executeService(int jobId, boolean isOverrideDeadlineExpired) {
        service.onStartJob(ShadowJobParameters.newInstance(jobStore.getJob(jobId), isOverrideDeadlineExpired));
    }

    private void assertBoundServiceCount(int count) {
        assertEquals(count, ShadowApplication.getInstance().getBoundServiceConnections().size());
    }
}
