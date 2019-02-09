package com.doist.jobschedulercompat.scheduler.jobscheduler;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.util.BundleUtils;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@TargetApi(Build.VERSION_CODES.N)
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.N, Build.VERSION_CODES.O, Build.VERSION_CODES.P})
public class JobSchedulerSchedulerTest {
    private Application application;
    private JobSchedulerSchedulerV21 scheduler;
    private JobScheduler jobScheduler;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            scheduler = new JobSchedulerSchedulerV26(application);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            scheduler = new JobSchedulerSchedulerV24(application);
        } else {
            scheduler = new JobSchedulerSchedulerV21(application);
        }
        jobScheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @Test
    public void testScheduleJob() {
        JobInfo job = createJob(0);
        scheduler.schedule(job);

        android.app.job.JobInfo nativeJob = getPendingJob(job.getId());
        assertNotNull(nativeJob);
        assertNativeJobInfoMatchesJobInfo(nativeJob, job);

        job = createJob(1);
        scheduler.schedule(job);

        nativeJob = getPendingJob(job.getId());
        assertNotNull(nativeJob);
        assertNativeJobInfoMatchesJobInfo(nativeJob, job);

        job = createJob(2);
        scheduler.schedule(job);

        nativeJob = getPendingJob(job.getId());
        assertNotNull(nativeJob);
        assertNativeJobInfoMatchesJobInfo(nativeJob, job);
    }

    @Test
    public void testCancelJob() {
        JobInfo job = createJob(0);
        scheduler.schedule(job);
        scheduler.cancel(job.getId());

        assertEquals(0, jobScheduler.getAllPendingJobs().size());
    }

    @Test
    public void testCancelAllBroadcasts() {
        scheduler.schedule(createJob(0));
        scheduler.schedule(createJob(1));
        scheduler.schedule(createJob(2));
        scheduler.cancelAll();

        assertEquals(0, jobScheduler.getAllPendingJobs().size());
    }

    private void assertNativeJobInfoMatchesJobInfo(android.app.job.JobInfo nativeJob, JobInfo job) {
        assertEquals(nativeJob.getId(), job.getId());
        assertEquals(new PersistableBundle(nativeJob.getExtras()).toMap(10), job.getExtras().toMap(10));
        assertEquals(nativeJob.getService(), new ComponentName(application, JobSchedulerJobService.class));
        assertEquals(nativeJob.isRequireCharging(), job.isRequireCharging());
        assertEquals(nativeJob.isRequireDeviceIdle(), job.isRequireDeviceIdle());
        assertEquals(nativeJob.getNetworkType(), job.getNetworkType());
        assertEquals(nativeJob.getMinLatencyMillis(), job.getMinLatencyMillis());
        assertEquals(nativeJob.getMaxExecutionDelayMillis(), job.getMaxExecutionDelayMillis());
        assertEquals(nativeJob.isPeriodic(), job.isPeriodic());
        assertEquals(nativeJob.isPersisted(), job.isPersisted());
        assertEquals(nativeJob.getInitialBackoffMillis(), job.getInitialBackoffMillis());
        assertEquals(nativeJob.getBackoffPolicy(), job.getBackoffPolicy());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertEquals(nativeJob.getFlexMillis(), job.getFlexMillis());
            assertArrayEquals(getUris(nativeJob.getTriggerContentUris()), getUris(job.getTriggerContentUris()));
            assertEquals(nativeJob.getTriggerContentUpdateDelay(), job.getTriggerContentUpdateDelay());
            assertEquals(nativeJob.getTriggerContentMaxDelay(), job.getTriggerContentMaxDelay());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(
                    BundleUtils.toMap(nativeJob.getTransientExtras(), 10),
                    BundleUtils.toMap(job.getTransientExtras(), 10));
            assertEquals(nativeJob.isRequireBatteryNotLow(), job.isRequireBatteryNotLow());
            assertEquals(nativeJob.isRequireStorageNotLow(), job.isRequireBatteryNotLow());
        }
    }

    private JobInfo createJob(int type) {
        switch (type) {
            case 0:
                return JobCreator.create(application)
                                 .setMinimumLatency(TimeUnit.HOURS.toMillis(2))
                                 .setOverrideDeadline(TimeUnit.DAYS.toMillis(1))
                                 .setRequiresCharging(true)
                                 .setPersisted(true)
                                 .build();

            case 1:
                PersistableBundle extras = new PersistableBundle();
                extras.putString("test", "test");
                Bundle transientExtras = new Bundle();
                transientExtras.putString("test2", "test2");
                return JobCreator.create(application)
                                 .setRequiresCharging(true)
                                 .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                 .setExtras(extras)
                                 .setTransientExtras(transientExtras)
                                 .addTriggerContentUri(new JobInfo.TriggerContentUri(
                                         MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                         JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                                 .setTriggerContentUpdateDelay(TimeUnit.SECONDS.toMillis(1))
                                 .setTriggerContentMaxDelay(TimeUnit.SECONDS.toMillis(30))
                                 .build();

            case 2:
            default:
                return JobCreator.create(application)
                                 .setPeriodic(TimeUnit.MINUTES.toMillis(30), TimeUnit.MINUTES.toMillis(5))
                                 .setRequiresDeviceIdle(true)
                                 .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                                 .build();
        }
    }

    /* API 23 and below don't support getPendingJob, filter manually if necessary. */
    private android.app.job.JobInfo getPendingJob(int jobId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return jobScheduler.getPendingJob(jobId);
        } else {
            List<android.app.job.JobInfo> jobs = jobScheduler.getAllPendingJobs();
            for (android.app.job.JobInfo job : jobs) {
                if (job.getId() == jobId) {
                    return job;
                }
            }
        }
        return null;
    }

    private Uri[] getUris(JobInfo.TriggerContentUri[] triggerContentUris) {
        if (triggerContentUris == null) {
            return null;
        } else {
            Uri[] uris = new Uri[triggerContentUris.length];
            for (int i = 0; i < uris.length; i++) {
                uris[i] = triggerContentUris[i].getUri();
            }
            return uris;
        }
    }

    private Uri[] getUris(android.app.job.JobInfo.TriggerContentUri[] triggerContentUris) {
        if (triggerContentUris == null) {
            return null;
        } else {
            Uri[] uris = new Uri[triggerContentUris.length];
            for (int i = 0; i < uris.length; i++) {
                uris[i] = triggerContentUris[i].getUri();
            }
            return uris;
        }
    }
}
