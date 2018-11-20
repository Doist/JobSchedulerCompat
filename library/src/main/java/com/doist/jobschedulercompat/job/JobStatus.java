package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.JobInfo;

import android.content.ComponentName;
import android.net.Uri;
import android.os.SystemClock;

import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Same as com.android.server.job.controllers.JobStatus, with minor modifications and unused code removed.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobStatus {
    public static final long NO_LATEST_RUNTIME = Long.MAX_VALUE;
    public static final long NO_EARLIEST_RUNTIME = 0L;

    public static final int CONSTRAINT_CHARGING = JobInfo.CONSTRAINT_FLAG_CHARGING;
    public static final int CONSTRAINT_IDLE = JobInfo.CONSTRAINT_FLAG_DEVICE_IDLE;
    public static final int CONSTRAINT_BATTERY_NOT_LOW = JobInfo.CONSTRAINT_FLAG_BATTERY_NOT_LOW;
    public static final int CONSTRAINT_STORAGE_NOT_LOW = JobInfo.CONSTRAINT_FLAG_STORAGE_NOT_LOW;
    public static final int CONSTRAINT_TIMING_DELAY = 1 << 31;
    public static final int CONSTRAINT_DEADLINE = 1 << 30;
    public static final int CONSTRAINT_UNMETERED = 1 << 29;
    public static final int CONSTRAINT_CONNECTIVITY = 1 << 28;
    //    public static final int CONSTRAINT_APP_NOT_IDLE = 1 << 27;
    public static final int CONSTRAINT_CONTENT_TRIGGER = 1 << 26;
    //    public static final int CONSTRAINT_DEVICE_NOT_DOZING = 1 << 25;
    public static final int CONSTRAINT_NOT_ROAMING = 1 << 24;
    public static final int CONSTRAINT_METERED = 1 << 23;

    static final int CONNECTIVITY_MASK =
            CONSTRAINT_UNMETERED | CONSTRAINT_CONNECTIVITY |
                    CONSTRAINT_NOT_ROAMING | CONSTRAINT_METERED;

    /** If not specified, trigger update delay is 10 seconds. */
    public static final long DEFAULT_TRIGGER_UPDATE_DELAY = 10 * 1000;

    /** The minimum possible update delay is 1/2 second. */
    public static final long MIN_TRIGGER_UPDATE_DELAY = 500;

    /** If not specified, trigger maximum delay is 2 minutes. */
    public static final long DEFAULT_TRIGGER_MAX_DELAY = 2 * 60 * 1000;

    /** The minimum possible update delay is 1 second. */
    public static final long MIN_TRIGGER_MAX_DELAY = 1000;

    private static final int CONSTRAINTS_OF_INTEREST =
            CONSTRAINT_CHARGING | CONSTRAINT_BATTERY_NOT_LOW | CONSTRAINT_STORAGE_NOT_LOW |
                    CONSTRAINT_TIMING_DELAY |
                    CONSTRAINT_CONNECTIVITY | CONSTRAINT_UNMETERED |
                    CONSTRAINT_NOT_ROAMING | CONSTRAINT_METERED |
                    CONSTRAINT_IDLE | CONSTRAINT_CONTENT_TRIGGER;

    private final JobInfo job;
    private final String scheduler;

    /**
     * Earliest point in the future at which this job will be eligible to run. A value of 0
     * indicates there is no delay constraint. See {@link #hasTimingDelayConstraint()}.
     */
    private final long earliestRunTimeElapsedMillis;

    /**
     * Latest point in the future at which this job must be run. A value of {@link Long#MAX_VALUE}
     * indicates there is no deadline constraint. See {@link #hasDeadlineConstraint()}.
     */
    private final long latestRunTimeElapsedMillis;

    /** How many times this job has failed, used to compute back-off. */
    private final int numFailures;

    // Constraints.
    private final int requiredConstraints;
    private int satisfiedConstraints = 0;

    public Set<Uri> changedUris;
    public Set<String> changedAuthorities;

    public JobStatus(@NonNull JobInfo job, @NonNull String scheduler, int numFailures,
                     long earliestRunTimeElapsedMillis, long latestRunTimeElapsedMillis) {
        this.job = job;
        this.scheduler = scheduler;

        this.earliestRunTimeElapsedMillis = earliestRunTimeElapsedMillis;
        this.latestRunTimeElapsedMillis = latestRunTimeElapsedMillis;
        this.numFailures = numFailures;

        int requiredConstraints = job.getConstraintFlags();

        switch (job.getNetworkType()) {
            case JobInfo.NETWORK_TYPE_NONE:
                // No constraint.
                break;
            case JobInfo.NETWORK_TYPE_ANY:
                requiredConstraints |= CONSTRAINT_CONNECTIVITY;
                break;
            case JobInfo.NETWORK_TYPE_UNMETERED:
                requiredConstraints |= CONSTRAINT_UNMETERED;
                break;
            case JobInfo.NETWORK_TYPE_NOT_ROAMING:
                requiredConstraints |= CONSTRAINT_NOT_ROAMING;
                break;
            case JobInfo.NETWORK_TYPE_METERED:
                requiredConstraints |= CONSTRAINT_METERED;
                break;
        }

        if (earliestRunTimeElapsedMillis != NO_EARLIEST_RUNTIME) {
            requiredConstraints |= CONSTRAINT_TIMING_DELAY;
        }
        if (latestRunTimeElapsedMillis != NO_LATEST_RUNTIME) {
            requiredConstraints |= CONSTRAINT_DEADLINE;
        }
        if (job.getTriggerContentUris() != null) {
            requiredConstraints |= CONSTRAINT_CONTENT_TRIGGER;
        }
        this.requiredConstraints = requiredConstraints;
    }

    /**
     * Create a new JobStatus that was loaded from disk. We ignore the provided {@link JobInfo} time criteria
     * because we can load a persisted periodic job from the {@link JobStore} and still want to respect its
     * wallclock runtime rather than resetting it on every boot. We consider a freshly loaded job to no longer
     * be in back-off.
     */
    public JobStatus(@NonNull JobInfo job, @NonNull String scheduler,
                     long earliestRunTimeElapsedMillis, long latestRunTimeElapsedMillis) {
        this(job, scheduler, 0, earliestRunTimeElapsedMillis, latestRunTimeElapsedMillis);
    }

    /**
     * Create a newly scheduled job.
     */
    public static JobStatus createFromJobInfo(@NonNull JobInfo job, @NonNull String scheduler) {
        long elapsedNow = SystemClock.elapsedRealtime();
        long earliestRunTimeElapsedMillis, latestRunTimeElapsedMillis;
        if (job.isPeriodic()) {
            latestRunTimeElapsedMillis = elapsedNow + job.getIntervalMillis();
            earliestRunTimeElapsedMillis = latestRunTimeElapsedMillis - job.getFlexMillis();
        } else {
            earliestRunTimeElapsedMillis =
                    job.hasEarlyConstraint() ? elapsedNow + job.getMinLatencyMillis() : NO_EARLIEST_RUNTIME;
            latestRunTimeElapsedMillis =
                    job.hasLateConstraint() ? elapsedNow + job.getMaxExecutionDelayMillis() : NO_LATEST_RUNTIME;
        }
        return new JobStatus(job, scheduler, 0, earliestRunTimeElapsedMillis, latestRunTimeElapsedMillis);
    }

    @NonNull
    public JobInfo getJob() {
        return job;
    }

    public int getJobId() {
        return job.getId();
    }

    public int getNumFailures() {
        return numFailures;
    }

    @NonNull
    public ComponentName getServiceComponent() {
        return job.getService();
    }

    @NonNull
    public String getSchedulerTag() {
        return scheduler;
    }

    public boolean hasConnectivityConstraint() {
        return (requiredConstraints & CONNECTIVITY_MASK) != 0;
    }

    public boolean needsAnyConnectivity() {
        return (requiredConstraints & CONSTRAINT_CONNECTIVITY) != 0;
    }

    public boolean needsUnmeteredConnectivity() {
        return (requiredConstraints & CONSTRAINT_UNMETERED) != 0;
    }

    public boolean needsMeteredConnectivity() {
        return (requiredConstraints & CONSTRAINT_METERED) != 0;
    }

    public boolean needsNonRoamingConnectivity() {
        return (requiredConstraints & CONSTRAINT_NOT_ROAMING) != 0;
    }

    public boolean hasChargingConstraint() {
        return (requiredConstraints & CONSTRAINT_CHARGING) != 0;
    }

    public boolean hasBatteryNotLowConstraint() {
        return (requiredConstraints & CONSTRAINT_BATTERY_NOT_LOW) != 0;
    }

    public boolean hasPowerConstraint() {
        return (requiredConstraints & (CONSTRAINT_CHARGING | CONSTRAINT_BATTERY_NOT_LOW)) != 0;
    }

    public boolean hasStorageNotLowConstraint() {
        return (requiredConstraints & CONSTRAINT_STORAGE_NOT_LOW) != 0;
    }

    public boolean hasTimingDelayConstraint() {
        return (requiredConstraints & CONSTRAINT_TIMING_DELAY) != 0;
    }

    public boolean hasDeadlineConstraint() {
        return (requiredConstraints & CONSTRAINT_DEADLINE) != 0;
    }

    public boolean hasIdleConstraint() {
        return (requiredConstraints & CONSTRAINT_IDLE) != 0;
    }

    public boolean hasContentTriggerConstraint() {
        return (requiredConstraints & CONSTRAINT_CONTENT_TRIGGER) != 0;
    }

    public long getTriggerContentUpdateDelay() {
        long time = job.getTriggerContentUpdateDelay();
        if (time < 0) {
            return DEFAULT_TRIGGER_UPDATE_DELAY;
        }
        return Math.max(time, MIN_TRIGGER_UPDATE_DELAY);
    }

    public long getTriggerContentMaxDelay() {
        long time = job.getTriggerContentMaxDelay();
        if (time < 0) {
            return DEFAULT_TRIGGER_MAX_DELAY;
        }
        return Math.max(time, MIN_TRIGGER_MAX_DELAY);
    }

    public boolean isPeriodic() {
        return job.isPeriodic();
    }

    public boolean isPersisted() {
        return job.isPersisted();
    }

    public long getEarliestRunTimeElapsed() {
        return earliestRunTimeElapsedMillis;
    }

    public long getLatestRunTimeElapsed() {
        return latestRunTimeElapsedMillis;
    }

    public boolean setConstraintSatisfied(int constraint, boolean state) {
        boolean old = (satisfiedConstraints & constraint) != 0;
        if (old == state) {
            return false;
        }
        satisfiedConstraints = (satisfiedConstraints & ~constraint) | (state ? constraint : 0);
        return true;
    }

    boolean isConstraintSatisfied(int constraint) {
        return (satisfiedConstraints & constraint) != 0;
    }

    /**
     * @return Whether or not this JobStatus's deadline constraint is satisfied, meaning it is ready to run regardless
     * of other constraints.
     */
    public boolean isDeadlineSatisfied() {
        return !job.isPeriodic() && hasDeadlineConstraint() && (satisfiedConstraints & CONSTRAINT_DEADLINE) != 0;
    }

    /**
     * @return Whether the constraints set on this job are satisfied.
     */
    private boolean isConstraintsSatisfied() {
        final int req = requiredConstraints & CONSTRAINTS_OF_INTEREST;
        final int sat = satisfiedConstraints & CONSTRAINTS_OF_INTEREST;
        return (sat & req) == req;
    }

    /**
     * @return Whether or not this job is ready to run, based on its requirements. This is true if
     * the constraints are satisfied <strong>or</strong> the deadline on the job has expired.
     */
    public boolean isReady() {
        // Deadline constraint trumps other constraints (except for periodic jobs where deadline is an
        // implementation detail. A periodic job should only run if its constraints are satisfied).
        return isConstraintsSatisfied() || isDeadlineSatisfied();
    }
}
