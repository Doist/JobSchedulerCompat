
JobSchedulerCompat
==================

JobSchedulerCompat is a backport of [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html) for API 16 and above. Depending on the context, it will use:

* [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html) (API 21 and above)
* [GcmNetworkManager](https://developers.google.com/android/reference/com/google/android/gms/gcm/GcmNetworkManager) (API 20 and below, if Google Play Services is available)
* [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager.html) (API 20 and below, if Google Play Services is unavailable)





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

| Component           | Modeled after                            | Limitations                              |
| ------------------- | ---------------------------------------- | ---------------------------------------- |
| `JobScheduler`      | [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html) | None.                                    |
| `JobInfo`           | [`JobInfo`](https://developer.android.com/reference/android/app/job/JobInfo.html) | [`JobInfo.TriggerContentUri`](https://developer.android.com/reference/android/app/job/JobInfo.TriggerContentUri.html) and related APIs:<ul><li>Unavailable in API 21's [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html);</li><li>Unavailable in [`GcmNetworkManager`](https://developers.google.com/android/reference/com/google/android/gms/gcm/GcmNetworkManager).</li></ul><br>[`JobInfo#getFlexMillis()`](https://developer.android.com/reference/android/app/job/JobInfo.html#getFlexMillis()) and related APIs:<ul><li>Unavailable in API 21's [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html).</li></ul> |
| `JobService`        | [`JobService`](https://developer.android.com/reference/android/app/job/JobService.html) | None.                                    |
| `JobParameters`     | [`JobParameters`](https://developer.android.com/reference/android/app/job/JobParameters.html) | [`JobParameters#getTriggeredContentUris()`](https://developer.android.com/reference/android/app/job/JobParameters.html#getTriggeredContentUris()) and related APIs:<ul><li>Unavailable in API 21's [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html);</li><li>Unavailable in [`GcmNetworkManager`](https://developers.google.com/android/reference/com/google/android/gms/gcm/GcmNetworkManager).</li></ul> |
| `PersistableBundle` | [`PersistableBundle`](https://developer.android.com/reference/android/os/PersistableBundle.html) | `boolean` and `boolean[]` value types:<ul><li>Unavailable in API 21's [`PersistableBundle`](https://developer.android.com/reference/android/os/PersistableBundle.html)</li></ul> |



Why
---

We wanted a library that offered the core functionality of [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html) all the way back to API 16. We wanted it to handle its context gracefully using the best engine available. We wanted it to not have a hard dependency on Google Play Services. We wanted its API to follow [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler.html)'s API closely, so that it can be easily swapped by changing a few import statements.

We looked at the status quo:

| Library                                  | Minimum SDK | Requires Google Play Services | Uses best job scheduling engine for context | Same API as JobScheduler |
| ---------------------------------------- | ----------- | ----------------------------- | ---------------------------------------- | ------------------------ |
| Framework's [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html) | 21          | No.                           | Yes.                                     | Yes.                     |
| [Firebase JobDispatcher](https://github.com/firebase/firebase-jobdispatcher-android) | 9           | Yes.                          | No.                                      | Similar.                 |
| Evernote's [Android-Job](https://github.com/evernote/android-job) | 14          | No.                           | Yes.                                     | No.                      |



While all these libraries are phenomenal, neither met all our requirements, so we built one. Its minimum SDK is 16, it doesn't require Google Play Services, it uses the best job scheduling engine depending on the context, and its API mimics [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler.html)'s.




License
-------

```
Copyright 2016 Google, Inc.
Copyright 2017 Doist

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