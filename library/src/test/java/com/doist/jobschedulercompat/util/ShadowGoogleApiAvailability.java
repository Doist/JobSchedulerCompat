package com.doist.jobschedulercompat.util;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import android.content.Context;

/*
 * We could technically use:
 *
 * ShadowGoogleApiAvailability shadowGoogleApiAvailability = shadowOf(GoogleApiAvailability.getInstance());
 * shadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
 *
 * But the current version of Robolectric crashes due to GoogleAuthUtil not being found.
 * We could fix this by depending on com.google.android.gms:play-services-auth, but it's not needed anywhere else.
 */
@Implements(GoogleApiAvailability.class)
public class ShadowGoogleApiAvailability {
    private static int availabilityCode = ConnectionResult.SERVICE_MISSING;

    @SuppressWarnings("unused")
    @Implementation
    public static int isGooglePlayServicesAvailable(Context context) {
        return availabilityCode;
    }

    public static void setIsGooglePlayServicesAvailable(int availabilityCode) {
        ShadowGoogleApiAvailability.availabilityCode = availabilityCode;
    }
}
