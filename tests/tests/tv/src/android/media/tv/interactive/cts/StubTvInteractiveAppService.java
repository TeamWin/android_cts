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
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppService;
import android.view.KeyEvent;
import android.view.Surface;

/**
 * Stub implementation of (@link android.media.tv.interactive.TvInteractiveAppService}.
 */
public class StubTvInteractiveAppService extends TvInteractiveAppService {

    public static StubSessionImpl sSession;

    @Override
    public Session onCreateSession(String iAppServiceId, int type) {
        sSession = new StubSessionImpl(this);
        return sSession;
    }

    public static class StubSessionImpl extends Session {
        StubSessionImpl(Context context) {
            super(context);
        }

        @Override
        public void onStartInteractiveApp() {
            notifySessionStateChanged(
                    TvInteractiveAppManager.SERVICE_STATE_READY,
                    TvInteractiveAppManager.ERROR_NONE);
        }

        @Override
        public void onRelease() {
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            return false;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            return false;
        }

        @Override
        public boolean onKeyLongPress(int keyCode, KeyEvent event) {
            return false;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            return false;
        }

        @Override
        public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
            return false;
        }
    }
}
