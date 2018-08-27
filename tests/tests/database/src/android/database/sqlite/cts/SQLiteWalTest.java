/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.database.sqlite.cts;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteCompatibilityWalFlags;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.OpenParams;
import android.test.AndroidTestCase;

import com.android.compatibility.common.util.SystemUtil;

import java.io.File;

public class SQLiteWalTest extends AndroidTestCase {
    private static final String TAG = "SQLiteWalTest";

    private static final String DB_FILE = "SQLiteWalTest.db";
    private static final String SHM_SUFFIX = "-shm";
    private static final String WAL_SUFFIX = "-wal";

    private static final String BACKUP_SUFFIX = ".bak";

    private static final String SQLITE_COMPATIBILITY_WAL_FLAGS = "sqlite_compatibility_wal_flags";
    private static final String TRUNCATE_SIZE_KEY = "truncate_size";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        SystemUtil.runShellCommand("settings delete global " + SQLITE_COMPATIBILITY_WAL_FLAGS);

        super.tearDown();
    }

    private void setCompatibilityWalFlags(String value) {
        // settings put global sqlite_compatibility_wal_flags truncate_size=0

        SystemUtil.runShellCommand("settings put global " + SQLITE_COMPATIBILITY_WAL_FLAGS + " "
                + value);
    }

    private void copyFile(String from, String to) {
        SystemUtil.runShellCommand("rm -f " + to);
        SystemUtil.runShellCommand("cp -p " + from + " " + to);
    }

    private void backupFile(String from) {
        SystemUtil.runShellCommand("cp -p " + from + " " + from + BACKUP_SUFFIX);
    }

    private void restoreFile(String from) {
        SystemUtil.runShellCommand("cp -p " + from + BACKUP_SUFFIX + " " + from);
    }

    private SQLiteDatabase openDatabase() {
        SQLiteDatabase db = mContext.openOrCreateDatabase(
                DB_FILE, Context.MODE_ENABLE_WRITE_AHEAD_LOGGING, null);
        db.execSQL("PRAGMA synchronous=FULL");
        return db;
    }

    private void assertTestTableExists(SQLiteDatabase db) {
        assertEquals(1, DatabaseUtils.longForQuery(db, "SELECT count(*) FROM test", null));
    }

    private SQLiteDatabase prepareDatabase() {
        SQLiteDatabase db = openDatabase();

        db.execSQL("CREATE TABLE test (column TEXT);");
        db.execSQL("INSERT INTO test (column) VALUES ("
                + "'12345678901234567890123456789012345678901234567890')");

        // Make sure all the 3 files exist and are bigger than 0 bytes.
        assertTrue((new File(db.getPath())).exists());
        assertTrue((new File(db.getPath() + SHM_SUFFIX)).exists());
        assertTrue((new File(db.getPath() + WAL_SUFFIX)).exists());

        assertTrue((new File(db.getPath())).length() > 0);
        assertTrue((new File(db.getPath() + SHM_SUFFIX)).length() > 0);
        assertTrue((new File(db.getPath() + WAL_SUFFIX)).length() > 0);

        // Make sure the table has 1 row.
        assertTestTableExists(db);

        return db;
    }

    /**
     * Open a WAL database when the WAL file size is bigger than the threshold, and make sure
     * the file gets truncated.
     */
    public void testWalTruncate() {
        mContext.deleteDatabase(DB_FILE);

        // Truncate the WAL file if it's bigger than 1 byte.
        setCompatibilityWalFlags(TRUNCATE_SIZE_KEY + "=1");
        SQLiteCompatibilityWalFlags.reset();

        SQLiteDatabase db = doOperation();

        // Make sure the WAL file is truncated into 0 bytes.
        assertEquals(0, (new File(db.getPath() + WAL_SUFFIX)).length());
    }

    /**
     * Open a WAL database when the WAL file size is smaller than the threshold, and make sure
     * the file does *not* get truncated.
     */
    public void testWalNoTruncate() {
        mContext.deleteDatabase(DB_FILE);

        setCompatibilityWalFlags(TRUNCATE_SIZE_KEY + "=1000000");
        SQLiteCompatibilityWalFlags.reset();

        SQLiteDatabase db = doOperation();

        assertTrue((new File(db.getPath() + WAL_SUFFIX)).length() > 0);
    }

    /**
     * When "truncate size" is set to 0, we don't truncate the wal file.
     */
    public void testWalTruncateDisabled() {
        mContext.deleteDatabase(DB_FILE);

        setCompatibilityWalFlags(TRUNCATE_SIZE_KEY + "=0");
        SQLiteCompatibilityWalFlags.reset();

        SQLiteDatabase db = doOperation();

        assertTrue((new File(db.getPath() + WAL_SUFFIX)).length() > 0);
    }

    private SQLiteDatabase doOperation() {
        SQLiteDatabase db = prepareDatabase();

        // db.close() will remove the wal file, so back the files up.
        backupFile(db.getPath());
        backupFile(db.getPath() + SHM_SUFFIX);
        backupFile(db.getPath() + WAL_SUFFIX);

        // Close the DB, this will remove the WAL file.
        db.close();

        // Restore the files.
        restoreFile(db.getPath());
        restoreFile(db.getPath() + SHM_SUFFIX);
        restoreFile(db.getPath() + WAL_SUFFIX);

        // Open the DB again.
        db = openDatabase();

        // Make sure the table still exists.
        assertTestTableExists(db);

        return db;
    }
}
