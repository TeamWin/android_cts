/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.location.cts.common;

import android.content.Context;
import android.location.GnssMeasurementRequest;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssRequest;
import android.location.GnssStatus;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import junit.framework.Assert;

/**
 * A {@code LocationManager} wrapper that logs GNSS turn-on and turn-off.
 */
public class TestLocationManager {

    private static final String TAG = "TestLocationManager";
    private LocationManager mLocationManager;
    private Context mContext;

    public TestLocationManager(Context context) {
        mContext = context;
        mLocationManager =
                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * See {@code LocationManager#removeUpdates(LocationListener)}.
     *
     * @param listener the listener to remove
     */
    public void removeLocationUpdates(LocationListener listener) {
        Log.i(TAG, "Remove Location updates.");
        mLocationManager.removeUpdates(listener);
    }

    /**
     * See {@link android.location.LocationManager#registerGnssMeasurementsCallback
     * (GnssMeasurementsEvent.Callback callback)}
     *
     * @param callback the listener to add
     */
    public void registerGnssMeasurementCallback(GnssMeasurementsEvent.Callback callback) {
        Log.i(TAG, "Add Gnss Measurement Callback.");
        boolean measurementListenerAdded =
                mLocationManager.registerGnssMeasurementsCallback(callback);
        if (!measurementListenerAdded) {
            // Registration of GnssMeasurements listener has failed, this indicates a platform bug.
            Log.i(TAG, TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
            Assert.fail(TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
        }
    }

    /**
     * See {@link android.location.LocationManager#registerGnssMeasurementsCallback(GnssMeasurementsEvent.Callback callback)}
     *
     * @param callback the listener to add
     * @param handler the handler that the callback runs at.
     */
    public void registerGnssMeasurementCallback(GnssMeasurementsEvent.Callback callback,
            Handler handler) {
        Log.i(TAG, "Add Gnss Measurement Callback.");
        boolean measurementListenerAdded =
                mLocationManager.registerGnssMeasurementsCallback(callback, handler);
        if (!measurementListenerAdded) {
            // Registration of GnssMeasurements listener has failed, this indicates a platform bug.
            Log.i(TAG, TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
            Assert.fail(TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
        }
    }

    /**
     * See {@link android.location.LocationManager#registerGnssMeasurementsCallback
     * (GnssMeasurementsEvent.Callback callback)}
     *
     * @param callback the listener to add
     */
    public void registerGnssMeasurementCallback(GnssMeasurementsEvent.Callback callback,
            GnssRequest request) {
        Log.i(TAG, "Add Gnss Measurement Callback. enableFullTracking=" + request);
        boolean measurementListenerAdded =
                mLocationManager.registerGnssMeasurementsCallback(request, Runnable::run, callback);
        if (!measurementListenerAdded) {
            // Registration of GnssMeasurements listener has failed, this indicates a platform bug.
            Log.i(TAG, TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
            Assert.fail(TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
        }
    }

    /**
     * See {@link android.location.LocationManager#registerGnssMeasurementsCallback
     * (GnssMeasurementsEvent.Callback callback)}
     *
     * @param callback the listener to add
     */
    public void registerGnssMeasurementCallback(GnssMeasurementsEvent.Callback callback,
            GnssMeasurementRequest request) {
        Log.i(TAG, "Add Gnss Measurement Callback. enableFullTracking=" + request);
        boolean measurementListenerAdded =
                mLocationManager.registerGnssMeasurementsCallback(request, Runnable::run, callback);
        if (!measurementListenerAdded) {
            // Registration of GnssMeasurements listener has failed, this indicates a platform bug.
            Log.i(TAG, TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
            Assert.fail(TestMeasurementUtil.REGISTRATION_ERROR_MESSAGE);
        }
    }

    /**
     * Request GNSS location updates with {@code LocationRequest#setLowPowerMode()} enabled.
     *
     * See {@code LocationManager#requestLocationUpdates}.
     *
     * @param locationListener location listener for request
     */
    public void requestLowPowerModeGnssLocationUpdates(int minTimeMillis,
            LocationListener locationListener) {
        if (mLocationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
            Log.i(TAG, "Request Location updates.");
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    new LocationRequest.Builder(minTimeMillis).setLowPower(true).build(),
                    mContext.getMainExecutor(),
                    locationListener);
        }
    }

    /**
     * See {@link android.location.LocationManager#unregisterGnssMeasurementsCallback
     * (GnssMeasurementsEvent.Callback)}.
     *
     * @param callback the listener to remove
     */
    public void unregisterGnssMeasurementCallback(GnssMeasurementsEvent.Callback callback) {
        Log.i(TAG, "Remove Gnss Measurement Callback.");
        mLocationManager.unregisterGnssMeasurementsCallback(callback);
    }

    /**
     * See {@code LocationManager#requestLocationUpdates}.
     *
     * @param locationListener location listener for request
     */
    public void requestLocationUpdates(LocationListener locationListener, int minTimeMsec) {
        if (mLocationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
            Log.i(TAG, "Request Location updates.");
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    minTimeMsec,
                    0 /* minDistance */,
                    locationListener,
                    Looper.getMainLooper());
        }
    }

    /**
     * See {@code LocationManager#requestLocationUpdates}.
     *
     * @param locationListener location listener for request
     */
    public void requestLocationUpdates(LocationListener locationListener) {
        requestLocationUpdates(locationListener, 0 /* minTimeMsec */);
    }

    /**
     * See {@code LocationManager#requestNetworkLocationUpdates}.
     *
     * @param locationListener location listener for request
     * @return {@code true} if the network provider is valid, {@code false} otherwise.
     */
    public boolean requestNetworkLocationUpdates(LocationListener locationListener) {
        if (mLocationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
            Log.i(TAG, "Request Network Location updates.");
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                0 /* minTime*/,
                0 /* minDistance */,
                locationListener,
                Looper.getMainLooper());
            return true;
        }
        return false;
    }

    /**
     * See {@code LocationManager#requestLocationUpdates}.
     *
     * @param locationListener location listener for request
     */
    public void requestPassiveLocationUpdates(LocationListener locationListener, int minTimeMsec) {
        if (mLocationManager.getProvider(LocationManager.PASSIVE_PROVIDER) != null) {
            Log.i(TAG, "Request Passive Location updates.");
            mLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                    minTimeMsec,
                    0 /* minDistance */,
                    locationListener,
                    Looper.getMainLooper());
        }
    }

    /**
     * See {@link android.location.LocationManager#sendExtraCommand}.
     *
     * @param command name of the command to send to the provider.
     *
     * @return true if the command succeeds.
     */
    public boolean sendExtraCommand(String command) {
        Log.i(TAG, "Send Extra Command = " + command);
        boolean extraCommandStatus = mLocationManager.sendExtraCommand(LocationManager.GPS_PROVIDER,
                command, null);
        Log.i(TAG, "Sent extra command (" + command + ") status = " + extraCommandStatus);
        return extraCommandStatus;
    }

    /**
     * Add a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessage.Callback} object to register.
     * @return {@code true} if the listener was added successfully, {@code false} otherwise.
     */
    public boolean registerGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback) {
        Log.i(TAG, "Add Gnss Navigation Message Callback.");
        return mLocationManager.registerGnssNavigationMessageCallback(callback);
    }

    /**
     * Add a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessage.Callback} object to register.
     * @param handler the handler that the callback runs at.
     * @return {@code true} if the listener was added successfully, {@code false} otherwise.
     */
    public boolean registerGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback, Handler handler) {
        Log.i(TAG, "Add Gnss Navigation Message Callback.");
        return mLocationManager.registerGnssNavigationMessageCallback(callback, handler);
    }

    /**
     * Removes a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessage.Callback} object to remove.
     */
    public void unregisterGnssNavigationMessageCallback(GnssNavigationMessage.Callback callback) {
        Log.i(TAG, "Remove Gnss Navigation Message Callback.");
        mLocationManager.unregisterGnssNavigationMessageCallback(callback);
    }

    /**
     * Add a GNSS Status callback.
     *
     * @param callback a {@link GnssStatus.Callback} object to register.
     * @return {@code true} if the listener was added successfully, {@code false} otherwise.
     */
    public boolean registerGnssStatusCallback(GnssStatus.Callback callback) {
        Log.i(TAG, "Add Gnss Status Callback.");
        return mLocationManager.registerGnssStatusCallback(
            callback, new Handler(Looper.getMainLooper()));
    }

    /**
     * Removes a GNSS Status callback.
     *
     * @param callback a {@link GnssStatus.Callback} object to remove.
     */
    public void unregisterGnssStatusCallback(GnssStatus.Callback callback) {
        Log.i(TAG, "Remove Gnss Status Callback.");
        mLocationManager.unregisterGnssStatusCallback(callback);
    }

    /**
     * Get LocationManager
     *
     * @return locationManager
     */
    public LocationManager getLocationManager() {
        return mLocationManager;
    }
    /**
     * Get Context
     *
     * @return context
     */
    public Context getContext() {
        return mContext;
    }
}
