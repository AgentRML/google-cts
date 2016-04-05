/*
 * Copyright (C) 2015 Google Inc.
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

package android.location.cts;

import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GpsStatus;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test for {@link GnssMeasurement} without a location fix.
 *
 * Test steps:
 * 1. Clear A-GPS: this ensures that the device is not in a warm mode and it has 4+ satellites
 *    acquired already.
 * 2. Register a listener for:
 *      - {@link GnssMeasurementsEvent}s,
 *      - location updates and
 *      - {@link GpsStatus} events.
 * 3. Wait for {@link TestGpsStatusListener#TIMEOUT_IN_SEC}.
 * 4. Check {@link GnssMeasurementsEvent} status: if the status is not
 *    {@link GnssMeasurementsEvent#STATUS_READY}, the test will be skipped because one of the
 *    following reasons:
 *          4.1 the device does not support the feature,
 *          4.2 GPS is disabled in the device,
 *          4.3 Location is disabled in the device.
 * 4. Check whether the device is deep indoor. This is done by performing the following steps:
 *          4.1 If no {@link GpsStatus} is received this will mean that the device is located
 *              indoor. The test will be skipped.
 * 5. When the device is not indoor, verify that we receive {@link GnssMeasurementsEvent}s before
 *    a GPS location is calculated, and reported by GPS HAL. If {@link GnssMeasurementsEvent}s are
 *    only received after a location update is received:
 *          4.1.1 The test will pass with a warning for the M release.
 *          4.1.2 The test might fail in a future Android release, when this requirement becomes
 *                mandatory.
 5. If {@link GnssMeasurementsEvent}s are received: verify all mandatory fields, the test will fail
    if any of the mandatory fields is not populated or in the expected range.
 */
public class GnssMeasurementWhenNoLocationTest extends AndroidTestCase {

    private static final String TAG = "GnssMeasWhenNoFixTest";
    private TestGnssMeasurementListener mMeasurementListener;
    private TestLocationManager mTestLocationManager;
    private TestGpsStatusListener mGpsStatusListener;
    private TestLocationListener mLocationListener;
    private static final int EVENTS_COUNT = 5;
    private static final int GPS_RAW_EVENTS_COUNT = 1;
    // Command to delete cached A-GPS data to get a truer GPS fix.
    private static final String AGPS_DELETE_COMMAND = "delete_aiding_data";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTestLocationManager = new TestLocationManager(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        // Unregister listeners
        if (mLocationListener != null) {
            mTestLocationManager.removeLocationUpdates(mLocationListener);
        }
        if (mMeasurementListener != null) {
            mTestLocationManager.unregisterGnssMeasurementCallback(mMeasurementListener);
        }
        if (mGpsStatusListener != null) {
            mTestLocationManager.removeGpsStatusListener(mGpsStatusListener);
        }
        super.tearDown();
    }

    /**
     * Test GPS measurements without a location fix.
     */
    public void testGnssMeasurementWhenNoLocation() throws Exception {
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager)) {
            return;
        }

        // Clear A-GPS and skip the test if the operation fails.
        if (!mTestLocationManager.sendExtraCommand(AGPS_DELETE_COMMAND)) {
            Log.i(TAG, "A-GPS failed to clear. Skip test.");
            return;
        }

        // Register for GPS measurements.
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_RAW_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener);

        // Register for Gps Status updates.
        mGpsStatusListener = new TestGpsStatusListener(EVENTS_COUNT, mTestLocationManager);
        mTestLocationManager.addGpsStatusListener(mGpsStatusListener);

        // Register for location updates.
        mLocationListener = new TestLocationListener(EVENTS_COUNT);
        mTestLocationManager.requestLocationUpdates(mLocationListener);

        // Wait for Gps Status updates.
        mGpsStatusListener.await();
        if (!mMeasurementListener.verifyState()) {
            return;
        }
        if (!mGpsStatusListener.isGpsStatusReceived()) {
            Log.i(TAG, "No Satellites are visible. Device may be indoors. Skip test.");
            return;
        }

        List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
        Log.i(TAG, "Number of GPS measurement events received = " + events.size());

        if (events.isEmpty()) {
            Log.i(TAG, "No GPS measurement events received. Device may be indoors. Skip test.");
            return;
        }

        // If device is not indoor, verify that we receive GPS measurements before being able to
        // calculate the position solution and verify that mandatory fields of GnssMeasurement are
        // in expected ranges.
        GnssMeasurementsEvent firstEvent = events.get(0);
        Collection<GnssMeasurement> gpsMeasurements = firstEvent.getMeasurements();
        int satelliteCount = gpsMeasurements.size();
        int[] gpsPrns = new int[satelliteCount];
        int i = 0;
        for (GnssMeasurement measurement : gpsMeasurements) {
            gpsPrns[i] = measurement.getSvid();
            ++i;
        }

        Log.i(TAG, "Gps Measurements 1st Event PRNs=" + Arrays.toString(gpsPrns));
        SoftAssert softAssert = new SoftAssert(TAG);
        long timeInNs = firstEvent.getClock().getTimeNanos();
        softAssert.assertTrue("GPS measurement satellite count not in expected range. ",
                timeInNs, // event time in ns
                "satelliteCount > 0", // expected value
                Integer.toString(satelliteCount), // actual value
                satelliteCount > 0); // condition

        // TODO: this verification should be enforced for all types of GPS clocks
        softAssert.assertTrueAsWarning("timeInNs:",
                timeInNs, // event time in ns
                "timeInNs > 0", // expected value
                Long.toString(timeInNs), // actual value
                timeInNs > 0L); // condition

        // Verify mandatory fields of GnssMeasurement
        for (GnssMeasurement measurement : gpsMeasurements) {
            TestMeasurementUtil.assertAllGnssMeasurementMandatoryFields(measurement,
                    softAssert, timeInNs);
        }
        softAssert.assertAll();
    }
}
