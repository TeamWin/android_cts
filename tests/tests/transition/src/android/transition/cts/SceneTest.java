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
package android.transition.cts;

import android.transition.Scene;
import android.view.View;
import android.view.ViewGroup;

public class SceneTest extends BaseTransitionTest {
    /**
     * Test Scene(ViewGroup) with enterAction and exitAction
     */
    public void testDynamicConstructor() throws Throwable {
        Scene scene = new Scene(mSceneRoot);
        assertEquals(mSceneRoot, scene.getSceneRoot());
        CallCheck enterCheck = new CallCheck() {
            @Override
            public void run() {
                super.run();
                mActivity.getLayoutInflater().inflate(R.layout.scene1, mSceneRoot, true);
            }
        };
        scene.setEnterAction(enterCheck);
        CallCheck exitCheck = new CallCheck();
        scene.setExitAction(exitCheck);
        enterScene(scene);

        assertTrue(enterCheck.wasRun);
        assertFalse(exitCheck.wasRun);

        View redSquare = mActivity.findViewById(R.id.redSquare);
        assertNotNull(redSquare);

        exitScene(scene);
        assertNotNull(mSceneRoot.findViewById(R.id.redSquare));
        assertTrue(exitCheck.wasRun);

        enterScene(R.layout.scene4);
        assertNull(mSceneRoot.findViewById(R.id.redSquare));
    }

    /**
     * Test Scene(ViewGroup, View)
     */
    public void testViewConstructor() throws Throwable {
        View view = loadLayout(R.layout.scene1);
        constructorTest(new Scene(mSceneRoot, view));
    }

    /**
     * Test Scene(ViewGroup, ViewGroup)
     */
    public void testDeprecatedConstructor() throws Throwable {
        View view = loadLayout(R.layout.scene1);
        constructorTest(new Scene(mSceneRoot, (ViewGroup) view));
    }

    /**
     * Test Scene.getSceneForLayout
     */
    public void testFactory() throws Throwable {
        Scene scene = loadScene(R.layout.scene1);
        constructorTest(scene);
    }

    /**
     * Tests that the Scene was constructed properly from a scene1
     */
    private void constructorTest(Scene scene) throws Throwable {
        assertEquals(mSceneRoot, scene.getSceneRoot());
        CallCheck enterCheck = new CallCheck();
        scene.setEnterAction(enterCheck);
        CallCheck exitCheck = new CallCheck();
        scene.setExitAction(exitCheck);
        enterScene(scene);

        assertTrue(enterCheck.wasRun);
        assertFalse(exitCheck.wasRun);

        View redSquare = mActivity.findViewById(R.id.redSquare);
        assertNotNull(redSquare);

        exitScene(scene);
        assertNotNull(mSceneRoot.findViewById(R.id.redSquare));
        assertTrue(exitCheck.wasRun);
    }

    private static class CallCheck implements Runnable {
        public boolean wasRun;

        @Override
        public void run() {
            wasRun = true;
        }
    }
}

