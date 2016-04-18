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

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.test.AndroidTestCase;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * Checks IMemory.cpp so that it should not return arbitrary memory locations (by checking against
 * the size of IMemoryHeap) which can lead to memory corruption or segmentation fault.
 */

public class IMemoryHeapCorruptionTest extends AndroidTestCase {

    static class MediaPlayerServiceOps {
        static int CREATE = IBinder.FIRST_CALL_TRANSACTION;
        static int CREATE_MEDIA_RECORDER = CREATE + 1;
        static int CREATE_METADATA_RETRIEVER = CREATE_MEDIA_RECORDER + 1;
        static int GET_OMX = CREATE_METADATA_RETRIEVER + 1;
        static int MAKE_CRYPTO = GET_OMX + 1;
    }

    static class SubSample {
        int mNumBytesOfClearData;
        int mNumBytesOfEncryptedData;

        public SubSample(int clearData, int encryptedData) {
            mNumBytesOfClearData = clearData;
            mNumBytesOfEncryptedData = encryptedData;
        }

        public SubSample() {
            this(0, 0);
        }
    }

    static interface IMemoryHeap {
        ParcelFileDescriptor getHeapID();

        int getSize();

        int getFlags();

        int getOffset();
    }

    static class IMemoryHeapStub extends Binder {
        static final String DESCRIPTOR = "android.utils.IMemoryHeap";
        private FileDescriptor mFd;
        private int mOffset;
        private int mSize;
        private int mFlags;

        public static final int READ_ONLY = 0x00000001;
        static final int HEAD_ID = IBinder.FIRST_CALL_TRANSACTION;

        public IMemoryHeapStub(FileDescriptor fd, int offset, int size, int flags) {
            mFd = fd;
            mOffset = offset;
            mSize = size;
            mFlags = flags;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply,
                int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case HEAD_ID: {
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeFileDescriptor(mFd);
                    reply.writeInt(mSize);
                    reply.writeInt(mFlags);
                    reply.writeInt(mOffset);
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    static class IMemoryHeapProxy implements IMemoryHeap {
        private boolean mLoaded;
        private int mFlags;
        private int mOffset;
        private int mSize;
        private ParcelFileDescriptor mHeapID;
        private IBinder mBinder;
        static int HEAP_ID = IBinder.FIRST_CALL_TRANSACTION;
        static final String DESCRIPTOR = "android.utils.IMemoryHeap";

        private void loadFromProxy() {
            if (!mLoaded) {
                mLoaded = true;
                try {
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    data.writeInterfaceToken(DESCRIPTOR);
                    mBinder.transact(HEAP_ID, data, reply, 0);
                    mHeapID = reply.readFileDescriptor();
                    mSize = reply.readInt();
                    mFlags = reply.readInt();
                    mOffset = reply.readInt();
                } catch (RemoteException ex) {
                }
            }
        }

        @Override
        public ParcelFileDescriptor getHeapID() {
            loadFromProxy();
            return mHeapID;
        }

        @Override
        public int getSize() {
            loadFromProxy();
            return mSize;
        }

        @Override
        public int getFlags() {
            loadFromProxy();
            return mFlags;
        }

        @Override
        public int getOffset() {
            loadFromProxy();
            return mOffset;
        }

        public IMemoryHeapProxy(IBinder binder) {
            mBinder = binder;
        }
    }

    static interface IMemory {
        IMemoryHeap getHeap();

        int getSize();

        int getOffset();
    }

    static class IMemoryStub extends Binder {
        static String DESCRIPTOR = "android.utils.IMemory";
        private IMemoryHeapStub mHeap;
        private int mOffset;
        private int mSize;

        public IMemoryStub(FileDescriptor fd, int offset, int size, int heapoffset,
                int heapsize, boolean readonly) {
            mHeap = new IMemoryHeapStub(fd, heapoffset, heapsize,
                    readonly ? IMemoryHeapStub.READ_ONLY : 0);
            mOffset = offset;
            mSize = size;
        }

        public IMemoryStub(FileDescriptor fd, int offset, int size) {
            this(fd, offset, size, offset, size, true);
        }

        static final int GET_MEMORY = IBinder.FIRST_CALL_TRANSACTION;

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply,
                int flags) throws android.os.RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case GET_MEMORY: {
                    data.enforceInterface(DESCRIPTOR);
                    reply.writeStrongBinder(mHeap);
                    reply.writeInt(mOffset);
                    reply.writeInt(mSize);
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    static class CryptoProxy {
        static int INIT_CHECK = IBinder.FIRST_CALL_TRANSACTION;
        static int IS_CRYPTO_SUPPORTED = INIT_CHECK + 1;
        static int CREATE_PLUGIN = IS_CRYPTO_SUPPORTED + 1;
        static int DESTROY_PLUGIN = CREATE_PLUGIN + 1;
        static int REQUIRES_SECURE_COMPONENT = DESTROY_PLUGIN + 1;
        static int DECRYPT = REQUIRES_SECURE_COMPONENT + 1;

        /**
         * For these error values, please refer MediaErrors.h under the path
         * /frameworks/av/include/media/stagefright/
         */
        static int DRM_ERROR_BASE = -2000;
        static int ERROR_DRM_UNKNOWN = DRM_ERROR_BASE;
        static int ERROR_DRM_LAST_USED_ERRORCODE = DRM_ERROR_BASE - 11;

        private IBinder mBinder;

        public CryptoProxy(IBinder binder) {
            mBinder = binder;
        }

        public boolean getIsCryptoSupported(byte[] uuid) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken("android.hardware.ICrypto");
            writeRawBytes(data, uuid);
            mBinder.transact(CryptoProxy.IS_CRYPTO_SUPPORTED, data, reply, 0);
            return reply.readInt() != 0;
        }

        public int createPlugin(byte[] uuid, byte[] opaqueData) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken("android.hardware.ICrypto");
            writeRawBytes(data, uuid);
            int length = opaqueData == null ? 0 : opaqueData.length;
            data.writeInt(length);
            if (length > 0) {
                writeRawBytes(data, opaqueData);
            }
            mBinder.transact(CryptoProxy.CREATE_PLUGIN, data, reply, 0);
            return reply.readInt();
        }

        byte[] decrypt(boolean secure, byte[] key, byte[] iv, int mode, IMemoryStub sharedBuffer,
                int offset, SubSample[] subSamples, long dstPtr) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken("android.hardware.ICrypto");
            data.writeInt(secure ? 1 : 0);
            data.writeInt(mode);
            if (key == null) {
                key = new byte[16];
            }
            if (iv == null) {
                iv = new byte[16];
            }
            writeRawBytes(data, key);
            writeRawBytes(data, iv);
            int totalSize = 0;
            for (SubSample ss : subSamples) {
                totalSize += ss.mNumBytesOfEncryptedData;
                totalSize += ss.mNumBytesOfClearData;
            }
            data.writeInt(totalSize);
            data.writeStrongBinder(sharedBuffer);
            data.writeInt(offset);
            data.writeInt(subSamples.length);
            for (SubSample ss : subSamples) {
                data.writeInt(ss.mNumBytesOfClearData);
                data.writeInt(ss.mNumBytesOfEncryptedData);
            }
            if (secure) {
                data.writeLong(dstPtr);
            }
            mBinder.transact(DECRYPT, data, reply, 0);
            int length = reply.readInt();

            /**
             * If the length value returned is negative and within the error range, this means the
             * call to mBinder.transact didn't go through and hence raise a Binder
             * remote-invocation error. For more information, refer decrypt method
             * in ICrypto.cpp under the path /frameworks/av/media/libmedia/
             */
            if ((length > ERROR_DRM_LAST_USED_ERRORCODE) && (length <= ERROR_DRM_UNKNOWN)) {
                throw new RemoteException(readCString(reply));
            }

            if (!secure && length > 0) {
                    return readRawBytes(reply, length);
            }

            return new byte[0];
        }
    }

    static int[] convertBytesToInts(byte[] byteArray) {
        IntBuffer intBuf = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        return array;
    }

    static void writeRawBytes(Parcel data, byte[] bytes) {
        int len = bytes.length;
        if ((len & 3) != 0) {
            bytes = Arrays.copyOf(bytes, (len + 3) & ~3);
        }
        for (int i : convertBytesToInts(bytes)) {
            data.writeInt(i);
        }
    }

    static byte[] convertIntToBytes(int i) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt(0, i);
        byte[] ret = new byte[4];
        byteBuffer.get(ret);
        return ret;
    }

    static String readCString(Parcel data) {
        StringBuilder builder = new StringBuilder();
        while (data.dataAvail() > 4) {
            int len;
            byte[] bytes = convertIntToBytes(data.readInt());
            for (len = 0; len < 4; ++len) {
                if (bytes[len] == 0) {
                    break;
                }
            }
            builder.append(new String(bytes, 0, len));
            if (len < 4) {
                break;
            }
        }
        return builder.toString();
    }

    static byte[] readRawBytes(Parcel data, int length) {
        int readLen = (length + 3) & ~3;
        byte[] ret = new byte[length];
        ByteBuffer byteBuffer = ByteBuffer.allocate(readLen);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < readLen / 4; ++i) {
            byteBuffer.putInt(data.readInt());
        }
        byteBuffer.position(0);
        byteBuffer.get(ret, 0, length);
        return ret;
    }

    static IBinder getService(String service) throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        Class mClass = Class.forName("android.os.ServiceManager");
        Method mMethod = mClass.getMethod("getService", String.class);
        return (IBinder) mMethod.invoke(null, service);
    }

    public void mediaPlayerTest(Context ctx) throws Throwable {
        IBinder serviceBinder = getService("media.player");
        serviceBinder.getInterfaceDescriptor();
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken("android.media.IMediaPlayerService");
        serviceBinder.transact(MediaPlayerServiceOps.MAKE_CRYPTO, data, reply, 0);
        IBinder crypto = reply.readStrongBinder();
        crypto.getInterfaceDescriptor();

        /**
         * Crypto schemes are assigned 16 byte UUIDs and they can be used to query if a given
         * scheme is supported on the device.
         * The following bytes indicate the UUID of the crypto scheme.
         */
        byte cryptoSchemeUUID[] = {
                (byte) 0x10, (byte) 0x77, (byte) 0xEF, (byte) 0xEC, (byte) 0xC0, (byte) 0xB2,
                (byte) 0x4D, (byte) 0x02, (byte) 0xAC, (byte) 0xE3, (byte) 0x3C, (byte) 0x1E,
                (byte) 0x52, (byte) 0xE2, (byte) 0xFB, (byte) 0x4B
        };
        CryptoProxy cryptoProxy = new CryptoProxy(crypto);
        cryptoProxy.getIsCryptoSupported(cryptoSchemeUUID);
        cryptoProxy.createPlugin(cryptoSchemeUUID, null);
        FileOutputStream fos = ctx.openFileOutput("dummy.bin", 0);
        byte[] byteArray = new byte[10];
        for (int i = 0; i < byteArray.length; ++i) {
            byteArray[i] = (byte) i;
        }
        fos.write(byteArray);
        fos.close();
        FileInputStream fis = ctx.openFileInput("dummy.bin");
        IMemoryStub sharedBuffer = new IMemoryStub(fis.getFD(), -1024 * 1024 * 1024,
                0xFFFFFFFF, 0, 10, true);
        SubSample[] subSamples = new SubSample[1];
        subSamples[0] = new SubSample(10, 0);
        cryptoProxy.decrypt(false, null, null, 0, sharedBuffer, 0x1234, subSamples, 0);
    }

    public void testIMemoryElevationOfPrivilegeExploit() {
        try {
            mediaPlayerTest(getContext());
        } catch (Throwable throwable) {
            fail("Device is vulnerable to bug #26877992!! For more information, refer - " +
                    "https://android.googlesource.com/platform/frameworks/native/+/" +
                    "f3199c228aced7858b75a8070b8358c155ae0149");
        }
    }
}