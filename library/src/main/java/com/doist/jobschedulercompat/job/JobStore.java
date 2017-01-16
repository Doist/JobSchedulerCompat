package com.doist.jobschedulercompat.job;

import com.doist.jobschedulercompat.JobInfo;
import com.doist.jobschedulercompat.PersistableBundle;
import com.doist.jobschedulercompat.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.RestrictTo;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.Xml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Same as com.android.server.job.JobStore, with minor modifications and unused code removed.
 *
 * Behavioral differences with the framework's JobStore include:
 * - Not assuming all jobs are persisted. The framework is always running but this isn't, so jobs that are not persisted
 * are removed at boot time in {@link JobGcReceiver}.
 * - Storing each job's scheduler alongside itself to allow picking up on scheduler changes and adjust accordingly.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobStore {
    private static final String LOG_TAG = "JobStore";

    public static final Object LOCK = new Object();

    private final JobSet jobSet;

    private final AtomicFile jobsFile;

    private final Executor executor =
            new ThreadPoolExecutor(0, 1, 3, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1),
                                   new ThreadPoolExecutor.DiscardOldestPolicy());

    private static JobStore instance;

    /** Used to instantiate the JobStore. */
    public static JobStore get(Context context) {
        synchronized (JobStore.class) {
            if (instance == null) {
                instance = new JobStore(context.getFilesDir());
            }
            return instance;
        }
    }

    /**
     * Construct the instance of the job store. This results in a blocking read from disk.
     */
    private JobStore(File dir) {
        dir.mkdirs();
        jobsFile = new AtomicFile(new File(dir, "jobs.xml"));

        jobSet = new JobSet();

        readJobMapFromDisk(jobSet);
    }

    public JobStatus getJob(int jobId) {
        return jobSet.get(jobId);
    }

    public List<JobStatus> getJobs() {
        return jobSet.getJobs();
    }

    public List<JobStatus> getJobsByScheduler(String scheduler) {
        return jobSet.getJobsByScheduler(scheduler);
    }

    /**
     * Add a job to the master list, persisting it if necessary. If the JobStatusCompat already exists,
     * it will be replaced.
     *
     * @param jobStatus Job to add.
     */
    public void add(JobStatus jobStatus) {
        jobSet.add(jobStatus);
        if (jobStatus.isPersisted()) {
            maybeWriteStatusToDiskAsync();
        }
    }

    boolean containsJob(JobStatus jobStatus) {
        return jobSet.contains(jobStatus);
    }

    public int size() {
        return jobSet.size();
    }

    /**
     * Remove the provided job. Will also delete the job if it was persisted.
     */
    public void remove(int jobId) {
        JobStatus jobStatus = jobSet.get(jobId);
        if (jobStatus != null) {
            jobSet.remove(jobStatus);
            if (jobStatus.isPersisted()) {
                maybeWriteStatusToDiskAsync();
            }
        }
    }

    public void clear() {
        jobSet.clear();
        maybeWriteStatusToDiskAsync();
    }

    /** Version of the db schema. */
    private static final int JOBS_FILE_VERSION = 0;
    /** Tag corresponds to constraints this job needs. */
    private static final String XML_TAG_PARAMS_CONSTRAINTS = "constraints";
    /** Tag corresponds to execution parameters. */
    private static final String XML_TAG_PERIODIC = "periodic";
    private static final String XML_TAG_ONEOFF = "one-off";
    private static final String XML_TAG_EXTRAS = "extras";

    /**
     * Every time the state changes we write all the jobs in one swath, instead of trying to
     * track incremental changes.
     */
    private void maybeWriteStatusToDiskAsync() {
        executor.execute(new WriteJobsMapToDiskRunnable());
    }

    void readJobMapFromDisk(JobSet jobSet) {
        new ReadJobMapFromDiskRunnable(jobSet).run();
    }

    /**
     * Runnable that writes {@link #jobSet} out to xml.
     */
    private class WriteJobsMapToDiskRunnable implements Runnable {
        @Override
        public void run() {
            List<JobStatus> jobs;
            synchronized (LOCK) {
                jobs = getJobs();
            }
            writeJobsMapImpl(jobs);
        }

        private void writeJobsMapImpl(List<JobStatus> jobs) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                XmlSerializer out = new XmlUtils.FastXmlSerializer();
                out.setOutput(baos, "utf-8");
                out.startDocument(null, true);
                out.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

                out.startTag(null, "job-info");
                out.attribute(null, "version", Integer.toString(JOBS_FILE_VERSION));
                for (int i = 0; i < jobs.size(); i++) {
                    JobStatus jobStatus = jobs.get(i);
                    out.startTag(null, "job");
                    addAttributesToJobTag(out, jobStatus);
                    out.attribute(null, "scheduler", jobStatus.getScheduler());
                    writeConstraintsToXml(out, jobStatus);
                    writeExecutionCriteriaToXml(out, jobStatus);
                    writePersistableBundleToXml(jobStatus.getExtras(), out);
                    out.endTag(null, "job");
                }
                out.endTag(null, "job-info");
                out.endDocument();

                // Write out to disk in one fell sweep.
                FileOutputStream fos = jobsFile.startWrite();
                fos.write(baos.toByteArray());
                jobsFile.finishWrite(fos);
            } catch (IOException e) {
                Log.w(LOG_TAG, "Error writing job data", e);
            } catch (XmlPullParserException e) {
                Log.w(LOG_TAG, "Error persisting bundle", e);
            }
        }

        /**
         * Write out a tag with data comprising the required fields and priority of this job and its client.
         */
        private void addAttributesToJobTag(XmlSerializer out, JobStatus jobStatus) throws IOException {
            out.attribute(null, "jobid", Integer.toString(jobStatus.getJobId()));
            out.attribute(null, "package", jobStatus.getService().getPackageName());
            out.attribute(null, "class", jobStatus.getService().getClassName());
            out.attribute(null, "persisted", Boolean.toString(jobStatus.isPersisted()));
        }

        private void writePersistableBundleToXml(PersistableBundle extras, XmlSerializer out)
                throws IOException, XmlPullParserException {
            out.startTag(null, XML_TAG_EXTRAS);
            XmlUtils.writeMapXml(extras.toMap(10), out);
            out.endTag(null, XML_TAG_EXTRAS);
        }

        /**
         * Write out a tag with data identifying this job's constraints. If the constraint isn't here it doesn't apply.
         */
        private void writeConstraintsToXml(XmlSerializer out, JobStatus jobStatus) throws IOException {
            out.startTag(null, XML_TAG_PARAMS_CONSTRAINTS);
            if (jobStatus.hasChargingConstraint()) {
                out.attribute(null, "charging", Boolean.toString(true));
            }
            if (jobStatus.hasIdleConstraint()) {
                out.attribute(null, "idle", Boolean.toString(true));
            }
            if (jobStatus.hasConnectivityConstraint()) {
                out.attribute(null, "connectivity", Boolean.toString(true));
            }
            if (jobStatus.hasNotRoamingConstraint()) {
                out.attribute(null, "not-roaming", Boolean.toString(true));
            }
            if (jobStatus.hasUnmeteredConstraint()) {
                out.attribute(null, "unmetered", Boolean.toString(true));
            }
            out.endTag(null, XML_TAG_PARAMS_CONSTRAINTS);
        }

        private void writeExecutionCriteriaToXml(XmlSerializer out, JobStatus jobStatus) throws IOException {
            final JobInfo job = jobStatus.getJob();
            if (jobStatus.getJob().isPeriodic()) {
                out.startTag(null, XML_TAG_PERIODIC);
                out.attribute(null, "period", Long.toString(job.getIntervalMillis()));
            } else {
                out.startTag(null, XML_TAG_ONEOFF);
            }

            if (jobStatus.hasDeadlineConstraint()) {
                // Wall clock deadline.
                final long deadlineWallclock = System.currentTimeMillis() +
                        (jobStatus.getLatestRunTimeElapsed() - SystemClock.elapsedRealtime());
                out.attribute(null, "deadline", Long.toString(deadlineWallclock));
            }
            if (jobStatus.hasTimingDelayConstraint()) {
                // Wall clock delay.
                final long delayWallclock = System.currentTimeMillis() +
                        (jobStatus.getEarliestRunTimeElapsed() - SystemClock.elapsedRealtime());
                out.attribute(null, "delay", Long.toString(delayWallclock));
            }

            // Only write out back-off policy if it differs from the default.
            // This also helps the case where the job is idle -> these aren't allowed to specify back-off.
            if (jobStatus.getJob().getInitialBackoffMillis() != JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS
                    || jobStatus.getJob().getBackoffPolicy() != JobInfo.DEFAULT_BACKOFF_POLICY) {
                out.attribute(null, "backoff-policy", Integer.toString(job.getBackoffPolicy()));
                out.attribute(null, "initial-backoff", Long.toString(job.getInitialBackoffMillis()));
            }
            if (job.isPeriodic()) {
                out.endTag(null, XML_TAG_PERIODIC);
            } else {
                out.endTag(null, XML_TAG_ONEOFF);
            }
        }
    }

    /**
     * Runnable that reads list of persisted job from xml. This is run once at start up,
     * so doesn't need to go through {@link JobStore#add(JobStatus)}.
     */
    private class ReadJobMapFromDiskRunnable implements Runnable {
        private final JobSet jobSet;

        /**
         * @param jobSet Reference to the (empty) set of JobStatusCompat objects that back the JobStore,
         *               so that after disk read we can populate it directly.
         */
        private ReadJobMapFromDiskRunnable(JobSet jobSet) {
            this.jobSet = jobSet;
        }

        @Override
        public void run() {
            try {
                List<JobStatus> jobs;
                FileInputStream fis = jobsFile.openRead();
                synchronized (LOCK) {
                    jobs = readJobMapImpl(fis);
                    if (jobs != null) {
                        for (int i = 0; i < jobs.size(); i++) {
                            this.jobSet.add(jobs.get(i));
                        }
                    }
                }
                fis.close();
            } catch (FileNotFoundException e) {
                // Could not find jobs file, probably there is nothing to load.
            } catch (XmlPullParserException e) {
                Log.w(LOG_TAG, "Error parsing bundle", e);
            } catch (IOException e) {
                Log.w(LOG_TAG, "Error reading job data");
            }
        }

        private List<JobStatus> readJobMapImpl(FileInputStream fis) throws XmlPullParserException, IOException {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "utf-8");

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next();
            }
            if (eventType == XmlPullParser.END_DOCUMENT) {
                return null;
            }

            String tagName = parser.getName();
            if ("job-info".equals(tagName)) {
                final List<JobStatus> jobs = new ArrayList<>();
                // Read in version info.
                try {
                    int version = Integer.parseInt(parser.getAttributeValue(null, "version"));
                    if (version != JOBS_FILE_VERSION) {
                        Log.w(LOG_TAG, "Invalid version number, aborting jobs file read");
                        return null;
                    }
                } catch (NumberFormatException e) {
                    Log.w(LOG_TAG, "Invalid version number, aborting jobs file read");
                    return null;
                }
                eventType = parser.next();
                do {
                    // Read each <job/>
                    if (eventType == XmlPullParser.START_TAG) {
                        tagName = parser.getName();
                        // Start reading job.
                        if ("job".equals(tagName)) {
                            JobStatus persistedJob = restoreJobFromXml(parser);
                            if (persistedJob != null) {
                                jobs.add(persistedJob);
                            } else {
                                Log.w(LOG_TAG, "Error reading job from file");
                            }
                        }
                    }
                    eventType = parser.next();
                } while (eventType != XmlPullParser.END_DOCUMENT);
                return jobs;
            }
            return null;
        }

        /**
         * @param parser Xml parser at the beginning of a "<job/>" tag. The next "parser.next()" call will take the
         *               parser into the body of the job tag.
         * @return Newly instantiated job holding all the information we just read out of the xml tag.
         */
        private JobStatus restoreJobFromXml(XmlPullParser parser) throws XmlPullParserException, IOException {
            JobInfo.Builder jobBuilder;
            String scheduler;

            // Read out job identifier attributes and priority.
            try {
                jobBuilder = buildBuilderFromXml(parser);
                scheduler = parser.getAttributeValue(null, "scheduler");
            } catch (NumberFormatException e) {
                Log.w(LOG_TAG, "Error parsing job's required fields, skipping");
                return null;
            }

            int eventType;
            // Read out constraints tag.
            do {
                eventType = parser.next();
            } while (eventType == XmlPullParser.TEXT);  // Push through to next START_TAG.

            if (!(eventType == XmlPullParser.START_TAG && XML_TAG_PARAMS_CONSTRAINTS.equals(parser.getName()))) {
                // Expecting a <constraints> start tag.
                return null;
            }
            try {
                buildConstraintsFromXml(jobBuilder, parser);
            } catch (NumberFormatException e) {
                Log.w(LOG_TAG, "Error reading constraints, skipping");
                return null;
            }
            // Consume </constraints>.
            parser.next();

            // Read out execution parameters tag.
            do {
                eventType = parser.next();
            } while (eventType == XmlPullParser.TEXT);
            if (eventType != XmlPullParser.START_TAG) {
                return null;
            }

            // Tuple of (earliest runtime, latest runtime) in elapsed realtime after disk load.
            Pair<Long, Long> runtimes;
            try {
                runtimes = buildExecutionTimesFromXml(parser);
            } catch (NumberFormatException e) {
                Log.w(LOG_TAG, "Error parsing execution time parameters, skipping");
                return null;
            }

            final long elapsedNow = SystemClock.elapsedRealtime();
            if (XML_TAG_PERIODIC.equals(parser.getName())) {
                try {
                    String val = parser.getAttributeValue(null, "period");
                    final long periodMillis = Long.parseLong(val);
                    jobBuilder.setPeriodic(periodMillis);
                } catch (NumberFormatException e) {
                    Log.w(LOG_TAG, "Error reading periodic execution criteria, skipping");
                    return null;
                }
            } else if (XML_TAG_ONEOFF.equals(parser.getName())) {
                try {
                    if (runtimes.first != JobStatus.NO_EARLIEST_RUNTIME) {
                        jobBuilder.setMinimumLatency(runtimes.first - elapsedNow);
                    }
                    if (runtimes.second != JobStatus.NO_LATEST_RUNTIME) {
                        jobBuilder.setOverrideDeadline(runtimes.second - elapsedNow);
                    }
                } catch (NumberFormatException e) {
                    Log.w(LOG_TAG, "Error reading job execution criteria, skipping");
                    return null;
                }
            } else {
                Log.w(LOG_TAG, "Invalid parameter tag, skipping - " + parser.getName());
                // Expecting a parameters start tag.
                return null;
            }
            maybeBuildBackoffPolicyFromXml(jobBuilder, parser);

            // Consume parameters end tag.
            parser.nextTag();

            // Read out extras Bundle.
            do {
                eventType = parser.next();
            } while (eventType == XmlPullParser.TEXT);
            if (!(eventType == XmlPullParser.START_TAG && XML_TAG_EXTRAS.equals(parser.getName()))) {
                // Expecting a <extras> start tag.
                return null;
            }
            // Consume extras start tag.
            parser.next();

            try {
                PersistableBundle extras = new PersistableBundle(XmlUtils.readMapXml(parser, XML_TAG_EXTRAS), 10);
                jobBuilder.setExtras(extras);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // Consume </extras>.
            parser.nextTag();

            // And now we're done
            return new JobStatus(jobBuilder.build(), scheduler, runtimes.first, runtimes.second);
        }

        private JobInfo.Builder buildBuilderFromXml(XmlPullParser parser) throws NumberFormatException {
            // Pull out required fields from <job> attributes.
            int jobId = Integer.parseInt(parser.getAttributeValue(null, "jobid"));
            String packageName = parser.getAttributeValue(null, "package");
            String className = parser.getAttributeValue(null, "class");
            ComponentName cname = new ComponentName(packageName, className);
            boolean persisted = Boolean.parseBoolean(parser.getAttributeValue(null, "persisted"));

            return new JobInfo.Builder(jobId, cname).setPersisted(persisted);
        }

        private void buildConstraintsFromXml(JobInfo.Builder jobBuilder, XmlPullParser parser) {
            String val = parser.getAttributeValue(null, "charging");
            if (val != null) {
                jobBuilder.setRequiresCharging(true);
            }
            val = parser.getAttributeValue(null, "idle");
            if (val != null) {
                jobBuilder.setRequiresDeviceIdle(true);
            }
            val = parser.getAttributeValue(null, "connectivity");
            if (val != null) {
                jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            }
            val = parser.getAttributeValue(null, "not-roaming");
            if (val != null) {
                jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING);
            }
            val = parser.getAttributeValue(null, "unmetered");
            if (val != null) {
                jobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
            }
        }

        /**
         * Builds the back-off policy out of the params tag. These attributes may not exist, depending
         * on whether the back-off was set when the job was first scheduled.
         */
        private void maybeBuildBackoffPolicyFromXml(JobInfo.Builder jobBuilder, XmlPullParser parser) {
            String val = parser.getAttributeValue(null, "initial-backoff");
            if (val != null) {
                long initialBackoff = Long.parseLong(val);
                val = parser.getAttributeValue(null, "backoff-policy");
                int backoffPolicy = Integer.parseInt(val);  // Will throw NFE which we catch higher up.
                jobBuilder.setBackoffCriteria(initialBackoff, backoffPolicy);
            }
        }

        /**
         * Convenience function to read out and convert deadline and delay from xml into elapsed real
         * time.
         *
         * @return A {@link android.util.Pair}, where the first value is the earliest elapsed runtime
         * and the second is the latest elapsed runtime.
         */
        private Pair<Long, Long> buildExecutionTimesFromXml(XmlPullParser parser) throws NumberFormatException {
            // Pull out execution time data.
            final long nowWallclock = System.currentTimeMillis();
            final long nowElapsed = SystemClock.elapsedRealtime();

            long earliestRunTimeElapsed = JobStatus.NO_EARLIEST_RUNTIME;
            long latestRunTimeElapsed = JobStatus.NO_LATEST_RUNTIME;
            String val = parser.getAttributeValue(null, "deadline");
            if (val != null) {
                long latestRuntimeWallclock = Long.parseLong(val);
                long maxDelayElapsed = Math.max(latestRuntimeWallclock - nowWallclock, 0);
                latestRunTimeElapsed = nowElapsed + maxDelayElapsed;
            }
            val = parser.getAttributeValue(null, "delay");
            if (val != null) {
                long earliestRuntimeWallclock = Long.parseLong(val);
                long minDelayElapsed = Math.max(earliestRuntimeWallclock - nowWallclock, 0);
                earliestRunTimeElapsed = nowElapsed + minDelayElapsed;

            }
            return Pair.create(earliestRunTimeElapsed, latestRunTimeElapsed);
        }
    }

    static class JobSet {
        SparseArray<JobStatus> mJobs;

        JobSet() {
            mJobs = new SparseArray<>();
        }

        List<JobStatus> getJobs() {
            List<JobStatus> jobs = new ArrayList<>(size());
            for (int i = 0; i < size(); i++) {
                jobs.add(mJobs.valueAt(i));
            }
            return jobs;
        }

        List<JobStatus> getJobsByScheduler(String scheduler) {
            List<JobStatus> jobs = new ArrayList<>(size());
            for (int i = 0; i < size(); i++) {
                JobStatus jobStatus = mJobs.valueAt(i);
                if (scheduler != null && scheduler.equals(jobStatus.getScheduler())) {
                    jobs.add(jobStatus);
                }
            }
            return jobs;
        }

        JobStatus get(int jobId) {
            return mJobs.get(jobId);
        }

        void add(JobStatus job) {
            mJobs.put(job.getJobId(), job);
        }

        boolean contains(JobStatus job) {
            return get(job.getJobId()) != null;
        }

        int size() {
            return mJobs.size();
        }

        void remove(JobStatus job) {
            mJobs.remove(job.getJobId());
        }

        void clear() {
            mJobs.clear();
        }
    }

    /**
     * Same as android.support.v4.util.AtomicFile, with minor modifications and unused code removed.
     *
     * Inlined here to avoid pulling the support library in.
     */
    private static class AtomicFile {
        private static final String LOG_TAG = "AtomicFile";

        private final File mBaseName;
        private final File mBackupName;

        /**
         * Create a new AtomicFile for a file located at the given File path.
         * The secondary backup file will be the same file path with ".bak" appended.
         */
        private AtomicFile(File baseName) {
            mBaseName = baseName;
            mBackupName = new File(baseName.getPath() + ".bak");
        }

        /**
         * Start a new write operation on the file. This returns a {@link FileOutputStream} to which you can write
         * the new file data. The existing file is replaced with the new data. You <em>must not</em> directly close
         * the given {@link FileOutputStream}; instead call {@link #finishWrite(FileOutputStream)}.
         *
         * <p>Note that if another thread is currently performing a write, this will simply replace whatever that thread
         * is writing with the new file being written by this thread, and when the other thread finishes the write the
         * new write operation will no longer be safe (or will be lost). You must do your own threading protection for
         * access to {@link AtomicFile}.
         */
        private FileOutputStream startWrite() throws IOException {
            // Rename the current file so it may be used as a backup during the next read
            if (mBaseName.exists()) {
                if (!mBackupName.exists()) {
                    if (!mBaseName.renameTo(mBackupName)) {
                        Log.w(LOG_TAG, "Couldn't rename file " + mBaseName + " to backup file " + mBackupName);
                    }
                } else {
                    mBaseName.delete();
                }
            }
            FileOutputStream str;
            try {
                str = new FileOutputStream(mBaseName);
            } catch (FileNotFoundException e) {
                File parent = mBaseName.getParentFile();
                if (!parent.mkdirs()) {
                    throw new IOException("Couldn't create directory " + mBaseName);
                }
                try {
                    str = new FileOutputStream(mBaseName);
                } catch (FileNotFoundException e2) {
                    throw new IOException("Couldn't create " + mBaseName);
                }
            }
            return str;
        }

        /**
         * Call when you have successfully finished writing to the stream returned by {@link #startWrite()}.
         * This will close, sync, and commit the new data. The next attempt to read the atomic file will return the
         * new file stream.
         */
        private void finishWrite(FileOutputStream str) {
            if (str != null) {
                sync(str);
                try {
                    str.close();
                    mBackupName.delete();
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Couldn't finish write", e);
                }
            }
        }

        /**
         * Open the atomic file for reading. If there previously was an incomplete write, this will roll back to the
         * last good data before opening for read. You should call {@link FileInputStream#close()} when you are done
         * reading from it.
         *
         * <p>Note that if another thread is currently performing a write, this will incorrectly consider it to be in
         * the state of a bad write and roll back, causing the new data currently being written to be dropped.
         * You must do your own threading protection for access to {@link AtomicFile}.
         */
        private FileInputStream openRead() throws FileNotFoundException {
            if (mBackupName.exists()) {
                mBaseName.delete();
                mBackupName.renameTo(mBaseName);
            }
            return new FileInputStream(mBaseName);
        }

        static boolean sync(FileOutputStream stream) {
            try {
                if (stream != null) {
                    stream.getFD().sync();
                }
                return true;
            } catch (IOException e) {
                // Do nothing.
            }
            return false;
        }
    }
}
