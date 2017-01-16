package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.util.NoopJobService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.ComponentName;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class JobStatusTest {
    private static final long METHOD_WAIT_MS = 5; // Allow up to 5ms of latency between method calls.

    private ComponentName component;

    @Before
    public void setup() {
        component = new ComponentName(RuntimeEnvironment.application, NoopJobService.class);
    }

    @Test
    public void testSingleConstraints() {
        JobStatus jobStatus;

        // Charging.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component).setRequiresCharging(true).build(), "noop");
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CHARGING, true);
        assertTrue(jobStatus.isReady());

        // Idle.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component).setRequiresDeviceIdle(true).build(), "noop");
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_IDLE, true);
        assertTrue(jobStatus.isReady());

        // Any connectivity.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(), "noop");
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_CONNECTIVITY, true);
        assertTrue(jobStatus.isReady());

        // Unmetered connectivity.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).build(), "noop");
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_UNMETERED, true);
        assertTrue(jobStatus.isReady());

        // Not roaming connectivity.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING).build(), "noop");
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_NOT_ROAMING, true);
        assertTrue(jobStatus.isReady());

        // Minimum latency.
        jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setMinimumLatency(TimeUnit.MICROSECONDS.toMillis(15)).build(), "noop");
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_TIMING_DELAY, true);
        assertTrue(jobStatus.isReady());
    }

    @Test
    public void testDeadlineTrumpsAllConstraints() {
        JobStatus jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setMinimumLatency(TimeUnit.HOURS.toMillis(24))
                        .setRequiresCharging(true)
                        .setOverrideDeadline(1L).build(),
                "noop");
        assertFalse(jobStatus.isReady());
        jobStatus.setConstraintSatisfied(JobStatus.CONSTRAINT_DEADLINE, true);
        assertTrue(jobStatus.isReady());
    }

    @Test
    public void testTimingsFollowSystemClock() {
        long minimumLatency = TimeUnit.MINUTES.toMillis(15);
        long overrideDeadline = TimeUnit.HOURS.toMillis(2);

        JobStatus jobStatus = JobStatus.createFromJobInfo(
                new JobInfo.Builder(0, component)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumLatency(minimumLatency)
                        .setOverrideDeadline(overrideDeadline)
                        .build(),
                "noop");
        compareTimestampsSubjectToCallLatency(
                jobStatus.getEarliestRunTimeElapsed(), SystemClock.elapsedRealtime() + minimumLatency);
        compareTimestampsSubjectToCallLatency(
                jobStatus.getLatestRunTimeElapsed(), SystemClock.elapsedRealtime() + overrideDeadline);
    }

    /**
     * Comparing timestamps before and after IO read/writes involves some latency.
     */
    private void compareTimestampsSubjectToCallLatency(long ts1, long ts2) {
        assertTrue(Math.abs(ts1 - ts2) < METHOD_WAIT_MS);
    }
}
