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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;
import androidx.media3.common.Player.PlaybackSuppressionReason;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.platform.app.InstrumentationRegistry;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlayerListener implements Player.Listener {

  private static final String LOG_TAG = PlayerListener.class.getSimpleName();
  public static final Object LISTENER_LOCK = new Object();
  private static final String COMMAND_ENABLE = "telecom set-phone-account-enabled";
  public static int CURRENT_MEDIA_INDEX = 0;
  private static final int NUM_OF_MESSAGE_NOTIFICATIONS = 2;
  private static final int ZOOM_IN_DURATION_MS = 4000;
  private static final int PINCH_STEP_COUNT = 10;
  private static final float SPAN_GAP = 50.0f;
  private static final int SCREEN_WIDTH = Resources.getSystem().getDisplayMetrics().widthPixels;
  private static final int SCREEN_HEIGHT = Resources.getSystem().getDisplayMetrics().heightPixels;
  private static final float LEFT_MARGIN_WIDTH_FACTOR = 0.1f;
  private static final float RIGHT_MARGIN_WIDTH_FACTOR = 0.9f;
  private static final float STEP_SIZE =
      (RIGHT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH - LEFT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH
          - 2 * SPAN_GAP) / (2 * PINCH_STEP_COUNT);

  // Enum Declared for Test Type
  private enum TestType {
    PLAYBACK_TEST,
    SEEK_TEST,
    ORIENTATION_TEST,
    ADAPTIVE_PLAYBACK_TEST,
    SCROLL_TEST,
    SWITCH_AUDIO_TRACK_TEST,
    SWITCH_SUBTITLE_TRACK_TEST,
    CALL_NOTIFICATION_TEST,
    MESSAGE_NOTIFICATION_TEST,
    PINCH_TO_ZOOM_TEST
  }

  public static boolean mPlaybackEnded;
  private long mExpectedTotalTime;
  private MainActivity mActivity;
  private ScrollTestActivity mScrollActivity;
  private final TestType mTestType;
  private int mNumOfSeekIteration;
  private long mSeekTimeUs;
  private long mSeed;
  private boolean mSeekDone;
  private long mSendMessagePosition;
  private boolean mOrientationChangeRequested;
  private int mPreviousOrientation;
  private int mOrientationIndex;
  private boolean mResolutionChangeRequested;
  private int mCurrentResolutionWidth;
  private int mCurrentResolutionHeight;
  private int mNumOfVideoTrack;
  private int mIndexIncrement = C.INDEX_UNSET;
  private int mCurrentTrackIndex = C.INDEX_UNSET;
  private Tracks.Group mVideoTrackGroup;
  private List<Format> mVideoFormatList;
  private int mNumOfScrollIteration;
  private boolean mScrollRequested;
  private boolean mTrackChangeRequested;
  private List<Tracks.Group> mTrackGroups;
  private Format mStartTrackFormat;
  private Format mCurrentTrackFormat;
  private Format mConfiguredTrackFormat;
  private int mNumOfAudioTrack;
  private int mNumOfSubtitleTrack;
  private TelecomManager mTelecomManager;
  private PhoneAccountHandle mPhoneAccountHandle;
  private long mStartTime;

  public PlayerListener(TestType testType) {
    mTestType = testType;
  }

  /**
   * Create default player listener.
   */
  public static PlayerListener createDefaultListener(TestType testType) {
    PlayerListener playerListener = new PlayerListener(testType);
    playerListener.mNumOfSeekIteration = 0;
    playerListener.mSeekTimeUs = 0;
    playerListener.mSeed = 0;
    playerListener.mSendMessagePosition = 0;
    playerListener.mNumOfScrollIteration = 0;
    playerListener.mNumOfAudioTrack = 0;
    playerListener.mNumOfSubtitleTrack = 0;
    return playerListener;
  }

  /**
   * Create player listener for playback test.
   */
  public static PlayerListener createListenerForPlaybackTest() {
    PlayerListener playerListener = createDefaultListener(TestType.PLAYBACK_TEST);
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
    PlayerListener playerListener = createDefaultListener(TestType.SEEK_TEST);
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
    PlayerListener playerListener = createDefaultListener(TestType.ORIENTATION_TEST);
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
    PlayerListener playerListener = createDefaultListener(TestType.ADAPTIVE_PLAYBACK_TEST);
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
    PlayerListener playerListener = createDefaultListener(TestType.SCROLL_TEST);
    playerListener.mNumOfScrollIteration = numOfScrollIteration;
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Create player listener for Switching Audio Tracks test.
   *
   * @param numOfAudioTrack     Number of audio track in input clip
   * @param sendMessagePosition The position at which message will be send
   */
  public static PlayerListener createListenerForSwitchAudioTracksTest(int numOfAudioTrack,
      long sendMessagePosition) {
    PlayerListener playerListener = createDefaultListener(TestType.SWITCH_AUDIO_TRACK_TEST);
    playerListener.mNumOfAudioTrack = numOfAudioTrack;
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Create player listener for Switching Subtitle Tracks test.
   *
   * @param numOfSubtitleTrack  Number of subtitle tracks in input clip
   * @param sendMessagePosition The position at which message will be send
   */
  public static PlayerListener createListenerForSwitchSubtitleTracksTest(int numOfSubtitleTrack,
      long sendMessagePosition) {
    PlayerListener playerListener = createDefaultListener(TestType.SWITCH_SUBTITLE_TRACK_TEST);
    playerListener.mNumOfSubtitleTrack = numOfSubtitleTrack;
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Create player listener for Call Notification test.
   *
   * @param sendMessagePosition The position at which message will be sent
   */
  public static PlayerListener createListenerForCallNotificationTest(long sendMessagePosition) {
    PlayerListener playerListener = createDefaultListener(TestType.CALL_NOTIFICATION_TEST);
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Create player listener for Message Notification test.
   *
   * @param sendMessagePosition The position at which message will be sent
   */
  public static PlayerListener createListenerForMessageNotificationTest(long sendMessagePosition) {
    PlayerListener playerListener = createDefaultListener(TestType.MESSAGE_NOTIFICATION_TEST);
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Create player listener for Pinch To Zoom test.
   *
   * @param sendMessagePosition The position at which message will be send
   */
  public static PlayerListener createListenerForPinchToZoomTest(long sendMessagePosition) {
    PlayerListener playerListener = createDefaultListener(TestType.PINCH_TO_ZOOM_TEST);
    playerListener.mSendMessagePosition = sendMessagePosition;
    return playerListener;
  }

  /**
   * Returns seed for Seek test.
   */
  private long getSeed() {
    // Truncate time to the nearest day.
    long seed = Clock.tick(Clock.systemUTC(), Duration.ofDays(1)).instant().toEpochMilli();
    Log.d(LOG_TAG, "Random seed = " + seed);
    return seed;
  }

  /**
   * Returns True for Seek test.
   */
  public boolean isSeekTest() {
    return mTestType.equals(TestType.SEEK_TEST);
  }

  /**
   * Returns True for Orientation test.
   */
  public boolean isOrientationTest() {
    return mTestType.equals(TestType.ORIENTATION_TEST);
  }

  /**
   * Returns True for Adaptive playback test.
   */
  public boolean isAdaptivePlaybackTest() {
    return mTestType.equals(TestType.ADAPTIVE_PLAYBACK_TEST);
  }

  /**
   * Returns True for Scroll test.
   */
  public boolean isScrollTest() {
    return mTestType.equals(TestType.SCROLL_TEST);
  }

  /**
   * Returns True for Switch audio track test.
   */
  public boolean isSwitchAudioTrackTest() {
    return mTestType.equals(TestType.SWITCH_AUDIO_TRACK_TEST);
  }

  /**
   * Returns True for Switch subtitle track test.
   */
  public boolean isSwitchSubtitleTrackTest() {
    return mTestType.equals(TestType.SWITCH_SUBTITLE_TRACK_TEST);
  }

  /**
   * Returns True for Call Notification test.
   */
  public boolean isCallNotificationTest() {
    return mTestType.equals(TestType.CALL_NOTIFICATION_TEST);
  }

  /**
   * Returns True for Message Notification test.
   */
  public boolean isMessageNotificationTest() {
    return mTestType.equals(TestType.MESSAGE_NOTIFICATION_TEST);
  }

  /**
   * Returns True for Resize playback test.
   */
  public boolean isPinchToZoomTest() {
    return mTestType.equals(TestType.PINCH_TO_ZOOM_TEST);
  }

  /**
   * Sets activity for test.
   */
  public void setActivity(MainActivity activity) {
    this.mActivity = activity;
    if (isOrientationTest()) {
      mOrientationIndex = 0;
      mActivity.setRequestedOrientation(
          ORIENTATIONS[mOrientationIndex] /* SCREEN_ORIENTATION_PORTRAIT */);
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
    mPreviousOrientation = ORIENTATIONS[mOrientationIndex];
    mOrientationIndex = (mOrientationIndex + 1) % ORIENTATIONS.length;
    mActivity.setRequestedOrientation(ORIENTATIONS[mOrientationIndex]);
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
   * Check if two formats are similar.
   *
   * @param refFormat  Reference format
   * @param testFormat Test format
   * @return True, if two formats are similar, false otherwise
   */
  private boolean isFormatSimilar(Format refFormat, Format testFormat) {
    String refMediaType = refFormat.sampleMimeType;
    String testMediaType = testFormat.sampleMimeType;
    if (isSwitchAudioTrackTest()) {
      assertTrue(refMediaType.startsWith("audio/") && testMediaType.startsWith("audio/"));
      if ((refFormat.channelCount != testFormat.channelCount) || (refFormat.sampleRate
          != testFormat.sampleRate)) {
        return false;
      }
    } else if (isSwitchSubtitleTrackTest()) {
      assertTrue((refMediaType.startsWith("text/") && testMediaType.startsWith("text/")) || (
          refMediaType.startsWith("application/") && testMediaType.startsWith("application/")));
    }
    if (!refMediaType.equals(testMediaType)) {
      return false;
    }
    if (!refFormat.id.equals(testFormat.id)) {
      return false;
    }
    return true;
  }

  /**
   * Select the first subtitle track explicitly.
   */
  private void selectFirstSubtitleTrack() {
    TrackSelectionParameters currentParameters =
        mActivity.mPlayer.getTrackSelectionParameters();
    TrackSelectionParameters newParameters = currentParameters
        .buildUpon()
        .setOverrideForType(
            new TrackSelectionOverride(mTrackGroups.get(0).getMediaTrackGroup(), 0))
        .build();
    mActivity.mPlayer.setTrackSelectionParameters(newParameters);
  }

  /**
   * Create a phone account using a unique handle and return it.
   */
  private PhoneAccount getSamplePhoneAccount() {
    mPhoneAccountHandle = new PhoneAccountHandle(
        new ComponentName(mActivity, CallNotificationService.class), "SampleID");
    return PhoneAccount.builder(mPhoneAccountHandle, "SamplePhoneAccount")
        .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
        .build();
  }

  /**
   * Enable the registered phone account by running adb command.
   */
  private void enablePhoneAccount() {
    final ComponentName component = mPhoneAccountHandle.getComponentName();
    final UserManager userManager = mActivity.getSystemService(UserManager.class);
    try {
      String command =
          COMMAND_ENABLE + " " + component.getPackageName() + "/" + component.getClassName() + " "
              + mPhoneAccountHandle.getId() + " " + userManager.getSerialNumberForUser(
              Process.myUserHandle());
      InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Verify Orientation change.
   */
  private void verifyOrientationChange() {
    int configuredOrientation = ORIENTATIONS[mOrientationIndex];
    int currentDeviceOrientation = getDeviceOrientation(mActivity);
    assertEquals(configuredOrientation, currentDeviceOrientation);
    assertNotEquals(mPreviousOrientation, currentDeviceOrientation);
    mOrientationChangeRequested = false;
    mPreviousOrientation = currentDeviceOrientation;
  }

  /**
   * Return a new pointer of the display.
   *
   * @param x x coordinate of the pointer
   * @param y y coordinate of the pointer
   */
  PointerCoords getDisplayPointer(float x, float y) {
    PointerCoords pointerCoords = new PointerCoords();
    pointerCoords.x = x;
    pointerCoords.y = y;
    pointerCoords.pressure = 1;
    pointerCoords.size = 1;
    return pointerCoords;
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
          if (isAdaptivePlaybackTest()) {
            mVideoFormatList = getVideoFormatList();
            mCurrentTrackIndex = 0;
          }
          if (isSwitchAudioTrackTest() || isSwitchSubtitleTrackTest()) {
            // When player is ready, get the list of audio/subtitle track groups in the mediaItem
            mTrackGroups = getTrackGroups();
            // For a subtitle track switching test, we need to explicitly select the first
            // subtitle track
            if (isSwitchSubtitleTrackTest()) {
              selectFirstSubtitleTrack();
            }
          }
          if (isCallNotificationTest() || isMessageNotificationTest()) {
            mStartTime = System.currentTimeMillis();
            // Add the duration of the incoming call
            if (isCallNotificationTest()) {
              mExpectedTotalTime += CallNotificationService.DURATION_MS;
            }
            // Let the ExoPlayer handle audio focus internally
            mActivity.mPlayer.setAudioAttributes(mActivity.mPlayer.getAudioAttributes(), true);
            mTelecomManager = (TelecomManager) mActivity.getApplicationContext().getSystemService(
                Context.TELECOM_SERVICE);
            mTelecomManager.registerPhoneAccount(getSamplePhoneAccount());
            enablePhoneAccount();
          }
          if (isPinchToZoomTest()) {
            // Register scale gesture detector
            mActivity.mScaleGestureDetector = new ScaleGestureDetector(mActivity,
                new ScaleGestureListener(mActivity.mExoplayerView));
          }
        }
      } else if (mTrackChangeRequested && player.getPlaybackState() == Player.STATE_ENDED) {
        assertEquals(mConfiguredTrackFormat, mCurrentTrackFormat);
        assertFalse(isFormatSimilar(mStartTrackFormat, mCurrentTrackFormat));
        mTrackChangeRequested = false;
        mStartTrackFormat = mCurrentTrackFormat;
      } else if (mOrientationChangeRequested && player.getPlaybackState() == Player.STATE_ENDED) {
        verifyOrientationChange();
      }
      synchronized (LISTENER_LOCK) {
        if (player.getPlaybackState() == Player.STATE_ENDED) {
          if (mPlaybackEnded) {
            throw new RuntimeException("mPlaybackEnded already set, player could be ended");
          }
          if (!isScrollTest()) {
            mActivity.removePlayerListener();
          } else {
            assertTrue(mScrollRequested);
            mScrollActivity.removePlayerListener();
          }
          // Verify the total time taken by the notification test
          if (isCallNotificationTest() || isMessageNotificationTest()) {
            long actualTime = System.currentTimeMillis() - mStartTime;
            assertEquals((float) mExpectedTotalTime, (float) actualTime, 3000);
          }
          mPlaybackEnded = true;
          LISTENER_LOCK.notify();
        }
      }
    }
    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
      if (isSeekTest() || isOrientationTest()) {
        if (mOrientationChangeRequested) {
          verifyOrientationChange();
        }
        mActivity.mPlayer.createMessage((messageType, payload) -> {
              if (isSeekTest()) {
                seek();
              } else {
                changeOrientation();
              }
            }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
            .setDeleteAfterDelivery(true)
            .send();
      } else if (isAdaptivePlaybackTest()) {
        // Iterating forwards and then backwards for all the available video tracks
        final int totalNumOfVideoTrackChange = mNumOfVideoTrack * 2;
        // Create messages to be executed at different positions
        for (int count = 1; count < totalNumOfVideoTrackChange; count++) {
          createAdaptivePlaybackMessage(mSendMessagePosition * (count));
        }
      } else if (isSwitchAudioTrackTest() || isSwitchSubtitleTrackTest()) {
        // Create messages to be executed at different positions
        final int numOfTrackGroup =
            isSwitchAudioTrackTest() ? mNumOfAudioTrack : mNumOfSubtitleTrack;
        // First trackGroupIndex is selected at the time of playback start, so changing
        // track from second track group Index onwards.
        for (int trackGroupIndex = 1; trackGroupIndex < numOfTrackGroup; trackGroupIndex++) {
          createSwitchTrackMessage(mSendMessagePosition * trackGroupIndex, trackGroupIndex,
              0 /* TrackIndex */);
        }
      }
      // In case of scroll test, send the message to scroll the view to change the surface
      // positions. Scroll has two surfaceView (top and bottom), playback start on top view and
      // after each mSendMessagePosition sec playback is switched to other view alternatively.
      if (isScrollTest()) {
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
      } else if (isCallNotificationTest()) {
        mActivity.mPlayer.createMessage((messageType, payload) -> {
              // Place a sample incoming call
              mTelecomManager.addNewIncomingCall(mPhoneAccountHandle, null);
            }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
            .setDeleteAfterDelivery(true)
            .send();
      } else if (isMessageNotificationTest()) {
        for (int i = 0; i < NUM_OF_MESSAGE_NOTIFICATIONS; i++) {
          mActivity.mPlayer.createMessage((messageType, payload) -> {
                // Place a sample message notification
                NotificationGenerator.createNotification(mActivity);
              }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition * (i + 1))
              .setDeleteAfterDelivery(true)
              .send();
        }
      } else if (isPinchToZoomTest()) {
        mActivity.mPlayer.createMessage((messageType, payload) -> {
              // Programmatically pinch and zoom in
              pinchAndZoom(true /* zoomIn */);
            }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
            .setDeleteAfterDelivery(true)
            .send();
        mActivity.mPlayer.createMessage((messageType, payload) -> {
              // Programmatically pinch and zoom out
              pinchAndZoom(false /* zoomOut */);
            }).setLooper(Looper.getMainLooper())
            .setPosition(mSendMessagePosition + ZOOM_IN_DURATION_MS)
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

  /**
   * Create a message at given position to change the audio or the subtitle track
   *
   * @param sendMessagePosition Position at which message needs to be executed
   * @param trackGroupIndex     Index of the current track group
   * @param trackIndex          Index of the current track
   */
  private void createSwitchTrackMessage(long sendMessagePosition, int trackGroupIndex,
      int trackIndex) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          TrackSelectionParameters currentParameters =
              mActivity.mPlayer.getTrackSelectionParameters();
          TrackSelectionParameters newParameters = currentParameters
              .buildUpon()
              .setOverrideForType(
                  new TrackSelectionOverride(
                      mTrackGroups.get(trackGroupIndex).getMediaTrackGroup(),
                      trackIndex))
              .build();
          mActivity.mPlayer.setTrackSelectionParameters(newParameters);
          mConfiguredTrackFormat = mTrackGroups.get(trackGroupIndex)
              .getTrackFormat(trackIndex);
          mTrackChangeRequested = true;
        }).setLooper(Looper.getMainLooper()).setPosition(sendMessagePosition)
        .setDeleteAfterDelivery(true).send();
  }

  /**
   * Called when the value of getCurrentTracks() changes. onEvents(Player, Player.Events) will also
   * be called to report this event along with other events that happen in the same Looper message
   * queue iteration.
   *
   * @param tracks The available tracks information. Never null, but may be of length zero.
   */
  @Override
  public void onTracksChanged(Tracks tracks) {
    for (Tracks.Group currentTrackGroup : tracks.getGroups()) {
      if (currentTrackGroup.isSelected() && ((isSwitchAudioTrackTest() && (
          currentTrackGroup.getType() == C.TRACK_TYPE_AUDIO)) || (isSwitchSubtitleTrackTest() && (
          currentTrackGroup.getType() == C.TRACK_TYPE_TEXT)))) {
        for (int trackIndex = 0; trackIndex < currentTrackGroup.length; trackIndex++) {
          if (currentTrackGroup.isTrackSelected(trackIndex)) {
            if (!mTrackChangeRequested) {
              mStartTrackFormat = currentTrackGroup.getTrackFormat(trackIndex);
            } else {
              mCurrentTrackFormat = currentTrackGroup.getTrackFormat(trackIndex);
            }
          }
        }
      }
    }
  }

  /**
   * Get all audio/subtitle tracks group from the player's Tracks.
   */
  private List<Tracks.Group> getTrackGroups() {
    List<Tracks.Group> trackGroups = new ArrayList<>();
    Tracks currentTracks = mActivity.mPlayer.getCurrentTracks();
    for (Tracks.Group currentTrackGroup : currentTracks.getGroups()) {
      if ((currentTrackGroup.getType() == C.TRACK_TYPE_AUDIO) || (currentTrackGroup.getType()
          == C.TRACK_TYPE_TEXT)) {
        trackGroups.add(currentTrackGroup);
      }
    }
    return trackGroups;
  }

  /**
   * Called when the value returned from getPlaybackSuppressionReason() changes. onEvents(Player,
   * Player.Events) will also be called to report this event along with other events that happen in
   * the same Looper message queue iteration.
   *
   * @param playbackSuppressionReason The current {@link PlaybackSuppressionReason}.
   */
  @Override
  public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
    // Verify suppression reason change caused by call notification test
    if (isCallNotificationTest()) {
      if (!mActivity.mPlayer.isPlaying()) {
        assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
            playbackSuppressionReason);
      } else {
        assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, playbackSuppressionReason);
      }
    }
  }

    /**
   * Create a new MotionEvent, filling in all of the basic values that define the motion. Then,
   * dispatch a pointer event into a window owned by the instrumented application.
   *
   * @param inst              An instance of {@link Instrumentation} for sending pointer event.
   * @param action            The kind of action being performed.
   * @param pointerCount      The number of pointers that will be in this event.
   * @param pointerProperties An array of <em>pointerCount</em> values providing a
   *                          {@link PointerProperties} property object for each pointer, which must
   *                          include the pointer identifier.
   * @param pointerCoords     An array of <em>pointerCount</em> values providing a
   *                          {@link PointerCoords} coordinate object for each pointer.
   */
  void obtainAndSendPointerEvent(Instrumentation inst, int action, int pointerCount,
      PointerProperties[] pointerProperties, PointerCoords[] pointerCoords) {
    MotionEvent pointerMotionEvent = MotionEvent.obtain(SystemClock.uptimeMillis() /* downTime */,
        SystemClock.uptimeMillis() /* eventTime */, action, pointerCount, pointerProperties,
        pointerCoords, 0 /* metaState */, 0 /* buttonState */, 1 /* xPrecision */,
        1 /* yPrecision */, 0 /* deviceId */, 0 /* edgeFlags */, 0 /* source */, 0 /* flags */);
    inst.sendPointerSync(pointerMotionEvent);
  }

  /**
   * Return array of two PointerCoords.
   *
   * @param isZoomIn  True for zoom in.
   */
  PointerCoords[] getPointerCoords(boolean isZoomIn) {
    PointerCoords leftPointerStartCoords;
    PointerCoords rightPointerStartCoords;
    float midDisplayHeight = SCREEN_HEIGHT / 2.0f;
    if (isZoomIn) {
      float midDisplayWidth = SCREEN_WIDTH / 2.0f;
      // During zoom in, start pinching from middle of the display towards the end.
      leftPointerStartCoords = getDisplayPointer(midDisplayWidth - SPAN_GAP, midDisplayHeight);
      rightPointerStartCoords = getDisplayPointer(midDisplayWidth + SPAN_GAP, midDisplayHeight);
    } else {
      // During zoom out, start pinching from end of the display towards the middle.
      leftPointerStartCoords = getDisplayPointer(LEFT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH,
          midDisplayHeight);
      rightPointerStartCoords = getDisplayPointer(RIGHT_MARGIN_WIDTH_FACTOR * SCREEN_WIDTH,
          midDisplayHeight);
    }
    return new PointerCoords[]{leftPointerStartCoords, rightPointerStartCoords};
  }

  /**
   * Return array of two PointerProperties.
   */
  PointerProperties[] getPointerProperties() {
    PointerProperties defaultPointerProperties = new PointerProperties();
    defaultPointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;
    PointerProperties leftPointerProperties = new PointerProperties(defaultPointerProperties);
    leftPointerProperties.id = 0;
    PointerProperties rightPointerProperties = new PointerProperties(defaultPointerProperties);
    rightPointerProperties.id = 1;
    return new PointerProperties[]{leftPointerProperties, rightPointerProperties};
  }

  /**
   * Simulate pinch gesture to zoom in and zoom out.
   *
   * @param isZoomIn  True for zoom in.
   */
  private void pinchAndZoom(boolean isZoomIn) {
    new Thread(() -> {
      try {
        PointerCoords[] pointerCoords = getPointerCoords(isZoomIn);
        PointerProperties[] pointerProperties = getPointerProperties();

        Instrumentation inst = new Instrumentation();
        // Pinch In
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_DOWN, 1 /* pointerCount*/,
            pointerProperties, pointerCoords);
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_POINTER_DOWN + (pointerProperties[1].id
                << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2 /* pointerCount */, pointerProperties,
            pointerCoords);

        for (int i = 0; i < PINCH_STEP_COUNT; i++) {
          if (isZoomIn) {
            pointerCoords[0].x -= STEP_SIZE;
            pointerCoords[1].x += STEP_SIZE;
          } else {
            pointerCoords[0].x += STEP_SIZE;
            pointerCoords[1].x -= STEP_SIZE;
          }
          obtainAndSendPointerEvent(inst, MotionEvent.ACTION_MOVE, 2 /* pointerCount */,
              pointerProperties, pointerCoords);
        }

        // Pinch Out
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_POINTER_UP + (pointerProperties[1].id
                << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2 /* pointerCount */, pointerProperties,
            pointerCoords);
        obtainAndSendPointerEvent(inst, MotionEvent.ACTION_UP, 1 /* pointerCount */,
            pointerProperties, pointerCoords);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).start();
  }
}
