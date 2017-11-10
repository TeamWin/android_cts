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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.MbmsDownloadSession;
import android.telephony.cts.embmstestapp.CtsDownloadService;
import android.telephony.mbms.MbmsDownloadReceiver;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class MbmsDownloadFlowTest extends MbmsDownloadTestBase {
    private File tempFileRootDir;
    private String tempFileRootDirPath;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        tempFileRootDir = new File(mContext.getFilesDir(), "CtsTestDir");
        tempFileRootDir.mkdir();
        tempFileRootDirPath = tempFileRootDir.getCanonicalPath();
        mDownloadSession.setTempFileRootDirectory(tempFileRootDir);
    }

    @Override
    public void tearDown() throws Exception {
        recursiveDelete(tempFileRootDir);
        tempFileRootDir = null;
        super.tearDown();
    }

    public void testFileDownloadFlow() throws Exception {
        MbmsDownloadReceiverTest.AppIntentCapture captor =
                new MbmsDownloadReceiverTest.AppIntentCapture(mContext, mCallbackHandler);
        mDownloadSession.download(MbmsDownloadReceiverTest.TEST_DOWNLOAD_REQUEST);
        mMiddlewareControl.actuallyStartDownloadFlow();
        Intent downloadDoneIntent = captor.getIntent();

        assertEquals(MbmsDownloadReceiverTest.APP_INTENT_ACTION, downloadDoneIntent.getAction());
        assertEquals(MbmsDownloadSession.RESULT_SUCCESSFUL,
                downloadDoneIntent.getIntExtra(MbmsDownloadSession.EXTRA_MBMS_DOWNLOAD_RESULT, -1));
        assertEquals(MbmsDownloadReceiverTest.TEST_DOWNLOAD_REQUEST,
                downloadDoneIntent.getParcelableExtra(
                        MbmsDownloadSession.EXTRA_MBMS_DOWNLOAD_REQUEST));
        assertEquals(CtsDownloadService.FILE_INFO,
                downloadDoneIntent.getParcelableExtra(MbmsDownloadSession.EXTRA_MBMS_FILE_INFO));
        Uri fileUri = downloadDoneIntent.getParcelableExtra(
                MbmsDownloadSession.EXTRA_MBMS_COMPLETED_FILE_URI);
        InputStream is = mContext.getContentResolver().openInputStream(fileUri);
        byte[] contents = new byte[CtsDownloadService.SAMPLE_FILE_DATA.length];
        is.read(contents);
        for (int i = 0; i < contents.length; i++) {
            assertEquals(contents[i], CtsDownloadService.SAMPLE_FILE_DATA[i]);
        }

        List<Bundle> downloadResultAck =
                getMiddlewareCalls(CtsDownloadService.METHOD_DOWNLOAD_RESULT_ACK);
        assertEquals(1, downloadResultAck.size());
        assertEquals(MbmsDownloadReceiver.RESULT_OK,
                downloadResultAck.get(0).getInt(CtsDownloadService.ARGUMENT_RESULT_CODE, -1));
    }
}
