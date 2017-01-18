package com.doist.jobschedulercompat;

import android.support.annotation.RestrictTo;

/** @see android.app.job.JobParameters */
public class JobParameters {
    private final int jobId;
    private final PersistableBundle extras;
    private final boolean overrideDeadlineExpired;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public JobParameters(int jobId, PersistableBundle extras, boolean overrideDeadlineExpired) {
        this.jobId = jobId;
        this.extras = extras;
        this.overrideDeadlineExpired = overrideDeadlineExpired;
    }

    /** @see android.app.job.JobParameters#getJobId() */
    public int getJobId() {
        return jobId;
    }

    /** @see android.app.job.JobParameters#getExtras() */
    public PersistableBundle getExtras() {
        return extras;
    }

    /** @see android.app.job.JobParameters#isOverrideDeadlineExpired() */
    public boolean isOverrideDeadlineExpired() {
        return overrideDeadlineExpired;
    }
}
