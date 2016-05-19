/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.security.cts;

import android.security.cts.R;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.MediaController;
import android.widget.VideoView;

public class DisplayDriverTest extends
        ActivityInstrumentationTestCase2<DisplayDriverActivity> {
    /**
     * The maximum time to wait for an operation.
     */
    private static final int TIMEOUT_ASYNC_PROCESSING = 3000;
    private Activity mActivity;

    public DisplayDriverTest() {
        super(DisplayDriverActivity.class);
    }

    /**
     * Checks whether video crashed or not
     * 1. Initializes mTriggered to false
     * 2. sets mTriggered to true if onError() occurred while playing video
     */
    private static class MockListener {
        private boolean mTriggered;

        MockListener() {
            mTriggered = false;
        }

        public boolean isTriggered() {
            return mTriggered;
        }

        protected void onEvent() {
            mTriggered = true;
        }
    }

    private static class MockOnErrorListener extends MockListener implements
            OnErrorListener {
        public boolean onError(MediaPlayer mp, int what, int extra) {
            super.onEvent();
            return false;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        assertNotNull("Failed to get the activity instance", mActivity);
    }

    /**
     * 1. Runs the vulnerable video by registering OnErrorListener for VideoView
     * 2. Wait for max time taken by video to crash and hit OnErrorListener
     * 3. if video crashed - Pass the test case otherwise Fail the test case
     */
    public void testDisplayDriver_cve_2015_6634() {
        final MockOnErrorListener listener = new MockOnErrorListener();
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaController mMediaControls =
                            new MediaController(mActivity);
                    VideoView mVideoView =
                            (VideoView) mActivity.findViewById(R.id.videoview);
                    mVideoView.setMediaController(mMediaControls);
                    mVideoView.setOnErrorListener(listener);
                    mVideoView.setVideoURI(Uri.parse("android.resource://" +
                            mActivity.getPackageName() + "/" + R.raw.fuzz));
                    mVideoView.start();
                } catch (Exception e) {
                    listener.onError(null, 0, 0);
                }
            }
        });
        SystemClock.sleep(TIMEOUT_ASYNC_PROCESSING);
        assertTrue("Test case failed due to vulnerability in the video: " +
                        "Device is vulenrable to CVE-2015-6634", listener.isTriggered());
    }

    @Override
    protected void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }
        super.tearDown();
    }
}
