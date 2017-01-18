package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.JobScheduler;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class JobGcReceiverTest {
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
    public void testBootReceiverRegistered() {
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        List<BroadcastReceiver> receivers = ShadowApplication.getInstance().getReceiversForIntent(intent);
        assertThat(receivers, hasItem(isA(JobGcReceiver.class)));
    }

    @Test
    public void testNonPersistedJobsAreCleared() {
        JobScheduler jobScheduler = JobScheduler.get(context);
        jobScheduler.schedule(
                JobCreator.create(context, 0)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .build());
        jobScheduler.schedule(
                JobCreator.create(context, 1)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(false)
                        .build());
        jobScheduler.schedule(
                JobCreator.create(context, 2)
                        .setPeriodic(TimeUnit.HOURS.toMillis(1))
                        .setPersisted(true)
                        .build());
        jobScheduler.schedule(
                JobCreator.create(context, 3)
                        .setPeriodic(TimeUnit.HOURS.toMillis(1))
                        .setPersisted(false)
                        .build());

        assertThat(jobScheduler.getAllPendingJobs(), hasSize(4));

        context.sendBroadcast(new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertThat(jobScheduler.getAllPendingJobs(), hasSize(2));
    }
}
