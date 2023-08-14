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

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;

public class PlayerListener implements Player.Listener {

  public static final Object LISTENER_LOCK = new Object();

  public static boolean mPlaybackEnded;
  private long mExpectedTotalTime;
  private final MainActivity mActivity;

  public PlayerListener(MainActivity activity) {
    this.mActivity = activity;
    mExpectedTotalTime = 0;
  }

  public long getExpectedTotalTime() {
    return mExpectedTotalTime;
  }

  public void onEvents(Player player, Events events) {
    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      if (player.getPlaybackState() == Player.STATE_READY) {
        mExpectedTotalTime += player.getDuration();
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
      // Add duration on media transition.
      long duration = player.getDuration();
      if (duration != C.TIME_UNSET) {
        mExpectedTotalTime += duration;
      }
    }
  }
}
