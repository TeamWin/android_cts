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


package com.android.cts.verifier.voicemail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.voicemail.VoicemailBroadcastReceiver.ReceivedListener;

/**
 * This test ask the tester to set the CTS verifier as the default dialer and leave a voicemail. The
 * test will pass if the verifier is able to receive a broadcast for the incoming voicemail. This
 * depends on telephony to send the broadcast to the default dialer when receiving a Message Waiting
 * Indicator SMS.
 */
public class VoicemailBroadcastActivity extends PassFailButtons.Activity {

    private String mDefaultDialer;

    private ImageView mSetDefaultDialerImage;
    private ImageView mLeaveVoicemailImage;
    private TextView mLeaveVoicemailText;
    private ImageView mRestoreDefaultDialerImage;
    private TextView mRestoreDefaultDialerText;

    private Button mSetDefaultDialerButton;
    private Button mRestoreDefaultDialerButton;

    private BroadcastReceiver mDefaultDialerChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName =
                    intent.getStringExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME);
            if (!getPassButton().isEnabled()) {
                updateSetDefaultDialerState(packageName);
            } else {
                if (packageName.equals(getPackageName())) {
                    mRestoreDefaultDialerImage
                            .setImageDrawable(getDrawable(R.drawable.fs_indeterminate));
                } else {
                    mRestoreDefaultDialerImage.setImageDrawable(getDrawable(R.drawable.fs_good));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.voicemail_broadcast, null);
        setContentView(view);
        setInfoResources(R.string.voicemail_broadcast_test,
                R.string.voicemail_broadcast_instructions, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mSetDefaultDialerImage = (ImageView) findViewById(R.id.set_default_dialer_image);
        mLeaveVoicemailImage = (ImageView) findViewById(R.id.leave_voicemail_image);
        mLeaveVoicemailText = (TextView) findViewById(R.id.leave_voicemail_text);
        mRestoreDefaultDialerImage = (ImageView) findViewById(R.id.restore_default_dialer_image);
        mRestoreDefaultDialerText = (TextView) findViewById(R.id.restore_default_dialer_text);

        mSetDefaultDialerButton = (Button) view.findViewById(R.id.set_default_dialer);
        mRestoreDefaultDialerButton = (Button) view.findViewById(R.id.restore_default_dialer);

        final TelecomManager telecomManager = getSystemService(TelecomManager.class);
        mDefaultDialer = telecomManager.getDefaultDialerPackage();
        updateSetDefaultDialerState(mDefaultDialer);
        if (mDefaultDialer.equals(getPackageName())) {
            // The CTS verifier is already the default dialer (probably due to the tester exiting
            // mid test. We don't know what the default dialer should be so just prompt the tester
            // to restore it through settings, and remove the button.
            mRestoreDefaultDialerText
                    .setText(R.string.voicemail_restore_default_dialer_no_default_description);
            mRestoreDefaultDialerButton.setVisibility(View.GONE);
        }

        mSetDefaultDialerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (telecomManager.getDefaultDialerPackage().equals(getPackageName())) {
                    Toast.makeText(VoicemailBroadcastActivity.this,
                            R.string.voicemail_default_dialer_already_set, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                final Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        getPackageName());
                startActivityForResult(intent, 0);
            }
        });

        mRestoreDefaultDialerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (telecomManager.getDefaultDialerPackage().equals(mDefaultDialer)) {
                    Toast.makeText(VoicemailBroadcastActivity.this,
                            R.string.voicemail_default_dialer_already_restored, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                final Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        mDefaultDialer);
                startActivityForResult(intent, 0);
            }
        });

        VoicemailBroadcastReceiver.setListener(new ReceivedListener() {
            @Override
            public void onReceived() {

                Toast.makeText(VoicemailBroadcastActivity.this,
                        R.string.voicemail_broadcast_received, Toast.LENGTH_SHORT).show();
                mLeaveVoicemailImage.setImageDrawable(getDrawable(R.drawable.fs_good));
                mLeaveVoicemailText.setText(R.string.voicemail_broadcast_received);
                getPassButton().setEnabled(true);
            }
        });

        registerReceiver(mDefaultDialerChangedReceiver,
                new IntentFilter(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED));
    }

    @Override
    protected void onDestroy() {
        VoicemailBroadcastReceiver.setListener(null);
        unregisterReceiver(mDefaultDialerChangedReceiver);
        super.onDestroy();
    }

    private void updateSetDefaultDialerState(String packageName) {
        if (packageName.equals(getPackageName())) {
            mSetDefaultDialerImage.setImageDrawable(getDrawable(R.drawable.fs_good));
        } else {
            mSetDefaultDialerImage.setImageDrawable(getDrawable(R.drawable.fs_indeterminate));
        }
    }
}
