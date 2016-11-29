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
 * limitations under the License
 */

package android.preference2.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceDataStore;
import android.preference.PreferenceScreen;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PreferenceDataStoreTest {

    private PreferenceWrapper mPreference;
    private PreferenceDataStore mDataStore;

    private static final String KEY = "TestPrefKey";
    private static final String TEST_STR = "Test";

    @Rule
    public ActivityTestRule<PreferenceFragmentActivity> mActivityRule =
            new ActivityTestRule<>(PreferenceFragmentActivity.class);


    @Before
    public void setup() {
        PreferenceFragmentActivity activity = mActivityRule.getActivity();
        mPreference = new PreferenceWrapper(activity);
        mPreference.setKey(KEY);

        // Assign the Preference to the PreferenceFragment.
        PreferenceScreen screen =
                activity.prefFragment.getPreferenceManager().createPreferenceScreen(activity);
        screen.addPreference(mPreference);

        mDataStore = mock(PreferenceDataStore.class);
    }

    @Test
    public void testPutStringWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        putStringTestCommon();
    }

    @Test
    public void testPutStringWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        putStringTestCommon();
    }

    @Test
    public void testGetStringWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mPreference.getString(TEST_STR);
        verify(mDataStore, atLeastOnce()).getString(eq(KEY), eq(TEST_STR));
    }

    @Test
    public void testGetStringWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        mPreference.getString(TEST_STR);
        verify(mDataStore, atLeastOnce()).getString(eq(KEY), eq(TEST_STR));
    }

    @Test
    public void testPutStringSetWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        putStringSetTestCommon();
    }

    @Test
    public void testPutStringSetWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        putStringSetTestCommon();
    }

    @Test
    public void testGetStringSetWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        Set<String> testSet = new HashSet<>();
        mPreference.getStringSet(testSet);
        verify(mDataStore, atLeastOnce()).getStringSet(eq(KEY), eq(testSet));
    }

    @Test
    public void testGetStringSetWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        Set<String> testSet = new HashSet<>();
        mPreference.getStringSet(testSet);
        verify(mDataStore, atLeastOnce()).getStringSet(eq(KEY), eq(testSet));
    }

    @Test
    public void testPutIntWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        putIntTestCommon();
    }

    @Test
    public void testPutIntWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        putIntTestCommon();
    }

    @Test
    public void testGetIntWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mPreference.getInt(1);
        verify(mDataStore, atLeastOnce()).getInt(eq(KEY), eq(1));
    }

    @Test
    public void testGetIntWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        mPreference.getInt(1);
        verify(mDataStore, atLeastOnce()).getInt(eq(KEY), eq(1));
    }

    @Test
    public void testPutLongWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        putLongTestCommon();
    }

    @Test
    public void testPutLongWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        putLongTestCommon();
    }

    @Test
    public void testGetLongWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mPreference.getLong(1L);
        verify(mDataStore, atLeastOnce()).getLong(eq(KEY), eq(1L));
    }

    @Test
    public void testGetLongWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        mPreference.getLong(1L);
        verify(mDataStore, atLeastOnce()).getLong(eq(KEY), eq(1L));
    }

    @Test
    public void testPutFloatWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        putFloatTestCommon();
    }

    @Test
    public void testPutFloatWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        putFloatTestCommon();
    }

    @Test
    public void testGetFloatWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mPreference.getFloat(1f);
        verify(mDataStore, atLeastOnce()).getFloat(eq(KEY), eq(1f));
    }

    @Test
    public void testGetFloatWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        mPreference.getFloat(1f);
        verify(mDataStore, atLeastOnce()).getFloat(eq(KEY), eq(1f));
    }

    @Test
    public void testPutBooleanWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        putBooleanTestCommon();
    }

    @Test
    public void testPutBooleanWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        putBooleanTestCommon();
    }

    @Test
    public void testGetBooleanWithDataStoreOnPref() {
        mPreference.setPreferenceDataStore(mDataStore);
        mPreference.getBoolean(true);
        verify(mDataStore, atLeastOnce()).getBoolean(eq(KEY), eq(true));
    }

    @Test
    public void testGetBooleanWithDataStoreOnMgr() {
        mPreference.getPreferenceManager().setPreferenceDataStore(mDataStore);
        mPreference.getBoolean(true);
        verify(mDataStore, atLeastOnce()).getBoolean(eq(KEY), eq(true));
    }

    @Test
    public void testDataStoresHierarchy() {
        mPreference.setPreferenceDataStore(mDataStore);
        PreferenceDataStore secondaryDataStore = mock(PreferenceDataStore.class);
        mPreference.getPreferenceManager().setPreferenceDataStore(secondaryDataStore);
        mPreference.putString(TEST_STR);

        // Check that the Preference returns the correct data store.
        assertEquals(mDataStore, mPreference.getPreferenceDataStore());

        // Check that the secondary data store assigned to the manager was NOT used.
        verifyZeroInteractions(secondaryDataStore);

        // Check that the primary data store assigned directly to the preference was used.
        verify(mDataStore, atLeast(0)).getString(eq(KEY), anyString());
    }

    private void putStringTestCommon() {
        mPreference.putString(TEST_STR);

        verify(mDataStore, atLeast(0)).getString(eq(KEY), anyString());
        verify(mDataStore, atLeastOnce()).putString(eq(KEY), anyString());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertNull(mPreference.getSharedPreferences().getString(KEY, null));
    }

    private void putStringSetTestCommon() {
        Set<String> testSet = new HashSet<>();
        testSet.add(TEST_STR);
        mPreference.putStringSet(testSet);

        verify(mDataStore, atLeast(0)).getStringSet(eq(KEY), or(isNull(Set.class), any()));
        verify(mDataStore, atLeastOnce()).putStringSet(eq(KEY), or(isNull(Set.class), any()));
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertNull(mPreference.getSharedPreferences().getStringSet(KEY, null));
    }

    private void putIntTestCommon() {
        mPreference.putInt(1);

        verify(mDataStore, atLeast(0)).getInt(eq(KEY), anyInt());
        verify(mDataStore, atLeastOnce()).putInt(eq(KEY), anyInt());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(-1, mPreference.getSharedPreferences().getInt(KEY, -1));
    }

    private void putLongTestCommon() {
        mPreference.putLong(1L);

        verify(mDataStore, atLeast(0)).getLong(eq(KEY), anyLong());
        verify(mDataStore, atLeastOnce()).putLong(eq(KEY), anyLong());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(-1, mPreference.getSharedPreferences().getLong(KEY, -1L));
    }

    private void putFloatTestCommon() {
        mPreference.putFloat(1f);

        verify(mDataStore, atLeast(0)).getFloat(eq(KEY), anyFloat());
        verify(mDataStore, atLeastOnce()).putFloat(eq(KEY), anyFloat());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(-1, mPreference.getSharedPreferences().getFloat(KEY, -1f), 0.1f /* epsilon */);
    }

    private void putBooleanTestCommon() {
        mPreference.putBoolean(true);

        verify(mDataStore, atLeast(0)).getBoolean(eq(KEY), anyBoolean());
        verify(mDataStore, atLeastOnce()).putBoolean(eq(KEY), anyBoolean());
        verifyNoMoreInteractions(mDataStore);

        // Test that the value was NOT propagated to SharedPreferences.
        assertEquals(false, mPreference.getSharedPreferences().getBoolean(KEY, false));
    }

    /**
     * Wrapper to allow to easily call protected methods.
     */
    private static class PreferenceWrapper extends Preference {

        PreferenceWrapper(Context context) {
            super(context);
        }

        void putString(String value) {
            persistString(value);
        }

        String getString(String defaultValue) {
            return getPersistedString(defaultValue);
        }

        void putStringSet(Set<String> values) {
            persistStringSet(values);
        }

        Set<String> getStringSet(Set<String> defaultValues) {
            return getPersistedStringSet(defaultValues);
        }

        void putInt(int value) {
            persistInt(value);
        }

        int getInt(int defaultValue) {
            return getPersistedInt(defaultValue);
        }

        void putLong(long value) {
            persistLong(value);
        }

        long getLong(long defaultValue) {
            return getPersistedLong(defaultValue);
        }

        void putFloat(float value) {
            persistFloat(value);
        }

        float getFloat(float defaultValue) {
            return getPersistedFloat(defaultValue);
        }

        void putBoolean(boolean value) {
            persistBoolean(value);
        }

        boolean getBoolean(boolean defaultValue) {
            return getPersistedBoolean(defaultValue);
        }
    }

}
