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

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;

import java.util.Random;

public class PlayerListener implements Player.Listener {

  public static final Object LISTENER_LOCK = new Object();
  private static final long SEED = 0x12b9b0a1;

  public static boolean mPlaybackEnded;
  private long mExpectedTotalTime;
  private final MainActivity mActivity;
  private boolean mIsSeekTest;
  private int mNumOfSeekIteration;
  private long mSeekStartPosition;
  private long mSeekTimeUs;
  private boolean mSeekDone = false;

  public PlayerListener(MainActivity activity) {
    this.mActivity = activity;
    mExpectedTotalTime = 0;
  }

  /**
   * Returns expected playback time for the playlist.
   */
  public long getExpectedTotalTime() {
    return mExpectedTotalTime;
  }

  /**
   * Sets params for seek test.
   */
  public void setSeekTestParams(boolean isSeekTest, int mumOfSeekIteration, long seekStartPosition,
      long seekTimeUs) {
    this.mIsSeekTest = isSeekTest;
    this.mNumOfSeekIteration = mumOfSeekIteration;
    this.mSeekStartPosition = seekStartPosition;
    this.mSeekTimeUs = seekTimeUs;
  }

/**
 * Called when player states changed.
 *
 * @param player The {@link Player} whose state changed. Use the getters to obtain the latest
 *     states.
 * @param events The {@link Events} that happened in this iteration, indicating which player
 *     states changed.
 */
 public void onEvents(@NonNull Player player, Events events) {
    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
      if (player.getPlaybackState() == Player.STATE_READY) {
        // Add change in duration due to seek
        if (mSeekDone) {
          mExpectedTotalTime += (mSeekStartPosition - player.getCurrentPosition());
          mSeekDone = false;
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
      if (mIsSeekTest) {
        mActivity.player.createMessage((messageType, payload) -> {
              Random random = new Random(SEED);
              // If number of seek requested is one then seek forward or backward alternatively for
              // mSeekTimeUs on given media list.
              // If number of seek requested is 30 then seek for mSeekTimeUs- forward 10 times,
              // backward 10 times and then randomly backwards or forwards 10 times on each
              // media item.
              for (int i = 0; i < mNumOfSeekIteration; i++) {
                mActivity.player.seekTo(mActivity.player.getCurrentPosition() + mSeekTimeUs);
                if (mNumOfSeekIteration == 1 || i == 10) {
                  mSeekTimeUs *= -1;
                } else if (i >= 20) {
                  mSeekTimeUs *= random.nextBoolean() ? -1 : 1;
                }
              }
              mSeekDone = true;
            }).setLooper(
                Looper.getMainLooper()).setPosition(mSeekStartPosition).setDeleteAfterDelivery(true)
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
