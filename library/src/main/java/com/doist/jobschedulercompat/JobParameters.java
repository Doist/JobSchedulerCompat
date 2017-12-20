package com.doist.jobschedulercompat;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

/** @see android.app.job.JobParameters */
public class JobParameters {
    private final int jobId;
    private final PersistableBundle extras;
    private final Bundle transientExtras;
    private final boolean overrideDeadlineExpired;
    private final Uri[] triggeredContentUris;
    private final String[] triggeredContentAuthorities;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public JobParameters(int jobId, PersistableBundle extras, Bundle transientExtras, boolean overrideDeadlineExpired,
                         Uri[] triggeredContentUris, String[] triggeredContentAuthorities) {
        this.jobId = jobId;
        this.extras = extras;
        this.transientExtras = transientExtras;
        this.overrideDeadlineExpired = overrideDeadlineExpired;
        this.triggeredContentUris = triggeredContentUris;
        this.triggeredContentAuthorities = triggeredContentAuthorities;
    }

    /** @see android.app.job.JobParameters#getJobId() */
    public int getJobId() {
        return jobId;
    }

    /** @see android.app.job.JobParameters#getExtras() */
    public @NonNull PersistableBundle getExtras() {
        return extras;
    }

    /** @see android.app.job.JobParameters#getTransientExtras() */
    public @NonNull Bundle getTransientExtras() {
        return transientExtras;
    }

    /** @see android.app.job.JobParameters#isOverrideDeadlineExpired() */
    public boolean isOverrideDeadlineExpired() {
        return overrideDeadlineExpired;
    }

    /** @see android.app.job.JobParameters#getTriggeredContentUris() */
    public @Nullable Uri[] getTriggeredContentUris() {
        return triggeredContentUris;
    }

    /** @see android.app.job.JobParameters#getTriggeredContentAuthorities() */
    public @Nullable String[] getTriggeredContentAuthorities() {
        return triggeredContentAuthorities;
    }
}
