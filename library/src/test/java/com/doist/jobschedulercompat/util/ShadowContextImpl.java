package com.doist.jobschedulercompat.util;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowInstrumentation;

import android.content.Intent;

import java.lang.reflect.Field;
import java.util.Map;

import static org.robolectric.Shadows.shadowOf;

/**
 * ShadowContextImpl doesn't handle {@link android.app.Application#removeStickyBroadcast(Intent)}.
 * This shims a simple implementation that uses reflection to update Robolectric's instrumentation map.
 */
@Implements(className = org.robolectric.shadows.ShadowContextImpl.CLASS_NAME)
public class ShadowContextImpl extends org.robolectric.shadows.ShadowContextImpl {
    @Implementation
    public void removeStickyBroadcast(Intent intent) {
        try {
            ShadowInstrumentation instrumentation = shadowOf(ShadowInstrumentation.getInstrumentation());
            Field field = ShadowInstrumentation.class.getDeclaredField("stickyIntents");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Intent> stickyIntents = (Map<String, Intent>) field.get(instrumentation);
            stickyIntents.remove(intent.getAction());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
