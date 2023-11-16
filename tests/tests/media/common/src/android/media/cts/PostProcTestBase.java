/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Looper;
import android.test.AndroidTestCase;

import java.lang.Boolean;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PostProcTestBase extends AndroidTestCase {
    private static final int CONTROL_PRIORITY = 100;

    protected int mSession = -1;
    protected boolean mHasControl = false;
    protected boolean mIsEnabled = false;
    protected boolean mInitialized = false;
    protected Looper mLooper = null;
    protected final Object mLock = new Object();
    protected int mChangedParameter = -1;
    protected final static String BUNDLE_VOLUME_EFFECT_UUID =
            "119341a0-8469-11df-81f9-0002a5d5c51b";

    private Map<UUID, Boolean> mOriginalEffectState = new HashMap<>();

    /**
     * All potential effects under test are disabled at setup.
     * Initial state is backuped, restored in tearDown
     * @throws Exception
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final UUID[] effectTypes = {
                AudioEffect.EFFECT_TYPE_BASS_BOOST,
                AudioEffect.EFFECT_TYPE_EQUALIZER,
                AudioEffect.EFFECT_TYPE_VIRTUALIZER,
                AudioEffect.EFFECT_TYPE_PRESET_REVERB,
                AudioEffect.EFFECT_TYPE_ENV_REVERB,
        };
        for (UUID effectType : effectTypes) {
            try {
                if (AudioEffect.isEffectTypeAvailable(effectType)) {
                    AudioEffect effect = new AudioEffect(effectType,
                            AudioEffect.EFFECT_TYPE_NULL,
                            CONTROL_PRIORITY,
                            0);
                    assertTrue("effect does not have control", effect.hasControl());
                    mOriginalEffectState.put(effectType, effect.getEnabled());

                    effect.setEnabled(false);
                    assertFalse("Could not disable effect", effect.getEnabled());
                    effect.release();
                }
            } catch (IllegalStateException e) {
            } catch (IllegalArgumentException e) {
            } catch (UnsupportedOperationException e) {
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (Map.Entry<UUID, Boolean> entry : mOriginalEffectState.entrySet()) {
            try {
                AudioEffect effect = new AudioEffect(entry.getKey(),
                        AudioEffect.EFFECT_TYPE_NULL,
                        CONTROL_PRIORITY,
                        0);
                assertTrue("effect does not have control", effect.hasControl());
                effect.setEnabled(entry.getValue());
                effect.release();
            } catch (IllegalStateException e) {
            } catch (IllegalArgumentException e) {
            } catch (UnsupportedOperationException e) {
            }
        }
    }

    protected boolean hasAudioOutput() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    protected boolean isBassBoostAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_BASS_BOOST);
    }

    protected boolean isVirtualizerAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_VIRTUALIZER);
    }

    protected boolean isPresetReverbAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_PRESET_REVERB);
    }

    protected boolean isEnvReverbAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_ENV_REVERB);
    }

    protected int getSessionId() {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        assertNotNull("could not get AudioManager", am);
        int sessionId = am.generateAudioSessionId();
        assertTrue("Could not generate session id", sessionId>0);
        return sessionId;
    }

}
