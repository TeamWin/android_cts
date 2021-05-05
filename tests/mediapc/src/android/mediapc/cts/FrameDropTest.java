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

package android.mediapc.cts;

import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class FrameDropTest extends FrameDropTestBase {
    private static final String LOG_TAG = FrameDropTest.class.getSimpleName();

    public FrameDropTest(String mimeType, String decoderName, boolean isAsync) {
        super(mimeType, decoderName, isAsync);
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        return prepareArgumentsList(null);
    }

    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodeToSurface() throws Exception {
        PlaybackFrameDrop playbackFrameDrop = new PlaybackFrameDrop(mMime, mDecoderName,
                new String[]{m1080pTestFiles.get(mMime)}, mSurface, FRAME_RATE, mIsAsync);
        int frameDropCount = playbackFrameDrop.getFrameDropCount();
        assertTrue("FrameDrop count for mime: " + mMime + ", decoder: " + mDecoderName +
                ", FrameRate: " + FRAME_RATE + ", is not as expected. act/exp: " + frameDropCount +
                "/" + MAX_FRAME_DROP_FOR_30S, frameDropCount <= MAX_FRAME_DROP_FOR_30S);
    }
}
