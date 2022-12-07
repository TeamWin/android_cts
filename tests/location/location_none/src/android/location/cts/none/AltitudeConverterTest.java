/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.location.cts.none;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.location.Location;
import android.location.altitude.AltitudeConverter;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AltitudeConverterTest {

    private AltitudeConverter mAltitudeConverter;

    private Context mContext;

    @Before
    public void setUp() {
        mAltitudeConverter = new AltitudeConverter();

        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testAddMslAltitude_expectedBehavior() throws IOException {
        // Interpolates between bffff, 95555, and 00001.
        Location location = new Location("");
        location.setLatitude(-35.246789);
        location.setLongitude(-44.962683);
        location.setAltitude(-1);
        location.setVerticalAccuracyMeters(1);
        mAltitudeConverter.addMslAltitude(mContext, location);
        assertThat(location.getMslAltitudeMeters()).isWithin(1e-3).of(5.000);
        assertThat(location.getMslAltitudeAccuracyMeters()).isWithin(1e-3f).of(1.063f);

        // Again interpolates between bffff, 95555, and 00001 - no disk read.
        location = new Location("");
        location.setLatitude(-35.281923);
        location.setLongitude(-44.887958);
        location.setAltitude(-1);
        location.setVerticalAccuracyMeters(1);
        mAltitudeConverter.addMslAltitude(mContext, location);
        assertThat(location.getMslAltitudeMeters()).isWithin(1e-3).of(5.000);
        assertThat(location.getMslAltitudeAccuracyMeters()).isWithin(1e-3f).of(1.063f);

        // Interpolates between 95555, 00001, 00007, and 95553 - no vertical accuracy.
        location = new Location("");
        location.setLatitude(-34.947045);
        location.setLongitude(-44.925335);
        location.setAltitude(-1);
        location.setVerticalAccuracyMeters(-1); // Invalid vertical accuracy
        mAltitudeConverter.addMslAltitude(mContext, location);
        assertThat(location.getMslAltitudeMeters()).isWithin(1e-3).of(5.000);
        assertThat(location.hasMslAltitudeAccuracy()).isFalse();

        // Interpolates somewhere else more interesting, i.e., Hawaii.
        location = new Location("");
        location.setLatitude(19.545519);
        location.setLongitude(-155.998774);
        location.setAltitude(-1);
        location.setVerticalAccuracyMeters(1);
        mAltitudeConverter.addMslAltitude(mContext, location);
        assertThat(location.getMslAltitudeMeters()).isWithin(1e-3).of(-18.938);
        assertThat(location.getMslAltitudeAccuracyMeters()).isWithin(1e-3f).of(1.063f);
    }

    @Test
    public void testAddMslAltitude_invalidLatitudeThrows() {
        Location location = new Location("");
        location.setLongitude(-44.962683);
        location.setAltitude(-1);

        location.setLatitude(Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));

        location.setLatitude(91);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));

        location.setLatitude(-91);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));
    }

    @Test
    public void testAddMslAltitude_invalidLongitudeThrows() {
        Location location = new Location("");
        location.setLatitude(-35.246789);
        location.setAltitude(-1);

        location.setLongitude(Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));

        location.setLongitude(181);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));

        location.setLongitude(-181);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));
    }

    @Test
    public void testAddMslAltitude_invalidAltitudeThrows() {
        Location location = new Location("");
        location.setLatitude(-35.246789);
        location.setLongitude(-44.962683);

        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));

        location.setAltitude(Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));

        location.setAltitude(Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class,
                () -> mAltitudeConverter.addMslAltitude(mContext, location));
    }
}
