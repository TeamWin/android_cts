/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.cts.broadcasts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.app.cts.broadcasts.ICommandReceiver;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingSupplier;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BroadcastDeliveryGroupTest {
    private static final String TAG = "BroadcastDeliveryGroupTest";

    private static final long TIMEOUT_BIND_SERVICE_SEC = 2;

    private static final String HELPER_PKG1 = "com.android.app.cts.broadcasts.helper";
    private static final String HELPER_PKG2 = "com.android.app.cts.broadcasts.helper2";
    private static final String HELPER_SERVICE = HELPER_PKG1 + ".TestService";

    private static final String TEST_ACTION1 = "com.android.app.cts.TEST_ACTION1";

    private static final String TEST_EXTRA1 = "com.android.app.cts.TEST_EXTRA1";

    private static final String TEST_VALUE1 = "value1";
    private static final String TEST_VALUE2 = "value2";

    private static final long BROADCAST_FORCED_DELAYED_DURATION_MS = 120_000;

    private Context mContext;
    private ActivityManager mAm;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mAm = mContext.getSystemService(ActivityManager.class);
    }

    @After
    public void tearDown() {
        for (String pkg : new String[] {HELPER_PKG1, HELPER_PKG2}) {
            forceDelayBroadcasts(pkg, 0);
        }
    }

    @Test
    public void testDeliveryGroupPolicy_policyAll() throws Exception {
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            cmdReceiver1.sendBroadcast(intent1, null /* options */);
            cmdReceiver1.sendBroadcast(intent2, null /* options */);

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent1, intent2), true);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    @Test
    public void testDeliveryGroupPolicy_withForceDelay_policyAll() throws Exception {
        assumeTrue(isModernBroadcastQueueEnabled());
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            // Now force delay the broadcasts to make sure delivery group policies are
            // applied as expected.
            forceDelayBroadcasts(HELPER_PKG2);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            cmdReceiver1.sendBroadcast(intent1, null /* options */);
            cmdReceiver1.sendBroadcast(intent2, null /* options */);

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent1, intent2), true);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    @Test
    public void testDeliveryGroupPolicy_policyMostRecent() throws Exception {
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
            options.setDeliveryGroupMatchingKey(TEST_ACTION1, TEST_EXTRA1);
            cmdReceiver1.sendBroadcast(intent1, options.toBundle());
            cmdReceiver1.sendBroadcast(intent2, options.toBundle());

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent2), false);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    @Test
    public void testDeliveryGroupPolicy_withForceDelay_policyMostRecent() throws Exception {
        assumeTrue(isModernBroadcastQueueEnabled());
        final TestServiceConnection connection1 = bindToHelperService(HELPER_PKG1);
        final TestServiceConnection connection2 = bindToHelperService(HELPER_PKG2);
        try {
            final ICommandReceiver cmdReceiver1 = connection1.getCommandReceiver();
            final ICommandReceiver cmdReceiver2 = connection2.getCommandReceiver();

            final IntentFilter filter = new IntentFilter(TEST_ACTION1);
            cmdReceiver2.clearCookie(TEST_ACTION1);
            cmdReceiver2.monitorBroadcasts(filter, TEST_ACTION1);

            // Now force delay the broadcasts to make sure delivery group policies are
            // applied as expected.
            forceDelayBroadcasts(HELPER_PKG2);

            final Intent intent1 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG2);
            final Intent intent2 = new Intent(TEST_ACTION1)
                    .putExtra(TEST_EXTRA1, TEST_VALUE2)
                    .setPackage(HELPER_PKG2);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT);
            options.setDeliveryGroupMatchingKey(TEST_ACTION1, TEST_EXTRA1);
            cmdReceiver1.sendBroadcast(intent1, options.toBundle());
            cmdReceiver1.sendBroadcast(intent2, options.toBundle());

            verifyReceivedBroadcasts(() -> cmdReceiver2.getReceivedBroadcasts(TEST_ACTION1),
                    List.of(intent2), true);
        } finally {
            connection1.unbind();
            connection2.unbind();
        }
    }

    private void forceDelayBroadcasts(String targetPackage) {
        forceDelayBroadcasts(targetPackage, BROADCAST_FORCED_DELAYED_DURATION_MS);
    }

    private void forceDelayBroadcasts(String targetPackage, long delayedDurationMs) {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mAm.forceDelayBroadcastDelivery(targetPackage, delayedDurationMs));
    }

    private boolean isModernBroadcastQueueEnabled() {
        return SystemUtil.runWithShellPermissionIdentity(() ->
                mAm.isModernBroadcastQueueEnabled());
    }

    /**
     * @param matchExact If {@code matchExact} is {@code true}, then it is verified that
     *                   expected broadcasts exactly match the actual received broadcasts.
     *                   Otherwise, it is verified that expected broadcasts are part of the
     *                   actual received broadcasts.
     */
    private void verifyReceivedBroadcasts(ThrowingSupplier<List<Intent>> actualBroadcastsSupplier,
            List<Intent> expectedBroadcasts, boolean matchExact) throws Exception {
        AmUtils.waitForBroadcastBarrier();
        final List<Intent> actualBroadcasts = actualBroadcastsSupplier.get();
        final String errorMsg = "Expected: " + toString(expectedBroadcasts)
                + "; Actual: " + toString(actualBroadcasts);
        if (matchExact) {
            assertWithMessage(errorMsg).that(actualBroadcasts.size())
                    .isEqualTo(expectedBroadcasts.size());
            for (int i = 0; i < expectedBroadcasts.size(); ++i) {
                final Intent expected = expectedBroadcasts.get(i);
                final Intent actual = actualBroadcasts.get(i);
                assertWithMessage(errorMsg).that(compareIntents(expected, actual))
                        .isEqualTo(true);
            }
        } else {
            assertWithMessage(errorMsg).that(actualBroadcasts.size())
                    .isAtLeast(expectedBroadcasts.size());
            final Iterator<Intent> it = actualBroadcasts.iterator();
            int countMatches = 0;
            while (it.hasNext()) {
                final Intent actual = it.next();
                final Intent expected = expectedBroadcasts.get(countMatches);
                if (compareIntents(expected, actual)) {
                    countMatches++;
                }
                if (countMatches == expectedBroadcasts.size()) {
                    break;
                }
            }
            assertWithMessage(errorMsg).that(countMatches).isEqualTo(expectedBroadcasts.size());
        }
    }

    private static <T> String toString(List<T> list) {
        return list == null ? null : Arrays.toString(list.toArray());
    }

    private boolean compareIntents(Intent expected, Intent actual) {
        if (!actual.getAction().equals(expected.getAction())) {
            return false;
        }
        if (!compareExtras(expected.getExtras(), actual.getExtras())) {
            return false;
        }
        return true;
    }

    private boolean compareExtras(Bundle expected, Bundle actual) {
        if (expected == actual) {
            return true;
        } else if (expected == null || actual == null) {
            return false;
        }
        if (!expected.keySet().equals(actual.keySet())) {
            return false;
        }
        for (String key : expected.keySet()) {
            final Object expectedValue = expected.get(key);
            final Object actualValue = actual.get(key);
            if (expectedValue == actualValue) {
                continue;
            } else if (expectedValue == null || actualValue == null) {
                return false;
            }
            if (actualValue.getClass() != expectedValue.getClass()) {
                return false;
            }
            if (expectedValue.getClass().isArray()) {
                if (Array.getLength(actualValue) != Array.getLength(expectedValue)) {
                    return false;
                }
                for (int i = 0; i < Array.getLength(expectedValue); ++i) {
                    if (!Objects.equal(Array.get(actualValue, i), Array.get(expectedValue, i))) {
                        return false;
                    }
                }
            } else if (expectedValue instanceof ArrayList) {
                final ArrayList<?> expectedList = (ArrayList<?>) expectedValue;
                final ArrayList<?> actualList = (ArrayList<?>) actualValue;
                if (actualList.size() != expectedList.size()) {
                    return false;
                }
                for (int i = 0; i < expectedList.size(); ++i) {
                    if (!Objects.equal(actualList.get(i), expectedList.get(i))) {
                        return false;
                    }
                }
            } else {
                if (!Objects.equal(actualValue, expectedValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    private TestServiceConnection bindToHelperService(String packageName) {
        final TestServiceConnection
                connection = new TestServiceConnection(mContext);
        final Intent intent = new Intent().setComponent(
                new ComponentName(packageName, HELPER_SERVICE));
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        return connection;
    }

    private class TestServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();
        private ICommandReceiver mCommandReceiver;

        TestServiceConnection(Context context) {
            mContext = context;
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service got connected: " + componentName);
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "Service got disconnected: " + componentName);
        }

        private IBinder getService() throws Exception {
            final IBinder service = mBlockingQueue.poll(TIMEOUT_BIND_SERVICE_SEC,
                    TimeUnit.SECONDS);
            return service;
        }

        public ICommandReceiver getCommandReceiver() throws Exception {
            if (mCommandReceiver == null) {
                mCommandReceiver = ICommandReceiver.Stub.asInterface(getService());
            }
            return mCommandReceiver;
        }

        public void unbind() {
            mCommandReceiver = null;
            mContext.unbindService(this);
        }
    }
}
