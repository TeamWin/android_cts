package android.telephonyprovider.cts;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.telephonyprovider.cts.DefaultSmsAppHelper.setDefaultSmsApp;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.Telephony;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

@SmallTest
public class ThreadsTest {
    private Context mContext;
    private ContentResolver mContentResolver;

    @Before
    public void setupTestEnvironment() {
        cleanup();
        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
    }

    @AfterClass
    public static void cleanup() {
        ContentResolver contentResolver =
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver();

        setDefaultSmsApp(true);
        contentResolver.delete(Telephony.Threads.CONTENT_URI, null, null);
        setDefaultSmsApp(false);
    }

    @Test
    public void testThreadDeletion_doNotReuseThreadIdsFromEmptyThreads() {
        setDefaultSmsApp(true);

        String destination1 = "+19998880001";
        String destination2 = "+19998880002";

        long threadId1 = Telephony.Threads.getOrCreateThreadId(mContext, destination1);

        Uri inboxUri = saveToTelephony(threadId1, destination1, "testThreadDeletion body");

        // The URI returned by the insert operation points to the message ID in the inbox. Though
        // this is a valid ID for queries, the SMS provider does not handle it for delete
        // operations. This takes the ID off the end of the URI and creates a URI pointing at that
        // row from the root of the SMS provider.
        Uri rootUri =
                Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, inboxUri.getLastPathSegment());

        int deletedCount = mContentResolver.delete(rootUri, null, null);

        assertThat(deletedCount).isEqualTo(1);

        long threadId2 = Telephony.Threads.getOrCreateThreadId(mContext, destination2);

        assertThat(threadId2).isGreaterThan(threadId1);
    }

    private Uri saveToTelephony(long threadId, String address, String body) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Telephony.Sms.THREAD_ID, threadId);
        contentValues.put(Telephony.Sms.ADDRESS, address);
        contentValues.put(Telephony.Sms.BODY, body);

        return mContext.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, contentValues);
    }
}

