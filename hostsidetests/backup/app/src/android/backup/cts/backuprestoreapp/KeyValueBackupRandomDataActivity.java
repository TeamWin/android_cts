/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.backup.cts.backuprestoreapp;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Scanner;

/**
 * Test activity that reads/writes to shared preferences and files.
 *
 * It uses logcat messages to send the data to the host side of the test.
 * The format of logcat messages: "DATA_PREF: VALUE".
 * VALUES_LOADED_MESSAGE is logged after all the values.
 *
 * Workflow onCreate:
 * - Read shared preferences and files
 * - If the values are default ones:
 *      - Randomly generate new values
 *      - Save new values to shared preferences and files
 *      - Load the new values.
 *
 * Migrated from BackupTestActivity in former BackupTest CTS Verfifier test.
 */
public class KeyValueBackupRandomDataActivity extends Activity {
    private static final String TAG = KeyValueBackupRandomDataActivity.class.getSimpleName();

    private static final String TEST_PREFS_1 = "test-prefs-1";
    private static final String INT_PREF = "int-pref";
    private static final String BOOL_PREF = "bool-pref";

    private static final String TEST_PREFS_2 = "test-prefs-2";
    private static final String FLOAT_PREF = "float-pref";
    private static final String LONG_PREF = "long-pref";
    private static final String STRING_PREF = "string-pref";

    private static final String TEST_FILE_1 = "test-file-1";
    private static final String TEST_FILE_2 = "test-file-2";

    private static final int DEFAULT_INT_VALUE = 0;
    private static final boolean DEFAULT_BOOL_VALUE = false;
    private static final float DEFAULT_FLOAT_VALUE = 0.0f;
    private static final long DEFAULT_LONG_VALUE = 0L;
    private static final String DEFAULT_STRING_VALUE = null;

    private static final String VALUES_LOADED_MESSAGE = "ValuesLoaded";
    private static final String EMPTY_STRING_LOG = "empty";

    private boolean mDefaultValues = true;
    private boolean mValuesWereGenerated;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new LoadBackupItemsTask().execute();
    }

    public static SharedPreferencesBackupHelper getSharedPreferencesBackupHelper(Context context) {
        return new SharedPreferencesBackupHelper(context, TEST_PREFS_1, TEST_PREFS_2);
    }

    public static FileBackupHelper getFileBackupHelper(Context context) {
        return new FileBackupHelper(context, TEST_FILE_1, TEST_FILE_2);
    }

    class LoadBackupItemsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            loadPreferenceGroup1();
            loadPreferenceGroup2();
            loadFile(TEST_FILE_1);
            loadFile(TEST_FILE_2);
            return null;
        }

        private void loadPreferenceGroup1() {
            SharedPreferences prefs = getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);

            int intValue = prefs.getInt(INT_PREF, DEFAULT_INT_VALUE);
            Log.i(TAG, INT_PREF + ":" + intValue);

            boolean boolValue = prefs.getBoolean(BOOL_PREF, DEFAULT_BOOL_VALUE);
            Log.i(TAG, BOOL_PREF + ":" + boolValue);

            mDefaultValues = mDefaultValues
                    && intValue == DEFAULT_INT_VALUE
                    && boolValue == DEFAULT_BOOL_VALUE;
        }

        private void loadPreferenceGroup2() {
            SharedPreferences prefs = getSharedPreferences(TEST_PREFS_2, MODE_PRIVATE);

            float floatValue = prefs.getFloat(FLOAT_PREF, DEFAULT_FLOAT_VALUE);
            Log.i(TAG, FLOAT_PREF + ":" + floatValue);

            long longValue = prefs.getLong(LONG_PREF, DEFAULT_LONG_VALUE);
            Log.i(TAG, LONG_PREF + ":" + longValue);

            String stringValue = prefs.getString(STRING_PREF, DEFAULT_STRING_VALUE);
            Log.i(TAG, STRING_PREF + ":" + stringValue);

            mDefaultValues = mDefaultValues
                            && floatValue == DEFAULT_FLOAT_VALUE
                            && longValue == DEFAULT_LONG_VALUE
                            && stringValue == DEFAULT_STRING_VALUE;
        }

        private void loadFile(String fileName) {
            StringBuilder contents = new StringBuilder();
            Scanner scanner = null;
            try {
                scanner = new Scanner(new File(getFilesDir(), fileName));
                while (scanner.hasNext()) {
                    contents.append(scanner.nextLine());
                }
                scanner.close();
            } catch (FileNotFoundException e) {
                Log.i(TAG, "Couldn't find test file but this may be fine...");
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
            String logString = contents.toString();
            logString = logString.isEmpty() ? EMPTY_STRING_LOG : logString;
            Log.i(TAG, fileName + ":" + logString);

            mDefaultValues = mDefaultValues && contents.toString().isEmpty();
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);

            if (mDefaultValues && !mValuesWereGenerated) {
                new GenerateValuesTask().execute();
            } else {
                Log.i(TAG, VALUES_LOADED_MESSAGE);
            }
        }
    }

    class GenerateValuesTask extends AsyncTask<Void, Void, Exception> {

        @Override
        protected Exception doInBackground(Void... params) {
            Random random = new Random();
            generatePreferenceGroup1(random);
            generatePreferenceGroup2(random);
            try {
                generateTestFile(TEST_FILE_1, random);
                generateTestFile(TEST_FILE_2, random);
            } catch (FileNotFoundException e) {
                return e;
            }
            return null;
        }

        private void generatePreferenceGroup1(Random random) {
            SharedPreferences prefs = getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(INT_PREF, (random.nextInt(100) + 1));
            editor.putBoolean(BOOL_PREF, random.nextBoolean());
            editor.commit();
        }

        private void generatePreferenceGroup2(Random random) {
            SharedPreferences prefs = getSharedPreferences(TEST_PREFS_2, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putFloat(FLOAT_PREF, random.nextFloat());
            editor.putLong(LONG_PREF, random.nextLong());
            editor.putString(STRING_PREF, "Random number " + (random.nextInt(100) + 1));
            editor.commit();
        }

        private void generateTestFile(String fileName, Random random)
                throws FileNotFoundException {
            File file = new File(getFilesDir(), fileName);
            PrintWriter writer = new PrintWriter(file);
            writer.write("Random number " + (random.nextInt(100) + 1));
            writer.close();
        }

        @Override
        protected void onPostExecute(Exception exception) {
            super.onPostExecute(exception);
            mValuesWereGenerated = true;

            if (exception != null) {
                Log.e(TAG, "Couldn't generate test data...", exception);
            } else {
                BackupManager backupManager = new BackupManager(
                        KeyValueBackupRandomDataActivity.this);
                backupManager.dataChanged();

                new LoadBackupItemsTask().execute();
            }
        }
    }
}
