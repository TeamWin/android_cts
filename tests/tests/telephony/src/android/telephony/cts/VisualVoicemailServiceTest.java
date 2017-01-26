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

package android.telephony.cts;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Intents;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailService;
import android.telephony.VisualVoicemailSms;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class VisualVoicemailServiceTest extends InstrumentationTestCase {

    private static final String TAG = "VvmServiceTest";

    private static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";

    private static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";

    private static final String PACKAGE = "android.telephony.cts";

    private static final long EVENT_RECEIVED_TIMEOUT_MILLIS = 60_000;
    private static final long EVENT_NOT_RECEIVED_TIMEOUT_MILLIS = 1_000;

    private Context mContext;

    private String mPreviousDefaultDialer;

    private PhoneAccountHandle mPhoneAccountHandle;
    private String mPhoneNumber;

    private SmsBroadcastReceiver mSmsReceiver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (hasTelephony(mContext)) {
            mPreviousDefaultDialer = getDefaultDialer(getInstrumentation());
            setDefaultDialer(getInstrumentation(), PACKAGE);

            TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
            mPhoneAccountHandle = telecomManager
                    .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
            mPhoneNumber = telecomManager.getLine1Number(mPhoneAccountHandle);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (hasTelephony(mContext)) {
            if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
                setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
            }

            if (mSmsReceiver != null) {
                mContext.unregisterReceiver(mSmsReceiver);
            }
        }
        super.tearDown();
    }

    public void testFilter() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");

        assertEquals("STATUS", result.getPrefix());
        assertEquals("R", result.getFields().getString("st"));
        assertEquals("0", result.getFields().getString("rc"));
        assertEquals("1", result.getFields().getString("srv"));
        assertEquals("1", result.getFields().getString("dn"));
        assertEquals("1", result.getFields().getString("ipt"));
        assertEquals("0", result.getFields().getString("spt"));
        assertEquals("eg@example.com", result.getFields().getString("u"));
        assertEquals("1", result.getFields().getString("pw"));
    }

    public void testFilter_TrailingSemiColon() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1;");

        assertEquals("STATUS", result.getPrefix());
        assertEquals("R", result.getFields().getString("st"));
        assertEquals("0", result.getFields().getString("rc"));
        assertEquals("1", result.getFields().getString("srv"));
        assertEquals("1", result.getFields().getString("dn"));
        assertEquals("1", result.getFields().getString("ipt"));
        assertEquals("0", result.getFields().getString("spt"));
        assertEquals("eg@example.com", result.getFields().getString("u"));
        assertEquals("1", result.getFields().getString("pw"));
    }

    public void testFilter_EmptyPrefix() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM::st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");

        assertEquals("", result.getPrefix());
        assertEquals("R", result.getFields().getString("st"));
        assertEquals("0", result.getFields().getString("rc"));
        assertEquals("1", result.getFields().getString("srv"));
        assertEquals("1", result.getFields().getString("dn"));
        assertEquals("1", result.getFields().getString("ipt"));
        assertEquals("0", result.getFields().getString("spt"));
        assertEquals("eg@example.com", result.getFields().getString("u"));
        assertEquals("1", result.getFields().getString("pw"));
    }

    public void testFilter_EmptyField() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:");
        assertTrue(result.getFields().isEmpty());
    }

    public void testFilterFail_NotVvm() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "helloworld");
    }

    public void testFilterFail_PrefixMismatch() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//FOOVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");
    }

    public void testFilterFail_MissingFirstColon() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVMSTATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");
    }

    public void testFilterFail_MissingSecondColon() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUSst=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");
    }

    public void testFilterFail_MessageEndAfterClientPrefix() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:");
    }

    public void testFilterFail_MessageEndAfterPrefix() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUS");
    }

    public void testFilterFail_InvalidKeyValuePair() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUS:key");
    }

    public void testFilterFail_InvalidMissingKey() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUS:=value");
    }

    public void testFilter_MissingValue() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:key=");
        assertEquals("STATUS", result.getPrefix());
        assertEquals("", result.getFields().getString("key"));
    }

    public void testGetVisualVoicemailPackageName_isSelf(){
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        assertEquals(PACKAGE, telephonyManager.getVisualVoicemailPackageName(mPhoneAccountHandle));
    }

    private VisualVoicemailSms getSmsFromText(String clientPrefix, String text) {
        return getSmsFromText(clientPrefix, text, true);
    }

    private void assertVisualVoicemailSmsNotReceived(String clientPrefix, String text) {
        getSmsFromText(clientPrefix, text, false);
    }

    /**
     * Setup the SMS filter with only the {@code clientPrefix}, and sends {@code text} to the
     * device. The SMS sent should not be written to the SMS provider. <p> If {@code expectVvmSms}
     * is {@code true}, the SMS should be be caught by the SMS filter. The user should not receive
     * the text, and the parsed result will be returned.* <p> If {@code expectVvmSms} is {@code
     * false}, the SMS should pass through the SMS filter. The user should receive the text, and
     * {@code null} be returned.
     */
    @Nullable
    private VisualVoicemailSms getSmsFromText(String clientPrefix, String text,
            boolean expectVvmSms) {

        VisualVoicemailService.setSmsFilterSettings(mContext, mPhoneAccountHandle,
                new VisualVoicemailSmsFilterSettings.Builder()
                        .setClientPrefix(clientPrefix)
                        .build());

        CompletableFuture<VisualVoicemailSms> future = new CompletableFuture<>();
        MockVisualVoicemailService.setSmsFuture(future);

        setupSmsReceiver();
        try (SentSmsObserver observer = new SentSmsObserver(mContext)) {
            VisualVoicemailService
                    .sendVisualVoicemailSms(mContext, mPhoneAccountHandle, mPhoneNumber, (short) 0,
                            text, null);

            if (expectVvmSms) {
                VisualVoicemailSms sms;
                try {
                    sms = future.get(EVENT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    throw new RuntimeException(e);
                }
                mSmsReceiver.assertNotReceived(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS);
                observer.assertNotChanged();
                return sms;
            } else {
                mSmsReceiver.assertReceived(EVENT_RECEIVED_TIMEOUT_MILLIS);
                try {
                    future.get(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    throw new RuntimeException("Unexpected visual voicemail SMS received");
                } catch (TimeoutException e) {
                    // expected
                    return null;
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void setupSmsReceiver() {
        mSmsReceiver = new SmsBroadcastReceiver();
        IntentFilter filter = new IntentFilter(Intents.SMS_RECEIVED_ACTION);
        mContext.registerReceiver(mSmsReceiver, filter);
    }

    private static class SmsBroadcastReceiver extends BroadcastReceiver {

        private CompletableFuture<Boolean> mFuture = new CompletableFuture<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            mFuture.complete(true);
        }

        public void assertReceived(long timeoutMillis) {
            try {
                mFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }

        public void assertNotReceived(long timeoutMillis) {
            try {
                mFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
                throw new RuntimeException("Unexpected SMS received");
            } catch (TimeoutException e) {
                // expected
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class SentSmsObserver extends ContentObserver implements AutoCloseable {

        private final Context mContext;

        public CompletableFuture<Boolean> mFuture = new CompletableFuture<>();

        public SentSmsObserver(Context context) {
            super(new Handler(Looper.getMainLooper()));
            mContext = context;
            mContext.getContentResolver().registerContentObserver(Sms.CONTENT_URI, true, this);
        }

        public void assertNotChanged() {
            try {
                mFuture.get(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                fail("Visual voicemail SMS should not be added into the sent SMS");
            } catch (TimeoutException e) {
                // expected
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            try (Cursor cursor = mContext.getContentResolver()
                    .query(uri, new String[] {Sms.TYPE}, null, null, null)) {
                cursor.moveToFirst();
                if (cursor.getInt(0) == Sms.MESSAGE_TYPE_SENT) {
                    mFuture.complete(true);
                }
            } catch (SQLiteException e) {

            }
        }

        @Override
        public void close() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    private static boolean hasTelephony(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE);
    }

    private static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
    }

    private static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
    }

    /**
     * Executes the given shell command and returns the output in a string. Note that even if we
     * don't care about the output, we have to read the stream completely to make the command
     * execute.
     */
    private static String executeShellCommand(Instrumentation instrumentation,
            String command) throws Exception {
        final ParcelFileDescriptor parcelFileDescriptor =
                instrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader bufferedReader = null;
        try (InputStream in = new FileInputStream(parcelFileDescriptor.getFileDescriptor())) {
            bufferedReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String string = null;
            StringBuilder out = new StringBuilder();
            while ((string = bufferedReader.readLine()) != null) {
                out.append(string);
            }
            return out.toString();
        } finally {
            if (bufferedReader != null) {
                closeQuietly(bufferedReader);
            }
            closeQuietly(parcelFileDescriptor);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }
}
