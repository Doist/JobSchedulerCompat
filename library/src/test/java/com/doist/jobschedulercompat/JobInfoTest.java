package com.doist.jobschedulercompat;

import com.doist.jobschedulercompat.util.NoopJobService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
public class JobInfoTest {
    private ComponentName component;

    @Before
    public void setup() {
        component = new ComponentName(ApplicationProvider.getApplicationContext(), NoopJobService.class);
    }

    @Test
    public void testMinPeriod() {
        long invalidPeriodicity = 0;
        JobInfo job = new JobInfo.Builder(0, component).setPeriodic(invalidPeriodicity).build();
        assertThat(job.getIntervalMillis(), greaterThan(invalidPeriodicity));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoServiceShouldFail() {
        new JobInfo.Builder(0, null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoConstraintsShouldFail() {
        new JobInfo.Builder(0, component).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPeriodicWithDeadlineShouldFail() {
        new JobInfo.Builder(0, component)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .setOverrideDeadline(TimeUnit.HOURS.toMillis(2))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPeriodicWithMinimumLatencyShouldFail() {
        new JobInfo.Builder(0, component)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .setMinimumLatency(TimeUnit.MINUTES.toMillis(30))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIdleWithBackoffPolicyShouldFail() {
        new JobInfo.Builder(0, component)
                .setRequiresDeviceIdle(true)
                .setBackoffCriteria(TimeUnit.MINUTES.toMillis(15), JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPeriodicWithTriggerContentUriShouldFail() {
        new JobInfo.Builder(0, component)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("com.doist"), 0))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPersistedWithTriggerContentUriShouldFail() {
        new JobInfo.Builder(0, component)
                .setPersisted(true)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("com.doist"), 0))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPersistedWithTransientExtrasShouldFail() {
        new JobInfo.Builder(0, component)
                .setPersisted(true)
                .setTransientExtras(new Bundle())
                .build();
    }
}
