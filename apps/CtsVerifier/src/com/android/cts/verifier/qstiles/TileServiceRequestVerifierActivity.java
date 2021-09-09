/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.verifier.qstiles;

import android.app.StatusBarManager;
import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class TileServiceRequestVerifierActivity extends InteractiveVerifierActivity {

    private StatusBarManager mStatusBarManager;
    private CharSequence mTileLabel;
    private Icon mIcon;

    @Override
    protected void onCreate(Bundle savedState) {
        mTileLabel = getString(R.string.tile_request_service_name);
        super.onCreate(savedState);
        mStatusBarManager = getSystemService(StatusBarManager.class);
        mIcon = Icon.createWithResource(this, android.R.drawable.ic_dialog_alert);
    }

    @Override
    protected ComponentName getTileComponentName() {
        String tilePath = "com.android.cts.verifier/"
                + "com.android.cts.verifier.qstiles.RequestTileService";
        return ComponentName.unflattenFromString(tilePath);
    }

    @Override
    protected int getTitleResource() {
        return R.string.tiles_request_test;
    }

    @Override
    protected int getInstructionsResource() {
        return R.string.tiles_request_info;
    }

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        ArrayList<InteractiveTestCase> list = new ArrayList<>();
        list.add(new SettingUpTile());
        list.add(new TileNotPresent());
        list.add(new RequestAddTileDismiss());
        list.add(new RequestAddTileAnswerNo());
        list.add(new RequestAddTileCorrectInfo());
        list.add(new RequestAddTileAnswerYes());
        list.add(new TilePresentAfterRequest());
        list.add(new RequestAddTileAlreadyAdded());
        return list;
    }

    private class SettingUpTile extends InteractiveTestCase {

        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_adding_tile);
        }

        @Override
        protected void test() {
            boolean result = setTileState(true);
            if (result) {
                status = PASS;
            } else {
                setFailed("Tile Service failed to enable");
            }
        }
    }

    // Tests
    private class TileNotPresent extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createUserPassFail(parent, R.string.tiles_request_tile_not_present, mTileLabel);

        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class RequestAddTileDismiss extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_dismissed);
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        @Override
        protected void requestAction() {
            int result = mStatusBarManager.requestAddTileService(
                    getTileComponentName(),
                    mTileLabel,
                    mIcon,
                    mContext.getMainExecutor(),
                    integer -> {
                        if (integer.equals(
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)) {
                            status = PASS;
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
            if (result != StatusBarManager.TILE_ADD_REQUEST_ANSWER_SUCCESS) {
                setFailed("Request returned error code " + result);
                next();
            }
        }

        @Override
        protected void test() {
            setTileState(true);
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class RequestAddTileAnswerNo extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_answer_no);
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        @Override
        protected void requestAction() {
            int result = mStatusBarManager.requestAddTileService(
                    getTileComponentName(),
                    mTileLabel,
                    mIcon,
                    mContext.getMainExecutor(),
                    integer -> {
                        if (integer.equals(
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)) {
                            status = PASS;
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
            if (result != StatusBarManager.TILE_ADD_REQUEST_ANSWER_SUCCESS) {
                setFailed("Request returned error code " + result);
                next();
            }
        }

        @Override
        protected void test() {
            setTileState(true);
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class RequestAddTileCorrectInfo extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createUserPassFail(parent, R.string.tiles_request_correct_info,
                    mContext.getString(R.string.app_name),
                    mTileLabel);
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        @Override
        protected void requestAction() {
            int result = mStatusBarManager.requestAddTileService(
                    getTileComponentName(),
                    mTileLabel,
                    mIcon,
                    mContext.getMainExecutor(),
                    integer -> {
                        if (integer.equals(
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)) {
                            status = WAIT_FOR_USER;
                            setPassFailButtonsEnabledState(true);
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
            if (result != StatusBarManager.TILE_ADD_REQUEST_ANSWER_SUCCESS) {
                setFailed("Request returned error code " + result);
                next();
            }
        }

        @Override
        protected void test() {
            setTileState(true);
            status = WAIT_FOR_USER;
            setPassFailButtonsEnabledState(false);
            next();
        }
    }

    private class RequestAddTileAnswerYes extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_answer_yes);
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        @Override
        protected void requestAction() {
            int result = mStatusBarManager.requestAddTileService(
                    getTileComponentName(),
                    mTileLabel,
                    mIcon,
                    mContext.getMainExecutor(),
                    integer -> {
                        if (integer.equals(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)) {
                            status = PASS;
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
            if (result != StatusBarManager.TILE_ADD_REQUEST_ANSWER_SUCCESS) {
                setFailed("Request returned error code " + result);
            }
        }

        @Override
        protected void test() {
            setTileState(true);
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class TilePresentAfterRequest extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createUserPassFail(parent, R.string.tiles_request_tile_present, mTileLabel);
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class RequestAddTileAlreadyAdded extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_check_tile_already_added);
        }

        @Override
        protected void test() {
            int result = mStatusBarManager.requestAddTileService(
                    getTileComponentName(),
                    mTileLabel,
                    mIcon,
                    mContext.getMainExecutor(),
                    integer -> {
                        if (integer.equals(
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)) {
                            status = PASS;
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
            if (result != StatusBarManager.TILE_ADD_REQUEST_ANSWER_SUCCESS) {
                setFailed("Request returned error code " + result);
                next();
                return;
            }
            status = READY_AFTER_LONG_DELAY;
            next();
        }
    }
}
