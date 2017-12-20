package com.doist.jobschedulercompat.util;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplication;

import android.content.Intent;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * ShadowContextImpl doesn't handle {@link android.app.Application#removeStickyBroadcast(Intent)}.
 * This shims a simple implementation that uses reflection to update super's map.
 */
@Implements(className = org.robolectric.shadows.ShadowContextImpl.CLASS_NAME)
public class ShadowContextImpl extends org.robolectric.shadows.ShadowContextImpl {
    @Implementation
    public void removeStickyBroadcast(Intent intent) {
        try {
            ShadowApplication application = ShadowApplication.getInstance();
            Field field = org.robolectric.shadows.ShadowApplication.class.getDeclaredField("stickyIntents");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Intent> stickyIntents = (Map<String, Intent>) field.get(application);
            stickyIntents.remove(intent.getAction());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
