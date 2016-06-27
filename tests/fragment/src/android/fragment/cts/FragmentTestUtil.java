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
package android.fragment.cts;

import static org.junit.Assert.assertEquals;

import android.app.Fragment;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.view.ViewGroup;

public class FragmentTestUtil {
    public static boolean executePendingTransactions(final ActivityTestRule<FragmentTestActivity> rule)
            throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final boolean[] ret = new boolean[1];
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ret[0] = rule.getActivity().getFragmentManager().executePendingTransactions();
            }
        });
        return ret[0];
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<FragmentTestActivity> rule)
            throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final boolean[] ret = new boolean[1];
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ret[0] = rule.getActivity().getFragmentManager().popBackStackImmediate();
            }
        });
        return ret[0];
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<FragmentTestActivity> rule,
            final int id, final int flags) throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final boolean[] ret = new boolean[1];
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ret[0] = rule.getActivity().getFragmentManager().popBackStackImmediate(id, flags);
            }
        });
        return ret[0];
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<FragmentTestActivity> rule,
            final String name, final int flags) throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final boolean[] ret = new boolean[1];
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ret[0] = rule.getActivity().getFragmentManager().popBackStackImmediate(name, flags);
            }
        });
        return ret[0];
    }

    public static void setContentView(final ActivityTestRule<FragmentTestActivity> rule,
            final int layoutId) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                rule.getActivity().setContentView(layoutId);
            }
        });
    }

    public static void assertChildren(ViewGroup container, Fragment... fragments) {
        final int numFragments = fragments == null ? 0 : fragments.length;
        assertEquals("There aren't the correct number of fragment Views in its container",
                numFragments, container.getChildCount());
        for (int i = 0; i < numFragments; i++) {
            assertEquals("Wrong Fragment View order for [" + i + "]", container.getChildAt(i),
                    fragments[i].getView());
        }
    }
}
