/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.TestCase;

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.ArrayList;
import java.util.Collection;

/** Tests for {@link MeanLargerThanVerification}. */
public class MeanLargerThanVerificationTest extends TestCase {
  private static final float[] MEANS = {2.0f, 1.5f, 3.0f};

  /** Test {@link MeanLargerThanVerification#verify(TestSensorEnvironment, SensorStats)}. */
  public void testVerify() {
    float[][] values = {
        {2, 1, 2},
        {1, 1.5f, 2},
        {3, 2.5f, 3},
        {2, 1, 3},
        {2, 2.5f, 5},
    };

    float[] expected = {2.0f, 1.5f, 3.0f};
    float[] thresholds = {0.0f, 0.0f, 0.0f};
    SensorStats stats = new SensorStats();
    MeanLargerThanVerification verification = getVerification(expected, thresholds, values);
    verification.verify(stats);
    verifyStats(stats, true, MEANS);

    // Test the threshold lower than any means.
    expected = new float[] {1.5f, 1.5f, 1.5f};
    thresholds = new float[] {0.0f, 0.0f, 0.0f};
    stats = new SensorStats();
    verification = getVerification(expected, thresholds, values);
    verification.verify(stats);
    verifyStats(stats, true, MEANS);

    expected = new float[] {2.0f, 2.0f, 2.0f};
    thresholds = new float[] {0.0f, 0.0f, 0.0f};
    stats = new SensorStats();
    verification = getVerification(expected, thresholds, values);
    verification.verify(stats);
    verifyStats(stats, true, MEANS);

    expected = new float[] {2.5f, 2.0f, 3.0f};
    thresholds = new float[] {0.0f, 0.0f, 0.0f};
    stats = new SensorStats();
    verification = getVerification(expected, thresholds, values);
    verification.verify(stats);
    verifyStats(stats, true, MEANS);

    thresholds = new float[] {1.5f, 0.5f, 2.0f};
    stats = new SensorStats();
    verification = getVerification(expected, thresholds, values);
    try {
      verification.verify(stats);
      throw new Error("Expected an AssertionError");
    } catch (AssertionError e) {
      // Expected;
    }
    verifyStats(stats, false, MEANS);
  }

  private static MeanLargerThanVerification getVerification(
      float[] expected, float[] thresholds, float[]... values) {
    Collection<TestSensorEvent> events = new ArrayList<>(values.length);
    for (float[] value : values) {
      events.add(new TestSensorEvent(null, 0, 0, value));
    }
    MeanLargerThanVerification verification =
        new MeanLargerThanVerification(expected, thresholds);
    verification.addSensorEvents(events);
    return verification;
  }

  private void verifyStats(SensorStats stats, boolean passed, float[] means) {
    assertEquals(passed, stats.getValue(MeanLargerThanVerification.PASSED_KEY));
    float[] actual = (float[]) stats.getValue(SensorStats.MEAN_KEY);
    assertEquals(means.length, actual.length);
    for (int i = 0; i < means.length; i++) {
      assertEquals(means[i], actual[i], 0.1);
    }
  }
}
