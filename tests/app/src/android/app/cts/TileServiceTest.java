/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.UiAutomation;
import android.app.stubs.TestTileService;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;

import junit.framework.Assert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TileServiceTest extends AndroidTestCase {
    final String TAG = TileServiceTest.class.getSimpleName();

    private TileService mTileService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        toggleServiceAccess(TestTileService.getComponentName().flattenToString(), false);
        Thread.sleep(200); // wait for service to be unbound
        assertNull(TestTileService.getInstance());
    }

    public void testCreateTileService() {
        final TileService tileService = new TileService();
    }

    public void testLocked_deviceNotLocked() throws Exception {
        if (!TileService.isQuickSettingsSupported()) return;
        startTileService();
        assertFalse(mTileService.isLocked());
    }

    public void testSecure_deviceNotSecure() throws Exception {
        if (!TileService.isQuickSettingsSupported()) return;
        startTileService();
        assertFalse(mTileService.isSecure());
    }

    public void testTile_hasCorrectIcon() throws Exception {
        if (!TileService.isQuickSettingsSupported()) return;
        startTileService();
        Tile tile = mTileService.getQsTile();
        assertEquals(TestTileService.ICON_ID, tile.getIcon().getResId());
    }

    public void testShowDialog() throws Exception {
        if (!TileService.isQuickSettingsSupported()) return;
        Looper.prepare();
        Dialog dialog = new AlertDialog.Builder(mContext).create();
        startTileService();
        clickTile(TestTileService.getComponentName().flattenToString());

        mTileService.showDialog(dialog);

        assertTrue(dialog.isShowing());
        dialog.dismiss();
    }

    public void testUnlockAndRun_phoneIsUnlockedActivityIsRun() throws Exception {
        if (!TileService.isQuickSettingsSupported()) return;
        startTileService();
        assertFalse(mTileService.isLocked());

        TestRunnable testRunnable = new TestRunnable();

        mTileService.unlockAndRun(testRunnable);
        Thread.sleep(100); // wait for activity to run
        assertTrue(testRunnable.hasRan);
    }

    private void startTileService() throws Exception {
        toggleServiceAccess(TestTileService.getComponentName().flattenToString(), true);
        Thread.sleep(200); // wait for service to be bound
        mTileService = TestTileService.getInstance();
        assertNotNull(mTileService);
    }

    private void toggleServiceAccess(String componentName, boolean on) throws Exception {

        String command = " cmd statusbar " + (on ? "add-tile " : "remove-tile ")
                + componentName;

        runCommand(command);
    }

    private void clickTile(String componentName) throws Exception {
        runCommand(" cmd statusbar click-tile " + componentName);
    }

    private void runCommand(String command) throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // Execute command
        try (ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command)) {
            Assert.assertNotNull("Failed to execute shell command: " + command, fd);
            // Wait for the command to finish by reading until EOF
            try (InputStream in = new FileInputStream(fd.getFileDescriptor())) {
                byte[] buffer = new byte[4096];
                while (in.read(buffer) > 0) {}
            } catch (IOException e) {
                throw new IOException("Could not read stdout of command: " + command, e);
            }
        } finally {
            uiAutomation.destroy();
        }
    }

    class TestRunnable implements Runnable {
        boolean hasRan = false;

        @Override
        public void run() {
            hasRan = true;
        }
    }
}
