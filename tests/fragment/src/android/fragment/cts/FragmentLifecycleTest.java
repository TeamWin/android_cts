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

import android.app.FragmentController;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentController;
import android.app.FragmentHostCallback;
import android.app.FragmentManager;
import android.app.FragmentManager.FragmentLifecycleCallbacks;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.view.Window;
import android.widget.TextView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FragmentLifecycleTest {

    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<FragmentTestActivity>(FragmentTestActivity.class);

    @Test
    public void basicLifecycle() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment strictFragment = new StrictFragment();

        // Add fragment; StrictFragment will throw if it detects any violation
        // in standard lifecycle method ordering or expected preconditions.
        fm.beginTransaction().add(strictFragment, "EmptyHeadless").commit();
        executePendingTransactions(fm);

        assertTrue("fragment is not added", strictFragment.isAdded());
        assertFalse("fragment is detached", strictFragment.isDetached());
        assertTrue("fragment is not resumed", strictFragment.isResumed());

        // Test removal as well; StrictFragment will throw here too.
        fm.beginTransaction().remove(strictFragment).commit();
        executePendingTransactions(fm);

        assertFalse("fragment is added", strictFragment.isAdded());
        assertFalse("fragment is resumed", strictFragment.isResumed());

        // This one is perhaps counterintuitive; "detached" means specifically detached
        // but still managed by a FragmentManager. The .remove call above
        // should not enter this state.
        assertFalse("fragment is detached", strictFragment.isDetached());
    }

    @Test
    public void detachment() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment f1 = new StrictFragment();
        final StrictFragment f2 = new StrictFragment();

        fm.beginTransaction().add(f1, "1").add(f2, "2").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());

        // Test detaching fragments using StrictFragment to throw on errors.
        fm.beginTransaction().detach(f1).detach(f2).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not detached", f1.isDetached());
        assertTrue("fragment 2 is not detached", f2.isDetached());
        assertFalse("fragment 1 is added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());

        // Only reattach f1; leave v2 detached.
        fm.beginTransaction().attach(f1).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertFalse("fragment 1 is detached", f1.isDetached());
        assertTrue("fragment 2 is not detached", f2.isDetached());

        // Remove both from the FragmentManager.
        fm.beginTransaction().remove(f1).remove(f2).commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());
        assertFalse("fragment 1 is detached", f1.isDetached());
        assertFalse("fragment 2 is detached", f2.isDetached());
    }

    @Test
    public void basicBackStack() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment f1 = new StrictFragment();
        final StrictFragment f2 = new StrictFragment();

        // Add a fragment normally to set up
        fm.beginTransaction().add(f1, "1").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());

        // Remove the first one and add a second. We're not using replace() here since
        // these fragments are headless and as of this test writing, replace() only works
        // for fragments with views and a container view id.
        // Add it to the back stack so we can pop it afterwards.
        fm.beginTransaction().remove(f1).add(f2, "2").addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());

        // Test popping the stack
        fm.popBackStack();
        executePendingTransactions(fm);

        assertFalse("fragment 2 is added", f2.isAdded());
        assertTrue("fragment 1 is not added", f1.isAdded());
    }

    @Test
    public void attachBackStack() throws Throwable {
        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictFragment f1 = new StrictFragment();
        final StrictFragment f2 = new StrictFragment();

        // Add a fragment normally to set up
        fm.beginTransaction().add(f1, "1").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());

        fm.beginTransaction().detach(f1).add(f2, "2").addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not detached", f1.isDetached());
        assertFalse("fragment 2 is detached", f2.isDetached());
        assertFalse("fragment 1 is added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());
    }

    @Test
    public void viewLifecycle() throws Throwable {
        // Test basic lifecycle when the fragment creates a view

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment f1 = new StrictViewFragment();

        fm.beginTransaction().add(android.R.id.content, f1).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        final View view = f1.getView();
        assertNotNull("fragment 1 returned null from getView", view);
        assertTrue("fragment 1's view is not attached to a window", view.isAttachedToWindow());

        fm.beginTransaction().remove(f1).commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertNull("fragment 1 returned non-null from getView after removal", f1.getView());
        assertFalse("fragment 1's previous view is still attached to a window",
                view.isAttachedToWindow());
    }

    @Test
    public void viewReplace() throws Throwable {
        // Replace one view with another, then reverse it with the back stack

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment f1 = new StrictViewFragment();
        final StrictViewFragment f2 = new StrictViewFragment();

        fm.beginTransaction().add(android.R.id.content, f1).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());

        View origView1 = f1.getView();
        assertNotNull("fragment 1 returned null view", origView1);
        assertTrue("fragment 1's view not attached", origView1.isAttachedToWindow());

        fm.beginTransaction().replace(android.R.id.content, f2).addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertTrue("fragment 2 is added", f2.isAdded());
        assertNull("fragment 1 returned non-null view", f1.getView());
        assertFalse("fragment 1's old view still attached", origView1.isAttachedToWindow());
        View origView2 = f2.getView();
        assertNotNull("fragment 2 returned null view", origView2);
        assertTrue("fragment 2's view not attached", origView2.isAttachedToWindow());

        fm.popBackStack();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());
        assertNull("fragment 2 returned non-null view", f2.getView());
        assertFalse("fragment 2's view still attached", origView2.isAttachedToWindow());
        View newView1 = f1.getView();
        assertNotSame("fragment 1 had same view from last attachment", origView1, newView1);
        assertTrue("fragment 1's view not attached", newView1.isAttachedToWindow());
    }

    @Test
    public void viewReplaceMultiple() throws Throwable {
        // Replace several views with one, then reverse it with the back stack

        final FragmentManager fm = mActivityRule.getActivity().getFragmentManager();
        final StrictViewFragment f1 = new StrictViewFragment();
        final StrictViewFragment f2 = new StrictViewFragment();
        final StrictViewFragment f3 = new StrictViewFragment();

        fm.beginTransaction().add(android.R.id.content, f1).commit();
        fm.beginTransaction().add(android.R.id.content, f2).commit();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());

        View origView1 = f1.getView();
        assertNotNull("fragment 1 returned null view", origView1);
        assertTrue("fragment 1's view not attached", origView1.isAttachedToWindow());
        assertSame(origView1, ((ViewGroup)origView1.getParent()).getChildAt(0));

        View origView2 = f2.getView();
        assertNotNull("fragment 2 returned null view", origView2);
        assertTrue("fragment 2's view not attached", origView2.isAttachedToWindow());
        assertSame(origView2, ((ViewGroup)origView1.getParent()).getChildAt(1));

        fm.beginTransaction().replace(android.R.id.content, f3).addToBackStack("stack1").commit();
        executePendingTransactions(fm);

        assertFalse("fragment 1 is added", f1.isAdded());
        assertFalse("fragment 2 is added", f2.isAdded());
        assertTrue("fragment 3 is added", f3.isAdded());
        assertNull("fragment 1 returned non-null view", f1.getView());
        assertNull("fragment 2 returned non-null view", f2.getView());
        assertFalse("fragment 1's old view still attached", origView1.isAttachedToWindow());
        assertFalse("fragment 2's old view still attached", origView2.isAttachedToWindow());
        View origView3 = f3.getView();
        assertNotNull("fragment 3 returned null view", origView3);
        assertTrue("fragment 3's view not attached", origView3.isAttachedToWindow());

        fm.popBackStack();
        executePendingTransactions(fm);

        assertTrue("fragment 1 is not added", f1.isAdded());
        assertTrue("fragment 2 is not added", f2.isAdded());
        assertFalse("fragment 3 is added", f3.isAdded());
        assertNull("fragment 3 returned non-null view", f3.getView());
        assertFalse("fragment 3's view still attached", origView3.isAttachedToWindow());
        View newView1 = f1.getView();
        View newView2 = f2.getView();
        assertNotSame("fragment 1 had same view from last attachment", origView1, newView1);
        assertNotSame("fragment 2 had same view from last attachment", origView2, newView1);
        assertTrue("fragment 1's view not attached", newView1.isAttachedToWindow());
        assertTrue("fragment 2's view not attached", newView2.isAttachedToWindow());
        assertSame(newView1, ((ViewGroup)newView1.getParent()).getChildAt(0));
        assertSame(newView2, ((ViewGroup)newView1.getParent()).getChildAt(1));
    }

    /**
     * This tests that fragments call onDestroy when the activity finishes.
     */
    @Test
    public void fragmentDestroyedOnFinish() throws Throwable {
        final FragmentController fc = FragmentTestUtil.createController(mActivityRule);
        FragmentTestUtil.resume(mActivityRule, fc, null);
        final StrictViewFragment fragmentA = StrictViewFragment.create(R.layout.text_a);
        final StrictViewFragment fragmentB = StrictViewFragment.create(R.layout.text_b);
        mActivityRule.runOnUiThread(() -> {
            FragmentManager fm = fc.getFragmentManager();

            fm.beginTransaction()
                    .add(android.R.id.content, fragmentA)
                    .commit();
            fm.executePendingTransactions();
            fm.beginTransaction()
                    .replace(android.R.id.content, fragmentB)
                    .addToBackStack(null)
                    .commit();
            fm.executePendingTransactions();
        });
        FragmentTestUtil.destroy(mActivityRule, fc);
        assertTrue(fragmentB.mCalledOnDestroy);
        assertTrue(fragmentA.mCalledOnDestroy);
    }

    /**
     * This test confirms that as long as a parent fragment has called super.onCreate,
     * any child fragments added, committed and with transactions executed will be brought
     * to at least the CREATED state by the time the parent fragment receives onCreateView.
     * This means the child fragment will have received onAttach/onCreate.
     */
    @Test
    @MediumTest
    public void childFragmentManagerAttach() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            public void run() {
                FragmentController fc = FragmentController.createController(
                        new HostCallbacks(mActivityRule.getActivity()));
                fc.attachHost(null);
                fc.dispatchCreate();

                FragmentLifecycleCallbacks mockLc = mock(FragmentLifecycleCallbacks.class);
                FragmentLifecycleCallbacks mockRecursiveLc = mock(FragmentLifecycleCallbacks.class);

                FragmentManager fm = fc.getFragmentManager();
                fm.registerFragmentLifecycleCallbacks(mockLc, false);
                fm.registerFragmentLifecycleCallbacks(mockRecursiveLc, true);

                ChildFragmentManagerFragment fragment = new ChildFragmentManagerFragment();
                fm.beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow();

                verify(mockLc, times(1)).onFragmentCreated(fm, fragment, null);

                fc.dispatchActivityCreated();

                Fragment childFragment = fragment.getChildFragment();

                verify(mockLc, times(1)).onFragmentActivityCreated(fm, fragment, null);
                verify(mockRecursiveLc, times(1)).onFragmentActivityCreated(fm, fragment, null);
                verify(mockRecursiveLc, times(1)).onFragmentActivityCreated(fm, childFragment, null);

                fc.dispatchStart();

                verify(mockLc, times(1)).onFragmentStarted(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStarted(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStarted(fm, childFragment);

                fc.dispatchResume();

                verify(mockLc, times(1)).onFragmentResumed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentResumed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentResumed(fm, childFragment);

                // Confirm that the parent fragment received onAttachFragment
                assertTrue("parent fragment did not receive onAttachFragment",
                        fragment.mCalledOnAttachFragment);

                fc.dispatchStop();

                verify(mockLc, times(1)).onFragmentStopped(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStopped(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentStopped(fm, childFragment);

                fc.dispatchDestroy();

                verify(mockLc, times(1)).onFragmentDestroyed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentDestroyed(fm, fragment);
                verify(mockRecursiveLc, times(1)).onFragmentDestroyed(fm, childFragment);
            }
        });
    }

    private void executePendingTransactions(final FragmentManager fm) throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fm.executePendingTransactions();
            }
        });
    }

    /**
     * This tests a deliberately odd use of a child fragment, added in onCreateView instead
     * of elsewhere. It simulates creating a UI child fragment added to the view hierarchy
     * created by this fragment.
     */
    public static class ChildFragmentManagerFragment extends StrictFragment {
        private FragmentManager mSavedChildFragmentManager;
        private ChildFragmentManagerChildFragment mChildFragment;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            mSavedChildFragmentManager = getChildFragmentManager();
        }


        @Override
        public View onCreateView(LayoutInflater inflater,  ViewGroup container,
                 Bundle savedInstanceState) {
            assertSame("child FragmentManagers not the same instance", mSavedChildFragmentManager,
                    getChildFragmentManager());
            ChildFragmentManagerChildFragment child =
                    (ChildFragmentManagerChildFragment) mSavedChildFragmentManager
                            .findFragmentByTag("tag");
            if (child == null) {
                child = new ChildFragmentManagerChildFragment("foo");
                mSavedChildFragmentManager.beginTransaction()
                        .add(child, "tag")
                        .commitNow();
                assertEquals("argument strings don't match", "foo", child.getString());
            }
            mChildFragment = child;
            return new TextView(container.getContext());
        }


        public Fragment getChildFragment() {
            return mChildFragment;
        }
    }

    public static class ChildFragmentManagerChildFragment extends StrictFragment {
        private String mString;

        public ChildFragmentManagerChildFragment() {
        }

        public ChildFragmentManagerChildFragment(String arg) {
            final Bundle b = new Bundle();
            b.putString("string", arg);
            setArguments(b);
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            mString = getArguments().getString("string", "NO VALUE");
        }

        public String getString() {
            return mString;
        }
    }

    static class HostCallbacks extends FragmentHostCallback<Activity> {
        private final Activity mActivity;

        public HostCallbacks(Activity activity) {
            super(activity, null, 0);
            mActivity = activity;
        }

        @Override
        public void onDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        }

        @Override
        public boolean onShouldSaveFragmentState(Fragment fragment) {
            return !mActivity.isFinishing();
        }

        @Override
        public LayoutInflater onGetLayoutInflater() {
            return mActivity.getLayoutInflater().cloneInContext(mActivity);
        }

        @Override
        public Activity onGetHost() {
            return mActivity;
        }

        @Override
        public void onStartActivityFromFragment(
                Fragment fragment, Intent intent, int requestCode,  Bundle options) {
            mActivity.startActivityFromFragment(fragment, intent, requestCode, options);
        }

        @Override
        public void onRequestPermissionsFromFragment( Fragment fragment,
                 String[] permissions, int requestCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean onHasWindowAnimations() {
            return mActivity.getWindow() != null;
        }

        @Override
        public int onGetWindowAnimations() {
            final Window w = mActivity.getWindow();
            return (w == null) ? 0 : w.getAttributes().windowAnimations;
        }

        @Override
        public void onAttachFragment(Fragment fragment) {
            mActivity.onAttachFragment(fragment);
        }

        @Override
        public View onFindViewById(int id) {
            return mActivity.findViewById(id);
        }

        @Override
        public boolean onHasView() {
            final Window w = mActivity.getWindow();
            return (w != null && w.peekDecorView() != null);
        }
    }
}
