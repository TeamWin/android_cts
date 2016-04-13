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

import android.app.Activity;
import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.inputmethod.EditorInfo;
import android.widget.SearchView;

import static org.mockito.Mockito.*;

/**
 * Test {@link SearchView}.
 */
@MediumTest
public class SearchViewTest extends ActivityInstrumentationTestCase2<SearchViewCtsActivity> {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private SearchView mSearchView;

    public SearchViewTest() {
        super("android.widget.cts", SearchViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mSearchView = (SearchView) mActivity.findViewById(R.id.search_view);
    }

    @UiThreadTest
    public void testConstructor() {
        new SearchView(mActivity);

        new SearchView(mActivity, null);

        new SearchView(mActivity, null, android.R.attr.searchViewStyle);

        new SearchView(mActivity, null, 0, android.R.style.Widget_Material_Light_SearchView);
    }

    @UiThreadTest
    public void testAttributesFromXml() {
        SearchView searchViewWithAttributes =
                (SearchView) mActivity.findViewById(R.id.search_view_with_defaults);
        assertEquals(mActivity.getString(R.string.search_query_hint),
                searchViewWithAttributes.getQueryHint());
        assertFalse(searchViewWithAttributes.isIconfiedByDefault());
        assertEquals(EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS | EditorInfo.TYPE_CLASS_TEXT,
                searchViewWithAttributes.getInputType());
        assertEquals(EditorInfo.IME_ACTION_DONE, searchViewWithAttributes.getImeOptions());
        assertEquals(mActivity.getResources().getDimensionPixelSize(R.dimen.search_view_maxwidth),
                searchViewWithAttributes.getMaxWidth());
    }

    public void testAccessIconified() {
        mInstrumentation.runOnMainSync(() -> mSearchView.setIconified(true));
        assertTrue(mSearchView.isIconified());

        mInstrumentation.runOnMainSync(() -> mSearchView.setIconified(false));
        assertFalse(mSearchView.isIconified());
    }

    public void testAccessIconifiedByDefault() {
        mInstrumentation.runOnMainSync(() -> mSearchView.setIconifiedByDefault(true));
        assertTrue(mSearchView.isIconfiedByDefault());

        mInstrumentation.runOnMainSync(() -> mSearchView.setIconifiedByDefault(false));
        assertFalse(mSearchView.isIconfiedByDefault());
    }

    public void testDenyIconifyingNonInconifiableView() {
        mInstrumentation.runOnMainSync(() -> {
            mSearchView.setIconifiedByDefault(false);
            mSearchView.setIconified(false);
        });

        mInstrumentation.runOnMainSync(() -> mSearchView.setIconified(true));
        mInstrumentation.waitForIdleSync();

        // Since our search view is marked with iconifiedByDefault=false, call to setIconified
        // with true us going to be ignored, as detailed in the class-level documentation of
        // SearchView.
        assertFalse(mSearchView.isIconified());
    }

    public void testDenyIconifyingInconifiableView() {
        mInstrumentation.runOnMainSync(() -> {
            mSearchView.setIconifiedByDefault(true);
            mSearchView.setIconified(false);
        });

        final SearchView.OnCloseListener mockDenyCloseListener =
                mock(SearchView.OnCloseListener.class);
        when(mockDenyCloseListener.onClose()).thenReturn(Boolean.TRUE);
        mSearchView.setOnCloseListener(mockDenyCloseListener);

        mInstrumentation.runOnMainSync(() -> mSearchView.setIconified(true));
        mInstrumentation.waitForIdleSync();

        // Our mock listener is configured to return true from its onClose, thereby preventing
        // the iconify request to be completed. Check that the listener was called and that the
        // search view is not iconified.
        verify(mockDenyCloseListener, times(1)).onClose();
        assertFalse(mSearchView.isIconified());
    }

    public void testAllowIconifyingInconifiableView() {
        mInstrumentation.runOnMainSync(() -> {
            mSearchView.setIconifiedByDefault(true);
            mSearchView.setIconified(false);
        });

        final SearchView.OnCloseListener mockAllowCloseListener =
                mock(SearchView.OnCloseListener.class);
        when(mockAllowCloseListener.onClose()).thenReturn(Boolean.FALSE);
        mSearchView.setOnCloseListener(mockAllowCloseListener);

        mInstrumentation.runOnMainSync(() -> mSearchView.setIconified(true));
        mInstrumentation.waitForIdleSync();

        // Our mock listener is configured to return false from its onClose, thereby allowing
        // the iconify request to be completed. Check that the listener was called and that the
        // search view is not iconified.
        verify(mockAllowCloseListener, times(1)).onClose();
        assertTrue(mSearchView.isIconified());
    }
}
