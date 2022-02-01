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

import android.content.Context;
import android.media.tv.AdResponse;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.interactive.AppLinkInfo;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppService;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Surface;

/**
 * Stub implementation of (@link android.media.tv.interactive.TvInteractiveAppService}.
 */
public class StubTvInteractiveAppService extends TvInteractiveAppService {

    public static StubSessionImpl sSession;
    public static int sType;
    public static Bundle sAppLinkCommand = null;

    @Override
    public Session onCreateSession(String iAppServiceId, int type) {
        sSession = new StubSessionImpl(this);
        return sSession;
    }

    @Override
    public void onPrepare(int type) {
        sType = type;
        notifyStateChanged(
                sType,
                TvInteractiveAppManager.SERVICE_STATE_PREPARING,
                TvInteractiveAppManager.ERROR_NONE);
    }

    @Override
    public void onAppLinkCommand(Bundle command) {
        sAppLinkCommand = command;
    }

    @Override
    public void onRegisterAppLinkInfo(AppLinkInfo bundle) {
    }

    @Override
    public void onUnregisterAppLinkInfo(AppLinkInfo bundle) {
    }

    public static class StubSessionImpl extends Session {
        public int mSetSurfaceCount;
        public int mSurfaceChangedCount;
        public int mStartInteractiveAppCount;
        public int mStopInteractiveAppCount;
        public int mKeyDownCount;
        public int mKeyUpCount;
        public int mKeyMultipleCount;
        public int mVideoAvailableCount;
        public int mTunedCount;
        public int mCreateBiIAppCount;
        public int mDestroyBiIAppCount;
        public int mAdResponseCount;
        public int mBroadcastInfoResponseCount;

        public Integer mKeyDownCode;
        public Integer mKeyUpCode;
        public Integer mKeyMultipleCode;
        public KeyEvent mKeyDownEvent;
        public KeyEvent mKeyUpEvent;
        public KeyEvent mKeyMultipleEvent;
        public Uri mTunedUri;
        public Uri mCreateBiIAppUri;
        public Bundle mCreateBiIAppParams;
        public String mDestroyBiIAppId;
        public AdResponse mAdResponse;
        public BroadcastInfoResponse mBroadcastInfoResponse;

        StubSessionImpl(Context context) {
            super(context);
        }

        public void resetValues() {
            mSetSurfaceCount = 0;
            mSurfaceChangedCount = 0;
            mStartInteractiveAppCount = 0;
            mStopInteractiveAppCount = 0;
            mKeyDownCount = 0;
            mKeyUpCount = 0;
            mKeyMultipleCount = 0;
            mVideoAvailableCount = 0;
            mTunedCount = 0;
            mCreateBiIAppCount = 0;
            mDestroyBiIAppCount = 0;
            mAdResponseCount = 0;
            mBroadcastInfoResponseCount = 0;

            mKeyDownCode = null;
            mKeyUpCode = null;
            mKeyMultipleCode = null;
            mKeyDownEvent = null;
            mKeyUpEvent = null;
            mKeyMultipleEvent = null;
            mTunedUri = null;
            mCreateBiIAppUri = null;
            mCreateBiIAppParams = null;
            mDestroyBiIAppId = null;
            mAdResponse = null;
            mBroadcastInfoResponse = null;
        }

        @Override
        public void onStartInteractiveApp() {
            mStartInteractiveAppCount++;
            notifySessionStateChanged(
                    TvInteractiveAppManager.INTERACTIVE_APP_STATE_RUNNING,
                    TvInteractiveAppManager.ERROR_NONE);
        }

        @Override
        public void onStopInteractiveApp() {
            mStopInteractiveAppCount++;
        }

        @Override
        public void onRelease() {
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            mSetSurfaceCount++;
            return false;
        }

        @Override
        public void onSurfaceChanged(int format, int width, int height) {
            mSurfaceChangedCount++;
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
        public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
            mKeyMultipleCount++;
            mKeyMultipleCode = keyCode;
            mKeyMultipleEvent = event;
            return false;
        }

        @Override
        public void onCreateBiInteractiveApp(Uri biIAppUri, Bundle params) {
            mCreateBiIAppCount++;
            mCreateBiIAppUri = biIAppUri;
            mCreateBiIAppParams = params;
            notifyBiInteractiveAppCreated(biIAppUri, "biIAppId");
        }

        @Override
        public void onDestroyBiInteractiveApp(String biIAppId) {
            mDestroyBiIAppCount++;
            mDestroyBiIAppId = biIAppId;
        }

        @Override
        public void onTuned(Uri uri) {
            mTunedCount++;
            mTunedUri = uri;
        }

        @Override
        public void onVideoAvailable() {
            mVideoAvailableCount++;
        }

        @Override
        public void onAdResponse(AdResponse response) {
            mAdResponseCount++;
            mAdResponse = response;
        }

        @Override
        public void onBroadcastInfoResponse(BroadcastInfoResponse response) {
            mBroadcastInfoResponseCount++;
            mBroadcastInfoResponse = response;
        }

        @Override
        public void onContentAllowed() {
        }

        @Override
        public void onContentBlocked(android.media.tv.TvContentRating rating) {
        }

        @Override
        public android.view.View onCreateMediaView() {
            return null;
        }

        @Override
        public void onCurrentChannelLcn(int lcn) {
        }

        @Override
        public void onCurrentChannelUri(android.net.Uri uri) {
        }

        @Override
        public void onCurrentTvInputId(String id) {
        }

        @Override
        public boolean onGenericMotionEvent(android.view.MotionEvent event) {
            return false;
        }

        @Override
        public void onMediaViewSizeChanged(int w, int h) {
        }

        @Override
        public void onResetInteractiveApp() {
        }

        @Override
        public void onSetTeletextAppEnabled(boolean enable) {
        }

        @Override
        public void onSignalStrength(int strength) {
        }

        @Override
        public void onStreamVolume(float v) {
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            return false;
        }

        @Override
        public void onTrackInfoList(java.util.List<android.media.tv.TvTrackInfo> infos) {
        }

        @Override
        public void onTrackSelected(int type, String id) {
        }

        @Override
        public boolean onTrackballEvent(android.view.MotionEvent event) {
            return false;
        }

        @Override
        public void onTracksChanged(java.util.List<android.media.tv.TvTrackInfo> info) {
        }

        @Override
        public void onVideoUnavailable(int reason) {
        }
    }
}
