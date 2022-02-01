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

package android.media.tv.interactive.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.tv.AdRequest;
import android.media.tv.AdResponse;
import android.media.tv.BroadcastInfoRequest;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.CommandRequest;
import android.media.tv.CommandResponse;
import android.media.tv.TsRequest;
import android.media.tv.TsResponse;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.interactive.TvInteractiveAppInfo;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppView;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.ParcelFileDescriptor;
import android.tv.cts.R;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Test {@link android.media.tv.interactive.TvInteractiveAppService}.
 */
@RunWith(AndroidJUnit4.class)
public class TvInteractiveAppServiceTest {
    private static final long TIME_OUT_MS = 20000L;
    private static final Uri CHANNEL_0 = TvContract.buildChannelUri(0);

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvInteractiveAppViewStubActivity> mActivityScenario;
    private TvInteractiveAppViewStubActivity mActivity;
    private TvInteractiveAppView mTvIAppView;

    private TvView mTvView;
    private TvInteractiveAppManager mManager;
    private TvInteractiveAppInfo mStubInfo;
    private StubTvInteractiveAppService.StubSessionImpl mSession;
    private TvInputManager mTvInputManager;
    private TvInputInfo mTvInputInfo;
    private StubTvInputService2.StubSessionImpl2 mInputSession;

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    private final MockCallback mCallback = new MockCallback();
    private final MockTvInputCallback mTvInputCallback = new MockTvInputCallback();

    public static class MockCallback extends TvInteractiveAppView.TvInteractiveAppCallback {
        private int mRequestCurrentChannelUriCount = 0;
        private int mStateChangedCount = 0;
        private int mBiIAppCreatedCount = 0;

        private String mIAppServiceId = null;
        private Integer mState = null;
        private Integer mErr = null;
        private Uri mBiIAppUri = null;
        private String mBiIAppId = null;

        private void resetValues() {
            mRequestCurrentChannelUriCount = 0;
            mStateChangedCount = 0;
            mBiIAppCreatedCount = 0;

            mIAppServiceId = null;
            mState = null;
            mErr = null;
            mBiIAppUri = null;
            mBiIAppId = null;
        }

        @Override
        public void onRequestCurrentChannelUri(String iAppServiceId) {
            mRequestCurrentChannelUriCount++;
        }

        @Override
        public void onStateChanged(String iAppServiceId, int state, int err) {
            mStateChangedCount++;
            mIAppServiceId = iAppServiceId;
            mState = state;
            mErr = err;
        }

        @Override
        public void onBiInteractiveAppCreated(String iAppServiceId, Uri biIAppUri,
                String biIAppId) {
            mBiIAppCreatedCount++;
            mIAppServiceId = iAppServiceId;
            mBiIAppUri = biIAppUri;
            mBiIAppId = biIAppId;
        }

        @Override
        public void onPlaybackCommandRequest(String id, String type, Bundle bundle) {
        }

        @Override
        public void onRequestCurrentChannelLcn(String id) {
        }

        @Override
        public void onRequestCurrentTvInputId(String id) {
        }

        @Override
        public void onRequestStreamVolume(String id) {
        }

        @Override
        public void onRequestTrackInfoList(String id) {
        }

        @Override
        public void onSetVideoBounds(String id, Rect rect) {
        }

        @Override
        public void onTeletextAppStateChanged(String id, int state) {
        }

    }

    public static class MockTvInputCallback extends TvView.TvInputCallback {
    }

    private TvInteractiveAppView findTvInteractiveAppViewById(int id) {
        return (TvInteractiveAppView) mActivity.findViewById(id);
    }

    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    private void runTestOnUiThread(final Runnable r) throws Throwable {
        final Throwable[] exceptions = new Throwable[1];
        mInstrumentation.runOnMainSync(new Runnable() {
            public void run() {
                try {
                    r.run();
                } catch (Throwable throwable) {
                    exceptions[0] = throwable;
                }
            }
        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    private void linkTvView() {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvView.setCallback(mTvInputCallback);
        mTvView.tune(mTvInputInfo.getId(), CHANNEL_0);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvView.getInputSession() != null);
        mInputSession = StubTvInputService2.sStubSessionImpl2;
        assertNotNull(mInputSession);
        mInputSession.resetValues();

        mTvIAppView.setTvView(mTvView);
        mTvView.setInteractiveAppNotificationEnabled(true);
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private static Bundle createTestBundle() {
        Bundle b = new Bundle();
        b.putString("stringKey", new String("Test String"));
        return b;
    }

    private static Uri createTestUri() {
        return Uri.parse("content://com.example/");
    }

    @Before
    public void setUp() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(
                mInstrumentation.getTargetContext(), TvInteractiveAppViewStubActivity.class);

        // DO NOT use ActivityScenario.launch(Class), which can cause ActivityNotFoundException
        // related to BootstrapActivity.
        mActivityScenario = ActivityScenario.launch(intent);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(TIME_OUT_MS);

        assertNotNull("Failed to acquire activity reference.", mActivity);
        mTvIAppView = findTvInteractiveAppViewById(R.id.tviappview);
        assertNotNull("Failed to find TvInteractiveAppView.", mTvIAppView);
        mTvView = findTvViewById(R.id.tviapp_tvview);
        assertNotNull("Failed to find TvView.", mTvView);

        mManager = (TvInteractiveAppManager) mActivity.getSystemService(
                Context.TV_INTERACTIVE_APP_SERVICE);
        assertNotNull("Failed to get TvInteractiveAppManager.", mManager);

        for (TvInteractiveAppInfo info : mManager.getTvInteractiveAppServiceList()) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())) {
                mStubInfo = info;
            }
        }
        assertNotNull(mStubInfo);
        mTvIAppView.setCallback(getExecutor(), mCallback);
        mTvIAppView.prepareInteractiveApp(mStubInfo.getId(), 1);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvIAppView.getInteractiveAppSession() != null);
        mSession = StubTvInteractiveAppService.sSession;

        mTvInputManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        assertNotNull("Failed to get TvInputManager.", mTvInputManager);

        for (TvInputInfo info : mTvInputManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(StubTvInputService2.class.getName())) {
                mTvInputInfo = info;
            }
        }
        assertNotNull(mTvInputInfo);
    }

    @After
    public void tearDown() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mTvIAppView.reset();
                mTvView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
        mActivity = null;
        mActivityScenario.close();
    }

    @Test
    public void testRequestCurrentChannelUri() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.requestCurrentChannelUri();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestCurrentChannelUriCount > 0);

        assertThat(mCallback.mRequestCurrentChannelUriCount).isEqualTo(1);
    }

    @Test
    public void testSetSurface() throws Throwable {
        assertNotNull(mSession);

        assertThat(mSession.mSetSurfaceCount).isEqualTo(1);
    }

    @Test
    public void testLayoutSurface() throws Throwable {
        assertNotNull(mSession);

        final int left = 10;
        final int top = 20;
        final int right = 30;
        final int bottom = 40;

        mSession.layoutSurface(left, top, right, bottom);

        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                int childCount = mTvIAppView.getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    View v = mTvIAppView.getChildAt(i);
                    if (v instanceof SurfaceView) {
                        return v.getLeft() == left
                            && v.getTop() == top
                            && v.getRight() == right
                            && v.getBottom() == bottom;
                    }
                }
                return false;
            }
        }.run();
        assertThat(mSession.mSurfaceChangedCount > 0).isTrue();
    }

    @Test
    public void testSessionStateChanged() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.notifySessionStateChanged(
                TvInteractiveAppManager.INTERACTIVE_APP_STATE_ERROR,
                TvInteractiveAppManager.ERROR_UNKNOWN);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mStateChangedCount > 0);

        assertThat(mCallback.mStateChangedCount).isEqualTo(1);
        assertThat(mCallback.mIAppServiceId).isEqualTo(mStubInfo.getId());
        assertThat(mCallback.mState)
                .isEqualTo(TvInteractiveAppManager.INTERACTIVE_APP_STATE_ERROR);
        assertThat(mCallback.mErr).isEqualTo(TvInteractiveAppManager.ERROR_UNKNOWN);
    }

    @Test
    public void testStartStopInteractiveApp() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.startInteractiveApp();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStartInteractiveAppCount > 0);
        assertThat(mSession.mStartInteractiveAppCount).isEqualTo(1);

        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.stopInteractiveApp();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStopInteractiveAppCount > 0);
        assertThat(mSession.mStopInteractiveAppCount).isEqualTo(1);
    }

    @Test
    public void testDispatchKeyDown() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyDownCount > 0);

        assertThat(mSession.mKeyDownCount).isEqualTo(1);
        assertThat(mSession.mKeyDownCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyDownEvent, event);
    }

    @Test
    public void testDispatchKeyUp() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_I;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyUpCount > 0);

        assertThat(mSession.mKeyUpCount).isEqualTo(1);
        assertThat(mSession.mKeyUpCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyUpEvent, event);
    }

    @Test
    public void testDispatchKeyMultiple() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_L;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_MULTIPLE, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyMultipleCount > 0);

        assertThat(mSession.mKeyMultipleCount).isEqualTo(1);
        assertThat(mSession.mKeyMultipleCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyMultipleEvent, event);
    }

    @Test
    public void testCreateBiInteractiveApp() {
        assertNotNull(mSession);
        mSession.resetValues();
        mCallback.resetValues();
        final Bundle bundle = createTestBundle();
        final Uri uri = createTestUri();
        final String biIAppId = "biIAppId";

        mTvIAppView.createBiInteractiveApp(uri, bundle);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mBiIAppCreatedCount > 0);

        assertThat(mSession.mCreateBiIAppCount).isEqualTo(1);
        assertThat(mSession.mCreateBiIAppUri).isEqualTo(uri);
        assertBundlesAreEqual(mSession.mCreateBiIAppParams, bundle);

        assertThat(mCallback.mIAppServiceId).isEqualTo(mStubInfo.getId());
        assertThat(mCallback.mBiIAppUri).isEqualTo(uri);
        assertThat(mCallback.mBiIAppId).isEqualTo(biIAppId);

        mTvIAppView.destroyBiInteractiveApp(biIAppId);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mDestroyBiIAppCount > 0);

        assertThat(mSession.mDestroyBiIAppCount).isEqualTo(1);
        assertThat(mSession.mDestroyBiIAppId).isEqualTo(biIAppId);
    }

    @Test
    public void testTuned() {
        linkTvView();

        mInputSession.notifyTuned(CHANNEL_0);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTunedCount > 0);

        assertThat(mSession.mTunedCount).isEqualTo(1);
        assertThat(mSession.mTunedUri).isEqualTo(CHANNEL_0);
    }

    @Test
    public void testVideoAvailable() {
        linkTvView();

        mInputSession.notifyVideoAvailable();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mVideoAvailableCount > 0);

        assertThat(mSession.mVideoAvailableCount).isEqualTo(1);
    }

    @Test
    public void testAdRequest() throws Throwable {
        linkTvView();

        File tmpFile = File.createTempFile("cts_tv_interactive_app", "tias_test");
        ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE);
        AdRequest adRequest = new AdRequest(
                567, AdRequest.REQUEST_TYPE_START, fd, 787L, 989L, 100L, "MMM", new Bundle());
        mSession.requestAd(adRequest);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mAdRequestCount > 0);

        assertThat(mInputSession.mAdRequestCount).isEqualTo(1);
        assertThat(mInputSession.mAdRequest.getId()).isEqualTo(567);
        assertThat(mInputSession.mAdRequest.getRequestType())
                .isEqualTo(AdRequest.REQUEST_TYPE_START);
        assertNotNull(mInputSession.mAdRequest.getFileDescriptor());
        assertThat(mInputSession.mAdRequest.getStartTimeMillis()).isEqualTo(787L);
        assertThat(mInputSession.mAdRequest.getStopTimeMillis()).isEqualTo(989L);
        assertThat(mInputSession.mAdRequest.getEchoIntervalMillis()).isEqualTo(100L);
        assertThat(mInputSession.mAdRequest.getMediaFileType()).isEqualTo("MMM");
        assertNotNull(mInputSession.mAdRequest.getMetadata());

        fd.close();
        tmpFile.delete();
    }

    @Test
    public void testAdResponse() throws Throwable {
        linkTvView();

        AdResponse adResponse = new AdResponse(767, AdResponse.RESPONSE_TYPE_PLAYING, 909L);
        mInputSession.notifyAdResponse(adResponse);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mAdResponseCount > 0);

        assertThat(mSession.mAdResponseCount).isEqualTo(1);
        assertThat(mSession.mAdResponse.getResponseType())
                .isEqualTo(AdResponse.RESPONSE_TYPE_PLAYING);
        assertThat(mSession.mAdResponse.getElapsedTimeMillis()).isEqualTo(909L);
    }

    // TODO: check the counts and values
    @Test
    public void testSignalStrength() throws Throwable {
        linkTvView();

        mInputSession.notifySignalStrength(TvInputManager.SIGNAL_STRENGTH_STRONG);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testTeletextAppState() throws Throwable {
        mSession.notifyTeletextAppStateChanged(TvInteractiveAppManager.TELETEXT_APP_STATE_HIDE);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestCurrentChannelLcn() throws Throwable {
        mSession.requestCurrentChannelLcn();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestCurrentTvInputId() throws Throwable {
        mSession.requestCurrentTvInputId();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestStreamVolume() throws Throwable {
        mSession.requestStreamVolume();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestTrackInfoList() throws Throwable {
        mSession.requestTrackInfoList();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendPlaybackCommandRequest() throws Throwable {
        mSession.sendPlaybackCommandRequest(mStubInfo.getId(), createTestBundle());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetMediaViewEnabled() throws Throwable {
        mSession.setMediaViewEnabled(false);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetVideoBounds() throws Throwable {
        mSession.setVideoBounds(new Rect());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testResetInteractiveApp() throws Throwable {
        mTvIAppView.resetInteractiveApp();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentChannelLcn() throws Throwable {
        mTvIAppView.sendCurrentChannelLcn(1);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentChannelUri() throws Throwable {
        mTvIAppView.sendCurrentChannelUri(createTestUri());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentTvInputId() throws Throwable {
        mTvIAppView.sendCurrentTvInputId("input_id");
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendStreamVolume() throws Throwable {
        mTvIAppView.sendStreamVolume(0.1f);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendTrackInfoList() throws Throwable {
        mTvIAppView.sendTrackInfoList(new ArrayList<TvTrackInfo>());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetTeletextAppEnabled() throws Throwable {
        mTvIAppView.setTeletextAppEnabled(false);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testTsRequest() throws Throwable {
        linkTvView();

        TsRequest request = new TsRequest(1, BroadcastInfoRequest.REQUEST_OPTION_REPEAT, 1);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (TsRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TS);
        assertThat(request.getRequestId()).isEqualTo(1);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTsPid()).isEqualTo(1);
    }

    @Test
    public void testTsResponse() {
        linkTvView();

        TsResponse response = new TsResponse(1, 1, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                "TestToken");
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (TsResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TS);
        assertThat(response.getRequestId()).isEqualTo(1);
        assertThat(response.getSequence()).isEqualTo(1);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSharedFilterToken()).isEqualTo("TestToken");
    }

    public static void assertKeyEventEquals(KeyEvent actual, KeyEvent expected) {
        if (expected != null && actual != null) {
            assertThat(actual.getDownTime()).isEqualTo(expected.getDownTime());
            assertThat(actual.getEventTime()).isEqualTo(expected.getEventTime());
            assertThat(actual.getAction()).isEqualTo(expected.getAction());
            assertThat(actual.getKeyCode()).isEqualTo(expected.getKeyCode());
            assertThat(actual.getRepeatCount()).isEqualTo(expected.getRepeatCount());
            assertThat(actual.getMetaState()).isEqualTo(expected.getMetaState());
            assertThat(actual.getDeviceId()).isEqualTo(expected.getDeviceId());
            assertThat(actual.getScanCode()).isEqualTo(expected.getScanCode());
            assertThat(actual.getFlags()).isEqualTo(expected.getFlags());
            assertThat(actual.getSource()).isEqualTo(expected.getSource());
            assertThat(actual.getCharacters()).isEqualTo(expected.getCharacters());
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static void assertBundlesAreEqual(Bundle actual, Bundle expected) {
        if (expected != null && actual != null) {
            assertThat(actual.keySet()).isEqualTo(expected.keySet());
            for (String key : expected.keySet()) {
                assertThat(actual.get(key)).isEqualTo(expected.get(key));
            }
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }
}
