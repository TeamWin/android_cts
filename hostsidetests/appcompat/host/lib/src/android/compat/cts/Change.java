/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.compat.cts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.compat.cts.CompatChangeGatingTestCase;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class Change {
    private static final Pattern CHANGE_REGEX = Pattern.compile("^ChangeId\\((?<changeId>[0-9]+)"
                                            + "(; name=(?<changeName>[^;]+))?"
                                            + "(; enableSinceTargetSdk=(?<sinceSdk>[0-9]+))?"
                                            + "(; (?<disabled>disabled))?"
                                            + "(; (?<loggingOnly>loggingOnly))?"
                                            + "(; deferredOverrides=(?<deferredOverrides>[^\\);]+))?"
                                            + "(; packageOverrides=(?<overrides>[^\\)]+))?"
                                            + "\\)");
    public long changeId;
    public String changeName;
    public int sinceSdk;
    public boolean disabled;
    public boolean loggingOnly;
    public boolean hasDeferredOverrides;
    public boolean hasOverrides;

    public String deferredOverridesStr;
    public String overridesStr;

    private Change(long changeId, String changeName, int sinceSdk,
            boolean disabled, boolean loggingOnly, boolean hasDeferredOverrides,
            boolean hasOverrides, String deferredOverridesStr,
            String overridesStr) {
        this.changeId = changeId;
        this.changeName = changeName;
        this.sinceSdk = sinceSdk;
        this.disabled = disabled;
        this.loggingOnly = loggingOnly;
        this.hasDeferredOverrides = hasDeferredOverrides;
        this.hasOverrides = hasOverrides;
        this.deferredOverridesStr = deferredOverridesStr;
        this.overridesStr = overridesStr;
    }

    public static Change fromString(String line) {
        long changeId = 0;
        String changeName;
        int sinceSdk = -1;
        boolean disabled = false;
        boolean loggingOnly = false;
        boolean hasDeferredOverrides = false;
        boolean hasOverrides = false;

        String deferredOverridesStr = null;
        String overridesStr = null;

        Matcher matcher = CHANGE_REGEX.matcher(line);
        if (!matcher.matches()) {
            throw new RuntimeException("Could not match line " + line);
        }

        try {
            changeId = Long.parseLong(matcher.group("changeId"));
        } catch (NumberFormatException e) {
            throw new RuntimeException("No or invalid changeId!", e);
        }
        changeName = matcher.group("changeName");
        String sinceSdkAsString = matcher.group("sinceSdk");
        if (sinceSdkAsString != null) {
            try {
                sinceSdk = Integer.parseInt(sinceSdkAsString);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid sinceSdk for change!", e);
            }
        }
        if (matcher.group("disabled") != null) {
            disabled = true;
        }
        if (matcher.group("loggingOnly") != null) {
            loggingOnly = true;
        }
        if (matcher.group("deferredOverrides") != null) {
            hasDeferredOverrides = true;
            deferredOverridesStr = matcher.group("deferredOverrides");
        }
        if (matcher.group("overrides") != null) {
            hasOverrides = true;
            overridesStr = matcher.group("overrides");
        }
        return new Change(changeId, changeName, sinceSdk, disabled, loggingOnly,
                          hasDeferredOverrides, hasOverrides, deferredOverridesStr,
                          overridesStr);
    }

    public static Change fromNode(Node node) {
        Element element = (Element) node;
        long changeId = Long.parseLong(element.getAttribute("id"));
        String changeName = element.getAttribute("name");
        int sinceSdk = -1;
        if (element.hasAttribute("enableAfterTargetSdk")
            && element.hasAttribute("enableSinceTargetSdk")) {
                throw new IllegalArgumentException("Invalid change node!"
                + "Change contains both enableAfterTargetSdk and enableSinceTargetSdk");
        }
        if (element.hasAttribute("enableAfterTargetSdk")) {
            sinceSdk = Integer.parseInt(element.getAttribute("enableAfterTargetSdk")) + 1;
        }
        if (element.hasAttribute("enableSinceTargetSdk")) {
            sinceSdk = Integer.parseInt(element.getAttribute("enableSinceTargetSdk"));
        }
        boolean disabled = false;
        if (element.hasAttribute("disabled")) {
            disabled = true;
        }
        boolean loggingOnly = false;
        if (element.hasAttribute("loggingOnly")) {
            loggingOnly = true;
        }
        return new Change(changeId, changeName, sinceSdk, disabled, loggingOnly, false, false,
                          null, null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changeId, changeName, sinceSdk, disabled, hasOverrides);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !(other instanceof Change)) {
            return false;
        }
        Change that = (Change) other;
        return this.changeId == that.changeId
            && Objects.equals(this.changeName, that.changeName)
            && this.sinceSdk == that.sinceSdk
            && this.disabled == that.disabled
            && this.loggingOnly == that.loggingOnly
            && this.hasDeferredOverrides == that.hasDeferredOverrides
            && this.hasOverrides == that.hasOverrides;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ChangeId(" + changeId);
        if (changeName != null && !changeName.isEmpty()) {
            sb.append("; name=" + changeName);
        }
        if (sinceSdk != 0) {
            sb.append("; enableSinceTargetSdk=" + sinceSdk);
        }
        if (disabled) {
            sb.append("; disabled");
        }
        if (hasDeferredOverrides) {
            sb.append("; deferredOverrides={");
            sb.append(deferredOverridesStr);
            sb.append("}");
        }
        if (hasOverrides) {
            sb.append("; packageOverrides={");
            sb.append(overridesStr);
            sb.append("}");
        }
        sb.append(")");
        return sb.toString();
    }
}