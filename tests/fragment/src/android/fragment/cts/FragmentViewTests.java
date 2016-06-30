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

import static junit.framework.Assert.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FragmentViewTests {
    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<FragmentTestActivity>(FragmentTestActivity.class);

    private Instrumentation mInstrumentation;

    @Before
    public void setupInstrumentation() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    // Test that adding a fragment adds the Views in the proper order. Popping the back stack
    // should remove the correct Views.
    @Test
    public void addFragments() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        // One fragment with a view
        final StrictViewFragment fragment1 = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment1).addToBackStack(null).commit();
        executePendingTransactions();
        assertChildren(container, fragment1);

        // Add another on top
        final StrictViewFragment fragment2 = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment2).addToBackStack(null).commit();
        executePendingTransactions();
        assertChildren(container, fragment1, fragment2);

        // Now add two in one transaction:
        final StrictViewFragment fragment3 = new StrictViewFragment();
        final StrictViewFragment fragment4 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment3)
                .add(R.id.fragmentContainer, fragment4)
                .addToBackStack(null)
                .commit();
        executePendingTransactions();
        assertChildren(container, fragment1, fragment2, fragment3, fragment4);

        fm.popBackStack();
        executePendingTransactions();
        assertChildren(container, fragment1, fragment2);

        fm.popBackStack();
        executePendingTransactions();
        assertEquals(1, container.getChildCount());
        assertChildren(container, fragment1);

        fm.popBackStack();
        executePendingTransactions();
        assertChildren(container);
    }

    // Add fragments to multiple containers in the same transaction. Make sure that
    // they pop correctly, too.
    @Test
    public void addTwoContainers() throws Throwable {
        setContentView(R.layout.double_container);
        ViewGroup container1 = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer1);
        ViewGroup container2 = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer2);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        final StrictViewFragment fragment1 = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer1, fragment1).addToBackStack(null).commit();
        executePendingTransactions();
        assertChildren(container1, fragment1);

        final StrictViewFragment fragment2 = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer2, fragment2).addToBackStack(null).commit();
        executePendingTransactions();
        assertChildren(container2, fragment2);

        final StrictViewFragment fragment3 = new StrictViewFragment();
        final StrictViewFragment fragment4 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer1, fragment3)
                .add(R.id.fragmentContainer2, fragment4)
                .addToBackStack(null)
                .commit();
        executePendingTransactions();

        assertChildren(container1, fragment1, fragment3);
        assertChildren(container2, fragment2, fragment4);

        fm.popBackStack();
        executePendingTransactions();
        assertChildren(container1, fragment1);
        assertChildren(container2, fragment2);

        fm.popBackStack();
        executePendingTransactions();
        assertChildren(container1, fragment1);
        assertChildren(container2);

        fm.popBackStack();
        executePendingTransactions();
        assertEquals(0, container1.getChildCount());
    }

    // When you add a fragment that's has already been added, it should throw.
    @Test
    public void doubleAdd() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment1).commit();
        executePendingTransactions();

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    fm.beginTransaction()
                            .add(R.id.fragmentContainer, fragment1)
                            .addToBackStack(null)
                            .commit();
                    fm.executePendingTransactions();
                    fail("Adding a fragment that is already added should be an error");
                } catch (IllegalStateException e) {
                    // expected
                }
            }
        });
    }

    // Make sure that removed fragments remove the right Views. Popping the back stack should
    // add the Views back properly
    @Test
    public void removeFragments() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();
        final StrictViewFragment fragment2 = new StrictViewFragment();
        final StrictViewFragment fragment3 = new StrictViewFragment();
        final StrictViewFragment fragment4 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment1, "1")
                .add(R.id.fragmentContainer, fragment2, "2")
                .add(R.id.fragmentContainer, fragment3, "3")
                .add(R.id.fragmentContainer, fragment4, "4")
                .commit();
        executePendingTransactions();
        assertChildren(container, fragment1, fragment2, fragment3, fragment4);

        // Remove a view
        fm.beginTransaction().remove(fragment4).addToBackStack(null).commit();
        executePendingTransactions();

        assertEquals(3, container.getChildCount());
        assertChildren(container, fragment1, fragment2, fragment3);

        // remove another one
        fm.beginTransaction().remove(fragment2).addToBackStack(null).commit();
        executePendingTransactions();
        assertChildren(container, fragment1, fragment3);

        // Now remove the remaining:
        fm.beginTransaction()
                .remove(fragment3)
                .remove(fragment1)
                .addToBackStack(null)
                .commit();
        executePendingTransactions();
        assertChildren(container);

        fm.popBackStack();
        executePendingTransactions();
        final Fragment replacement1 = fm.findFragmentByTag("1");
        final Fragment replacement3 = fm.findFragmentByTag("3");
        assertChildren(container, replacement1, replacement3);

        fm.popBackStack();
        executePendingTransactions();
        final Fragment replacement2 = fm.findFragmentByTag("2");
        assertChildren(container, replacement1, replacement3, replacement2);

        fm.popBackStack();
        executePendingTransactions();
        final Fragment replacement4 = fm.findFragmentByTag("4");
        assertChildren(container, replacement1, replacement3, replacement2, replacement4);
    }

    // Removing a hidden fragment should remove the View and popping should bring it back hidden
    @Test
    public void removeHiddenView() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment1, "1").hide(fragment1).commit();
        executePendingTransactions();
        assertChildren(container, fragment1);
        assertTrue(fragment1.isHidden());

        fm.beginTransaction().remove(fragment1).addToBackStack(null).commit();
        executePendingTransactions();
        assertChildren(container);

        fm.popBackStack();
        executePendingTransactions();
        final Fragment replacement1 = fm.findFragmentByTag("1");
        assertChildren(container, replacement1);
        assertTrue(replacement1.isHidden());
        assertEquals(View.GONE, replacement1.getView().getVisibility());
        mInstrumentation.waitForIdleSync();
    }

    // Removing a detached fragment should do nothing to the View and popping should bring
    // the Fragment back detached
    @Test
    public void removeDetatchedView() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment1, "1")
                .detach(fragment1)
                .commit();
        executePendingTransactions();
        assertChildren(container);
        assertTrue(fragment1.isDetached());

        fm.beginTransaction().remove(fragment1).addToBackStack(null).commit();
        executePendingTransactions();
        assertChildren(container);

        fm.popBackStack();
        executePendingTransactions();
        final Fragment replacement1 = fm.findFragmentByTag("1");
        assertChildren(container);
        assertTrue(replacement1.isDetached());
    }

    // Unlike adding the same fragment twice, you should be able to add and then remove and then
    // add the same fragment in one transaction.
    @Test
    public void addRemoveAdd() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment)
                .remove(fragment)
                .add(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
        executePendingTransactions();
        assertChildren(container, fragment);

        fm.popBackStack();
        executePendingTransactions();
        assertChildren(container);
    }

    // Removing a fragment that isn't in should throw
    @Test
    public void removeNothThere() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction().remove(fragment).commit();
        try {
            executePendingTransactions();
            fail("Removing a fragment that isn't in should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Hide a fragment and its View should be GONE. Then pop it and the View should be VISIBLE
    @Test
    public void hideFragment() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertEquals(View.VISIBLE, fragment.getView().getVisibility());

        fm.beginTransaction().hide(fragment).addToBackStack(null).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertTrue(fragment.isHidden());
        assertEquals(View.GONE, fragment.getView().getVisibility());

        fm.popBackStack();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertFalse(fragment.isHidden());
        assertEquals(View.VISIBLE, fragment.getView().getVisibility());
    }

    // Hiding a hidden fragment should throw
    @Test
    public void doubleHide() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment)
                .hide(fragment)
                .hide(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Hiding a hidden fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Hiding a non-existing fragment should throw
    @Test
    public void hideUnAdded() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .hide(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Hiding a non-existing fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Show a hidden fragment and its View should be VISIBLE. Then pop it and the View should be
    // BONE.
    @Test
    public void showFragment() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).hide(fragment).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertTrue(fragment.isHidden());
        assertEquals(View.GONE, fragment.getView().getVisibility());

        fm.beginTransaction().show(fragment).addToBackStack(null).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertFalse(fragment.isHidden());
        assertEquals(View.VISIBLE, fragment.getView().getVisibility());

        fm.popBackStack();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertTrue(fragment.isHidden());
        assertEquals(View.GONE, fragment.getView().getVisibility());
    }

    // Showing a shown fragment should throw
    @Test
    public void showShown() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment)
                .show(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Showing a visible fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Showing a non-existing fragment should throw
    @Test
    public void showUnAdded() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .show(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Showing a non-existing fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Detaching a fragment should remove the View from the hierarchy. Then popping it should
    // bring it back VISIBLE
    @Test
    public void detachFragment() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertFalse(fragment.isDetached());
        assertEquals(View.VISIBLE, fragment.getView().getVisibility());

        fm.beginTransaction().detach(fragment).addToBackStack(null).commit();
        executePendingTransactions();

        assertChildren(container);
        assertTrue(fragment.isDetached());

        fm.popBackStack();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertFalse(fragment.isDetached());
        assertEquals(View.VISIBLE, fragment.getView().getVisibility());
    }

    // Detaching a hidden fragment should remove the View from the hierarchy. Then popping it should
    // bring it back hidden
    @Test
    public void detachHiddenFragment() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).hide(fragment).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertFalse(fragment.isDetached());
        assertTrue(fragment.isHidden());
        assertEquals(View.GONE, fragment.getView().getVisibility());

        fm.beginTransaction().detach(fragment).addToBackStack(null).commit();
        executePendingTransactions();

        assertChildren(container);
        assertTrue(fragment.isHidden());
        assertTrue(fragment.isDetached());

        fm.popBackStack();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertTrue(fragment.isHidden());
        assertFalse(fragment.isDetached());
        assertEquals(View.GONE, fragment.getView().getVisibility());
    }

    // Detaching a detached fragment should throw
    @Test
    public void detachDetatched() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment)
                .detach(fragment)
                .detach(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Detaching a detached fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Detaching a non-existing fragment should throw
    @Test
    public void detachUnAdded() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .detach(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Detaching a non-existing fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Attaching a fragment should add the View back into the hierarchy. Then popping it should
    // remove it again
    @Test
    public void attachFragment() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).detach(fragment).commit();
        executePendingTransactions();

        assertChildren(container);
        assertTrue(fragment.isDetached());

        fm.beginTransaction().attach(fragment).addToBackStack(null).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertFalse(fragment.isDetached());
        assertEquals(View.VISIBLE, fragment.getView().getVisibility());

        fm.popBackStack();
        executePendingTransactions();

        assertChildren(container);
        assertTrue(fragment.isDetached());
    }

    // Attaching a hidden fragment should add the View as GONE the hierarchy. Then popping it should
    // remove it again.
    @Test
    public void attachHiddenFragment() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment)
                .hide(fragment)
                .detach(fragment)
                .commit();
        executePendingTransactions();

        assertChildren(container);
        assertTrue(fragment.isDetached());
        assertTrue(fragment.isHidden());

        fm.beginTransaction().attach(fragment).addToBackStack(null).commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertTrue(fragment.isHidden());
        assertFalse(fragment.isDetached());
        assertEquals(View.GONE, fragment.getView().getVisibility());

        fm.popBackStack();
        executePendingTransactions();

        assertChildren(container);
        assertTrue(fragment.isDetached());
        assertTrue(fragment.isHidden());
    }

    // Attaching an attached fragment should throw
    @Test
    public void attachAttached() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment)
                .attach(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Attaching an attached fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Attaching a non-existing fragment should throw
    @Test
    public void attachUnAdded() throws Throwable {
        setContentView(R.layout.simple_container);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .attach(fragment)
                .commit();
        try {
            executePendingTransactions();
            fail("Attaching a non-existing fragment should throw an exception");
        } catch (Throwable t) {
            // expected
        }
    }

    // Simple replace of one fragment in a container. Popping should replace it back again
    @Test
    public void replaceOne() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();
        fm.beginTransaction().add(R.id.fragmentContainer, fragment1, "1").commit();
        executePendingTransactions();

        assertChildren(container, fragment1);

        final StrictViewFragment fragment2 = new StrictViewFragment();
        fm.beginTransaction()
                .replace(R.id.fragmentContainer, fragment2)
                .addToBackStack(null)
                .commit();
        executePendingTransactions();

        assertChildren(container, fragment2);
        assertEquals(View.VISIBLE, fragment2.getView().getVisibility());

        fm.popBackStack();
        executePendingTransactions();

        Fragment replacement1 = fm.findFragmentByTag("1");
        assertNotNull(replacement1);
        assertChildren(container, replacement1);
        assertFalse(replacement1.isHidden());
        assertTrue(replacement1.isAdded());
        assertFalse(replacement1.isDetached());
        assertEquals(View.VISIBLE, replacement1.getView().getVisibility());
    }

    // Replace of multiple fragments in a container. Popping should replace it back again
    @Test
    public void replaceTwo() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment fragment1 = new StrictViewFragment();
        final StrictViewFragment fragment2 = new StrictViewFragment();
        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragment1, "1")
                .add(R.id.fragmentContainer, fragment2, "2")
                .hide(fragment2)
                .commit();
        executePendingTransactions();

        assertChildren(container, fragment1, fragment2);

        final StrictViewFragment fragment3 = new StrictViewFragment();
        fm.beginTransaction()
                .replace(R.id.fragmentContainer, fragment3)
                .addToBackStack(null)
                .commit();
        executePendingTransactions();

        assertChildren(container, fragment3);
        assertEquals(View.VISIBLE, fragment3.getView().getVisibility());

        fm.popBackStack();
        executePendingTransactions();

        Fragment replacement1 = fm.findFragmentByTag("1");
        Fragment replacement2 = fm.findFragmentByTag("2");
        assertNotNull(replacement1);
        assertNotNull(replacement2);
        assertChildren(container, replacement1, replacement2);
        assertFalse(replacement1.isHidden());
        assertTrue(replacement1.isAdded());
        assertFalse(replacement1.isDetached());
        assertEquals(View.VISIBLE, replacement1.getView().getVisibility());

        // fragment2 was hidden, so it should be returned hidden
        assertTrue(replacement2.isHidden());
        assertTrue(replacement2.isAdded());
        assertFalse(replacement2.isDetached());
        assertEquals(View.GONE, replacement2.getView().getVisibility());
    }

    // Replace of empty container. Should act as add and popping should just remove the fragment
    @Test
    public void replaceZero() throws Throwable {
        setContentView(R.layout.simple_container);
        ViewGroup container = (ViewGroup)
                mActivityRule.getActivity().findViewById(R.id.fragmentContainer);
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();

        final StrictViewFragment fragment = new StrictViewFragment();
        fm.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
        executePendingTransactions();

        assertChildren(container, fragment);
        assertEquals(View.VISIBLE, fragment.getView().getVisibility());

        fm.popBackStack();
        executePendingTransactions();

        assertChildren(container);
    }

    private void executePendingTransactions() throws Throwable {
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivityRule.getActivity().getFragmentManager().executePendingTransactions();
            }
        });
    }

    private void setContentView(final int layoutId) throws Throwable {
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivityRule.getActivity().setContentView(layoutId);
            }
        });
    }

    private void assertChildren(ViewGroup container, Fragment... fragments) {
        final int numFragments = fragments == null ? 0 : fragments.length;
        assertEquals("There aren't the correct number of fragment Views in its container",
                numFragments, container.getChildCount());
        for (int i = 0; i < numFragments; i++) {
            assertEquals("Wrong Fragment View order for [" + i + "]", container.getChildAt(i),
                    fragments[i].getView());
        }
    }
}
