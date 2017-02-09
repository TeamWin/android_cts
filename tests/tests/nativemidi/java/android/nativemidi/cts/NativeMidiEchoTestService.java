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
 * limitations under the License.
 */

package android.nativemidi.cts;

import android.content.Context;

import android.media.midi.MidiDeviceService;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;

import java.io.IOException;

import android.util.Log;

/**
 * Virtual MIDI Device that copies its input to its output.
 * This is used for loop-back testing of MIDI I/O.
 */

public class NativeMidiEchoTestService extends MidiDeviceService {
    private static final String TAG = "NativeMidiEchoTestService";

    // Other apps will write to this port.
    private MidiReceiver mInputReceiver = new MyReceiver();
    // This app will copy the data to this port.
    private MidiReceiver mOutputReceiver;
    private static NativeMidiEchoTestService mInstance;

    // These are public so we can easily read them from CTS test.
    public int statusChangeCount;
    public boolean inputOpened;
    public int outputOpenCount;

    @Override
    public void onCreate() {
        // Log.i(TAG, "++++ onCreate()");
        super.onCreate();
        mInstance = this;
        MidiManager midiManager = (MidiManager)getSystemService(Context.MIDI_SERVICE);
        // Log.i(TAG, "++++   midiManager:" + midiManager);
    }

    @Override
    public void onDestroy() {
        // Log.i(TAG, "++++ onDestroy()");
        super.onDestroy();
    }

    // For CTS testing, so I can read test fields.
    public static NativeMidiEchoTestService getInstance() {
        // Log.i(TAG, "++++ getInstance()");
        return mInstance;
    }

    @Override
    public MidiReceiver[] onGetInputPortReceivers() {
        // Log.i(TAG, "++++ onGetInputPortReceivers()");
        return new MidiReceiver[] { mInputReceiver };
    }

    class MyReceiver extends MidiReceiver {
        @Override
        public void onSend(byte[] data, int offset, int count, long timestamp)
                throws IOException {
            if (mOutputReceiver == null) {
                mOutputReceiver = getOutputPortReceivers()[0];
            }
            // Copy input to output.
            mOutputReceiver.send(data, offset, count, timestamp);
        }
    }

    @Override
    public void onDeviceStatusChanged(MidiDeviceStatus status) {
        statusChangeCount++;
        inputOpened = status.isInputPortOpen(0);
        outputOpenCount = status.getOutputPortOpenCount(0);
    }
}
