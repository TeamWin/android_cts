/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.ParcelUuid;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CallComposerTest {
    private static final String TEST_FILE_NAME = "red_velvet_cupcake.png";
    private static final String TEST_FILE_CONTENT_TYPE = "image/png";
    private static final long TEST_TIMEOUT_MILLIS = 5000;

    private String mPreviousDefaultDialer;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        overrideDefaultDialer();
    }

    @After
    public void tearDown() throws Exception {
        restoreDefaultDialer();
        Files.deleteIfExists(mContext.getFilesDir().toPath().resolve(TEST_FILE_NAME));
    }

    @Test
    public void testUploadPictureWithFile() throws Exception {
        Path testFile = mContext.getFilesDir().toPath().resolve(TEST_FILE_NAME);
        byte[] imageData = getSamplePictureAsBytes();
        Files.write(testFile, imageData);

        pictureUploadHelper(testFile, null, -1);
    }

    @Test
    public void testUploadPictureAsStream() throws Exception {
        byte[] imageData = getSamplePictureAsBytes();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);

        pictureUploadHelper(null, inputStream, -1);
    }

    @Test
    public void testExcessivelyLargePictureAsFile() throws Exception {
        int targetSize = (int) TelephonyManager.getMaximumCallComposerPictureSize() + 1;
        byte[] imageData = getSamplePictureAsBytes();
        byte[] paddedData = new byte[targetSize];
        System.arraycopy(imageData, 0, paddedData, 0, imageData.length);
        Path testFile = mContext.getFilesDir().toPath().resolve(TEST_FILE_NAME);
        Files.write(testFile, paddedData);

        pictureUploadHelper(testFile, null,
                TelephonyManager.CallComposerException.ERROR_FILE_TOO_LARGE);
    }

    @Test
    public void testExcessivelyLargePictureAsStream() throws Exception {
        int targetSize = (int) TelephonyManager.getMaximumCallComposerPictureSize() + 1;
        byte[] imageData = getSamplePictureAsBytes();
        byte[] paddedData = new byte[targetSize];
        System.arraycopy(imageData, 0, paddedData, 0, imageData.length);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(paddedData);

        pictureUploadHelper(null, inputStream,
                TelephonyManager.CallComposerException.ERROR_FILE_TOO_LARGE);
    }

    private void pictureUploadHelper(Path inputFile, InputStream inputStream,
            int expectedErrorCode) throws Exception {
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        CompletableFuture<Pair<ParcelUuid, TelephonyManager.CallComposerException>> resultFuture =
                new CompletableFuture<>();
        OutcomeReceiver<ParcelUuid, TelephonyManager.CallComposerException> callback =
                new OutcomeReceiver<ParcelUuid, TelephonyManager.CallComposerException>() {
                    @Override
                    public void onResult(@NonNull ParcelUuid result) {
                        resultFuture.complete(Pair.create(result, null));
                    }

                    @Override
                    public void onError(TelephonyManager.CallComposerException error) {
                        resultFuture.complete(Pair.create(null, error));
                    }
        };

        if (inputFile != null) {
            tm.uploadCallComposerPicture(inputFile, TEST_FILE_CONTENT_TYPE,
                    Executors.newSingleThreadExecutor(), callback);
        } else {
            tm.uploadCallComposerPicture(inputStream, TEST_FILE_CONTENT_TYPE,
                    Executors.newSingleThreadExecutor(), callback);
        }

        Pair<ParcelUuid, TelephonyManager.CallComposerException> result;
        try {
            result = resultFuture.get(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fail("Timed out waiting for response from TelephonyManager");
            return;
        }

        if (result.second != null && expectedErrorCode < 0) {
            String error = TelephonyUtils.parseErrorCodeToString(result.second.getErrorCode(),
                    TelephonyManager.CallComposerException.class, "ERROR_");
            fail("Upload failed with " + error
                    + "\nIOException: " + result.second.getIOException());
        } else if (expectedErrorCode >= 0) {
            String expectedError = TelephonyUtils.parseErrorCodeToString(expectedErrorCode,
                    TelephonyManager.CallComposerException.class, "ERROR_");
            if (result.second == null) {
                fail("Did not get the expected error: " + expectedError);
            } else if (result.first != null) {
                fail("Got a UUID from Telephony when we expected " + expectedError);
            } else if (result.second.getErrorCode() != expectedErrorCode) {
                String observedError =
                        TelephonyUtils.parseErrorCodeToString(result.second.getErrorCode(),
                                TelephonyManager.CallComposerException.class, "ERROR_");
                fail("Expected " + expectedError + ", got " + observedError);
            }
            // If we expected an error, the test ends here
            return;
        }

        assertNotNull(result.first);
        // TODO: test the actual upload and/or storage to the call log.

        // Make sure that any file descriptors opened to the test file have been closed.
        if (inputFile != null) {
            try {
                Files.newOutputStream(inputFile, StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND).close();
            } catch (IOException e) {
                fail("Couldn't open+close the file after upload -- leaked fd? " + e);
            }
        }
    }

    private byte[] getSamplePictureAsBytes() throws Exception {
        InputStream resourceInput = mContext.getResources().openRawResource(R.drawable.cupcake);
        return readBytes(resourceInput);
    }

    private static byte[] readBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int numRead;
        do {
            numRead = inputStream.read(buffer);
            if (numRead > 0) output.write(buffer, 0, numRead);
        } while (numRead > 0);
        return output.toByteArray();
    }

    private void overrideDefaultDialer() throws Exception {
        mPreviousDefaultDialer = TelephonyUtils.executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), "telecom get-default-dialer");
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd role add-role-holder --user " + UserHandle.myUserId()
                        + " android.app.role.DIALER " + mContext.getPackageName());
    }

    private void restoreDefaultDialer() throws Exception {
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd role add-role-holder --user " + UserHandle.myUserId()
                        + " android.app.role.DIALER " + mPreviousDefaultDialer);
    }
}
