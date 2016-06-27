/*
 * Copyright (C) 2009 The Android Open Source Project
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


import android.cts.util.PollingCheck;
import android.cts.util.ReadElf;
import android.cts.util.TestThread;
import android.os.Looper;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.Filter;
import android.widget.Filter.FilterListener;
import org.junit.Assert;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class FilterTest extends ActivityInstrumentationTestCase2<CtsActivity> {
    private static final long TIME_OUT = 10000;
    private static final long RUN_TIME = 1000;
    private static final String TEST_CONSTRAINT = "filter test";
    private MockFilter mMockFilter;

    public FilterTest() {
        super("android.widget.cts", CtsActivity.class);
    }

    public void testConstructor() throws Throwable {
        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();
                new MockFilter();
            }
        });
        t.runTest(RUN_TIME);
    }

    public void testConvertResultToString() throws Throwable {
        final String testStr = "Test";
        new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();
                MockFilter filter = new MockFilter();
                assertEquals("", filter.convertResultToString(null));
                assertEquals(testStr, filter.convertResultToString(testStr));
            }
        }).runTest(RUN_TIME);
    }

    public void testFilter1() {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                mMockFilter = new MockFilter();
                mMockFilter.filter(TEST_CONSTRAINT);
            }
        });
        getInstrumentation().waitForIdleSync();

        PollingCheck.waitFor(TIME_OUT, () -> mMockFilter.hadPerformedFiltering());
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPerformFilteringConstraint());

        PollingCheck.waitFor(TIME_OUT, () -> mMockFilter.hadPublishedResults());
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPublishResultsConstraint());
        assertSame(mMockFilter.getExpectResults(), mMockFilter.getResults());
    }

    public void testFilter2() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Filter.FilterListener mockFilterListener = mock(Filter.FilterListener.class);
        doAnswer((InvocationOnMock invocation) -> {
            countDownLatch.countDown();
            return null;
        }).when(mockFilterListener).onFilterComplete(anyInt());

        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                mMockFilter = new MockFilter();
                mMockFilter.filter(TEST_CONSTRAINT, mockFilterListener);
            }
        });
        getInstrumentation().waitForIdleSync();

        PollingCheck.waitFor(TIME_OUT, () -> mMockFilter.hadPerformedFiltering());
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPerformFilteringConstraint());

        PollingCheck.waitFor(TIME_OUT, () -> mMockFilter.hadPublishedResults());
        assertEquals(TEST_CONSTRAINT, mMockFilter.getPublishResultsConstraint());
        assertSame(mMockFilter.getExpectResults(), mMockFilter.getResults());

        try {
            countDownLatch.await(TIME_OUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Assert.fail(ie.toString());
        }
    }

    private static class MockFilter extends Filter {
        private boolean mHadPublishedResults = false;
        private boolean mHadPerformedFiltering = false;
        private CharSequence mPerformFilteringConstraint;
        private CharSequence mPublishResultsConstraint;
        private FilterResults mResults;
        private FilterResults mExpectResults = new FilterResults();

        public MockFilter() {
            super();
        }

        public boolean hadPublishedResults() {
            synchronized (this) {
                return mHadPublishedResults;
            }
        }

        public boolean hadPerformedFiltering() {
            synchronized (this) {
                return mHadPerformedFiltering;
            }
        }

        public CharSequence getPerformFilteringConstraint() {
            synchronized (this) {
                return mPerformFilteringConstraint;
            }
        }

        public CharSequence getPublishResultsConstraint() {
            synchronized (this) {
                return mPublishResultsConstraint;
            }
        }

        public FilterResults getResults() {
            synchronized (this) {
                return mResults;
            }
        }

        public FilterResults getExpectResults() {
            synchronized (this) {
                return mExpectResults;
            }
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            synchronized (this) {
                mHadPerformedFiltering = true;
                mPerformFilteringConstraint = constraint;
                return mExpectResults;
            }
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            synchronized (this) {
                mPublishResultsConstraint = constraint;
                mResults = results;
                mHadPublishedResults = true;
            }
        }
    }
}
