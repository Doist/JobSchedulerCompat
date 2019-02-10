
JobSchedulerCompat
==================

JobSchedulerCompat is a backport of [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html) for API 16 and above.

Behind the scenes, it relies on [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html), [GcmNetworkManager](https://developers.google.com/android/reference/com/google/android/gms/gcm/GcmNetworkManager) and [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager.html).




Usage
-----

The API follows [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html)'s very closely:

```java
PersistableBundle extras = new PersistableBundle();
extras.putString("key", "value");

JobInfo.Builder builder =
    new JobInfo.Builder(0, new ComponentName(context, MyJobService.class))
        .setMinimumLatency(TimeUnit.MINUTES.toMillis(15))
        .setOverrideDeadline(TimeUnit.HOURS.toMillis(2))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setExtras(extras);

JobScheduler.get(context).schedule(builder.build());
```



This is how `MyJobService` could look like:

```java
public class MyJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        // Spawn a thread to execute your logic.
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Stop executing this job.
        return false;
    }
}
```



As all regular services, it needs to be declared in your `AndroidManifest.xml`:

```xml
<service android:name=".MyJobService" />
```



All necessary components have compatibility variants and follow their counterparts' APIs, with a few limitations:

| Component           | Modeled after                                                | Limitations                                                  |
| ------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `JobScheduler`      | [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html) | [`JobScheduler#enqueue(JobInfo, JobWorkItem)`](https://developer.android.com/reference/android/app/job/JobScheduler.html#enqueue(android.app.job.JobInfo,%20android.app.job.JobWorkItem)) and related APIs are unavailable. |
| `JobInfo`           | [`JobInfo`](https://developer.android.com/reference/android/app/job/JobInfo.html) | [`JobInfo.Builder#setClipData(ClipData, int)`](https://developer.android.com/reference/android/app/job/JobInfo.Builder.html#setClipData(android.content.ClipData,%20int)) and related APIs are only available on O+.<br/>[`JobInfo.Builder#setRequiredNetwork`](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setRequiredNetwork(android.net.NetworkRequest)) and related APIs are only available on P+. |
| `JobService`        | [`JobService`](https://developer.android.com/reference/android/app/job/JobService.html) | None.                                                        |
| `JobParameters`     | [`JobParameters`](https://developer.android.com/reference/android/app/job/JobParameters.html) | [`JobParameters#getClipData()`](https://developer.android.com/reference/android/app/job/JobParameters.html#getClipData()) and related APIs are only available on O+.<br>[`JobParameters#getNetwork()`](https://developer.android.com/reference/android/app/job/JobParameters.html#getNetwork()) and related APIs are only available on P+. |
| `PersistableBundle` | [`PersistableBundle`](https://developer.android.com/reference/android/os/PersistableBundle.html) | None.                                                        |



## Schedulers

For each job, the best possible scheduler is used:

- [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html) (API 21 and above)
- [GcmNetworkManager](https://developers.google.com/android/reference/com/google/android/gms/gcm/GcmNetworkManager) (API 20 and below, if Google Play Services is available)
- [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager.html) (API 20 and below, if Google Play Services is unavailable)

Whenever a job relies on unsupported APIs, JobSchedulerCompat falls back to the next best scheduler. For example, if your job relies on [`JobInfo.TriggerContentUri`](https://developer.android.com/reference/android/app/job/JobInfo.TriggerContentUri.html) while running on API 21 (where this workflow didn't exist), [GcmNetworkManager](https://developers.google.com/android/reference/com/google/android/gms/gcm/GcmNetworkManager) will be used instead of [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html) for that particular job.



Why
---

We wanted a library that offered the core functionality of [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html) all the way back to API 16. We wanted it to handle its context gracefully using the best engine available. We wanted it to not have a hard dependency on Google Play Services. We wanted its API to follow [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html)'s API closely, so that it can be easily swapped by changing a few import statements.

We looked at the status quo:

| Library                                  | Minimum SDK  | Requires Google Play Services | Uses best job scheduling engine for context | Same API as JobScheduler |
| ---------------------------------------- | ------------ | ----------------------------- | ---------------------------------------- | ------------------------ |
| Framework's [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html) | 21, 24, 26 or 28 | No                           | Yes                                     | Yes                     |
| [Firebase JobDispatcher](https://github.com/firebase/firebase-jobdispatcher-android) | 14          | Yes                          | No                                      | No                      |
| Evernote's [Android-Job](https://github.com/evernote/android-job) | 14           | No                           | Yes                                     | No                      |



While all these libraries are phenomenal, neither met all our requirements, so we built one. Its minimum SDK is 16, it doesn't require Google Play Services, it uses the best job scheduling engine depending on the context, and its API mimics [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html)'s.




License
-------

```
Copyright 2019 Doist

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
