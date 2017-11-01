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

import android.os.Bundle;
import android.telephony.MbmsDownloadSession;
import android.telephony.cts.embmstestapp.CtsDownloadService;
import android.telephony.mbms.FileServiceInfo;
import android.telephony.mbms.MbmsErrors;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MbmsDownloadSessionTest extends MbmsDownloadTestBase {
    public void testDuplicateSession() throws Exception {
        try {
            MbmsDownloadSession failure = MbmsDownloadSession.create(
                    mContext, mCallback, mCallbackHandler);
            fail("Duplicate create should've thrown an exception");
        } catch (IllegalStateException e) {
            // Succeed
        }
    }

    public void testRequestUpdateDownloadServices() throws Exception {
        List<String> testClasses = Arrays.asList("class1", "class2");
        mDownloadSession.requestUpdateFileServices(testClasses);

        // Make sure we got the streaming services
        List<FileServiceInfo> serviceInfos =
                (List<FileServiceInfo>) mCallback.waitOnFileServicesUpdated().arg1;
        assertEquals(CtsDownloadService.FILE_SERVICE_INFO, serviceInfos.get(0));
        assertEquals(0, mCallback.getNumErrorCalls());

        // Make sure the middleware got the call with the right args
        List<Bundle> requestDownloadServicesCalls =
                getMiddlewareCalls(CtsDownloadService.METHOD_REQUEST_UPDATE_FILE_SERVICES);
        assertEquals(1, requestDownloadServicesCalls.size());
        List<String> middlewareReceivedServiceClasses =
                 requestDownloadServicesCalls.get(0)
                         .getStringArrayList(CtsDownloadService.ARGUMENT_SERVICE_CLASSES);
        assertEquals(testClasses.size(), middlewareReceivedServiceClasses.size());
        for (int i = 0; i < testClasses.size(); i++) {
            assertEquals(testClasses.get(i), middlewareReceivedServiceClasses.get(i));
        }
    }

    public void testClose() throws Exception {
        mDownloadSession.close();

        // Make sure we can't use it anymore
        try {
            mDownloadSession.requestUpdateFileServices(Collections.emptyList());
            fail("Download session should not be usable after close");
        } catch (IllegalStateException e) {
            // Succeed
        }

        // Make sure that the middleware got the call to close
        List<Bundle> closeCalls = getMiddlewareCalls(CtsDownloadService.METHOD_CLOSE);
        assertEquals(1, closeCalls.size());
    }

    public void testSetTempFileDirectory() throws Exception {
        String tempFileDirName = "CTSTestDir";
        File tempFileRootDirectory = new File(mContext.getFilesDir(), tempFileDirName);
        tempFileRootDirectory.mkdirs();

        mDownloadSession.setTempFileRootDirectory(tempFileRootDirectory);
        List<Bundle> setTempRootCalls =
                getMiddlewareCalls(CtsDownloadService.METHOD_SET_TEMP_FILE_ROOT);
        assertEquals(1, setTempRootCalls.size());
        assertEquals(tempFileRootDirectory.getCanonicalPath(),
                setTempRootCalls.get(0).getString(CtsDownloadService.ARGUMENT_ROOT_DIRECTORY_PATH));
    }

    public void testErrorDelivery() throws Exception {
        mMiddlewareControl.forceErrorCode(
                MbmsErrors.GeneralErrors.ERROR_MIDDLEWARE_TEMPORARILY_UNAVAILABLE);
        mDownloadSession.requestUpdateFileServices(Collections.emptyList());
        assertEquals(MbmsErrors.GeneralErrors.ERROR_MIDDLEWARE_TEMPORARILY_UNAVAILABLE,
                mCallback.waitOnError().arg1);
    }
}
