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

import static android.media.cujcommon.cts.CujTestBase.ORIENTATIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;

import java.time.Clock;
import java.time.Duration;
import java.util.Random;

public class PlayerListener implements Player.Listener {

  private static final String LOG_TAG = PlayerListener.class.getSimpleName();

  public static final Object LISTENER_LOCK = new Object();

  public static boolean mPlaybackEnded;
  private long mExpectedTotalTime;
  private MainActivity mActivity;
  private boolean mIsSeekTest;
  private int mNumOfSeekIteration;
  private long mSeekTimeUs;
  private long mSeed;
  private boolean mSeekDone;
  private boolean mIsOrientationTest;
  private long mSendMessagePosition;
  private boolean mOrientationChangeRequested;
  private int mStartOrientation;
  private int mCurrentOrientation;

  /**
   * Create player listener for playback test.
   */
  public static PlayerListener createListenerForPlaybackTest() {
    PlayerListener playerListener = new PlayerListener();
    playerListener.mIsSeekTest = false;
    playerListener.mNumOfSeekIteration = 0;
    playerListener.mSeekTimeUs = 0;
    playerListener.mSeed = 0;
    playerListener.mIsOrientationTest = false;
    playerListener.mSendMessagePosition = 0;
    return playerListener;
  }

  /**
   * Create player listener for seek test.
   *
   * @param numOfSeekIteration  Number of seek operations to be performed in seek test
   * @param seekTimeUs          Number of milliseconds to seek
   * @param sendMessagePosition The position at which message will be sent
   */
  public static PlayerListener createListenerForSeekTest(int numOfSeekIteration, long seekTimeUs,
      long sendMessagePosition) {
    PlayerListener playerListener = createListenerForPlaybackTest();
    playerListener.mIsSeekTest = true;
    playerListener.mNumOfSeekIteration = numOfSeekIteration;
    playerListener.mSeekTimeUs = seekTimeUs;
    playerListener.mSeed = playerListener.getSeed();
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Create player listener for orientation test.
   *
   * @param sendMessagePosition The position at which message will be send
   */
  public static PlayerListener createListenerForOrientationTest(long sendMessagePosition) {
    PlayerListener playerListener = createListenerForPlaybackTest();
    playerListener.mIsOrientationTest = true;
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Returns seed for Seek test.
   */
  private long getSeed() {
    // Truncate time to the nearest day.
    long seed = Clock.tick(Clock.systemDefaultZone(), Duration.ofDays(1)).instant().toEpochMilli();
    Log.d(LOG_TAG, "Random seed = " + seed);
    return seed;
  }

  /**
   * Returns True for Orientation test.
   */
  public boolean isOrientationTest() {
    return mIsOrientationTest;
  }

  /**
   * Sets activity for test.
   */
  public void setActivity(MainActivity activity) {
    this.mActivity = activity;
    if (isOrientationTest()) {
      mActivity.setRequestedOrientation(ORIENTATIONS[0] /* SCREEN_ORIENTATION_PORTRAIT */);
      mStartOrientation = getDeviceOrientation(mActivity);
    }
  }

  /**
   * Returns expected playback time for the playlist.
   */
  public long getExpectedTotalTime() {
    return mExpectedTotalTime;
  }

  /**
   * Get Orientation of the device.
   */
  private static int getDeviceOrientation(final Activity activity) {
    final DisplayMetrics displayMetrics = new DisplayMetrics();
    activity.getDisplay().getRealMetrics(displayMetrics);
    if (displayMetrics.widthPixels < displayMetrics.heightPixels) {
      return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    } else {
      return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }
  }

  /**
   * Seek the player.
   */
  private void seek() {
    Random random = new Random(mSeed);
    // If number of seek requested is one then seek forward or backward alternatively for
    // mSeekTimeUs on given media list.
    // If number of seek requested is 30 then seek for mSeekTimeUs- forward 10 times,
    // backward 10 times and then randomly backwards or forwards 10 times on each
    // media item.
    for (int i = 0; i < mNumOfSeekIteration; i++) {
      mActivity.mPlayer.seekTo(mActivity.mPlayer.getCurrentPosition() + mSeekTimeUs);
      if (mNumOfSeekIteration == 1 || i == 10) {
        mSeekTimeUs *= -1;
      } else if (i >= 20) {
        mSeekTimeUs *= random.nextBoolean() ? -1 : 1;
      }
    }
    mSeekDone = true;
  }

  /**
   * Change the Orientation of the device.
   */
  private void changeOrientation() {
    mCurrentOrientation = (mCurrentOrientation + 1) % ORIENTATIONS.length;
    mActivity.setRequestedOrientation(ORIENTATIONS[mCurrentOrientation]);
    mOrientationChangeRequested = true;
  }

  /**
   * Called when player states changed.
   *
   * @param player The {@link Player} whose state changed. Use the getters to obtain the latest
   *               states.
   * @param events The {@link Events} that happened in this iteration, indicating which player
   *               states changed.
   */
  public void onEvents(@NonNull Player player, Events events) {
    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
      if (player.getPlaybackState() == Player.STATE_READY) {
        // Add change in duration due to seek
        if (mSeekDone) {
          mExpectedTotalTime += (mSendMessagePosition - player.getCurrentPosition());
          mSeekDone = false;
        } else if (mOrientationChangeRequested) {
          int configuredOrientation = ORIENTATIONS[mCurrentOrientation];
          int currentDeviceOrientation = getDeviceOrientation(mActivity);
          assertEquals(configuredOrientation, currentDeviceOrientation);
          assertNotEquals(mStartOrientation, currentDeviceOrientation);
          mOrientationChangeRequested = false;
          mStartOrientation = currentDeviceOrientation;
        } else {
          // At the first media transition player is not ready. So, add duration of
          // first clip when player is ready
          mExpectedTotalTime += player.getDuration();
        }
      }
      synchronized (LISTENER_LOCK) {
        if (player.getPlaybackState() == Player.STATE_ENDED) {
          if (mPlaybackEnded) {
            throw new RuntimeException("mPlaybackEnded already set, player could be ended");
          }
          mActivity.removePlayerListener();
          mPlaybackEnded = true;
          LISTENER_LOCK.notify();
        }
      }
    }
    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
      if (mIsSeekTest || mIsOrientationTest) {
        mActivity.mPlayer.createMessage((messageType, payload) -> {
              if (mIsSeekTest) {
                seek();
              } else {
                changeOrientation();
              }
            }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
            .setDeleteAfterDelivery(true)
            .send();
      }
      // Add duration on media transition.
      long duration = player.getDuration();
      if (duration != C.TIME_UNSET) {
        mExpectedTotalTime += duration;
      }
    }
  }
}
