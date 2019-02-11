package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;
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
        synchronized (JobStore.LOCK) {
            jobStore.clear();
        }
    }

    @Test
    public void testJobRuns() {
        JobInfo job = JobCreator.create(application, 2000).setMinimumLatency(LATENCY_MS).build();
        jobStore.add(JobStatus.createFromJobInfo(job, getSchedulerTag()));
        DeviceTestUtils.advanceTime(LATENCY_MS);
        executeService(job.getId());

        assertBoundServiceCount(1);
    }

    @Test
    public void testJobFinishes() {
        JobInfo job = JobCreator.create(application, 50).setMinimumLatency(LATENCY_MS).build();
        jobStore.add(JobStatus.createFromJobInfo(job, getSchedulerTag()));
        DeviceTestUtils.advanceTime(LATENCY_MS);
        executeService(job.getId());

        assertBoundServiceCount(1);

        JobCreator.waitForJob(job.getId());

        assertBoundServiceCount(0);
    }

    @Test
    public void testStopJobStopsJob() {
        DeviceTestUtils.setCharging(application, true);
        JobInfo job = JobCreator.create(application, 2000).setRequiresCharging(true).build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, getSchedulerTag());
        jobStore.add(jobStatus);
        executeService(job.getId());

        assertBoundServiceCount(1);

        service.onStopJob(ShadowJobParameters.newInstance(jobStore.getJob(job.getId())));

        assertBoundServiceCount(0);
    }

    private void executeService(int jobId) {
        service.onStartJob(ShadowJobParameters.newInstance(jobStore.getJob(jobId)));
    }

    private void assertBoundServiceCount(int count) {
        assertEquals(count, shadowOf(application).getBoundServiceConnections().size());
    }

    private String getSchedulerTag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return JobSchedulerSchedulerV26.TAG;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return JobSchedulerSchedulerV24.TAG;
        } else {
            return JobSchedulerSchedulerV21.TAG;
        }
    }
}
