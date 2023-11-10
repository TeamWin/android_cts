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
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlayerListener implements Player.Listener {

  private static final String LOG_TAG = PlayerListener.class.getSimpleName();

  public static final Object LISTENER_LOCK = new Object();
  public static int CURRENT_MEDIA_INDEX = 0;

  public static boolean mPlaybackEnded;
  private long mExpectedTotalTime;
  private MainActivity mActivity;
  private ScrollTestActivity mScrollActivity;
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
  private boolean mIsAdaptivePlaybackTest;
  private boolean mResolutionChangeRequested;
  private int mCurrentResolutionWidth;
  private int mCurrentResolutionHeight;
  private int mNumOfVideoTrack;
  private int mIndexIncrement = C.INDEX_UNSET;
  private int mCurrentTrackIndex = C.INDEX_UNSET;
  private Tracks.Group mVideoTrackGroup;
  private List<Format> mVideoFormatList;
  private boolean mIsScrollTest;
  private int mNumOfScrollIteration;
  private boolean mScrollRequested;

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
    playerListener.mIsScrollTest = false;
    playerListener.mNumOfScrollIteration = 0;
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
   * Create player listener for adaptive playback test.
   *
   * @param numOfVideoTrack     Number of video tracks in the input clip
   * @param sendMessagePosition The position at which message will be send
   */
  public static PlayerListener createListenerForAdaptivePlaybackTest(int numOfVideoTrack,
      long sendMessagePosition) {
    PlayerListener playerListener = createListenerForPlaybackTest();
    playerListener.mIsAdaptivePlaybackTest = true;
    playerListener.mSendMessagePosition = sendMessagePosition;
    playerListener.mNumOfVideoTrack = numOfVideoTrack;
    playerListener.mIndexIncrement = 1;
    return playerListener;
  }

  /**
   * Create player listener for scroll test.
   *
   * @param sendMessagePosition The position at which message will be send
   */
  public static PlayerListener createListenerForScrollTest(int numOfScrollIteration,
      long sendMessagePosition) {
    PlayerListener playerListener = createListenerForPlaybackTest();
    playerListener.mIsScrollTest = true;
    playerListener.mNumOfScrollIteration = numOfScrollIteration;
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
   * Returns True for Scroll test.
   */
  public boolean isScrollTest() {
    return mIsScrollTest;
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
   * Sets activity for scroll test.
   */
  public void setScrollActivity(ScrollTestActivity activity) {
    this.mScrollActivity = activity;
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
   * Scroll the View vertically.
   *
   * @param yIndex The yIndex to scroll the view vertically.
   */
  private void scrollView(int yIndex) {
    mScrollActivity.mScrollView.scrollTo(0, yIndex);
    if (CURRENT_MEDIA_INDEX == mNumOfScrollIteration) {
      mScrollRequested = true;
    }
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
        } else if (mResolutionChangeRequested) {
          int configuredResolutionWidth = mVideoFormatList.get(mCurrentTrackIndex).width;
          int configuredResolutionHeight = mVideoFormatList.get(mCurrentTrackIndex).height;
          assertEquals(configuredResolutionWidth, mCurrentResolutionWidth);
          assertEquals(configuredResolutionHeight, mCurrentResolutionHeight);
          // Reversing the track iteration order
          if (mCurrentTrackIndex == mVideoFormatList.size() - 1) {
            mIndexIncrement *= -1;
          }
          mCurrentTrackIndex += mIndexIncrement;
          mResolutionChangeRequested = false;
        } else {
          // At the first media transition player is not ready. So, add duration of
          // first clip when player is ready
          mExpectedTotalTime += player.getDuration();
          // When player is ready, get the list of supported video Format(s) in the DASH mediaItem
          if (mIsAdaptivePlaybackTest) {
            mVideoFormatList = getVideoFormatList();
            mCurrentTrackIndex = 0;
          }
        }
      }
      synchronized (LISTENER_LOCK) {
        if (player.getPlaybackState() == Player.STATE_ENDED) {
          if (mPlaybackEnded) {
            throw new RuntimeException("mPlaybackEnded already set, player could be ended");
          }
          if (!mIsScrollTest) {
            mActivity.removePlayerListener();
          } else {
            assertTrue(mScrollRequested);
            mScrollActivity.removePlayerListener();
          }
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
      } else if (mIsAdaptivePlaybackTest) {
        // Iterating forwards and then backwards for all the available video tracks
        final int totalNumOfVideoTrackChange = mNumOfVideoTrack * 2;
        // Create messages to be executed at different positions
        for (int count = 1; count < totalNumOfVideoTrackChange; count++) {
          createAdaptivePlaybackMessage(mSendMessagePosition * (count));
        }
      }
      // In case of scroll test, send the message to scroll the view to change the surface
      // positions. Scroll has two surfaceView (top and bottom), playback start on top view and
      // after each mSendMessagePosition sec playback is switched to other view alternatively.
      if (mIsScrollTest) {
        int yIndex;
        ExoPlayer currentPlayer;
        if ((CURRENT_MEDIA_INDEX % 2) == 0) {
          currentPlayer = mScrollActivity.mFirstPlayer;
          yIndex = mScrollActivity.SURFACE_HEIGHT * 2;
        } else {
          currentPlayer = mScrollActivity.mSecondPlayer;
          yIndex = 0;
        }
        CURRENT_MEDIA_INDEX++;
        for (int i = 0; i < mNumOfScrollIteration; i++) {
          currentPlayer.createMessage((messageType, payload) -> {
                scrollView(yIndex);
              }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition * (i + 1))
              .setDeleteAfterDelivery(true)
              .send();
        }
      }
      // Add duration on media transition.
      long duration = player.getDuration();
      if (duration != C.TIME_UNSET) {
        mExpectedTotalTime += duration;
      }
    }
  }

  /**
   * Called each time when getVideoSize() changes. onEvents(Player, Player.Events) will also be
   * called to report this event along with other events that happen in the same Looper message
   * queue iteration.
   *
   * @param videoSize The new size of the video.
   */
  public void onVideoSizeChanged(VideoSize videoSize) {
    mCurrentResolutionWidth = videoSize.width;
    mCurrentResolutionHeight = videoSize.height;
  }

  /**
   * Create a message at given position to change the resolution
   *
   * @param sendMessagePosition Position at which message needs to be executed
   */
  private void createAdaptivePlaybackMessage(long sendMessagePosition) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          TrackSelectionParameters currentParameters =
              mActivity.mPlayer.getTrackSelectionParameters();
          TrackSelectionParameters newParameters = currentParameters
              .buildUpon()
              .setOverrideForType(
                  new TrackSelectionOverride(mVideoTrackGroup.getMediaTrackGroup(),
                      mCurrentTrackIndex))
              .build();
          mActivity.mPlayer.setTrackSelectionParameters(newParameters);
          mResolutionChangeRequested = true;
        }).setLooper(Looper.getMainLooper()).setPosition(sendMessagePosition)
        .setDeleteAfterDelivery(true).send();
  }

  /**
   * Fetch only video tracks group from the given clip
   *
   * @param currentTracks Current tracks in the clip
   * @return Video tracks group
   */
  private Tracks.Group getVideoTrackGroup(Tracks currentTracks) {
    Tracks.Group videoTrackGroup = null;
    for (Tracks.Group currentTrackGroup : currentTracks.getGroups()) {
      if (currentTrackGroup.getType() == C.TRACK_TYPE_VIDEO) {
        videoTrackGroup = currentTrackGroup;
        break;
      }
    }
    return videoTrackGroup;
  }

  /**
   * Get all available formats from videoTrackGroup.
   */
  private List<Format> getVideoFormatList() {
    Tracks currentTracks = mActivity.mPlayer.getCurrentTracks();
    mVideoTrackGroup = getVideoTrackGroup(currentTracks);
    List<Format> videoFormatList = new ArrayList<>();
    // Populate the videoFormatList with video tracks
    for (int trackIndex = 0; trackIndex < mVideoTrackGroup.length; trackIndex++) {
      Format trackFormat = mVideoTrackGroup.getTrackFormat(trackIndex);
      videoFormatList.add(trackFormat);
    }
    assertEquals(mNumOfVideoTrack, videoFormatList.size());
    return videoFormatList;
  }
}
