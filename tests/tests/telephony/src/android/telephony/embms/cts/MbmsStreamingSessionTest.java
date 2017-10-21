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

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.MbmsStreamingSession;
import android.telephony.cts.embmstestapp.CtsStreamingService;
import android.telephony.cts.embmstestapp.ICtsMiddlewareControl;
import android.telephony.mbms.MbmsErrors;
import android.telephony.mbms.MbmsStreamingSessionCallback;
import android.telephony.mbms.ServiceInfo;
import android.telephony.mbms.StreamingServiceInfo;
import android.test.InstrumentationTestCase;

import com.android.internal.os.SomeArgs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MbmsStreamingSessionTest extends InstrumentationTestCase {
    private static final int ASYNC_TIMEOUT = 10000;

    private class TestCallback extends MbmsStreamingSessionCallback {
        private final BlockingQueue<SomeArgs> mErrorCalls = new LinkedBlockingQueue<>();
        private final BlockingQueue<SomeArgs> mStreamingServicesUpdatedCalls =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<SomeArgs> mMiddlewareReadyCalls = new LinkedBlockingQueue<>();
        private int mNumErrorCalls = 0;
        private int mNumStreamingServicesUpdatedCalls = 0;
        private int mNumMiddlwareReadyCalls = 0;

        @Override
        public void onError(int errorCode, @Nullable String message) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = errorCode;
            args.arg2 = message;
            mErrorCalls.add(args);
        }

        @Override
        public void onStreamingServicesUpdated(List<StreamingServiceInfo> services) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = services;
            mStreamingServicesUpdatedCalls.add(args);
        }

        @Override
        public void onMiddlewareReady() {
            mMiddlewareReadyCalls.add(SomeArgs.obtain());
        }

        public SomeArgs waitOnError() {
            try {
                return mErrorCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        public SomeArgs waitOnStreamingServicesUpdated() {
            try {
                return mStreamingServicesUpdatedCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        public boolean waitOnMiddlewareReady() {
            try {
                return mMiddlewareReadyCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS) != null;
            } catch (InterruptedException e) {
                return false;
            }
        }

        public int getNumErrorCalls() {
            return mNumErrorCalls;
        }
    }

    private Context mContext;
    private HandlerThread mHandlerThread;
    private Handler mCallbackHandler;
    private ICtsMiddlewareControl mMiddlewareControl;
    private MbmsStreamingSession mStreamingSession;
    private TestCallback mCallback = new TestCallback();

    @Override
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mHandlerThread = new HandlerThread("EmbmsCtsTestWorker");
        mHandlerThread.start();
        mCallbackHandler = new Handler(mHandlerThread.getLooper());
        getControlBinder();
        setupStreamingSession();
    }

    @Override
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        mStreamingSession.close();
        mMiddlewareControl.reset();
    }

    private void setupStreamingSession() throws Exception {
        mStreamingSession = MbmsStreamingSession.create(
                mContext, mCallback, mCallbackHandler);
        assertNotNull(mStreamingSession);
        assertTrue(mCallback.waitOnMiddlewareReady());
        assertEquals(0, mCallback.getNumErrorCalls());
        List initializeCall = (List) mMiddlewareControl.getStreamingSessionCalls().get(0);
        assertEquals(CtsStreamingService.METHOD_INITIALIZE, initializeCall.get(0));
    }

    public void testDuplicateSession() throws Exception {
        try {
            MbmsStreamingSession failure = MbmsStreamingSession.create(
                    mContext, mCallback, mCallbackHandler);
            fail("Duplicate create should've thrown an exception");
        } catch (IllegalStateException e) {
            // Succeed
        }
    }

    public void testRequestUpdateStreamingServices() throws Exception {
        List<String> testClasses = Arrays.asList("class1", "class2");
        mStreamingSession.requestUpdateStreamingServices(testClasses);

        // Make sure we got the streaming services
        List<StreamingServiceInfo> serviceInfos =
                (List<StreamingServiceInfo>) mCallback.waitOnStreamingServicesUpdated().arg1;
        assertEquals((ServiceInfo) CtsStreamingService.STREAMING_SERVICE_INFO,
                (ServiceInfo) serviceInfos.get(0));
        assertEquals(0, mCallback.getNumErrorCalls());

        // Make sure the middleware got the call with the right args
        List<List> requestStreamingServicesCalls =
                getMiddlewareCalls(CtsStreamingService.METHOD_REQUEST_UPDATE_STREAMING_SERVICES);
        assertEquals(1, requestStreamingServicesCalls.size());
        assertEquals(3, requestStreamingServicesCalls.get(0).size());
        List<String> middlewareReceivedServiceClasses =
                (List<String>) requestStreamingServicesCalls.get(0).get(2);
        assertEquals(testClasses.size(), middlewareReceivedServiceClasses.size());
        for (int i = 0; i < testClasses.size(); i++) {
            assertEquals(testClasses.get(i), middlewareReceivedServiceClasses.get(i));
        }
    }

    public void testClose() throws Exception {
        mStreamingSession.close();

        // Make sure we can't use it anymore
        try {
            mStreamingSession.requestUpdateStreamingServices(Collections.emptyList());
            fail("Streaming session should not be usable after close");
        } catch (IllegalStateException e) {
            // Succeed
        }

        // Make sure that the middleware got the call to close
        List<List> closeCalls = getMiddlewareCalls(CtsStreamingService.METHOD_CLOSE);
        assertEquals(1, closeCalls.size());
    }

    public void testErrorDelivery() throws Exception {
        mMiddlewareControl.forceErrorCode(
                MbmsErrors.GeneralErrors.ERROR_MIDDLEWARE_TEMPORARILY_UNAVAILABLE);
        mStreamingSession.requestUpdateStreamingServices(Collections.emptyList());
        assertEquals(MbmsErrors.GeneralErrors.ERROR_MIDDLEWARE_TEMPORARILY_UNAVAILABLE,
                mCallback.waitOnError().arg1);
    }

    private void getControlBinder() throws InterruptedException {
        Intent bindIntent = new Intent(CtsStreamingService.CONTROL_INTERFACE_ACTION);
        bindIntent.setComponent(CtsStreamingService.CONTROL_INTERFACE_COMPONENT);
        final CountDownLatch bindLatch = new CountDownLatch(1);

        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mMiddlewareControl = ICtsMiddlewareControl.Stub.asInterface(service);
                bindLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mMiddlewareControl = null;
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        bindLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private List<List> getMiddlewareCalls(String methodName) throws RemoteException {
        return ((List<List>) mMiddlewareControl.getStreamingSessionCalls()).stream()
                .filter((elem) -> elem.get(0).equals(methodName))
                .collect(Collectors.toList());
    }
}