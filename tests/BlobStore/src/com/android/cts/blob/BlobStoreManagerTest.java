/*
 * Copyright 2019 The Android Open Source Project
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
package com.android.cts.blob;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.blob.BlobHandle;
import android.app.blob.BlobStoreManager;
import android.content.Context;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;

import com.android.cts.blob.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

@RunWith(AndroidJUnit4.class)
public class BlobStoreManagerTest {
    private static final long TIMEOUT_COMMIT_CALLBACK_SEC = 5;

    private Context mContext;
    private BlobStoreManager mBlobStoreManager;
    private Random mRandom;

    private final ArrayList<Long> mCreatedSessionIds = new ArrayList<>();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mBlobStoreManager = (BlobStoreManager) mContext.getSystemService(
                Context.BLOB_STORE_SERVICE);
        mRandom = new Random(0);
        mCreatedSessionIds.clear();
    }

    @After
    public void tearDown() {
        for (long sessionId : mCreatedSessionIds) {
            try {
                mBlobStoreManager.deleteSession(sessionId);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    public void testGetCreateSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            assertThat(mBlobStoreManager.openSession(sessionId)).isNotNull();
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testDeleteSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            // Verify that session can be opened.
            assertThat(mBlobStoreManager.openSession(sessionId)).isNotNull();

            mBlobStoreManager.deleteSession(sessionId);
            // Verify that trying to open session after it is deleted will throw.
            assertThrows(SecurityException.class, () -> mBlobStoreManager.openSession(sessionId));
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testOpenReadWriteSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);
            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session, 0, blobData.mFileSize);
                blobData.readFromSessionAndVerifyDigest(session);
                blobData.readFromSessionAndVerifyBytes(session,
                        101 /* offset */, 1001 /* length */);

                blobData.writeToSession(session, 202 /* offset */, 2002 /* length */);
                blobData.readFromSessionAndVerifyBytes(session,
                        202 /* offset */, 2002 /* length */);
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAbandonSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                // Verify session can be opened for read/write.
                assertThat(session).isNotNull();
                assertThat(session.openWrite(0, 0)).isNotNull();

                // Verify that trying to read/write to the session after it is abandoned will throw.
                session.abandon();
                assertThrows(IllegalStateException.class, () -> session.openWrite(0, 0));
            }

            // Verify that trying to open the session after it is abandoned will throw.
            assertThrows(SecurityException.class, () -> mBlobStoreManager.openSession(sessionId));
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testCloseSession() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            // Verify session can be opened for read/write.
            BlobStoreManager.Session session = null;
            try {
                session = mBlobStoreManager.openSession(sessionId);
                assertThat(session).isNotNull();
                assertThat(session.openWrite(0, 0)).isNotNull();
            } finally {
                session.close();
            }

            // Verify trying to read/write to session after it is closed will throw.
            // an exception.
            final BlobStoreManager.Session closedSession = session;
            assertThrows(IllegalStateException.class, () -> closedSession.openWrite(0, 0));

            // Verify that the session can be opened again for read/write.
            try {
                session = mBlobStoreManager.openSession(sessionId);
                assertThat(session).isNotNull();
                assertThat(session.openWrite(0, 0)).isNotNull();
            } finally {
                session.close();
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowPublicAccess() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowPublicAccess();
                assertThat(session.isPublicAccessAllowed()).isTrue();

                session.abandon();
                assertThrows(IllegalStateException.class,
                        () -> session.allowPublicAccess());
                assertThrows(IllegalStateException.class,
                        () -> session.isPublicAccessAllowed());
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowSameSignatureAccess() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowSameSignatureAccess();
                assertThat(session.isSameSignatureAccessAllowed()).isTrue();

                session.abandon();
                assertThrows(IllegalStateException.class,
                        () -> session.allowSameSignatureAccess());
                assertThrows(IllegalStateException.class,
                        () -> session.isSameSignatureAccessAllowed());
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAllowPackageAccess() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowPackageAccess("com.example", "test_bytes".getBytes());
                assertThat(session.isPackageAccessAllowed("com.example", "test_bytes".getBytes()))
                        .isTrue();

                session.abandon();
                assertThrows(IllegalStateException.class,
                        () -> session.allowPackageAccess(
                                "com.example2", "test_bytes2".getBytes()));
                assertThrows(IllegalStateException.class,
                        () -> session.isPackageAccessAllowed(
                                "com.example", "test_bytes".getBytes()));
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testMixedAccessType() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                session.allowSameSignatureAccess();
                session.allowPackageAccess("com.example", "test_bytes".getBytes());

                assertThat(session.isSameSignatureAccessAllowed()).isTrue();
                assertThat(session.isPackageAccessAllowed("com.example", "test_bytes".getBytes()))
                        .isTrue();
                assertThat(session.isPublicAccessAllowed()).isFalse();

                session.allowPublicAccess();
                assertThat(session.isPublicAccessAllowed()).isTrue();
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testSessionCommit() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);

                final ParcelFileDescriptor pfd = session.openWrite(
                        0L /* offset */, 0L /* length */);
                assertThat(pfd).isNotNull();
                blobData.writeToFd(pfd.getFileDescriptor(), 0 /* offset */, 100 /* length */);

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);

                // Verify that writing to the session after commit will throw.
                assertThrows(IOException.class, () -> blobData.writeToFd(
                        pfd.getFileDescriptor() /* length */, 0 /* offset */, 100 /* length */));
                assertThrows(IllegalStateException.class, () -> session.openWrite(0L, 0L));
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testOpenBlob() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);

                // Verify that trying to access the blob before commit throws
                assertThrows(SecurityException.class,
                        () -> mBlobStoreManager.openBlob(blobData.getBlobHandle()));

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            // Verify that blob can be access after committing.
            try (ParcelFileDescriptor pfd = mBlobStoreManager.openBlob(blobData.getBlobHandle())) {
                assertThat(pfd).isNotNull();

                blobData.verifyBlob(pfd);
            }
        } finally {
            blobData.delete();
        }
    }

    @Test
    public void testAcquireReleaseLease() throws Exception {
        final DummyBlobData blobData = new DummyBlobData();
        blobData.prepare();
        try {
            final long sessionId = createSession(blobData.getBlobHandle());
            assertThat(sessionId).isGreaterThan(0L);

            try (BlobStoreManager.Session session = mBlobStoreManager.openSession(sessionId)) {
                blobData.writeToSession(session);

                final CompletableFuture<Integer> callback = new CompletableFuture<>();
                session.commit(mContext.getMainExecutor(), callback::complete);
                assertThat(callback.get(TIMEOUT_COMMIT_CALLBACK_SEC, TimeUnit.SECONDS))
                        .isEqualTo(0);
            }

            assertThrows(IllegalArgumentException.class, () ->
                    mBlobStoreManager.acquireLease(blobData.getBlobHandle(),
                            R.string.test_desc, blobData.mExpiryTimeMs + 1000));

            mBlobStoreManager.acquireLease(blobData.getBlobHandle(), R.string.test_desc,
                    blobData.mExpiryTimeMs - 1000);
            mBlobStoreManager.acquireLease(blobData.getBlobHandle(), R.string.test_desc);
            // TODO: verify acquiring lease took effect.
            mBlobStoreManager.releaseLease(blobData.getBlobHandle());
        } finally {
            blobData.delete();
        }
    }

    private long createSession(BlobHandle blobHandle) throws Exception {
        final long sessionId = mBlobStoreManager.createSession(blobHandle);
        mCreatedSessionIds.add(sessionId);
        return sessionId;
    }

    class DummyBlobData {
        private static final long DEFAULT_SIZE_BYTES = 10 * 1024L * 1024L;
        private static final int BUFFER_SIZE_BYTES = 16 * 1024;

        final File mFile;
        final long mFileSize;

        byte[] mFileDigest;
        long mExpiryTimeMs;

        DummyBlobData() {
            this(DEFAULT_SIZE_BYTES);
        }

        DummyBlobData(long fileSize) {
            mFile = new File(mContext.getFilesDir(), "blob_" + System.nanoTime());
            mFileSize = fileSize;
        }

        void prepare() throws Exception {
            try (RandomAccessFile file = new RandomAccessFile(mFile, "rw")) {
                writeRandomData(file, mFileSize);
            }
            mFileDigest = FileUtils.digest(mFile, "SHA-256");
            mExpiryTimeMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        }

        BlobHandle getBlobHandle() throws Exception {
            return BlobHandle.createWithSha256(createSha256Digest(mFile), "Test blob",
                    mExpiryTimeMs, "test_tag");
        }

        void delete() {
            mFile.delete();
        }

        void writeToSession(BlobStoreManager.Session session) throws Exception {
            writeToSession(session, 0, mFileSize);
        }

        void writeToSession(BlobStoreManager.Session session, long offsetBytes, long lengthBytes)
                throws Exception {
            try (FileInputStream in = new FileInputStream(mFile)) {
                in.getChannel().position(offsetBytes);
                try (FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(
                        session.openWrite(offsetBytes, lengthBytes))) {
                    copy(in, out, lengthBytes);
                }
            }
        }

        void writeToFd(FileDescriptor fd, long offsetBytes, long lengthBytes) throws Exception {
            try (FileInputStream in = new FileInputStream(mFile)) {
                in.getChannel().position(offsetBytes);
                try (FileOutputStream out = new FileOutputStream(fd)) {
                    copy(in, out, lengthBytes);
                }
            }
        }

        void copy(InputStream in, OutputStream out, long lengthBytes) throws Exception {
            final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            long bytesWrittern = 0;
            while (bytesWrittern < lengthBytes) {
                final int toWrite = (bytesWrittern + buffer.length <= lengthBytes)
                        ? buffer.length : (int) (lengthBytes - bytesWrittern);
                in.read(buffer, 0, toWrite);
                out.write(buffer, 0, toWrite);
                bytesWrittern += toWrite;
            }
        }

        void readFromSessionAndVerifyBytes(BlobStoreManager.Session session,
                long offsetBytes, int lengthBytes) throws Exception {
            final byte[] expectedBytes = new byte[lengthBytes];
            try (FileInputStream in = new FileInputStream(mFile)) {
                read(in, expectedBytes, offsetBytes, lengthBytes);
            }

            final byte[] actualBytes = new byte[lengthBytes];
            try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
                    session.openRead())) {
                read(in, actualBytes, offsetBytes, lengthBytes);
            }

            assertThat(actualBytes).isEqualTo(expectedBytes);

        }

        private void read(FileInputStream in, byte[] buffer,
                long offsetBytes, int lengthBytes) throws Exception {
            in.getChannel().position(offsetBytes);
            in.read(buffer, 0, lengthBytes);
        }

        void readFromSessionAndVerifyDigest(BlobStoreManager.Session session)
                throws Exception {
            readFromSessionAndVerifyDigest(session, 0, mFile.length());
        }

        void readFromSessionAndVerifyDigest(BlobStoreManager.Session session,
                long offsetBytes, long lengthBytes) throws Exception {
            final byte[] actualDigest;
            try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
                    session.openRead())) {
                actualDigest = createSha256Digest(in, offsetBytes, lengthBytes);
            }

            assertThat(actualDigest).isEqualTo(mFileDigest);
        }

        void verifyBlob(ParcelFileDescriptor pfd) throws Exception {
            final byte[] actualDigest;
            try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
                actualDigest = FileUtils.digest(in, "SHA-256");
            }
            assertThat(actualDigest).isEqualTo(mFileDigest);
        }

        private byte[] createSha256Digest(FileInputStream in, long offsetBytes, long lengthBytes)
                throws Exception {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            in.getChannel().position(offsetBytes);
            final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            long bytesRead = 0;
            while (bytesRead < lengthBytes) {
                int toRead = (bytesRead + buffer.length <= lengthBytes)
                        ? buffer.length : (int) (lengthBytes - bytesRead);
                toRead = in.read(buffer, 0, toRead);
                digest.update(buffer, 0, toRead);
                bytesRead += toRead;
            }
            return digest.digest();
        }

        private byte[] createSha256Digest(File file) throws Exception {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (BufferedInputStream in = new BufferedInputStream(
                    Files.newInputStream(file.toPath()))) {
                final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return digest.digest();
        }

        private void writeRandomData(RandomAccessFile file, long fileSize)
                throws Exception {
            long bytesWritten = 0;
            final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            while (bytesWritten < fileSize) {
                mRandom.nextBytes(buffer);
                final int toWrite = (bytesWritten + buffer.length <= fileSize)
                        ? buffer.length : (int) (fileSize - bytesWritten);
                file.seek(bytesWritten);
                file.write(buffer, 0, toWrite);
                bytesWritten += toWrite;
            }
        }
    }
}
