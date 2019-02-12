/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.widget.cts

import android.app.Activity
import android.support.test.InstrumentationRegistry
import android.support.test.filters.MediumTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.support.test.uiautomator.UiDevice
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.Toolbar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class WidgetAttributeTest {
    companion object {
        const val DISABLE_SHELL_COMMAND =
                "settings delete global debug_view_attributes_application_package"
        const val ENABLE_SHELL_COMMAND =
                "settings put global debug_view_attributes_application_package android.widget.cts"
    }

    @get:Rule
    val activityRule = ActivityTestRule<Activity>(Activity::class.java, true, false)
    private lateinit var uiDevice: UiDevice

    @Before
    fun setUp() {
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.executeShellCommand(ENABLE_SHELL_COMMAND)
        activityRule.launchActivity(null)
    }

    @After
    fun tearDown() {
        uiDevice.executeShellCommand(DISABLE_SHELL_COMMAND)
    }

    @Test
    fun testGetAttributeSourceResourceMap() {
        val inflater = LayoutInflater.from(activityRule.getActivity())
        val rootView = inflater.inflate(R.layout.widget_attribute_layout, null) as LinearLayout

        // ProgressBar that has an explicit style ExplicitStyle1 set via style = ...
        val progressBar = rootView.findViewById<ProgressBar>(R.id.progress_bar)
        val attributeMapProgressBar = progressBar!!.attributeSourceResourceMap
        assertEquals(R.layout.widget_attribute_layout,
                attributeMapProgressBar[android.R.attr.minWidth]!!.toInt())
        assertEquals(R.layout.widget_attribute_layout,
                attributeMapProgressBar[android.R.attr.maxWidth]!!.toInt())
        assertEquals(R.layout.widget_attribute_layout,
                attributeMapProgressBar[android.R.attr.progressTint]!!.toInt())
        assertEquals(R.style.ExplicitStyle1,
                attributeMapProgressBar[android.R.attr.progress]!!.toInt())
        assertEquals(R.style.ExplicitStyle1,
                attributeMapProgressBar[android.R.attr.padding]!!.toInt())
        assertEquals(R.style.ExplicitStyle1,
                attributeMapProgressBar[android.R.attr.max]!!.toInt())
        assertEquals(R.style.ParentOfExplicitStyle1,
                attributeMapProgressBar[android.R.attr.mirrorForRtl]!!.toInt())

        // Switch that has an explicit style ExplicitStyle2 set via style = ...
        val switch = rootView.findViewById<Switch>(R.id.switch_view)
        val attributeMapSwitch = switch!!.attributeSourceResourceMap
        assertEquals(R.layout.widget_attribute_layout,
                attributeMapSwitch[android.R.attr.switchPadding]!!.toInt())

        // Toolbar that has MyToolbarStyle set via the theme android:toolbarStyle = ...
        val toolbar = rootView.findViewById<Toolbar>(R.id.toolbar_view)
        val attributeMapToobar = toolbar!!.attributeSourceResourceMap
        assertEquals(R.style.MyToolbarStyle,
                attributeMapToobar[android.R.attr.titleMarginEnd]!!.toInt())
        assertEquals(R.style.MyToolbarStyleParent,
                attributeMapToobar[android.R.attr.titleMarginStart]!!.toInt())
    }
}