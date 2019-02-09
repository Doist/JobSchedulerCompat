package com.doist.jobschedulercompat.scheduler.alarm;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.job.JobStatus;
import com.doist.jobschedulercompat.job.JobStore;
import com.doist.jobschedulercompat.util.DeviceTestUtils;
import com.doist.jobschedulercompat.util.JobCreator;
import com.doist.jobschedulercompat.util.ShadowContextImpl;
import com.doist.jobschedulercompat.util.ShadowNetworkInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;

import java.util.concurrent.TimeUnit;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.KITKAT, shadows = {ShadowContextImpl.class, ShadowNetworkInfo.class})
public class AlarmReceiverTest {
    private Application application;
    private PackageManager packageManager;
    private JobStore jobStore;
    private ServiceController<AlarmJobService> service;

    @Before
    public void setup() {
        application = ApplicationProvider.getApplicationContext();
        packageManager = application.getPackageManager();
        jobStore = JobStore.get(application);
        service = Robolectric.buildService(AlarmJobService.class).create();
    }

    @Test
    public void testBaseReceiverLifecycle() {
        ComponentName receiver = new ComponentName(application, AlarmReceiver.class);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        JobInfo job = JobCreator.create(application).setMinimumLatency(TimeUnit.HOURS.toMillis(1)).build();
        jobStore.add(JobStatus.createFromJobInfo(job, AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertTrue(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        jobStore.remove(job.getId());
        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));
    }

    @Test
    public void testChargingReceiverLifecycle() {
        ComponentName receiver = new ComponentName(application, AlarmReceiver.BatteryReceiver.class);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setCharging(application, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application).setRequiresCharging(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertTrue(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setCharging(application, true);
        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));
    }

    @Test
    public void testStorageReceiverLifecycle() {
        ComponentName receiver = new ComponentName(application, AlarmReceiver.StorageReceiver.class);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setStorageNotLow(application, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application).setRequiresStorageNotLow(true).build(), AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertTrue(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setStorageNotLow(application, true);
        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));
    }

    @Test
    public void testConnectivityReceiverLifecycle() {
        ComponentName receiver = new ComponentName(application, AlarmReceiver.ConnectivityReceiver.class);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setNetworkInfo(application, false, false, false);
        jobStore.add(JobStatus.createFromJobInfo(
                JobCreator.create(application).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
                AlarmScheduler.TAG));
        service.startCommand(0, 0);

        assertTrue(DeviceTestUtils.isComponentEnabled(packageManager, receiver));

        DeviceTestUtils.setNetworkInfo(application, true, false, false);
        service.startCommand(0, 0);

        assertFalse(DeviceTestUtils.isComponentEnabled(packageManager, receiver));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testReceiversStartService() {
        new AlarmReceiver().onReceive(application, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertEquals(shadowOf(application).getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());

        new AlarmReceiver.BatteryReceiver().onReceive(application, new Intent(Intent.ACTION_POWER_CONNECTED));

        assertEquals(shadowOf(application).getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());

        new AlarmReceiver.StorageReceiver().onReceive(application, new Intent(Intent.ACTION_DEVICE_STORAGE_LOW));

        assertEquals(shadowOf(application).getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());

        new AlarmReceiver.ConnectivityReceiver().onReceive(
                application, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        assertEquals(shadowOf(application).getNextStartedService().getComponent().getClassName(),
                     AlarmJobService.class.getName());
    }
}
