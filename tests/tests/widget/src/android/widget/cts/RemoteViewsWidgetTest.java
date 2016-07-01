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

package android.widget.cts;

import android.app.Instrumentation;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.cts.util.PollingCheck;
import android.cts.util.SystemUtil;
import android.os.Process;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.StackView;
import android.widget.cts.appwidget.MyAppWidgetProvider;
import android.widget.cts.appwidget.MyAppWidgetService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Test {@link RemoteViews} that expect to operate within a {@link AppWidgetHostView} root.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class RemoteViewsWidgetTest {
    public static final String[] COUNTRY_LIST = new String[]{
        "Argentina", "Australia", "China", "France", "Germany", "Italy", "Japan", "United States"
    };

    private static final String GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND =
        "appwidget grantbind --package android.widget.cts --user 0";

    private static final String REVOKE_BIND_APP_WIDGET_PERMISSION_COMMAND =
        "appwidget revokebind --package android.widget.cts --user 0";

    private static final long TEST_TIMEOUT_MS = 5000;

    private Instrumentation mInstrumentation;

    private Context mContext;

    private boolean mHasAppWidgets;

    private AppWidgetHostView mAppWidgetHostView;

    private int mAppWidgetId;

    private StackView mStackView;

    private AppWidgetHost mAppWidgetHost;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();

        mHasAppWidgets = hasAppWidgets();
        if (!mHasAppWidgets) {
            return;
        }

        // We want to bind widgets - run a shell command to grant bind permission to our
        // package.
        grantBindAppWidgetPermission();

        mAppWidgetHost = new AppWidgetHost(mContext, 0);

        mAppWidgetHost.deleteHost();
        mAppWidgetHost.startListening();

        // Configure the app widget provider behavior
        final CountDownLatch providerCountDownLatch = new CountDownLatch(2);
        MyAppWidgetProvider.setCountDownLatch(providerCountDownLatch);

        // Grab the provider to be bound
        final AppWidgetProviderInfo providerInfo = getAppWidgetProviderInfo();

        // Allocate a widget id to bind
        mAppWidgetId = mAppWidgetHost.allocateAppWidgetId();

        // Bind the app widget
        boolean isBinding = getAppWidgetManager().bindAppWidgetIdIfAllowed(mAppWidgetId,
                providerInfo.getProfile(), providerInfo.provider, null);
        assertTrue(isBinding);

        // Wait for onEnabled and onUpdate calls on our provider
        try {
            assertTrue(providerCountDownLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        // Configure the app widget service behavior
        final CountDownLatch factoryCountDownLatch = new CountDownLatch(2);
        RemoteViewsService.RemoteViewsFactory factory =
                mock(RemoteViewsService.RemoteViewsFactory.class);
        when(factory.getCount()).thenReturn(COUNTRY_LIST.length);
        doAnswer(new Answer<RemoteViews>() {
            @Override
            public RemoteViews answer(InvocationOnMock invocation) throws Throwable {
                final int position = (Integer) invocation.getArguments()[0];
                RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(),
                        R.layout.remoteviews_adapter_item);
                remoteViews.setTextViewText(R.id.item, COUNTRY_LIST[position]);
                if (position == 0) {
                    factoryCountDownLatch.countDown();
                }
                return remoteViews;
            }
        }).when(factory).getViewAt(any(int.class));
        when(factory.getViewTypeCount()).thenReturn(1);
        MyAppWidgetService.setFactory(factory);

        mInstrumentation.runOnMainSync(
                () -> mAppWidgetHostView = mAppWidgetHost.createView(
                        mContext, mAppWidgetId, providerInfo));

        // Wait our factory to be called to create the first item
        try {
            assertTrue(factoryCountDownLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }
    }

    @After
    public void teardown() {
        mAppWidgetHost.deleteHost();
        revokeBindAppWidgetPermission();
    }

    private void grantBindAppWidgetPermission() {
        try {
            SystemUtil.runShellCommand(mInstrumentation, GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND);
        } catch (IOException e) {
            fail("Error granting app widget permission. Command: "
                    + GRANT_BIND_APP_WIDGET_PERMISSION_COMMAND + ": ["
                    + e.getMessage() + "]");
        }
    }

    private void revokeBindAppWidgetPermission() {
        try {
            SystemUtil.runShellCommand(mInstrumentation, REVOKE_BIND_APP_WIDGET_PERMISSION_COMMAND);
        } catch (IOException e) {
            fail("Error revoking app widget permission. Command: "
                    + REVOKE_BIND_APP_WIDGET_PERMISSION_COMMAND + ": ["
                    + e.getMessage() + "]");
        }
    }

    private boolean hasAppWidgets() {
        return mInstrumentation.getTargetContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS);
    }

    private AppWidgetManager getAppWidgetManager() {
        return (AppWidgetManager) mContext.getSystemService(Context.APPWIDGET_SERVICE);
    }

    private AppWidgetProviderInfo getAppWidgetProviderInfo() {
        ComponentName firstComponentName = new ComponentName(mContext.getPackageName(),
                MyAppWidgetProvider.class.getName());

        return getProviderInfo(firstComponentName);
    }

    private AppWidgetProviderInfo getProviderInfo(ComponentName componentName) {
        List<AppWidgetProviderInfo> providers = getAppWidgetManager().getInstalledProviders();

        final int providerCount = providers.size();
        for (int i = 0; i < providerCount; i++) {
            AppWidgetProviderInfo provider = providers.get(i);
            if (componentName.equals(provider.provider)
                    && Process.myUserHandle().equals(provider.getProfile())) {
                return provider;

            }
        }

        return null;
    }

    @Test
    public void testInitialState() {
        if (!mHasAppWidgets) {
            return;
        }

        assertNotNull(mAppWidgetHostView);
        mStackView = (StackView) mAppWidgetHostView.findViewById(R.id.remoteViews_stack);
        assertNotNull(mStackView);

        assertEquals(COUNTRY_LIST.length, mStackView.getCount());
        assertEquals(0, mStackView.getDisplayedChild());
        assertEquals(R.id.remoteViews_empty, mStackView.getEmptyView().getId());
    }

    private void verifySetDisplayedChild(int displayedChildIndex) {
        final CountDownLatch updateLatch = new CountDownLatch(1);
        MyAppWidgetProvider.setCountDownLatch(updateLatch);

        // Create the intent to update the widget. Note that we're passing the value
        // for displayed child index in the intent
        Intent intent = new Intent(mContext, MyAppWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new  int[] { mAppWidgetId });
        intent.putExtra(MyAppWidgetProvider.KEY_DISPLAYED_CHILD_INDEX, displayedChildIndex);
        mContext.sendBroadcast(intent);

        // Wait until the update request has been processed
        try {
            assertTrue(updateLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }
        // And wait until the underlying StackView has been updated to switch to the requested
        // child
        PollingCheck.waitFor(TEST_TIMEOUT_MS,
                () -> mStackView.getDisplayedChild() == displayedChildIndex);
    }

    @Test
    public void testSetDisplayedChild() {
        if (!mHasAppWidgets) {
            return;
        }

        mStackView = (StackView) mAppWidgetHostView.findViewById(R.id.remoteViews_stack);

        verifySetDisplayedChild(4);
        verifySetDisplayedChild(2);
        verifySetDisplayedChild(6);
    }

    private void verifyShowCommand(String intentShowKey, int expectedDisplayedChild) {
        final CountDownLatch updateLatch = new CountDownLatch(1);
        MyAppWidgetProvider.setCountDownLatch(updateLatch);

        // Create the intent to update the widget. Note that we're passing the "indication"
        // which one of showNext / showPrevious APIs to execute in the intent that we're
        // creating.
        Intent intent = new Intent(mContext, MyAppWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new  int[] { mAppWidgetId });
        intent.putExtra(intentShowKey, true);
        mContext.sendBroadcast(intent);

        // Wait until the update request has been processed
        try {
            assertTrue(updateLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }
        // And wait until the underlying StackView has been updated to switch to the expected
        // child
        PollingCheck.waitFor(TEST_TIMEOUT_MS,
                () -> mStackView.getDisplayedChild() == expectedDisplayedChild);
    }

    @Test
    public void testShowNextPrevious() {
        if (!mHasAppWidgets) {
            return;
        }

        mStackView = (StackView) mAppWidgetHostView.findViewById(R.id.remoteViews_stack);

        // Two forward
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_NEXT, 1);
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_NEXT, 2);
        // Four back (looping to the end of the adapter data)
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_PREVIOUS, 1);
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_PREVIOUS, 0);
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_PREVIOUS, COUNTRY_LIST.length - 1);
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_PREVIOUS, COUNTRY_LIST.length - 2);
        // And three forward (looping to the start of the adapter data)
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_NEXT, COUNTRY_LIST.length - 1);
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_NEXT, 0);
        verifyShowCommand(MyAppWidgetProvider.KEY_SHOW_NEXT, 1);
    }
}
