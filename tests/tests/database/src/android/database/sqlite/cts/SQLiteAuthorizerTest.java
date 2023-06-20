/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.database.Cursor;
import android.database.sqlite.SQLiteAuthorizer;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.test.AndroidTestCase;
import android.util.Log;

/**
 * Tests to verify {@link SQLiteAuthorizer}.
 */
public class SQLiteAuthorizerTest extends AndroidTestCase {
    private static final String TAG = "SQLiteAuthorizerTest";

    private SQLiteDatabase mDatabase;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        getContext().deleteDatabase(TAG);
        mDatabase = SQLiteDatabase.openDatabase(getContext().getDatabasePath(TAG),
                new SQLiteDatabase.OpenParams.Builder()
                        .addOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY)
                        .setAuthorizerSupportEnabled(true).build());

        mDatabase.execSQL("CREATE TABLE employee (_id INTEGER PRIMARY KEY, "
                + "name TEXT, month INTEGER, salary INTEGER);");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) "
                + "VALUES ('Mike', 9, 1000);");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) "
                + "VALUES ('Jim', 10, 3000);");
        mDatabase.execSQL("INSERT INTO employee (name, month, salary) "
                + "VALUES ('Eric', 10, 4000);");
    }

    private static final SQLiteAuthorizer SALARY_ALLOW = (int action,
            String arg3, String arg4, String arg5, String arg6) -> {
        Log.v(TAG, "Action " + action + " " + arg3 + " " + arg4 + " " + arg5 + " " + arg6);
        return SQLiteAuthorizer.SQLITE_AUTHORIZER_RESULT_OK;
    };

    private static final SQLiteAuthorizer SALARY_IGNORE = (int action,
            String arg3, String arg4, String arg5, String arg6) -> {
        Log.v(TAG, "Action " + action + " " + arg3 + " " + arg4 + " " + arg5 + " " + arg6);
        switch (action) {
            case SQLiteAuthorizer.SQLITE_ACTION_READ:
            case SQLiteAuthorizer.SQLITE_ACTION_UPDATE:
                if ("employee".equals(arg3) && "salary".equals(arg4)) {
                    return SQLiteAuthorizer.SQLITE_AUTHORIZER_RESULT_IGNORE;
                }
        }
        return SQLiteAuthorizer.SQLITE_AUTHORIZER_RESULT_OK;
    };

    private static final SQLiteAuthorizer SALARY_DENY = (int action,
            String arg3, String arg4, String arg5, String arg6) -> {
        Log.v(TAG, "Action " + action + " " + arg3 + " " + arg4 + " " + arg5 + " " + arg6);
        switch (action) {
            case SQLiteAuthorizer.SQLITE_ACTION_READ:
            case SQLiteAuthorizer.SQLITE_ACTION_UPDATE:
                if ("employee".equals(arg3) && "salary".equals(arg4)) {
                    return SQLiteAuthorizer.SQLITE_AUTHORIZER_RESULT_DENY;
                }
        }
        return SQLiteAuthorizer.SQLITE_AUTHORIZER_RESULT_OK;
    };

    @Override
    public void tearDown() throws Exception {
        try {
            mDatabase.close();
            getContext().deleteDatabase(TAG);
        } finally {
            super.tearDown();
        }
    }

    public void testEnabled() {
        SQLiteDatabase mDatabaseAuthEnabled = SQLiteDatabase.openDatabase(
                getContext().getDatabasePath(TAG),
                new SQLiteDatabase.OpenParams.Builder()
                        .addOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY)
                        .setAuthorizerSupportEnabled(true).build());
        assertTrue(mDatabaseAuthEnabled.isAuthorizerSupportEnabled());
        assertTrue(new SQLiteDatabase.OpenParams.Builder().setAuthorizerSupportEnabled(true)
                .isAuthorizerSupportEnabled());

        SQLiteDatabase mDatabaseAuthDisabled = SQLiteDatabase.openDatabase(
                getContext().getDatabasePath(TAG),
                new SQLiteDatabase.OpenParams.Builder()
                        .addOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY)
                        .setAuthorizerSupportEnabled(false).build());
        assertFalse(mDatabaseAuthDisabled.isAuthorizerSupportEnabled());
        assertFalse(new SQLiteDatabase.OpenParams.Builder().setAuthorizerSupportEnabled(false)
                .isAuthorizerSupportEnabled());
    }

    public void testQuery_Allow() {
        try (Cursor c = mDatabase.rawQueryWithFactory(null,
                "SELECT name, month, salary FROM employee", null, null, null, SALARY_ALLOW)) {
            assertEquals(3, c.getCount());
            while (c.moveToNext()) {
                assertFalse(c.isNull(0));
                assertFalse(c.isNull(1));
                assertFalse(c.isNull(2));
            }
        }
    }

    public void testQuery_Ignore() {
        try (Cursor c = mDatabase.rawQueryWithFactory(null,
                "SELECT name, month, salary FROM employee", null, null, null, SALARY_IGNORE)) {
            assertEquals(3, c.getCount());
            while (c.moveToNext()) {
                assertFalse(c.isNull(0));
                assertFalse(c.isNull(1));
                assertTrue(c.isNull(2));
            }
        }
    }

    public void testQuery_Deny() {
        // Some columns are allowed
        try (Cursor c = mDatabase.rawQueryWithFactory(null,
                "SELECT name, month FROM employee", null, null, null, SALARY_DENY)) {
            assertEquals(3, c.getCount());
        }

        // Other columns are blocked
        assertThrows(SQLiteException.class, () -> {
            mDatabase.rawQueryWithFactory(null,
                    "SELECT name, month, salary FROM employee",
                    null, null, null, SALARY_DENY);
        });
        assertThrows(SQLiteException.class, () -> {
            mDatabase.rawQueryWithFactory(null,
                    "SELECT name FROM employee WHERE salary > 2000",
                    null, null, null, SALARY_DENY);
        });
    }

    public void testStatement_Deny() {
        // Some columns are allowed
        mDatabase.compileStatement("UPDATE employee SET month = 4",
                SALARY_DENY).executeUpdateDelete();

        // Other columns are blocked
        assertThrows(SQLiteException.class, () -> {
            mDatabase.compileStatement("UPDATE employee SET salary = 0",
                    SALARY_DENY).executeUpdateDelete();
        });
    }

    public void testExec_Deny() {
        // Some columns are allowed
        mDatabase.execSQL("UPDATE employee SET month = 4",
                null, SALARY_DENY);

        // Other columns are blocked
        assertThrows(SQLiteException.class, () -> {
            mDatabase.execSQL("UPDATE employee SET salary = 0",
                    null, SALARY_DENY);
        });
    }

    public void testValidate_Deny() {
        // Some columns are allowed
        mDatabase.validateSql("UPDATE employee SET month = 4",
                null, SALARY_DENY);

        // Other columns are blocked
        assertThrows(SQLiteException.class, () -> {
            mDatabase.validateSql("UPDATE employee SET salary = 0",
                    null, SALARY_DENY);
        });
    }

    public void testThrows() {
        final SQLiteAuthorizer authorizer = (int action,
                String arg3, String arg4, String arg5, String arg6) -> {
            throw new IllegalArgumentException();
        };

        assertThrows(SQLiteException.class, () -> {
            mDatabase.rawQueryWithFactory(null,
                    "SELECT name, month, salary FROM employee",
                    null, null, null, authorizer);
        });
    }
}
