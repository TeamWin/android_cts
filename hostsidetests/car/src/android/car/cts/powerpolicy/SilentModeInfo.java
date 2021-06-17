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

package android.car.cts.powerpolicy;

import java.util.Arrays;

public final class SilentModeInfo {
    private static final String[] ATTR_HEADERS = {"Monitoring HW state signal",
            "Silent mode by HW state signal", "Forced silent mode"};
    private static final int NUMBER_OF_ATTRS = 3;

    public static final String COMMAND = "cmd car_service silent-mode query";
    public static final SilentModeInfo NO_SILENT = new SilentModeInfo(true, false, false);
    public static final SilentModeInfo FORCED_SILENT = new SilentModeInfo(false, true, true);

    private final boolean[] mAttrs;

    private SilentModeInfo(boolean monitoring, boolean byHW, boolean forced) {
        mAttrs = new boolean[] {monitoring, byHW, forced};
    }

    private SilentModeInfo(boolean[] attrs) throws Exception {
        if (attrs.length != NUMBER_OF_ATTRS) {
            throw new IllegalArgumentException("attrs.length must be 3");
        }
        mAttrs = attrs;
    }

    public boolean getForcedSilentMode() {
        return mAttrs[2];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SilentModeInfo that = (SilentModeInfo) o;
        return Arrays.equals(mAttrs, that.mAttrs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mAttrs);
    }

    @Override
    public String toString() {
        return Arrays.toString(mAttrs);
    }

    public static SilentModeInfo parse(String cmdOutput) throws Exception {
        boolean[] attrs = new boolean[ATTR_HEADERS.length];
        String[] lines = cmdOutput.split("\n");

        if (lines.length != SilentModeInfo.ATTR_HEADERS.length) {
            throw new IllegalArgumentException(
                    "SilentModeQueryResult.parse(): malformatted cmd output: " + cmdOutput);
        }
        for (int idx = 0; idx < SilentModeInfo.ATTR_HEADERS.length; idx++) {
            String[] tokens = lines[idx].trim().split(":");
            if (tokens.length != 2
                    || !tokens[0].contains(SilentModeInfo.ATTR_HEADERS[idx])) {
                throw new IllegalArgumentException(
                        "SilentModeQueryResult.parse(): malformatted attr line: " + lines[idx]);
            }
            attrs[idx] = Boolean.parseBoolean(tokens[1].trim());
        }

        return new SilentModeInfo(attrs);
    }

}
