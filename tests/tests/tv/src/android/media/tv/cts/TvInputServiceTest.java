/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvRecordingClient;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.cts.TvInputServiceTest.CountingTvInputService.CountingRecordingSession;
import android.media.tv.cts.TvInputServiceTest.CountingTvInputService.CountingSession;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.internal.util.ToBooleanFunction;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Test {@link android.media.tv.TvInputService}.
 */
@RunWith(AndroidJUnit4.class)
public class TvInputServiceTest {

    @ClassRule
    public static RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    @Rule
    public ActivityScenarioRule<TvViewStubActivity> activityRule =
            new ActivityScenarioRule(TvViewStubActivity.class);

    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT = 5000L;
    private static final String DUMMT_TRACK_ID = "dummyTrackId";
    private static final TvTrackInfo DUMMY_TRACK =
            new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, DUMMT_TRACK_ID)
                    .setVideoWidth(1920).setVideoHeight(1080).setLanguage("und").build();
    private static Bundle sDummyBundle;

    private TvRecordingClient mTvRecordingClient;
    private Instrumentation mInstrumentation;
    private TvInputManager mManager;
    private TvInputInfo mStubInfo;
    private TvInputInfo mFaultyStubInfo;
    private final StubCallback mCallback = new StubCallback();
    private final StubTimeShiftPositionCallback mTimeShiftPositionCallback =
            new StubTimeShiftPositionCallback();
    private final StubRecordingCallback mRecordingCallback = new StubRecordingCallback();

    private static class StubCallback extends TvView.TvInputCallback {
        private int mChannelRetunedCount;
        private int mVideoAvailableCount;
        private int mVideoUnavailableCount;
        private int mTrackSelectedCount;
        private int mTrackChangedCount;
        private int mVideoSizeChanged;
        private int mContentAllowedCount;
        private int mContentBlockedCount;
        private int mTimeShiftStatusChangedCount;

        private Uri mChannelRetunedUri;
        private Integer mVideoUnavailableReason;
        private Integer mTrackSelectedType;
        private String mTrackSelectedTrackId;
        private List<TvTrackInfo> mTracksChangedTrackList;
        private TvContentRating mContentBlockedRating;
        private Integer mTimeShiftStatusChangedStatus;

        @Override
        public void onChannelRetuned(String inputId, Uri channelUri) {
            mChannelRetunedCount++;
            mChannelRetunedUri = channelUri;
        }

        @Override
        public void onVideoAvailable(String inputId) {
            mVideoAvailableCount++;
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            mVideoUnavailableCount++;
            mVideoUnavailableReason = reason;
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            mTrackSelectedCount++;
            mTrackSelectedType = type;
            mTrackSelectedTrackId = trackId;
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> trackList) {
            mTrackChangedCount++;
            mTracksChangedTrackList = trackList;
        }

        @Override
        public void onVideoSizeChanged(String inputId, int width, int height) {
            mVideoSizeChanged++;
        }

        @Override
        public void onContentAllowed(String inputId) {
            mContentAllowedCount++;
        }

        @Override
        public void onContentBlocked(String inputId, TvContentRating rating) {
            mContentBlockedCount++;
            mContentBlockedRating = rating;
        }

        @Override
        public void onTimeShiftStatusChanged(String inputId, int status) {
            mTimeShiftStatusChangedCount++;
            mTimeShiftStatusChangedStatus = status;
        }

        public void resetCounts() {
            mChannelRetunedCount = 0;
            mVideoAvailableCount = 0;
            mVideoUnavailableCount = 0;
            mTrackSelectedCount = 0;
            mTrackChangedCount = 0;
            mContentAllowedCount = 0;
            mContentBlockedCount = 0;
            mTimeShiftStatusChangedCount = 0;
        }

        public void resetPassedValues() {
            mChannelRetunedUri = null;
            mVideoUnavailableReason = null;
            mTrackSelectedType = null;
            mTrackSelectedTrackId = null;
            mTracksChangedTrackList = null;
            mContentBlockedRating = null;
            mTimeShiftStatusChangedStatus = null;
        }
    }

    private static class StubTimeShiftPositionCallback extends TvView.TimeShiftPositionCallback {
        private int mTimeShiftStartPositionChanged;
        private int mTimeShiftCurrentPositionChanged;

        @Override
        public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
            mTimeShiftStartPositionChanged++;
        }

        @Override
        public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
            mTimeShiftCurrentPositionChanged++;
        }

        public void resetCounts() {
            mTimeShiftStartPositionChanged = 0;
            mTimeShiftCurrentPositionChanged = 0;
        }
    }


    @BeforeClass
    public static void initDummyBundle() {
        sDummyBundle = new Bundle();
        sDummyBundle.putString("stringKey", new String("Test String"));
    }

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry
                .getInstrumentation();
        mTvRecordingClient = new TvRecordingClient(mInstrumentation.getTargetContext(),
                "TvInputServiceTest",
                mRecordingCallback, null);
        mManager = (TvInputManager) mInstrumentation.getTargetContext().getSystemService(
                Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : mManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(CountingTvInputService.class.getName())) {
                mStubInfo = info;
            }
            if (info.getServiceInfo().name.equals(FaultyTvInputService.class.getName())) {
                mFaultyStubInfo = info;
            }
            if (mStubInfo != null && mFaultyStubInfo != null) {
                break;
            }
        }
        assertThat(mStubInfo).isNotNull();

        CountingTvInputService.sSession = null;
        CountingTvInputService.sTvInputSessionId = null;
        resetCounts();
        resetPassedValues();
    }

    @After
    public void tearDown() {
        activityRule.getScenario().onActivity(activity -> {
            activity.getTvView().reset();
        });
    }

    @Test
    public void verifyCommandTuneForRecording() {
        final Uri fakeChannelUri = tuneForRecording();
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        assertThat(CountingTvInputService.sTvInputSessionId).isNotEmpty();
        assertThat(session.mTuneCount).isEqualTo(1);
        assertThat(session.mTunedChannelUri).isEqualTo(fakeChannelUri);
    }

    @Test
    public void verifyCommandTuneForRecordingWithBundle() {
        final Uri fakeChannelUri = tuneForRecording(sDummyBundle);
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        assertThat(CountingTvInputService.sTvInputSessionId).isNotEmpty();
        assertThat(session.mTuneCount).isEqualTo(1);
        assertThat(session.mTuneWithBundleCount).isEqualTo(1);
        assertThat(session.mTunedChannelUri).isEqualTo(fakeChannelUri);
        assertThat(bundleEquals(session.mTuneWithBundleData, sDummyBundle)).isTrue();
    }

    @Test
    public void verifyCommandRelease() {
        tuneForRecording();
        mTvRecordingClient.release();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null && session.mReleaseCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCommandStartRecording() {
        Uri fakeChannelUri = tuneForRecording();
        notifyTuned(fakeChannelUri);
        mTvRecordingClient.startRecording(fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null
                        && session.mStartRecordingCount > 0
                        && Objects.equals(session.mProgramHint, fakeChannelUri);
            }
        }.run();
    }

    @Test
    public void verifyCommandStartRecordingWithBundle() {
        Uri fakeChannelUri = tuneForRecording(sDummyBundle);
        notifyTuned(fakeChannelUri);
        mTvRecordingClient.startRecording(fakeChannelUri, sDummyBundle);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null
                        && session.mStartRecordingCount > 0
                        && session.mStartRecordingWithBundleCount > 0
                        && Objects.equals(session.mProgramHint, fakeChannelUri)
                        && bundleEquals(session.mStartRecordingWithBundleData, sDummyBundle);
            }
        }.run();
    }

    @Test
    public void verifyCommandStopRecording() {
        Uri fakeChannelUri = tuneForRecording();
        notifyTuned(fakeChannelUri);
        mTvRecordingClient.startRecording(fakeChannelUri);
        mTvRecordingClient.stopRecording();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null && session.mStopRecordingCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCommandSendAppPrivateCommandForRecording() {
        tuneForRecording();
        final String action = "android.media.tv.cts.TvInputServiceTest.privateCommand";
        mTvRecordingClient.sendAppPrivateCommand(action, sDummyBundle);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
                return session != null
                        && session.mAppPrivateCommandCount > 0
                        && bundleEquals(session.mAppPrivateCommandData, sDummyBundle)
                        && TextUtils.equals(session.mAppPrivateCommandAction, action);
            }
        }.run();
    }

    @Test
    public void verifyCallbackTuned() {
        Uri fakeChannelUri = tuneForRecording();
        notifyTuned(fakeChannelUri);
        assertThat(mRecordingCallback.mTunedCount).isEqualTo(1);
        assertThat(mRecordingCallback.mTunedChannelUri).isEqualTo(fakeChannelUri);
    }


    @Test
    public void verifyCallbackError() {
        Uri fakeChannelUri = tuneForRecording();
        notifyTuned(fakeChannelUri);
        mTvRecordingClient.startRecording(fakeChannelUri);
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        assertThat(session).isNotNull();
        final int error = TvInputManager.RECORDING_ERROR_UNKNOWN;
        session.notifyError(error);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mErrorCount > 0
                        && mRecordingCallback.mError == error;
            }
        }.run();
    }

    @Test
    public void verifyCallbackRecordingStopped() {
        Uri fakeChannelUri = tuneForRecording();
        notifyTuned(fakeChannelUri);
        mTvRecordingClient.startRecording(fakeChannelUri);
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        assertThat(session).isNotNull();
        session.notifyRecordingStopped(fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mRecordingStoppedCount > 0
                        && Objects.equals(mRecordingCallback.mRecordedProgramUri, fakeChannelUri);
            }
        }.run();
    }

    @Test
    public void verifyCallbackConnectionFailed() {
        resetCounts();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune("invalid_input_id", fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mConnectionFailedCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCallbackDisconnected() {
        resetCounts();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune(mFaultyStubInfo.getId(), fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mRecordingCallback.mDisconnectedCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCommandTune() throws Exception {
        resetCounts();
        resetPassedValues();
        final Uri fakeChannelUri = tune();

        final CountingSession session = CountingTvInputService.sSession;
        final String tvInputSessionId = CountingTvInputService.sTvInputSessionId;
        assertWithMessage("session").that(session).isNotNull();
        assertWithMessage("tvInputSessionId").that(tvInputSessionId).isNotEmpty();
        assertWithMessage("mTuneCount").that(session.mTuneCount).isGreaterThan(0);
        assertWithMessage("mCreateOverlayView").that(session.mCreateOverlayView).isGreaterThan(0);
        assertWithMessage("mTunedChannelUri").that(session.mTunedChannelUri).isEqualTo(
                fakeChannelUri);
    }

    @Test
    public void verifyCommandTuneWithBundle() {
        resetCounts();
        resetPassedValues();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        onTvView(tvView -> tvView.tune(mStubInfo.getId(), fakeChannelUri, sDummyBundle));
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                final String tvInputSessionId = CountingTvInputService.sTvInputSessionId;
                return session != null
                        && tvInputSessionId != null
                        && session.mTuneCount > 0
                        && session.mTuneWithBundleCount > 0
                        && Objects.equals(session.mTunedChannelUri, fakeChannelUri)
                        && bundleEquals(session.mTuneWithBundleData, sDummyBundle);
            }
        }.run();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        assertThat(session.mTuneWithBundleCount).isEqualTo(1);
    }

    @Test
    public void verifyCommandSetStreamVolume() throws Exception {
        tune();
        resetPassedValues();
        final float volume = 0.8f;
        onTvView(tvView -> tvView.setStreamVolume(volume));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mSetStreamVolumeCount > 0
                        && session.mStreamVolume == volume;
            }
        }.run();
    }

    @Test
    public void verifyCommandSetCaptionEnabled() throws Exception {
        tune();
        resetPassedValues();
        final boolean enable = true;
        onTvView(tvView -> tvView.setCaptionEnabled(enable));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mSetCaptionEnabledCount > 0
                        && session.mCaptionEnabled == enable;
            }
        }.run();
    }

    @Test
    public void verifyCommandSelectTrack() throws Exception {
        tune();
        resetPassedValues();
        verifyCallbackTracksChanged();
        final int dummyTrackType = DUMMY_TRACK.getType();
        final String dummyTrackId = DUMMY_TRACK.getId();
        onTvView(tvView -> tvView.selectTrack(dummyTrackType, dummyTrackId));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mSelectTrackCount > 0
                        && session.mSelectTrackType == dummyTrackType
                        && TextUtils.equals(session.mSelectTrackId, dummyTrackId);
            }
        }.run();
    }

    @Test
    public void verifyCommandDispatchKeyDown() throws Exception {
        tune();
        resetPassedValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        onTvView(tvView -> tvView.dispatchKeyEvent(event));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mKeyDownCount > 0
                        && session.mKeyDownCode == keyCode
                        && keyEventEquals(event, session.mKeyDownEvent);
            }
        }.run();
    }

    @Test
    public void verifyCommandDispatchKeyMultiple() throws Exception {
        tune();
        resetPassedValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_MULTIPLE, keyCode);
        onTvView(tvView -> tvView.dispatchKeyEvent(event));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mKeyMultipleCount > 0
                        && session.mKeyMultipleCode == keyCode
                        && keyEventEquals(event, session.mKeyMultipleEvent)
                        && session.mKeyMultipleNumber == event.getRepeatCount();
            }
        }.run();
    }

    @Test
    public void verifyCommandDispatchKeyUp() throws Exception {
        tune();
        resetPassedValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        onTvView(tvView -> tvView.dispatchKeyEvent(event));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mKeyUpCount > 0
                        && session.mKeyUpCode == keyCode
                        && keyEventEquals(event, session.mKeyUpEvent);
            }
        }.run();
    }

    @Test
    public void verifyCommandDispatchTouchEvent() throws Exception {
        tune();
        resetPassedValues();
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1.0f, 1.0f,
                1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        onTvView(tvView -> tvView.dispatchTouchEvent(event));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mTouchEventCount > 0
                        && motionEventEquals(session.mTouchEvent, event);
            }
        }.run();
    }

    @Test
    public void verifyCommandDispatchTrackballEvent() throws Exception {
        tune();
        resetPassedValues();
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1.0f, 1.0f,
                1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(InputDevice.SOURCE_TRACKBALL);
        onTvView(tvView -> tvView.dispatchTouchEvent(event));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mTrackballEventCount > 0
                        && motionEventEquals(session.mTrackballEvent, event);
            }
        }.run();
    }

    @Test
    public void verifyCommandDispatchGenericMotionEvent() throws Exception {
        tune();
        resetPassedValues();
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1.0f, 1.0f,
                1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        onTvView(tvView -> tvView.dispatchGenericMotionEvent(event));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mGenricMotionEventCount > 0
                        && motionEventEquals(session.mGenricMotionEvent, event);
            }
        }.run();
    }

    @Test
    public void verifyCommandTimeShiftPause() throws Exception {
        tune();
        onTvView(tvView -> tvView.timeShiftPause());
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftPauseCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCommandTimeShiftResume() throws Exception {
        tune();
        onTvView(tvView -> {
            tvView.timeShiftResume();
        });
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftResumeCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCommandTimeShiftSeekTo() throws Exception {
        tune();
        resetPassedValues();
        final long timeMs = 0;
        onTvView(tvView -> tvView.timeShiftSeekTo(timeMs));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftSeekToCount > 0
                        && session.mTimeShiftSeekTo == timeMs;
            }
        }.run();
    }

    @Test
    public void verifyCommandTimeShiftSetPlaybackParams() throws Exception {
        tune();
        resetPassedValues();
        final PlaybackParams param = new PlaybackParams().setSpeed(2.0f)
                .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT);
        onTvView(tvView -> tvView.timeShiftSetPlaybackParams(param));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftSetPlaybackParamsCount > 0
                        && playbackParamsEquals(session.mTimeShiftSetPlaybackParams, param);
            }
        }.run();
    }

    @Test
    public void verifyCommandTimeShiftPlay() throws Exception {
        tune();
        resetPassedValues();
        final Uri fakeRecordedProgramUri = TvContract.buildRecordedProgramUri(0);
        onTvView(tvView -> tvView.timeShiftPlay(mStubInfo.getId(), fakeRecordedProgramUri));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null && session.mTimeShiftPlayCount > 0
                        && Objects.equals(session.mRecordedProgramUri, fakeRecordedProgramUri);
            }
        }.run();
    }

    @Test
    public void verifyCommandSetTimeShiftPositionCallback() throws Exception {
        tune();
        onTvView(tvView -> tvView.setTimeShiftPositionCallback(mTimeShiftPositionCallback));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mTimeShiftPositionCallback.mTimeShiftCurrentPositionChanged > 0
                        && mTimeShiftPositionCallback.mTimeShiftStartPositionChanged > 0;
            }
        }.run();
    }

    @Test
    @Ignore("b/174076887")
    public void verifyCommandOverlayViewSizeChanged() throws Exception {
        tune();
        resetPassedValues();
        final int width = 10;
        final int height = 20;

        onTvView(tvView -> tvView.setLayoutParams(new LinearLayout.LayoutParams(width, height)));

        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                mInstrumentation.waitForIdleSync();
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mOverlayViewSizeChangedCount > 0
                        && session.mOverlayViewSizeChangedWidth == width
                        && session.mOverlayViewSizeChangedHeight == height;
            }
        }.run();
    }

    @Test
    public void verifyCommandSendAppPrivateCommand() throws Exception {
        tune();
        final String action = "android.media.tv.cts.TvInputServiceTest.privateCommand";
        onTvView(tvView -> tvView.sendAppPrivateCommand(action, sDummyBundle));
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final CountingSession session = CountingTvInputService.sSession;
                return session != null
                        && session.mAppPrivateCommandCount > 0
                        && bundleEquals(session.mAppPrivateCommandData, sDummyBundle)
                        && TextUtils.equals(session.mAppPrivateCommandAction, action);
            }
        }.run();
    }

    @Test
    public void verifyCallbackChannelRetuned() throws Exception {
        tune();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        session.notifyChannelRetuned(fakeChannelUri);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mChannelRetunedCount > 0
                        && Objects.equals(mCallback.mChannelRetunedUri, fakeChannelUri);
            }
        }.run();
    }

    @Test
    public void verifyCallbackVideoAvailable() throws Exception {
        tune();
        resetCounts();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        session.notifyVideoAvailable();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mVideoAvailableCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCallbackVideoUnavailable() throws Exception {
        tune();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        final int reason = TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING;
        session.notifyVideoUnavailable(reason);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mVideoUnavailableCount > 0
                        && mCallback.mVideoUnavailableReason == reason;
            }
        }.run();
    }

    @Test
    public void verifyCallbackTracksChanged() throws Exception {
        tune();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        ArrayList<TvTrackInfo> tracks = new ArrayList<>();
        tracks.add(DUMMY_TRACK);
        session.notifyTracksChanged(tracks);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mTrackChangedCount > 0
                        && Objects.equals(mCallback.mTracksChangedTrackList, tracks);
            }
        }.run();
    }

    @Test
    @Ignore("b/174076887")
    public void verifyCallbackVideoSizeChanged() throws Exception {
        tune();
        resetCounts();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        ArrayList<TvTrackInfo> tracks = new ArrayList<>();
        tracks.add(DUMMY_TRACK);
        session.notifyTracksChanged(tracks);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mVideoSizeChanged > 0;
            }
        }.run();
    }

    @Test
    public void verifyCallbackTrackSelected() throws Exception {
        tune();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        session.notifyTrackSelected(DUMMY_TRACK.getType(), DUMMY_TRACK.getId());
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mTrackSelectedCount > 0
                        && mCallback.mTrackSelectedType == DUMMY_TRACK.getType()
                        && TextUtils.equals(DUMMY_TRACK.getId(), mCallback.mTrackSelectedTrackId);
            }
        }.run();
    }

    @Test
    public void verifyCallbackContentAllowed() throws Exception {
        tune();
        resetCounts();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        session.notifyContentAllowed();
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mContentAllowedCount > 0;
            }
        }.run();
    }

    @Test
    public void verifyCallbackContentBlocked() throws Exception {
        tune();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        final TvContentRating rating = TvContentRating.createRating("android.media.tv", "US_TVPG",
                "US_TVPG_TV_MA", "US_TVPG_S", "US_TVPG_V");
        session.notifyContentBlocked(rating);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mContentBlockedCount > 0
                        && Objects.equals(mCallback.mContentBlockedRating, rating);
            }
        }.run();
    }

    @Test
    public void verifyCallbackTimeShiftStatusChanged() throws Exception {
        tune();
        resetPassedValues();
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        final int status = TvInputManager.TIME_SHIFT_STATUS_AVAILABLE;
        session.notifyTimeShiftStatusChanged(status);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                return mCallback.mTimeShiftStatusChangedCount > 0
                        && mCallback.mTimeShiftStatusChangedStatus == status;
            }
        }.run();
    }

    @Test
    @Ignore("b/174076887")
    public void verifyCallbackLayoutSurface() throws Exception {
        tune();
        final int left = 10;
        final int top = 20;
        final int right = 30;
        final int bottom = 40;
        final CountingSession session = CountingTvInputService.sSession;
        assertThat(session).isNotNull();
        session.layoutSurface(left, top, right, bottom);
        new PollingCheck(TIME_OUT) {
            @Override
            protected boolean check() {
                final AtomicBoolean retValue = new AtomicBoolean();
                onTvView(tvView -> {
                    int childCount = tvView.getChildCount();
                    for (int i = 0; i < childCount; ++i) {
                        View v = tvView.getChildAt(i);
                        if (v instanceof SurfaceView) {
                            retValue.set(v.getLeft() == left && v.getTop() == top
                                    && v.getRight() == right
                                    && v.getBottom() == bottom
                            );
                            break;
                        }
                    }
                });
                mInstrumentation.waitForIdleSync();
                return retValue.get();
            }
        }.run();
    }

    public static boolean keyEventEquals(KeyEvent event, KeyEvent other) {
        if (event == other) return true;
        if (event == null || other == null) return false;
        return event.getDownTime() == other.getDownTime()
                && event.getEventTime() == other.getEventTime()
                && event.getAction() == other.getAction()
                && event.getKeyCode() == other.getKeyCode()
                && event.getRepeatCount() == other.getRepeatCount()
                && event.getMetaState() == other.getMetaState()
                && event.getDeviceId() == other.getDeviceId()
                && event.getScanCode() == other.getScanCode()
                && event.getFlags() == other.getFlags()
                && event.getSource() == other.getSource()
                && TextUtils.equals(event.getCharacters(), other.getCharacters());
    }

    public static boolean motionEventEquals(MotionEvent event, MotionEvent other) {
        if (event == other) return true;
        if (event == null || other == null) return false;
        return event.getDownTime() == other.getDownTime()
                && event.getEventTime() == other.getEventTime()
                && event.getAction() == other.getAction()
                && event.getX() == other.getX()
                && event.getY() == other.getY()
                && event.getPressure() == other.getPressure()
                && event.getSize() == other.getSize()
                && event.getMetaState() == other.getMetaState()
                && event.getXPrecision() == other.getXPrecision()
                && event.getYPrecision() == other.getYPrecision()
                && event.getDeviceId() == other.getDeviceId()
                && event.getEdgeFlags() == other.getEdgeFlags()
                && event.getSource() == other.getSource();
    }

    public static boolean playbackParamsEquals(PlaybackParams param, PlaybackParams other) {
        if (param == other) return true;
        if (param == null || other == null) return false;
        return param.getAudioFallbackMode() == other.getAudioFallbackMode()
                && param.getSpeed() == other.getSpeed();
    }

    public static boolean bundleEquals(Bundle b, Bundle other) {
        if (b == other) return true;
        if (b == null || other == null) return false;
        if (b.size() != other.size()) return false;

        Set<String> keys = b.keySet();
        for (String key : keys) {
            if (!other.containsKey(key)) return false;
            Object objOne = b.get(key);
            Object objTwo = other.get(key);
            if (!Objects.equals(objOne, objTwo)) {
                return false;
            }
        }
        return true;
    }

    private void notifyTuned(Uri fakeChannelUri) {
        final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
        session.notifyTuned(fakeChannelUri);
        PollingCheck.waitFor(TIME_OUT, () -> mRecordingCallback.mTunedCount > 0);
    }

    private void onTvView(Consumer<TvView> tvViewConsumer) {
        activityRule.getScenario().onActivity(viewAction(tvViewConsumer));

    }

    private void resetCounts() {
        if (CountingTvInputService.sSession != null) {
            CountingTvInputService.sSession.resetCounts();
        }
        if (CountingTvInputService.sRecordingSession != null) {
            CountingTvInputService.sRecordingSession.resetCounts();
        }
        mCallback.resetCounts();
        mTimeShiftPositionCallback.resetCounts();
        mRecordingCallback.resetCounts();
    }

    private void resetPassedValues() {
        if (CountingTvInputService.sSession != null) {
            CountingTvInputService.sSession.resetPassedValues();
        }
        if (CountingTvInputService.sRecordingSession != null) {
            CountingTvInputService.sRecordingSession.resetPassedValues();
        }
        mCallback.resetPassedValues();
        mRecordingCallback.resetPassedValues();
    }

    private static PollingCheck.PollingCheckCondition recordingSessionCheck(
            ToBooleanFunction<CountingRecordingSession> toBooleanFunction) {
        return () -> {
            final CountingRecordingSession session = CountingTvInputService.sRecordingSession;
            return session != null && toBooleanFunction.apply(session);
        };
    }

    private static PollingCheck.PollingCheckCondition sessionCheck(
            ToBooleanFunction<CountingSession> toBooleanFunction) {
        return () -> {
            final CountingSession session = CountingTvInputService.sSession;
            return session != null && toBooleanFunction.apply(session);
        };
    }

    private Uri tune() {
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        onTvView(tvView -> {
            tvView.setCallback(mCallback);
            tvView.tune(mStubInfo.getId(), fakeChannelUri);
        });
        PollingCheck.waitFor(TIME_OUT, sessionCheck(session -> session.mTuneCount > 0));
        return fakeChannelUri;
    }

    private Uri tuneForRecording() {
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune(mStubInfo.getId(), fakeChannelUri);
        PollingCheck.waitFor(TIME_OUT, recordingSessionCheck(s -> s.mTuneCount > 0));
        return fakeChannelUri;
    }

    private Uri tuneForRecording(Bundle bundle) {
        final Uri fakeChannelUri = TvContract.buildChannelUri(0);
        mTvRecordingClient.tune(mStubInfo.getId(), fakeChannelUri, bundle);
        PollingCheck.waitFor(TIME_OUT,
                recordingSessionCheck(s -> s.mTuneCount > 0 && s.mTuneWithBundleCount > 0));
        return fakeChannelUri;
    }

    private static ActivityScenario.ActivityAction<TvViewStubActivity> viewAction(
            Consumer<TvView> consumer) {
        return activity -> consumer.accept(activity.getTvView());
    }

    public static class CountingTvInputService extends StubTvInputService {
        static CountingSession sSession;
        static CountingRecordingSession sRecordingSession;
        static String sTvInputSessionId;

        @Override
        public Session onCreateSession(String inputId) {
            sSession = new CountingSession(this);
            sSession.setOverlayViewEnabled(true);
            return sSession;
        }

        @Override
        public RecordingSession onCreateRecordingSession(String inputId) {
            sRecordingSession = new CountingRecordingSession(this);
            return sRecordingSession;
        }

        @Override
        public Session onCreateSession(String inputId, String tvInputSessionId) {
            sTvInputSessionId = tvInputSessionId;
            return onCreateSession(inputId);
        }

        @Override
        public RecordingSession onCreateRecordingSession(String inputId, String tvInputSessionId) {
            sTvInputSessionId = tvInputSessionId;
            return onCreateRecordingSession(inputId);
        }

        @Override
        public IBinder createExtension() {
            return null;
        }

        public static class CountingSession extends Session {
            public volatile int mTuneCount;
            public volatile int mTuneWithBundleCount;
            public volatile int mSetStreamVolumeCount;
            public volatile int mSetCaptionEnabledCount;
            public volatile int mSelectTrackCount;
            public volatile int mCreateOverlayView;
            public volatile int mKeyDownCount;
            public volatile int mKeyLongPressCount;
            public volatile int mKeyMultipleCount;
            public volatile int mKeyUpCount;
            public volatile int mTouchEventCount;
            public volatile int mTrackballEventCount;
            public volatile int mGenricMotionEventCount;
            public volatile int mOverlayViewSizeChangedCount;
            public volatile int mTimeShiftPauseCount;
            public volatile int mTimeShiftResumeCount;
            public volatile int mTimeShiftSeekToCount;
            public volatile int mTimeShiftSetPlaybackParamsCount;
            public volatile int mTimeShiftPlayCount;
            public volatile long mTimeShiftGetCurrentPositionCount;
            public volatile long mTimeShiftGetStartPositionCount;
            public volatile int mAppPrivateCommandCount;

            public volatile String mAppPrivateCommandAction;
            public volatile Bundle mAppPrivateCommandData;
            public volatile Uri mTunedChannelUri;
            public volatile Bundle mTuneWithBundleData;
            public volatile Float mStreamVolume;
            public volatile Boolean mCaptionEnabled;
            public volatile Integer mSelectTrackType;
            public volatile String mSelectTrackId;
            public volatile Integer mKeyDownCode;
            public volatile KeyEvent mKeyDownEvent;
            public volatile Integer mKeyLongPressCode;
            public volatile KeyEvent mKeyLongPressEvent;
            public volatile Integer mKeyMultipleCode;
            public volatile Integer mKeyMultipleNumber;
            public volatile KeyEvent mKeyMultipleEvent;
            public volatile Integer mKeyUpCode;
            public volatile KeyEvent mKeyUpEvent;
            public volatile MotionEvent mTouchEvent;
            public volatile MotionEvent mTrackballEvent;
            public volatile MotionEvent mGenricMotionEvent;
            public volatile Long mTimeShiftSeekTo;
            public volatile PlaybackParams mTimeShiftSetPlaybackParams;
            public volatile Uri mRecordedProgramUri;
            public volatile Integer mOverlayViewSizeChangedWidth;
            public volatile Integer mOverlayViewSizeChangedHeight;

            CountingSession(Context context) {
                super(context);
            }

            public void resetCounts() {
                mTuneCount = 0;
                mTuneWithBundleCount = 0;
                mSetStreamVolumeCount = 0;
                mSetCaptionEnabledCount = 0;
                mSelectTrackCount = 0;
                mCreateOverlayView = 0;
                mKeyDownCount = 0;
                mKeyLongPressCount = 0;
                mKeyMultipleCount = 0;
                mKeyUpCount = 0;
                mTouchEventCount = 0;
                mTrackballEventCount = 0;
                mGenricMotionEventCount = 0;
                mOverlayViewSizeChangedCount = 0;
                mTimeShiftPauseCount = 0;
                mTimeShiftResumeCount = 0;
                mTimeShiftSeekToCount = 0;
                mTimeShiftSetPlaybackParamsCount = 0;
                mTimeShiftPlayCount = 0;
                mTimeShiftGetCurrentPositionCount = 0;
                mTimeShiftGetStartPositionCount = 0;
                mAppPrivateCommandCount = 0;
            }

            public void resetPassedValues() {
                mAppPrivateCommandAction = null;
                mAppPrivateCommandData = null;
                mTunedChannelUri = null;
                mTuneWithBundleData = null;
                mStreamVolume = null;
                mCaptionEnabled = null;
                mSelectTrackType = null;
                mSelectTrackId = null;
                mKeyDownCode = null;
                mKeyDownEvent = null;
                mKeyLongPressCode = null;
                mKeyLongPressEvent = null;
                mKeyMultipleCode = null;
                mKeyMultipleNumber = null;
                mKeyMultipleEvent = null;
                mKeyUpCode = null;
                mKeyUpEvent = null;
                mTouchEvent = null;
                mTrackballEvent = null;
                mGenricMotionEvent = null;
                mTimeShiftSeekTo = null;
                mTimeShiftSetPlaybackParams = null;
                mRecordedProgramUri = null;
                mOverlayViewSizeChangedWidth = null;
                mOverlayViewSizeChangedHeight = null;
            }

            @Override
            public void onAppPrivateCommand(String action, Bundle data) {
                mAppPrivateCommandCount++;
                mAppPrivateCommandAction = action;
                mAppPrivateCommandData = data;
            }

            @Override
            public void onRelease() {
            }

            @Override
            public boolean onSetSurface(Surface surface) {
                return false;
            }

            @Override
            public boolean onTune(Uri channelUri) {
                mTuneCount++;
                mTunedChannelUri = channelUri;
                return false;
            }

            @Override
            public boolean onTune(Uri channelUri, Bundle data) {
                mTuneWithBundleCount++;
                mTuneWithBundleData = data;
                // Also calls {@link #onTune(Uri)} since it will never be called if the
                // implementation overrides {@link #onTune(Uri, Bundle)}.
                onTune(channelUri);
                return false;
            }

            @Override
            public void onSetStreamVolume(float volume) {
                mSetStreamVolumeCount++;
                mStreamVolume = volume;
            }

            @Override
            public void onSetCaptionEnabled(boolean enabled) {
                mSetCaptionEnabledCount++;
                mCaptionEnabled = enabled;
            }

            @Override
            public boolean onSelectTrack(int type, String id) {
                mSelectTrackCount++;
                mSelectTrackType = type;
                mSelectTrackId = id;
                return false;
            }

            @Override
            public View onCreateOverlayView() {
                mCreateOverlayView++;
                return null;
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                mKeyDownCount++;
                mKeyDownCode = keyCode;
                mKeyDownEvent = event;
                return false;
            }

            @Override
            public boolean onKeyLongPress(int keyCode, KeyEvent event) {
                mKeyLongPressCount++;
                mKeyLongPressCode = keyCode;
                mKeyLongPressEvent = event;
                return false;
            }

            @Override
            public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
                mKeyMultipleCount++;
                mKeyMultipleCode = keyCode;
                mKeyMultipleNumber = count;
                mKeyMultipleEvent = event;
                return false;
            }

            @Override
            public boolean onKeyUp(int keyCode, KeyEvent event) {
                mKeyUpCount++;
                mKeyUpCode = keyCode;
                mKeyUpEvent = event;
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                mTouchEventCount++;
                mTouchEvent = event;
                return false;
            }

            @Override
            public boolean onTrackballEvent(MotionEvent event) {
                mTrackballEventCount++;
                mTrackballEvent = event;
                return false;
            }

            @Override
            public boolean onGenericMotionEvent(MotionEvent event) {
                mGenricMotionEventCount++;
                mGenricMotionEvent = event;
                return false;
            }

            @Override
            public void onTimeShiftPause() {
                mTimeShiftPauseCount++;
            }

            @Override
            public void onTimeShiftResume() {
                mTimeShiftResumeCount++;
            }

            @Override
            public void onTimeShiftSeekTo(long timeMs) {
                mTimeShiftSeekToCount++;
                mTimeShiftSeekTo = timeMs;
            }

            @Override
            public void onTimeShiftSetPlaybackParams(PlaybackParams param) {
                mTimeShiftSetPlaybackParamsCount++;
                mTimeShiftSetPlaybackParams = param;
            }

            @Override
            public void onTimeShiftPlay(Uri recordedProgramUri) {
                mTimeShiftPlayCount++;
                mRecordedProgramUri = recordedProgramUri;
            }

            @Override
            public long onTimeShiftGetCurrentPosition() {
                return ++mTimeShiftGetCurrentPositionCount;
            }

            @Override
            public long onTimeShiftGetStartPosition() {
                return ++mTimeShiftGetStartPositionCount;
            }

            @Override
            public void onOverlayViewSizeChanged(int width, int height) {
                mOverlayViewSizeChangedCount++;
                mOverlayViewSizeChangedWidth = width;
                mOverlayViewSizeChangedHeight = height;
            }
        }

        public static class CountingRecordingSession extends RecordingSession {
            public volatile int mTuneCount;
            public volatile int mTuneWithBundleCount;
            public volatile int mReleaseCount;
            public volatile int mStartRecordingCount;
            public volatile int mStartRecordingWithBundleCount;
            public volatile int mStopRecordingCount;
            public volatile int mAppPrivateCommandCount;

            public volatile Uri mTunedChannelUri;
            public volatile Bundle mTuneWithBundleData;
            public volatile Uri mProgramHint;
            public volatile Bundle mStartRecordingWithBundleData;
            public volatile String mAppPrivateCommandAction;
            public volatile Bundle mAppPrivateCommandData;

            CountingRecordingSession(Context context) {
                super(context);
            }

            public void resetCounts() {
                mTuneCount = 0;
                mTuneWithBundleCount = 0;
                mReleaseCount = 0;
                mStartRecordingCount = 0;
                mStartRecordingWithBundleCount = 0;
                mStopRecordingCount = 0;
                mAppPrivateCommandCount = 0;
            }

            public void resetPassedValues() {
                mTunedChannelUri = null;
                mTuneWithBundleData = null;
                mProgramHint = null;
                mStartRecordingWithBundleData = null;
                mAppPrivateCommandAction = null;
                mAppPrivateCommandData = null;
            }

            @Override
            public void onTune(Uri channelUri) {
                mTuneCount++;
                mTunedChannelUri = channelUri;
            }

            @Override
            public void onTune(Uri channelUri, Bundle data) {
                mTuneWithBundleCount++;
                mTuneWithBundleData = data;
                // Also calls {@link #onTune(Uri)} since it will never be called if the
                // implementation overrides {@link #onTune(Uri, Bundle)}.
                onTune(channelUri);
            }

            @Override
            public void onRelease() {
                mReleaseCount++;
            }

            @Override
            public void onStartRecording(Uri programHint) {
                mStartRecordingCount++;
                mProgramHint = programHint;
            }

            @Override
            public void onStartRecording(Uri programHint, Bundle data) {
                mStartRecordingWithBundleCount++;
                mProgramHint = programHint;
                mStartRecordingWithBundleData = data;
                // Also calls {@link #onStartRecording(Uri)} since it will never be called if the
                // implementation overrides {@link #onStartRecording(Uri, Bundle)}.
                onStartRecording(programHint);
            }

            @Override
            public void onStopRecording() {
                mStopRecordingCount++;
            }

            @Override
            public void onAppPrivateCommand(String action, Bundle data) {
                mAppPrivateCommandCount++;
                mAppPrivateCommandAction = action;
                mAppPrivateCommandData = data;
            }
        }
    }

    private static class StubRecordingCallback extends TvRecordingClient.RecordingCallback {
        private int mTunedCount;
        private int mRecordingStoppedCount;
        private int mErrorCount;
        private int mConnectionFailedCount;
        private int mDisconnectedCount;

        private Uri mTunedChannelUri;
        private Uri mRecordedProgramUri;
        private Integer mError;

        @Override
        public void onTuned(Uri channelUri) {
            mTunedCount++;
            mTunedChannelUri = channelUri;
        }

        @Override
        public void onRecordingStopped(Uri recordedProgramUri) {
            mRecordingStoppedCount++;
            mRecordedProgramUri = recordedProgramUri;
        }

        @Override
        public void onError(int error) {
            mErrorCount++;
            mError = error;
        }

        @Override
        public void onConnectionFailed(String inputId) {
            mConnectionFailedCount++;
        }

        @Override
        public void onDisconnected(String inputId) {
            mDisconnectedCount++;
        }

        public void resetCounts() {
            mTunedCount = 0;
            mRecordingStoppedCount = 0;
            mErrorCount = 0;
            mConnectionFailedCount = 0;
            mDisconnectedCount = 0;
        }

        public void resetPassedValues() {
            mTunedChannelUri = null;
            mRecordedProgramUri = null;
            mError = null;
        }
    }


}
