/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import static androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Instrumentation;
import android.app.stubs.DialogStubActivity;
import android.app.stubs.R;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WindowUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

@SmallTest
@RunWith(JUnit4.class)
public class AlertDialog_BuilderTest  {
    private Builder mBuilder;
    private Instrumentation mInstrumentation;
    private final CharSequence mTitle = "title";
    private Drawable mDrawable;
    private AlertDialog mDialog;
    private Button mButton;
    private CharSequence mSelectedItem;

    private View mView;
    private ListView mListView;

    private OnClickListener mOnClickListener = mock(OnClickListener.class);

    private OnCancelListener mOnCancelListener = mock(OnCancelListener.class);

    private OnDismissListener mOnDismissListener = mock(OnDismissListener.class);

    private OnKeyListener mOnKeyListener = mock(OnKeyListener.class);

    private OnItemSelectedListener mOnItemSelectedListener = mock(OnItemSelectedListener.class);

    @Rule
    public ActivityTestRule<DialogStubActivity> mActivityRule =
            new ActivityTestRule<>(DialogStubActivity.class);

    private Context mContext;

    private OnMultiChoiceClickListener mOnMultiChoiceClickListener =
            mock(OnMultiChoiceClickListener.class);

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = mActivityRule.getActivity();
        mContext = activity;
        WindowUtil.waitForFocus(activity);
    }

    @Test
    public void testConstructor() {
        new AlertDialog.Builder(mContext);
    }

    @Test
    public void testConstructorWithThemeId() {
        mBuilder = new AlertDialog.Builder(mContext, R.style.DialogTheme_Test);

        // Get the context from the builder and attempt to resolve a custom attribute
        // set on our theme. This way we verify that our theme has been applied to the
        // builder.
        final Context themedContext = mBuilder.getContext();
        int[] attrs = new int[] { R.attr.themeInteger };
        TypedArray ta = themedContext.obtainStyledAttributes(attrs);
        assertEquals(20, ta.getInt(0, 0));
    }

    @Test
    public void testSetIconWithParamInt() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mDrawable = mContext.getResources().getDrawable(android.R.drawable.btn_default);
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setIcon(android.R.drawable.btn_default);
                mDialog = mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetIconWithParamDrawable() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mDrawable = mContext.getResources().getDrawable(android.R.drawable.btn_default);
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setIcon(mDrawable);
                mDialog = mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetIconAttribute() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mDrawable = mContext.getResources().getDrawable(android.R.drawable.btn_default);
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setIconAttribute(android.R.attr.alertDialogIcon);
                mDialog = mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetPositiveButtonWithParamInt() throws Throwable {
       runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setPositiveButton(android.R.string.yes, mOnClickListener);
                mBuilder.setOnDismissListener(mOnDismissListener);
                mDialog = mBuilder.show();
                mButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                mButton.performClick();
            }
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(mContext.getText(android.R.string.yes), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetPositiveButtonWithParamCharSequence() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setPositiveButton(android.R.string.yes, mOnClickListener);
                mBuilder.setOnDismissListener(mOnDismissListener);
                mDialog = mBuilder.show();
                mButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                mButton.performClick();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mContext.getText(android.R.string.yes), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNegativeButtonWithParamCharSequence() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setNegativeButton(mTitle, mOnClickListener);
                mBuilder.setOnDismissListener(mOnDismissListener);
                mDialog = mBuilder.show();
                mButton = mDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                mButton.performClick();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTitle, mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEGATIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNegativeButtonWithParamInt() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setNegativeButton(R.string.notify, mOnClickListener);
                mBuilder.setOnDismissListener(mOnDismissListener);
                mDialog = mBuilder.show();
                mButton = mDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                mButton.performClick();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mContext.getText(R.string.notify), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEGATIVE);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNeutralButtonWithParamInt() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setNeutralButton(R.string.notify, mOnClickListener);
                mBuilder.setOnDismissListener(mOnDismissListener);
                mDialog = mBuilder.show();
                mButton = mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                mButton.performClick();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mContext.getText(R.string.notify), mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEUTRAL);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetNeutralButtonWithParamCharSequence() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setNeutralButton(mTitle, mOnClickListener);
                mBuilder.setOnDismissListener(mOnDismissListener);
                mDialog = mBuilder.show();
                mButton = mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                mButton.performClick();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mTitle, mButton.getText());
        verify(mOnClickListener, times(1)).onClick(mDialog, DialogInterface.BUTTON_NEUTRAL);
        verifyNoMoreInteractions(mOnClickListener);
        // Button click should also dismiss the dialog and notify the listener
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    private void testCancelable(final boolean cancelable) throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setCancelable(cancelable);
                mDialog = mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(mDialog::isShowing);
        sendKeySync(KeyEvent.KEYCODE_BACK);
        mInstrumentation.waitForIdleSync();
        new PollingCheck() {
            @Override
            protected boolean check() {
                boolean showing = mDialog.isShowing();
                if (cancelable) {
                    // if the dialog is cancelable, then pressing back
                    // should cancel it. Thus it should not be showing
                    return !showing;
                } else {
                    // if the dialog is not cancelable, pressing back
                    // should so nothing and it should still be showing
                    return showing;
                }
            }
        }.run();
    }

    @Test
    public void testSetCancelable() throws Throwable {
        testCancelable(true);
    }

    @Test
    public void testDisableCancelable() throws Throwable {
        testCancelable(false);
    }

    @Test
    public void testSetOnCancelListener() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setOnCancelListener(mOnCancelListener);
                mDialog = mBuilder.show();
                mDialog.cancel();
            }
        });
        mInstrumentation.waitForIdleSync();
        verify(mOnCancelListener, times(1)).onCancel(mDialog);
        verifyNoMoreInteractions(mOnCancelListener);
    }

    @Test
    public void testSetOnDismissListener() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setOnDismissListener(mOnDismissListener);
                mDialog = mBuilder.show();
                mDialog.dismiss();
            }
        });
        mInstrumentation.waitForIdleSync();
        verify(mOnDismissListener, times(1)).onDismiss(mDialog);
        verifyNoMoreInteractions(mOnDismissListener);
    }

    @Test
    public void testSetOnKeyListener() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setOnKeyListener(mOnKeyListener);
                mDialog = mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();
        sendKeySync(KeyEvent.KEYCODE_0);
        sendKeySync(KeyEvent.KEYCODE_1);
        mInstrumentation.waitForIdleSync();
        // Use Mockito captures so that we can verify that each "sent" key code resulted
        // in one DOWN event and one UP event.
        ArgumentCaptor<KeyEvent> keyEvent0Captor = ArgumentCaptor.forClass(KeyEvent.class);
        ArgumentCaptor<KeyEvent> keyEvent1Captor = ArgumentCaptor.forClass(KeyEvent.class);
        verify(mOnKeyListener, times(2)).onKey(eq(mDialog), eq(KeyEvent.KEYCODE_0),
                keyEvent0Captor.capture());
        verify(mOnKeyListener, times(2)).onKey(eq(mDialog), eq(KeyEvent.KEYCODE_1),
                keyEvent1Captor.capture());
        verifyNoMoreInteractions(mOnKeyListener);
        assertEquals(KeyEvent.ACTION_DOWN, keyEvent0Captor.getAllValues().get(0).getAction());
        assertEquals(KeyEvent.ACTION_UP, keyEvent0Captor.getAllValues().get(1).getAction());
        assertEquals(KeyEvent.ACTION_DOWN, keyEvent1Captor.getAllValues().get(0).getAction());
        assertEquals(KeyEvent.ACTION_UP, keyEvent1Captor.getAllValues().get(1).getAction());
    }

    @Test
    public void testSetItemsWithParamInt() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setItems(R.array.difficultyLevel, mOnClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
            }
        });
        mInstrumentation.waitForIdleSync();

        final CharSequence[] levels = mContext.getResources().getTextArray(
                R.array.difficultyLevel);
        assertEquals(levels[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetItemsWithParamCharSequence() throws Throwable {
        final CharSequence[] expect = mContext.getResources().getTextArray(
                R.array.difficultyLevel);

        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setItems(expect, mOnClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(expect[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetAdapter() throws Throwable {
        final ListAdapter adapter = new AdapterTest();
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setAdapter(adapter, mOnClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(adapter, mListView.getAdapter());
    }

    @Test
    public void testSetMultiChoiceItemsWithParamInt() throws Throwable {

        final CharSequence[] items = mContext.getResources().getTextArray(
                R.array.difficultyLevel);

        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setMultiChoiceItems(R.array.difficultyLevel, null,
                        mOnMultiChoiceClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
                mSelectedItem = (CharSequence)mListView.getSelectedItem();
                mListView.performItemClick(null, 0, 0);
                mListView.performItemClick(null, 1, 0);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 0, true);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 1, true);
        verifyNoMoreInteractions(mOnMultiChoiceClickListener);
        assertEquals(items[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetMultiChoiceItemsWithParamCharSequence() throws Throwable {
        final CharSequence[] items = mContext.getResources().getTextArray(
                R.array.difficultyLevel);

        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setMultiChoiceItems(items, null, mOnMultiChoiceClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
                mSelectedItem = (CharSequence)mListView.getSelectedItem();
                mListView.performItemClick(null, 0, 0);
                mListView.performItemClick(null, 1, 0);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 0, true);
        verify(mOnMultiChoiceClickListener, times(1)).onClick(mDialog, 1, true);
        verifyNoMoreInteractions(mOnMultiChoiceClickListener);
        assertEquals(items[0], mListView.getItemAtPosition(0));
    }

    @Test
    public void testSetSingleChoiceItemsWithParamInt() throws Throwable {
        final CharSequence[] items = mContext.getResources().getTextArray(
                R.array.difficultyLevel);

        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setSingleChoiceItems(R.array.difficultyLevel, 0,
                        mOnClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
                mSelectedItem = (CharSequence)mListView.getSelectedItem();
                mListView.performItemClick(null, 0, 0);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        assertEquals(items[0], mListView.getItemAtPosition(0));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    @Test
    public void testSetSingleChoiceItemsWithParamCharSequence() throws Throwable {
        final CharSequence[] items = mContext.getResources().getTextArray(
                R.array.difficultyLevel);

        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setSingleChoiceItems(items, 0, mOnClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
                mSelectedItem = (CharSequence)mListView.getSelectedItem();
                mListView.performItemClick(null, 0, 0);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        assertEquals(items[0], mListView.getItemAtPosition(0));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    @Test
    public void testSetSingleChoiceItems() throws Throwable {
        final CharSequence[] items = mContext.getResources().getTextArray(
                R.array.difficultyLevel);

        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setSingleChoiceItems(new ArrayAdapter<CharSequence>(mContext,
                        android.R.layout.select_dialog_singlechoice, android.R.id.text1, items), 0,
                        mOnClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
                mSelectedItem = (CharSequence)mListView.getSelectedItem();
                mListView.performItemClick(null, 0, 0);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(items[0], mSelectedItem);
        assertEquals(items[0], mListView.getItemAtPosition(0));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    @Test
    public void testSetOnItemSelectedListener() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setOnItemSelectedListener(mOnItemSelectedListener);
                mBuilder.setItems(R.array.difficultyLevel, mOnClickListener);
                mDialog = mBuilder.show();
                mListView = mDialog.getListView();
                mListView.pointToPosition(0, 0);
            }
        });
        mInstrumentation.waitForIdleSync();
        verify(mOnItemSelectedListener, times(1)).onItemSelected(eq(mListView), any(View.class),
                eq(0), any(Long.class));
        verifyNoMoreInteractions(mOnItemSelectedListener);
    }

    @Test
    public void testSetView() throws Throwable {
        final View view = new View(mContext);
        view.setId(100);
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setView(view);
                mDialog = mBuilder.show();
                mView = mDialog.getWindow().findViewById(100);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(view, mView);
    }

    @Test
    public void testSetViewFromInflater() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setView(LayoutInflater.from(mBuilder.getContext()).inflate(
                        R.layout.alert_dialog_text_entry_2, null, false));
                mDialog = mBuilder.show();
                mView = mDialog.getWindow().findViewById(R.id.username_form);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mView);
        assertNotNull(mView.findViewById(R.id.username_view));
        assertNotNull(mView.findViewById(R.id.username_edit));
    }

    @Test
    public void testSetViewById() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setView(R.layout.alert_dialog_text_entry_2);
                mDialog = mBuilder.show();
                mView = mDialog.getWindow().findViewById(R.id.username_form);
            }
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mView);
        assertNotNull(mView.findViewById(R.id.username_view));
        assertNotNull(mView.findViewById(R.id.username_edit));
    }

    @Test
    public void testSetCustomTitle() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setCustomTitle(LayoutInflater.from(mBuilder.getContext()).inflate(
                        R.layout.alertdialog_custom_title, null, false));
                mDialog = mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetInverseBackgroundForced() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mBuilder.setInverseBackgroundForced(true);
                mDialog = mBuilder.create();
                mDialog.show();
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testCreate() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mDialog = mBuilder.create();
                mDialog.show();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertNotNull(mDialog);
        assertTrue(mDialog.isShowing());
    }

    @Test
    public void testShow() throws Throwable {
        runOnUiThread(new Runnable() {
            public void run() {
                mBuilder = new AlertDialog.Builder(mContext);
                mDialog = mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mDialog.isShowing());
    }

    private void sendKeySync(int keyCode) {
        final long downTime = SystemClock.uptimeMillis();
        final KeyEvent downEvent =
                new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);
        mInstrumentation.getUiAutomation().injectInputEvent(downEvent, true /*sync*/);

        final KeyEvent upEvent =
                new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0);
        mInstrumentation.getUiAutomation().injectInputEvent(upEvent, true /*sync*/);
    }

    private static class AdapterTest implements android.widget.ListAdapter {
        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean isEnabled(int position) {
            return false;
        }

        public int getCount() {
            return 0;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public android.view.View getView( int position,
                                          android.view.View convertView,
                                          android.view.ViewGroup parent){
            return null;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public boolean hasStableIds() {
            return false;
        }

        public boolean isEmpty() {
            return true;
        }

        public void registerDataSetObserver(
            android.database.DataSetObserver observer) {
        }

        public void unregisterDataSetObserver(
            android.database.DataSetObserver observer) {
        }
    }
}
