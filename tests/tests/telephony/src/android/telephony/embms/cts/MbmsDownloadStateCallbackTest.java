/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.telephony.embms.cts;

import android.telephony.MbmsDownloadSession;
import android.telephony.cts.embmstestapp.CtsDownloadService;
import android.telephony.mbms.DownloadRequest;
import android.telephony.mbms.DownloadStateCallback;
import android.telephony.mbms.FileInfo;

import com.android.internal.os.SomeArgs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MbmsDownloadStateCallbackTest extends MbmsDownloadTestBase {
    private static final long SHORT_TIMEOUT = 500;

    private class TestDSCallback extends DownloadStateCallback {
        private final BlockingQueue<SomeArgs> mProgressUpdatedCalls = new LinkedBlockingQueue<>();
        private final BlockingQueue<SomeArgs> mStateUpdatedCalls = new LinkedBlockingQueue<>();

        public TestDSCallback(int filterFlags) {
            super(filterFlags);
        }

        @Override
        public void onProgressUpdated(DownloadRequest request, FileInfo fileInfo, int
                currentDownloadSize, int fullDownloadSize, int currentDecodedSize, int
                fullDecodedSize) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = request;
            args.arg2 = fileInfo;
            args.arg3 = currentDownloadSize;
            args.arg4 = fullDownloadSize;
            args.arg5 = currentDecodedSize;
            args.arg6 = fullDecodedSize;
            mProgressUpdatedCalls.add(args);
        }

        @Override
        public void onStateUpdated(DownloadRequest request, FileInfo fileInfo,
                @MbmsDownloadSession.DownloadStatus int state) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = request;
            args.arg2 = fileInfo;
            args.arg3 = state;
            mStateUpdatedCalls.add(args);
        }

        public SomeArgs waitOnProgressUpdated(long timeout) {
            try {
                return mProgressUpdatedCalls.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        public SomeArgs waitOnStateUpdated(long timeout) {
            try {
                return mStateUpdatedCalls.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }
    }

    public void testFullCallback() throws Exception {
        int sampleInt = 10;
        TestDSCallback callback = new TestDSCallback(DownloadStateCallback.ALL_UPDATES);
        DownloadRequest request = DOWNLOAD_REQUEST_TEMPLATE.build();
        mDownloadSession.registerStateCallback(request, callback, mCallbackHandler);
        mMiddlewareControl.fireOnProgressUpdated(request, CtsDownloadService.FILE_INFO,
                sampleInt, sampleInt, sampleInt, sampleInt);
        SomeArgs progressArgs = callback.waitOnProgressUpdated(ASYNC_TIMEOUT);
        assertEquals(request, progressArgs.arg1);
        assertEquals(CtsDownloadService.FILE_INFO, progressArgs.arg2);
        assertEquals(sampleInt, progressArgs.arg3);
        assertEquals(sampleInt, progressArgs.arg4);
        assertEquals(sampleInt, progressArgs.arg5);
        assertEquals(sampleInt, progressArgs.arg6);

        mMiddlewareControl.fireOnStateUpdated(request, CtsDownloadService.FILE_INFO, sampleInt);
        SomeArgs stateArgs = callback.waitOnStateUpdated(ASYNC_TIMEOUT);
        assertEquals(request, stateArgs.arg1);
        assertEquals(CtsDownloadService.FILE_INFO, stateArgs.arg2);
        assertEquals(sampleInt, stateArgs.arg3);
    }

    public void testDeregistration() throws Exception {
        TestDSCallback callback = new TestDSCallback(DownloadStateCallback.ALL_UPDATES);
        DownloadRequest request = DOWNLOAD_REQUEST_TEMPLATE.build();
        mDownloadSession.registerStateCallback(request, callback, mCallbackHandler);
        mDownloadSession.unregisterStateCallback(request, callback);

        mMiddlewareControl.fireOnStateUpdated(null, null, 0);
        assertNull(callback.waitOnStateUpdated(SHORT_TIMEOUT));
        mMiddlewareControl.fireOnProgressUpdated(null, null, 0, 0, 0, 0);
        assertNull(callback.waitOnProgressUpdated(SHORT_TIMEOUT));
    }

    public void testCallbackFiltering1() throws Exception {
        TestDSCallback callback = new TestDSCallback(DownloadStateCallback.PROGRESS_UPDATES);
        DownloadRequest request = DOWNLOAD_REQUEST_TEMPLATE.build();
        mDownloadSession.registerStateCallback(request, callback, mCallbackHandler);

        mMiddlewareControl.fireOnStateUpdated(null, null, 0);
        assertNull(callback.waitOnStateUpdated(SHORT_TIMEOUT));
        mMiddlewareControl.fireOnProgressUpdated(null, null, 0, 0, 0, 0);
        assertNotNull(callback.waitOnProgressUpdated(SHORT_TIMEOUT));
    }

    public void testCallbackFiltering2() throws Exception {
        TestDSCallback callback = new TestDSCallback(DownloadStateCallback.STATE_UPDATES);
        DownloadRequest request = DOWNLOAD_REQUEST_TEMPLATE.build();
        mDownloadSession.registerStateCallback(request, callback, mCallbackHandler);

        mMiddlewareControl.fireOnStateUpdated(null, null, 0);
        assertNotNull(callback.waitOnStateUpdated(SHORT_TIMEOUT));
        mMiddlewareControl.fireOnProgressUpdated(null, null, 0, 0, 0, 0);
        assertNull(callback.waitOnProgressUpdated(SHORT_TIMEOUT));
    }
}
