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

package com.android.cts.verifier.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;

public class MtpHostTestActivity extends PassFailButtons.Activity implements Handler.Callback {
    private static final int ITEM_STATE_PASS = 0;
    private static final int ITEM_STATE_FAIL = 1;
    private static final int ITEM_STATE_INDETERMINATE = 2;

    private static final int STEP_RECEIVE_DEVICE = 0;
    private static final int STEP_GRANT_PERMISSION = 1;
    private static final int STEP_COMPLETE = 2;

    private static final String ACTION_PERMISSION_GRANTED =
            "com.android.cts.verifier.usb.ACTION_PERMISSION_GRANTED";

    private final Handler mHandler = new Handler(this);
    private int mStep;
    private final ArrayList<TestItem> mItems = new ArrayList<>();

    private UsbManager mUsbManager;
    private BroadcastReceiver mReceiver;
    private UsbDevice mUsbDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mtp_host_activity);
        setInfoResources(R.string.mtp_host_test, R.string.usb_accessory_test_info, -1);
        setPassFailButtonClickListeners();

        final LayoutInflater inflater = getLayoutInflater();
        final LinearLayout itemsView = (LinearLayout) findViewById(R.id.mtp_host_list);

        // Don't allow a test pass until the accessory and the Android device exchange messages...
        getPassButton().setEnabled(false);

        // Build test items.
        mItems.add(new TestItem(
                STEP_RECEIVE_DEVICE,
                inflater.inflate(R.layout.mtp_host_item, itemsView, false),
                R.string.mtp_host_device_lookup_message));
        mItems.add(new TestItem(
                STEP_GRANT_PERMISSION,
                inflater.inflate(R.layout.mtp_host_item, itemsView, false),
                R.string.mtp_host_grant_permission_message));

        for (final TestItem item : mItems) {
            itemsView.addView(item.view);
        }
        mUsbManager = getSystemService(UsbManager.class);

        mStep = STEP_RECEIVE_DEVICE;
        mHandler.sendEmptyMessage(mStep);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        mStep = msg.what;
        switch (msg.what) {
            case STEP_RECEIVE_DEVICE:
                stepReceiveDevice();
                break;
            case STEP_GRANT_PERMISSION:
                stepGrantPermission();
                break;
            case STEP_COMPLETE:
                getPassButton().setEnabled(true);
                break;
        }
        return true;
    }

    private void completeStep(int status) {
        final TestItem item = mItems.get(mStep);
        item.setState(status);
        if (status == ITEM_STATE_PASS) {
            mHandler.sendEmptyMessage(mStep + 1);
        }
    }

    private void stepReceiveDevice() {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
                mUsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                completeStep(ITEM_STATE_PASS);
            }
        };
        registerReceiver(mReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
    }

    private void stepGrantPermission() {
        final Intent intent = new Intent(ACTION_PERMISSION_GRANTED);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
                completeStep(
                        mUsbManager.hasPermission(mUsbDevice) ? ITEM_STATE_PASS : ITEM_STATE_FAIL);
            }
        };
        registerReceiver(mReceiver, new IntentFilter(ACTION_PERMISSION_GRANTED));
        mUsbManager.requestPermission(mUsbDevice, PendingIntent.getBroadcast(this, 0, intent, 0));
    }

    private static class TestItem {
        final int step;
        final View view;
        int state;

        TestItem(int step, View view, int messageText) {
            this.step = step;
            this.state = ITEM_STATE_INDETERMINATE;
            this.view = view;

            final TextView textView = (TextView) view.findViewById(R.id.instructions);
            textView.setText(messageText);
        }

        void setState(int state) {
            this.state = state;
            final ImageView imageView = (ImageView) view.findViewById(R.id.status);
            switch (state) {
                case ITEM_STATE_PASS:
                    imageView.setImageResource(R.drawable.fs_good);
                    break;
                case ITEM_STATE_FAIL:
                    imageView.setImageResource(R.drawable.fs_error);
                    break;
                case ITEM_STATE_INDETERMINATE:
                    imageView.setImageResource(R.drawable.fs_indeterminate);
                    break;
            }
        }
    }
}
