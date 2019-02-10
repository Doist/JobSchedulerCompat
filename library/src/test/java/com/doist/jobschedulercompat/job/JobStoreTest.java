package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.util.BundleUtils;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JobStoreTest {
    private Application application;
    private JobStore jobStore;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        jobStore = JobStore.get(application);
    }

    @After
    public void teardown() {
        jobStore.clear();
    }

    @Test
    public void testMaybeWriteStatusToDisk() {
        JobInfo job = JobCreator.create(application)
                                .setRequiresCharging(true)
                                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                .setBackoffCriteria(10000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                                .setOverrideDeadline(20000L)
                                .setMinimumLatency(2000L)
                                .setPersisted(true)
                                .build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, "noop");
        jobStore.add(jobStatus);

        waitForJobStoreWrite();

        // Manually load tasks from xml file.
        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);

        assertEquals("Incorrect # of persisted tasks", 1, jobStatusSet.size());
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
    public void testWritingTwoFilesToDisk() {
        JobInfo job1 = JobCreator.create(application)
                                 .setRequiresDeviceIdle(true)
                                 .setPeriodic(10000L)
                                 .setRequiresCharging(true)
                                 .setPersisted(true)
                                 .build();
        JobInfo job2 = JobCreator.create(application)
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

        waitForJobStoreWrite();

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 2, jobStatusSet.size());
        Iterator<JobStatus> it = jobStatusSet.getJobs().iterator();
        JobStatus loaded1 = it.next();
        JobStatus loaded2 = it.next();

        // Reverse them so we know which comparison to make.
        if (loaded1.getJobId() != job1.getId()) {
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
    public void testWritingTaskWithExtras() {
        JobInfo.Builder builder =
                JobCreator.create(application)
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

        waitForJobStoreWrite();

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.getJobs().iterator().next();
        assertJobInfoEquals(job, loaded.getJob());
    }

    public void testWritingTaskWithFlex() {
        JobInfo.Builder builder =
                JobCreator.create(application)
                          .setRequiresDeviceIdle(true)
                          .setPeriodic(TimeUnit.HOURS.toMillis(5), TimeUnit.HOURS.toMillis(1))
                          .setRequiresCharging(true)
                          .setPersisted(true);
        JobStatus taskStatus = JobStatus.createFromJobInfo(builder.build(), "noop");

        waitForJobStoreWrite();

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.getJobs().iterator().next();
        assertEquals("Period not equal", loaded.getJob().getIntervalMillis(), taskStatus.getJob().getIntervalMillis());
        assertEquals("Flex not equal", loaded.getJob().getFlexMillis(), taskStatus.getJob().getFlexMillis());
    }

    @Test
    public void testMassivePeriodClampedOnRead() {
        long period = TimeUnit.HOURS.toMillis(2);
        JobInfo job = JobCreator.create(application).setPeriodic(period).setPersisted(true).build();

        long invalidLateRuntimeElapsedMillis = SystemClock.elapsedRealtime() + (period) + period;  // > period.
        long invalidEarlyRuntimeElapsedMillis = invalidLateRuntimeElapsedMillis - period; // Early = (late - period).
        JobStatus jobStatus =
                new JobStatus(job, "noop", invalidEarlyRuntimeElapsedMillis, invalidLateRuntimeElapsedMillis);
        jobStore.add(jobStatus);

        waitForJobStoreWrite();

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
    public void testSchedulerPersisted() {
        JobInfo job = JobCreator.create(application)
                                .setOverrideDeadline(5000)
                                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                                .setRequiresBatteryNotLow(true)
                                .setPersisted(true)
                                .build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, "noop");
        jobStore.add(jobStatus);

        waitForJobStoreWrite();

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        Iterator<JobStatus> it = jobStatusSet.getJobs().iterator();
        assertTrue(it.hasNext());
        assertEquals("Scheduler not correctly persisted.", "noop", it.next().getSchedulerTag());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCompat() {
        Uri uri = Uri.parse("doist.com");
        String authority = "com.doist";

        JobInfo.Builder builder =
                JobCreator.create(application)
                          .addTriggerContentUri(new JobInfo.TriggerContentUri(uri, 0))
                          .setTriggerContentUpdateDelay(TimeUnit.SECONDS.toMillis(5))
                          .setTriggerContentMaxDelay(TimeUnit.SECONDS.toMillis(30));

        Bundle transientExtras = new Bundle();
        transientExtras.putBoolean("test", true);
        builder.setTransientExtras(transientExtras);

        JobInfo job = builder.build();
        JobStatus jobStatus = JobStatus.createFromJobInfo(job, "noop");

        jobStatus.changedUris = Collections.singleton(uri);
        jobStatus.changedAuthorities = Collections.singleton(authority);

        jobStore.add(jobStatus);

        waitForJobStoreWrite();

        JobStore.JobSet jobStatusSet = new JobStore.JobSet();
        jobStore.readJobMapFromDisk(jobStatusSet);
        assertEquals("Incorrect # of persisted tasks.", 1, jobStatusSet.size());
        JobStatus loaded = jobStatusSet.getJobs().iterator().next();
        assertEquals(jobStatus.changedUris, loaded.changedUris);
        assertEquals(jobStatus.changedAuthorities, loaded.changedAuthorities);
        assertJobInfoEquals(job, loaded.getJob());
    }

    private void waitForJobStoreWrite() {
        try {
            final Semaphore semaphore = new Semaphore(1);
            semaphore.acquire();
            jobStore.queue.offer(new Runnable() {
                @Override
                public void run() {
                    semaphore.release();
                }
            }, 1, TimeUnit.SECONDS);
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper function to assert that two {@link JobInfo} are equal.
     */
    private void assertJobInfoEquals(JobInfo first, JobInfo second) {
        assertEquals("Different task ids", first.getId(), second.getId());
        assertEquals("Different components", first.getService(), second.getService());
        assertEquals("Different periodic status", first.isPeriodic(), second.isPeriodic());
        assertEquals("Different period", first.getIntervalMillis(), second.getIntervalMillis());
        assertEquals("Different initial backoff", first.getInitialBackoffMillis(), second.getInitialBackoffMillis());
        assertEquals("Different backoff policy", first.getBackoffPolicy(), second.getBackoffPolicy());
        assertEquals("Invalid charging constraint", first.isRequireCharging(), second.isRequireCharging());
        assertEquals("Invalid battery not low constraint",
                     first.isRequireBatteryNotLow(), second.isRequireBatteryNotLow());
        assertEquals("Invalid idle constraint", first.isRequireDeviceIdle(), second.isRequireDeviceIdle());
        assertEquals("Invalid connectivity constraint", first.getNetworkType(), second.getNetworkType());
        assertEquals("Invalid deadline constraint", first.hasLateConstraint(), second.hasLateConstraint());
        assertEquals("Invalid delay constraint", first.hasEarlyConstraint(), second.hasEarlyConstraint());
        assertEquals("Extras don't match", first.getExtras().toMap(10), second.getExtras().toMap(10));
        assertEquals("Transient extras don't match",
                     BundleUtils.toMap(first.getTransientExtras(), 10),
                     BundleUtils.toMap(second.getTransientExtras(), 10));
    }

    /**
     * Comparing timestamps before and after IO read/writes involves some latency.
     */
    private void compareTimestampsSubjectToIoLatency(String error, long ts1, long ts2) {
        assertTrue(error, Math.abs(ts1 - ts2) < TimeUnit.SECONDS.toMillis(1000));
    }
}
