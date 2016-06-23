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
import android.content.res.Resources;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.InputType;
import android.text.TextUtils;
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

        new SearchView(mActivity, null, 0, android.R.style.Widget_Material_SearchView);

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
        assertEquals(mActivity.getResources().getDimensionPixelSize(R.dimen.search_view_max_width),
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

    public void testAccessMaxWidth() {
        final Resources res = mActivity.getResources();
        final int maxWidth1 = res.getDimensionPixelSize(R.dimen.search_view_max_width);
        final int maxWidth2 = res.getDimensionPixelSize(R.dimen.search_view_max_width2);

        // Set search view to not be iconified before running max-width tests
        mInstrumentation.runOnMainSync(() -> mSearchView.setIconified(false));

        mInstrumentation.runOnMainSync(() -> mSearchView.setMaxWidth(maxWidth1));
        mInstrumentation.waitForIdleSync();
        assertEquals(maxWidth1, mSearchView.getMaxWidth());
        assertTrue(mSearchView.getWidth() <= maxWidth1);

        mInstrumentation.runOnMainSync(() -> mSearchView.setMaxWidth(maxWidth2));
        mInstrumentation.waitForIdleSync();
        assertEquals(maxWidth2, mSearchView.getMaxWidth());
        assertTrue(mSearchView.getWidth() <= maxWidth2);
    }

    public void testAccessQuery() {
        mInstrumentation.runOnMainSync(() -> mSearchView.setIconified(false));

        final SearchView.OnQueryTextListener mockQueryTextListener =
                mock(SearchView.OnQueryTextListener.class);
        when(mockQueryTextListener.onQueryTextSubmit(anyString())).thenReturn(Boolean.TRUE);
        mSearchView.setOnQueryTextListener(mockQueryTextListener);

        mInstrumentation.runOnMainSync(() -> mSearchView.setQuery("alpha", false));
        assertTrue(TextUtils.equals("alpha", mSearchView.getQuery()));
        // Since we passed false as the second parameter to setQuery, our query text listener
        // should have been invoked only with text change
        verify(mockQueryTextListener, times(1)).onQueryTextChange("alpha");
        verify(mockQueryTextListener, never()).onQueryTextSubmit(anyString());

        mInstrumentation.runOnMainSync(() -> mSearchView.setQuery("beta", true));
        assertTrue(TextUtils.equals("beta", mSearchView.getQuery()));
        // Since we passed true as the second parameter to setQuery, our query text listener
        // should have been invoked on both callbacks
        verify(mockQueryTextListener, times(1)).onQueryTextChange("beta");
        verify(mockQueryTextListener, times(1)).onQueryTextSubmit("beta");

        mInstrumentation.runOnMainSync(() -> mSearchView.setQuery("gamma", true));
        assertTrue(TextUtils.equals("gamma", mSearchView.getQuery()));
        // Since we passed true as the second parameter to setQuery, our query text listener
        // should have been invoked on both callbacks
        verify(mockQueryTextListener, times(1)).onQueryTextChange("gamma");
        verify(mockQueryTextListener, times(1)).onQueryTextSubmit("gamma");

        verifyNoMoreInteractions(mockQueryTextListener);
    }

    public void testAccessQueryHint() {
        mInstrumentation.runOnMainSync(() -> mSearchView.setQueryHint("hint 1"));
        assertTrue(TextUtils.equals("hint 1", mSearchView.getQueryHint()));

        mInstrumentation.runOnMainSync(() -> mSearchView.setQueryHint("hint 2"));
        assertTrue(TextUtils.equals("hint 2", mSearchView.getQueryHint()));
    }

    public void testAccessInputType() {
        mInstrumentation.runOnMainSync(() ->
                mSearchView.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED));
        assertEquals(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, mSearchView.getInputType());

        mInstrumentation.runOnMainSync(() ->
                mSearchView.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS));
        assertEquals(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, mSearchView.getInputType());

        mInstrumentation.runOnMainSync(() -> mSearchView.setInputType(InputType.TYPE_CLASS_PHONE));
        assertEquals(InputType.TYPE_CLASS_PHONE, mSearchView.getInputType());
    }

    public void testAccessImeOptions() {
        mInstrumentation.runOnMainSync(() -> mSearchView.setImeOptions(EditorInfo.IME_ACTION_GO));
        assertEquals(EditorInfo.IME_ACTION_GO, mSearchView.getImeOptions());

        mInstrumentation.runOnMainSync(() -> mSearchView.setImeOptions(EditorInfo.IME_ACTION_DONE));
        assertEquals(EditorInfo.IME_ACTION_DONE, mSearchView.getImeOptions());

        mInstrumentation.runOnMainSync(() -> mSearchView.setImeOptions(EditorInfo.IME_NULL));
        assertEquals(EditorInfo.IME_NULL, mSearchView.getImeOptions());
    }
}
