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

package android.theme.app;

import android.view.View;

/**
 * Interface used to do further setup on a view after it has been inflated.
 */
public interface LayoutModifier {

    /**
     * Modifies the view before it has been added to a parent. Useful for avoiding animations in
     * response to setter calls.
     *
     * @param view the view inflated by the test activity
     */
    void modifyViewBeforeAdd(View view);

    /**
     * Modifies the view after it has been added to a parent. Useful for running animations in
     * response to setter calls.
     *
     * @param view the view inflated by the test activity
     */
    void modifyViewAfterAdd(View view);
}
