package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;

import android.content.ComponentName;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

/**
 * Same as com.android.server.job.controllers.JobStatus, with minor modifications and unused code removed.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobStatus {
    public static final long NO_LATEST_RUNTIME = Long.MAX_VALUE;
    public static final long NO_EARLIEST_RUNTIME = 0L;

    public static final int CONSTRAINT_CHARGING = 1 << 0;
    public static final int CONSTRAINT_IDLE = 1 << 1;
    public static final int CONSTRAINT_TIMING_DELAY = 1 << 2;
    public static final int CONSTRAINT_DEADLINE = 1 << 3;
    public static final int CONSTRAINT_CONNECTIVITY = 1 << 4;
    public static final int CONSTRAINT_NOT_ROAMING = 1 << 5;
    public static final int CONSTRAINT_UNMETERED = 1 << 6;

    private static final int CONSTRAINTS_OF_INTEREST =
            CONSTRAINT_CHARGING | CONSTRAINT_IDLE | CONSTRAINT_TIMING_DELAY |
                    CONSTRAINT_CONNECTIVITY | CONSTRAINT_NOT_ROAMING | CONSTRAINT_UNMETERED;

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

    public JobStatus(@NonNull JobInfo job, @NonNull String scheduler, int numFailures,
                     long earliestRunTimeElapsedMillis, long latestRunTimeElapsedMillis) {
        this.job = job;
        this.scheduler = scheduler;

        this.earliestRunTimeElapsedMillis = earliestRunTimeElapsedMillis;
        this.latestRunTimeElapsedMillis = latestRunTimeElapsedMillis;
        this.numFailures = numFailures;

        int requiredConstraints = 0;
        if (job.isRequireCharging()) {
            requiredConstraints |= CONSTRAINT_CHARGING;
        }
        if (job.isRequireDeviceIdle()) {
            requiredConstraints |= CONSTRAINT_IDLE;
        }
        if (earliestRunTimeElapsedMillis != NO_EARLIEST_RUNTIME) {
            requiredConstraints |= CONSTRAINT_TIMING_DELAY;
        }
        if (latestRunTimeElapsedMillis != NO_LATEST_RUNTIME) {
            requiredConstraints |= CONSTRAINT_DEADLINE;
        }
        if (job.getNetworkType() == JobInfo.NETWORK_TYPE_ANY) {
            requiredConstraints |= CONSTRAINT_CONNECTIVITY;
        }
        if (job.getNetworkType() == JobInfo.NETWORK_TYPE_NOT_ROAMING) {
            requiredConstraints |= CONSTRAINT_NOT_ROAMING;
        }
        if (job.getNetworkType() == JobInfo.NETWORK_TYPE_UNMETERED) {
            requiredConstraints |= CONSTRAINT_UNMETERED;
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
            earliestRunTimeElapsedMillis = elapsedNow; // latestRunTimeElapsedMillis - job.getFlexMillis();
            latestRunTimeElapsedMillis = elapsedNow + job.getIntervalMillis();
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

    @NonNull
    public ComponentName getService() {
        return job.getService();
    }

    public PersistableBundle getExtras() {
        return job.getExtras();
    }

    @NonNull
    public String getScheduler() {
        return scheduler;
    }

    public int getNumFailures() {
        return numFailures;
    }

    public boolean hasChargingConstraint() {
        return (requiredConstraints & CONSTRAINT_CHARGING) != 0;
    }

    public boolean hasIdleConstraint() {
        return (requiredConstraints & CONSTRAINT_IDLE) != 0;
    }

    public boolean hasTimingDelayConstraint() {
        return (requiredConstraints & CONSTRAINT_TIMING_DELAY) != 0;
    }

    public boolean hasDeadlineConstraint() {
        return (requiredConstraints & CONSTRAINT_DEADLINE) != 0;
    }

    public boolean hasConnectivityConstraint() {
        return (requiredConstraints & CONSTRAINT_CONNECTIVITY) != 0;
    }

    public boolean hasNotRoamingConstraint() {
        return (requiredConstraints & CONSTRAINT_NOT_ROAMING) != 0;
    }

    public boolean hasUnmeteredConstraint() {
        return (requiredConstraints & CONSTRAINT_UNMETERED) != 0;
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

    /**
     * @return Whether or not this JobStatus's deadline constraint is satisfied, meaning it is ready to run regardless
     * of other constraints.
     */
    public boolean isDeadlineSatisfied() {
        return !job.isPeriodic() && hasDeadlineConstraint() && (satisfiedConstraints & CONSTRAINT_DEADLINE) != 0;
    }

    /**
     * @return Whether or not this JobStatus is ready to run, based on its requirements. This is true if
     * the constraints are satisfied <strong>or</strong> the deadline on the job has expired.
     */
    public boolean isReady() {
        // Deadline constraint trumps other constraints (except for periodic jobs where deadline
        // is an implementation detail. A periodic job should only run if its constraints are satisfied).
        return areConstraintsSatisfied() || isDeadlineSatisfied();
    }

    /**
     * @return Whether the constraints set on this JobStatus are satisfied.
     */
    private boolean areConstraintsSatisfied() {
        final int req = requiredConstraints & CONSTRAINTS_OF_INTEREST;
        final int sat = satisfiedConstraints & CONSTRAINTS_OF_INTEREST;
        return (sat & req) == req;
    }
}
