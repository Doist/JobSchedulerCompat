package com.doist.jobschedulercompat.util;

import com.doist.jobschedulercompat.job.JobStatus;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.internal.Shadow;
import org.robolectric.internal.ShadowExtractor;

import android.app.job.JobParameters;
import android.os.PersistableBundle;

@Implements(JobParameters.class)
public class ShadowJobParameters {
    private JobStatus jobStatus;
    private boolean isOverrideDeadlineExpired;

    public static JobParameters newInstance(JobStatus jobStatus, boolean isOverrideDeadlineExpired) {
        JobParameters jobParameters = Shadow.newInstanceOf(JobParameters.class);
        ShadowJobParameters shadowJobParameters = (ShadowJobParameters) ShadowExtractor.extract(jobParameters);
        shadowJobParameters.setJobStatus(jobStatus);
        shadowJobParameters.setOverrideDeadlineExpired(isOverrideDeadlineExpired);
        return jobParameters;
    }

    public ShadowJobParameters() {
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    public void setOverrideDeadlineExpired(boolean overrideDeadlineExpired) {
        isOverrideDeadlineExpired = overrideDeadlineExpired;
    }

    @Implementation
    public int getJobId() {
        return jobStatus.getJobId();
    }

    @Implementation
    public PersistableBundle getExtras() {
        return jobStatus.getExtras().toPersistableBundle();
    }

    @Implementation
    public boolean isOverrideDeadlineExpired() {
        return isOverrideDeadlineExpired;
    }
}
