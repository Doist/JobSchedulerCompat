package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.JobScheduler;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Intent;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class JobGcReceiverTest {
    private Application application;
    private JobStore jobStore;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        jobStore = JobStore.get(application);
    }

    @After
    public void teardown() {
        synchronized (JobStore.LOCK) {
            jobStore.clear();
        }
    }

    @Test
    public void testBootReceiverRegistered() {
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        List<BroadcastReceiver> receivers = shadowOf(application).getReceiversForIntent(intent);
        assertThat(receivers, hasItem(isA(JobGcReceiver.class)));
    }

    @Test
    public void testNonPersistedJobsAreCleared() {
        JobScheduler jobScheduler = JobScheduler.get(application);
        jobScheduler.schedule(
                JobCreator.create(application)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                          .setPersisted(true)
                          .build());
        jobScheduler.schedule(
                JobCreator.create(application)
                          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                          .setPersisted(false)
                          .build());
        jobScheduler.schedule(
                JobCreator.create(application)
                          .setPeriodic(TimeUnit.HOURS.toMillis(1))
                          .setPersisted(true)
                          .build());
        jobScheduler.schedule(
                JobCreator.create(application)
                          .setPeriodic(TimeUnit.HOURS.toMillis(1))
                          .setPersisted(false)
                          .build());

        assertThat(jobScheduler.getAllPendingJobs(), hasSize(4));

        application.sendBroadcast(new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertThat(jobScheduler.getAllPendingJobs(), hasSize(2));
    }
}
