package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.BuildConfig;
import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowContentResolver;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.KITKAT)
public class AlarmContentObserverServiceTest {

    private Context context;
    private JobStore jobStore;
    private ServiceController<AlarmContentObserverService> service;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
        jobStore = JobStore.get(context);
        service = Robolectric.buildService(AlarmContentObserverService.class).create();
    }

    @After
    public void teardown() {
        JobCreator.interruptJobs();
        jobStore.clear();
    }

    @Test
    public void testObserversRegistered() {
        Uri uri = Uri.parse("doist.com");

        ShadowContentResolver contentResolver = shadowOf(context.getContentResolver());
        assertEquals(0, contentResolver.getContentObservers(uri).size());

        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(context, 0)
                          .addTriggerContentUri(new JobInfo.TriggerContentUri(uri, 0))
                          .build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertEquals(1, contentResolver.getContentObservers(uri).size());
        assertThat(contentResolver.getContentObservers(uri), hasItem(isA(AlarmContentObserverService.Observer.class)));
    }

    @Test
    public void testObserversFire() {
        Uri[] uris = new Uri[]{Uri.parse("doist.com"), Uri.parse("todoist.com"), Uri.parse("twistapp.com")};

        for (int i = 0; i < uris.length; i++) {
            jobStore.add(JobStatus.createFromJobInfo(
                    JobCreator.create(context, i, 5000)
                              .addTriggerContentUri(new JobInfo.TriggerContentUri(uris[i], 0))
                              .build(),
                    AlarmScheduler.TAG));
        }
        service.startCommand(0, 0);

        ShadowApplication application = ShadowApplication.getInstance();
        ShadowContentResolver contentResolver = shadowOf(context.getContentResolver());
        for (Uri uri : uris) {
            assertEquals(0, application.getBoundServiceConnections().size());
            assertEquals(1, contentResolver.getContentObservers(uri).size());
            contentResolver.notifyChange(uri, null);
            DeviceTestUtils.advanceTime(JobStatus.DEFAULT_TRIGGER_MAX_DELAY);
            assertEquals(1, contentResolver.getContentObservers(uri).size());
            assertEquals(AlarmJobService.class.getCanonicalName(),
                         application.getNextStartedService().getComponent().getClassName());
        }
    }
}
