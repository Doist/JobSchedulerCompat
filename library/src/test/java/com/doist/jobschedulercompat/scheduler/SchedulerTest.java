package com.doist.jobschedulercompat.scheduler;

import com.google.android.gms.common.ConnectionResult;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.NoopAsyncJobService;
import com.doist.jobschedulercompat.util.NoopScheduler;
import com.doist.jobschedulercompat.util.ShadowGoogleApiAvailability;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.Context;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class SchedulerTest {
    private static final long DELAY_MS = 2000;

    private Context context;
    private JobStore jobStore;
    private Scheduler scheduler;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobStore = JobStore.get(context);
        scheduler = new NoopScheduler(context, jobStore);

        ShadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
    }

    @After
    public void teardown() {
        NoopAsyncJobService.stopAll();
        jobStore.clear();
    }

    @Test
    public void testSchedule() {
        scheduler.schedule(
                JobCreator.create(context, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());

        assertNotNull(jobStore.getJob(0));
    }

    @Test(expected = IllegalStateException.class)
    public void testScheduleHasUpperLimit() {
        for (int i = 0; i <= Scheduler.MAX_JOBS + 1; i++) {
            scheduler.schedule(
                    JobCreator.create(context, i, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
        }
    }

    @Test
    public void testCancellingJob() {
        scheduler.schedule(
                JobCreator.create(context, 0, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());

        assertNotNull(jobStore.getJob(0));

        scheduler.cancel(0);

        assertNull(jobStore.getJob(0));
    }

    @Test
    public void testCancellingAllJobs() {
        for (int i = 0; i < 10; i++) {
            scheduler.schedule(
                    JobCreator.create(context, i, DELAY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
        }

        assertEquals(10, jobStore.size());

        scheduler.cancelAll();

        assertEquals(0, jobStore.size());
    }

    @Test
    public void testJobFinishedSuccess() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setRequiresCharging(true /* Random constraint. */).build(), "noop");
        jobStore.add(jobStatus);

        scheduler.onJobCompleted(0, false, scheduler.getTag());
        jobStatus = jobStore.getJob(0);
        assertNull(jobStatus);
    }

    @Test
    public void testPeriodicJobFinishedSuccess() {
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0).setPeriodic(timeMs).build(), "noop");
        jobStore.add(jobStatus);
        Robolectric.getForegroundThreadScheduler().advanceBy(timeMs, TimeUnit.MILLISECONDS);

        scheduler.onJobCompleted(0, false, scheduler.getTag());
        JobStatus newJobStatus = jobStore.getJob(0);
        assertEquals(jobStatus.getEarliestRunTimeElapsed() + timeMs, newJobStatus.getEarliestRunTimeElapsed());
        assertEquals(jobStatus.getLatestRunTimeElapsed() + timeMs, newJobStatus.getLatestRunTimeElapsed());
    }

    @Test
    public void testPeriodicJobFinishedFailure() {
        long currentTimeMs = SystemClock.elapsedRealtime();
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        long timeMaxMs = TimeUnit.HOURS.toMillis(5);

        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0)
                        .setPeriodic(timeMs)
                        .setBackoffCriteria(timeMs, JobInfo.BACKOFF_POLICY_LINEAR)
                        .build(), "noop");
        jobStore.add(jobStatus);

        // Fail until we reach 5 hours.
        for (int i = 0; i < timeMaxMs / timeMs; i++) {
            scheduler.onJobCompleted(0, true, scheduler.getTag());
            jobStatus = jobStore.getJob(0);
            assertEquals(currentTimeMs + timeMs * (i + 1), jobStatus.getEarliestRunTimeElapsed());
        }
        scheduler.onJobCompleted(0, true, scheduler.getTag());
        jobStatus = jobStore.getJob(0);
        assertEquals(currentTimeMs + timeMaxMs, jobStatus.getEarliestRunTimeElapsed());
    }

    @Test
    public void testJobFinishedFailureExponential() {
        long currentTimeMs = SystemClock.elapsedRealtime();
        long timeMs = TimeUnit.MINUTES.toMillis(15);
        long timeMaxMs = TimeUnit.HOURS.toMillis(5);

        JobStatus jobStatus = JobStatus.createFromJobInfo(
                JobCreator.create(context, 0)
                        .setRequiresCharging(true /* Random constraint. */)
                        .setBackoffCriteria(timeMs, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                        .build(), "noop");
        jobStore.add(jobStatus);

        // Fail until we reach five hours.
        for (int i = 0; i < Math.sqrt(timeMaxMs) / Math.sqrt(timeMs); i++) {
            scheduler.onJobCompleted(0, true, scheduler.getTag());
            jobStatus = jobStore.getJob(0);
            assertEquals(currentTimeMs + timeMs * Math.pow(2, i), jobStatus.getEarliestRunTimeElapsed(), 1);
        }
        scheduler.onJobCompleted(0, true, scheduler.getTag());
        jobStatus = jobStore.getJob(0);
        assertEquals(currentTimeMs + timeMaxMs, jobStatus.getEarliestRunTimeElapsed(), 1);
    }
}
