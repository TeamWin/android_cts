/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.cujcommon.cts;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import java.util.List;
import java.util.concurrent.TimeoutException;


/** This class comprises of routines that are generic to all tests. */
public class CujTestBase {
  protected MainActivity mActivity;
  protected PlayerListener mListener;

  public CujTestBase() {
    ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
    scenario.onActivity(activity -> {
      this.mActivity = activity;
    });
    mListener = new PlayerListener(mActivity);
    mActivity.addPlayerListener(mListener);
  }

  public void play(List<String> mediaUrls, long timeoutMilliSeconds)
      throws TimeoutException, InterruptedException {
    long startTime = System.currentTimeMillis();
    mActivity.runOnUiThread(() -> {
      mActivity.prepareMediaItems(mediaUrls);
      mActivity.run();
    });
    long endTime = System.currentTimeMillis() + timeoutMilliSeconds;
    // Wait for playback to finish
    synchronized (PlayerListener.LISTENER_LOCK) {
      while(!PlayerListener.mPlaybackEnded) {
        PlayerListener.LISTENER_LOCK.wait(timeoutMilliSeconds);
        if (endTime < System.currentTimeMillis()) {
          throw new TimeoutException(
              "playback timed out after " + timeoutMilliSeconds + " milli seconds.");
        }
      }
      PlayerListener.mPlaybackEnded = false;
    }
    long actualTotalTime = System.currentTimeMillis() - startTime;
    long expectedTotalTime = mListener.getExpectedTotalTime();
    assertEquals((float)expectedTotalTime, (float)actualTotalTime, 30000);
  }
}
