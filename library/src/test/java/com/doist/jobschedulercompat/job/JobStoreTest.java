package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.Context;
import android.os.SystemClock;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class JobStoreTest {
    private static final long IO_WAIT_MS = 500;

    private Context context;
    private JobStore jobStore;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobStore = JobStore.get(context);
    }

    @After
    public void teardown() {
        jobStore.clear();
    }

    @Test
    public void testMaybeWriteStatusToDisk() throws InterruptedException {
        JobInfo job = JobCreator.create(context, 0)
                .setRequiresCharging(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(10000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setOverrideDeadline(20000L)
                .setMinimumLatency(2000L)
                .setPersisted(true)
                .build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, "noop");
        jobStore.add(jobStatus);

        Thread.sleep(IO_WAIT_MS);

        // Manually load tasks from xml file.
        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);

        assertEquals("Didn't get expected number of persisted tasks", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.getJobs().get(0);
        assertJobInfoEquals(job, loaded.getJob());
        assertTrue("JobStore#containsJob invalid", jobStore.containsJob(jobStatus));
        compareTimestampsSubjectToIoLatency(
                "Early run-times not the same after read",
                jobStatus.getEarliestRunTimeElapsed(),
                loaded.getEarliestRunTimeElapsed());
        compareTimestampsSubjectToIoLatency(
                "Late run-times not the same after read",
                jobStatus.getLatestRunTimeElapsed(),
                loaded.getLatestRunTimeElapsed());
    }

    @Test
    public void testWritingTwoFilesToDisk() throws Exception {
        JobInfo job1 = JobCreator.create(context, 1)
                .setRequiresDeviceIdle(true)
                .setPeriodic(10000L)
                .setRequiresCharging(true)
                .setPersisted(true)
                .build();
        JobInfo job2 = JobCreator.create(context, 2)
                .setMinimumLatency(5000L)
                .setBackoffCriteria(15000L, JobInfo.BACKOFF_POLICY_LINEAR)
                .setOverrideDeadline(30000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .build();
        JobStatus jobStatus1 = JobStatus.createFromJobInfo(job1, "noop");
        JobStatus jobStatus2 = JobStatus.createFromJobInfo(job2, "noop");
        jobStore.add(jobStatus1);
        jobStore.add(jobStatus2);

        Thread.sleep(IO_WAIT_MS);

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 2, jobStatusSet.size());
        Iterator<JobStatus> it = jobStatusSet.getJobs().iterator();
        JobStatus loaded1 = it.next();
        JobStatus loaded2 = it.next();

        // Reverse them so we know which comparison to make.
        if (loaded1.getJobId() != 1) {
            JobStatus tmp = loaded1;
            loaded1 = loaded2;
            loaded2 = tmp;
        }

        assertJobInfoEquals(job1, loaded1.getJob());
        assertJobInfoEquals(job2, loaded2.getJob());
        assertTrue("JobStore#containsJob invalid.", jobStore.containsJob(jobStatus1));
        assertTrue("JobStore#containsJob invalid.", jobStore.containsJob(jobStatus2));
        // Check that the loaded task has the correct runtimes.
        compareTimestampsSubjectToIoLatency(
                "Early run-times not the same after read.",
                jobStatus1.getEarliestRunTimeElapsed(),
                loaded1.getEarliestRunTimeElapsed());
        compareTimestampsSubjectToIoLatency(
                "Late run-times not the same after read.",
                jobStatus1.getLatestRunTimeElapsed(),
                loaded1.getLatestRunTimeElapsed());
        compareTimestampsSubjectToIoLatency(
                "Early run-times not the same after read.",
                jobStatus2.getEarliestRunTimeElapsed(),
                loaded2.getEarliestRunTimeElapsed());
        compareTimestampsSubjectToIoLatency(
                "Late run-times not the same after read.",
                jobStatus2.getLatestRunTimeElapsed(),
                loaded2.getLatestRunTimeElapsed());

    }

    @Test
    public void testWritingTaskWithExtras() throws Exception {
        JobInfo.Builder builder = JobCreator.create(context, 8)
                .setRequiresDeviceIdle(true)
                .setPeriodic(10000L)
                .setRequiresCharging(true)
                .setPersisted(true);

        PersistableBundle extras = new PersistableBundle();
        extras.putDouble("hello", 3.2);
        extras.putString("hi", "there");
        extras.putInt("into", 3);
        builder.setExtras(extras);
        JobInfo job = builder.build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, "noop");
        jobStore.add(jobStatus);

        Thread.sleep(IO_WAIT_MS);

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.getJobs().iterator().next();
        assertJobInfoEquals(job, loaded.getJob());
    }

    @Test
    public void testMassivePeriodClampedOnRead() throws Exception {
        long period = TimeUnit.HOURS.toMillis(2);
        JobInfo job = JobCreator.create(context, 8).setPeriodic(period).setPersisted(true).build();

        long invalidLateRuntimeElapsedMillis = SystemClock.elapsedRealtime() + (period) + period;  // > period.
        long invalidEarlyRuntimeElapsedMillis = invalidLateRuntimeElapsedMillis - period; // Early = (late - period).
        JobStatus jobStatus =
                new JobStatus(job, "noop", invalidEarlyRuntimeElapsedMillis, invalidLateRuntimeElapsedMillis);
        jobStore.add(jobStatus);

        Thread.sleep(IO_WAIT_MS);

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.getJobs().iterator().next();

        // Assert early runtime was clamped to be under now + period. We can do <= here b/c we'll
        // call SystemClock.elapsedRealtime after doing the disk i/o.
        long newNowElapsed = SystemClock.elapsedRealtime();
        assertTrue("Early runtime wasn't correctly clamped.",
                   loaded.getEarliestRunTimeElapsed() <= newNowElapsed + period);
        // Assert late runtime was clamped to be now + period + flex.
        assertTrue("Early runtime wasn't correctly clamped.",
                   loaded.getEarliestRunTimeElapsed() <= newNowElapsed + period);
    }

    @Test
    public void testSchedulerPersisted() throws Exception {
        JobInfo job = JobCreator.create(context, 92)
                .setOverrideDeadline(5000)
                .setPersisted(true)
                .build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, "noop");
        jobStore.add(jobStatus);

        Thread.sleep(IO_WAIT_MS);

        final JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        JobStatus loaded = jobStatusSet.getJobs().iterator().next();
        assertEquals("Priority not correctly persisted.", "noop", loaded.getScheduler());
    }

    /**
     * Helper function to assert that two {@link JobInfo} are equal.
     */
    private void assertJobInfoEquals(JobInfo first, JobInfo second) {
        assertEquals("Different task ids", first.getId(), second.getId());
        assertEquals("Different components", first.getService(), second.getService());
        assertEquals("Different periodic status", first.isPeriodic(), second.isPeriodic());
        assertEquals("Different period", first.getIntervalMillis(), second.getIntervalMillis());
        assertEquals("Different inital backoff", first.getInitialBackoffMillis(), second.getInitialBackoffMillis());
        assertEquals("Different backoff policy", first.getBackoffPolicy(), second.getBackoffPolicy());
        assertEquals("Invalid charging constraint", first.isRequireCharging(), second.isRequireCharging());
        assertEquals("Invalid idle constraint", first.isRequireDeviceIdle(), second.isRequireDeviceIdle());
        assertEquals("Invalid unmetered constraint", first.getNetworkType(), second.getNetworkType());
        assertEquals("Invalid deadline constraint", first.hasLateConstraint(), second.hasLateConstraint());
        assertEquals("Invalid delay constraint", first.hasEarlyConstraint(), second.hasEarlyConstraint());
        assertEquals("Extras don't match", first.getExtras().toMap(10), second.getExtras().toMap(10));
    }

    /**
     * Comparing timestamps before and after IO read/writes involves some latency.
     */
    private void compareTimestampsSubjectToIoLatency(String error, long ts1, long ts2) {
        assertTrue(error, Math.abs(ts1 - ts2) < IO_WAIT_MS * 2);
    }
}
