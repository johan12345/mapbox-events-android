package com.mapbox.android.core.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

/**
 * A location engine that uses core android.location and has no external dependencies
 * https://developer.android.com/guide/topics/location/strategies.html
 */
class AndroidLocationEngineImpl implements LocationEngineImpl<LocationListener> {
  private static final String TAG = "AndroidLocationEngine";
  final LocationManager locationManager;

  String currentProvider = LocationManager.PASSIVE_PROVIDER;

  AndroidLocationEngineImpl(@NonNull Context context) {
    locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
  }

  @NonNull
  @Override
  public LocationListener createListener(LocationEngineCallback<LocationEngineResult> callback) {
    return new AndroidLocationEngineCallbackTransport(callback);
  }

  @Override
  public void getLastLocation(@NonNull LocationEngineCallback<LocationEngineResult> callback)
    throws SecurityException {
    Location lastLocation = getLastLocationFor(currentProvider);
    if (lastLocation != null) {
      callback.onSuccess(LocationEngineResult.create(lastLocation));
      return;
    }

    for (String provider : locationManager.getAllProviders()) {
      lastLocation = getLastLocationFor(provider);
      if (lastLocation != null) {
        callback.onSuccess(LocationEngineResult.create(lastLocation));
        return;
      }
    }
    callback.onFailure(new Exception("Last location unavailable"));
  }

  Location getLastLocationFor(String provider) throws SecurityException {
    Location location = null;
    try {
      location = locationManager.getLastKnownLocation(provider);
    } catch (IllegalArgumentException iae) {
      Log.e(TAG, iae.toString());
    }
    return location;
  }

  @Override
  public void requestLocationUpdates(@NonNull LocationEngineRequest request,
                                     @NonNull LocationListener listener,
                                     @Nullable Looper looper) throws SecurityException {
    // Pick best provider only if user has not explicitly chosen passive mode
    currentProvider = getBestProvider(request.getPriority());
    ((LocationCallbackTransport) listener).setFastInterval(request.getFastestInterval());
    locationManager.requestLocationUpdates(currentProvider, request.getInterval(), request.getDisplacemnt(),
      listener, looper);
  }

  @Override
  public void requestLocationUpdates(@NonNull LocationEngineRequest request,
                                     @NonNull PendingIntent pendingIntent) throws SecurityException {
    // Pick best provider only if user has not explicitly chosen passive mode
    currentProvider = getBestProvider(request.getPriority());
    locationManager.requestLocationUpdates(currentProvider, request.getInterval(),
      request.getDisplacemnt(), pendingIntent);
  }

  @Override
  public void removeLocationUpdates(@NonNull LocationListener listener) {
    if (listener != null) {
      ((LocationCallbackTransport) listener).cleanUp();
      locationManager.removeUpdates(listener);
    }
  }

  @Override
  public void removeLocationUpdates(PendingIntent pendingIntent) {
    if (pendingIntent != null) {
      locationManager.removeUpdates(pendingIntent);
    }
  }

  private String getBestProvider(int priority) {
    String provider = null;
    // Pick best provider only if user has not explicitly chosen passive mode
    if (priority != LocationEngineRequest.PRIORITY_NO_POWER) {
      provider = locationManager.getBestProvider(getCriteria(priority), true);
    }
    return provider != null ? provider : LocationManager.PASSIVE_PROVIDER;
  }

  @VisibleForTesting
  static Criteria getCriteria(int priority) {
    Criteria criteria = new Criteria();
    criteria.setAccuracy(priorityToAccuracy(priority));
    criteria.setCostAllowed(true);
    criteria.setPowerRequirement(priorityToPowerRequirement(priority));
    return criteria;
  }

  private static int priorityToAccuracy(int priority) {
    switch (priority) {
      case LocationEngineRequest.PRIORITY_HIGH_ACCURACY:
      case LocationEngineRequest.PRIORITY_BALANCED_POWER_ACCURACY:
        return Criteria.ACCURACY_FINE;
      case LocationEngineRequest.PRIORITY_LOW_POWER:
      case LocationEngineRequest.PRIORITY_NO_POWER:
      default:
        return Criteria.ACCURACY_COARSE;
    }
  }

  private static int priorityToPowerRequirement(int priority) {
    switch (priority) {
      case LocationEngineRequest.PRIORITY_HIGH_ACCURACY:
        return Criteria.POWER_HIGH;
      case LocationEngineRequest.PRIORITY_BALANCED_POWER_ACCURACY:
        return Criteria.POWER_MEDIUM;
      case LocationEngineRequest.PRIORITY_LOW_POWER:
      case LocationEngineRequest.PRIORITY_NO_POWER:
      default:
        return Criteria.POWER_LOW;
    }
  }

  @VisibleForTesting
  static final class AndroidLocationEngineCallbackTransport extends LocationCallbackTransport {
    AndroidLocationEngineCallbackTransport(LocationEngineCallback<LocationEngineResult> callback) {
      super(callback);
    }

    @Override
    public void onProviderDisabled(String s) {
      super.onProviderDisabled(s);
      callback.onFailure(new Exception("Current provider disabled"));
    }
  }
}