package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.ShadowJobParameters;
import com.doist.jobschedulercompat.util.ShadowNetworkInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.app.Application;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.N, Build.VERSION_CODES.O, Build.VERSION_CODES.P},
       shadows = {ShadowJobParameters.class, ShadowNetworkInfo.class})
public class JobSchedulerJobServiceTest {
    private static final long LATENCY_MS = TimeUnit.HOURS.toMillis(1);

    private Application application;
    private JobStore jobStore;
    private JobSchedulerJobService service;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        jobStore = JobStore.get(application);
        service = Robolectric.buildService(JobSchedulerJobService.class).create().bind().get();
    }

    @After
    public void teardown() {
        JobCreator.interruptJobs();
        jobStore.clear();
    }

    @Test
    public void testJobRuns() {
        long delayMs = 5000;
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, delayMs).setMinimumLatency(LATENCY_MS).build(),
                JobSchedulerSchedulerV26.TAG));
        DeviceTestUtils.advanceTime(LATENCY_MS);
        executeService(0);

        assertBoundServiceCount(1);
    }

    @Test
    public void testJobFinishes() {
        long delayMs = 5;
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, delayMs).setMinimumLatency(LATENCY_MS).build(),
                JobSchedulerSchedulerV26.TAG));
        DeviceTestUtils.advanceTime(LATENCY_MS);
        executeService(0);

        assertBoundServiceCount(1);

        JobCreator.waitForJob(0);

        assertBoundServiceCount(0);
    }

    @Test
    public void testStopJobStopsJob() {
        DeviceTestUtils.setCharging(application, true);
        long delayMs = 5000;
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(application, 0, delayMs).setRequiresCharging(true).build(),
                JobSchedulerSchedulerV26.TAG);
        jobStore.add(jobStatus);
        executeService(0);

        assertBoundServiceCount(1);

        service.onStopJob(ShadowJobParameters.newInstance(jobStore.getJob(0)));

        assertBoundServiceCount(0);
    }

    private void executeService(int jobId) {
        service.onStartJob(ShadowJobParameters.newInstance(jobStore.getJob(jobId)));
    }

    private void assertBoundServiceCount(int count) {
        assertEquals(count, shadowOf(application).getBoundServiceConnections().size());
    }
}
