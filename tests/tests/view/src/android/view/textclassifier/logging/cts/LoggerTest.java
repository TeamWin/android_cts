/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.textclassifier.logging.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.textclassifier.logging.Logger;
import android.view.textclassifier.logging.Logger.Config;
import android.view.textclassifier.logging.Logger.WidgetType;
import android.view.textclassifier.logging.SelectionEvent;
import android.view.textclassifier.logging.SelectionEvent.EventType;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LoggerTest {

    // TODO: Add more tests.

    private static final @WidgetType String WIDGET_TYPE = Logger.WIDGET_TEXTVIEW;
    private static final String WIDGET_VERSION = null;
    private static final int START = 0;
    private static final int END = 2;

    private Logger mLogger;
    private Config mConfig;
    private SelectionEvent mCapturedSelectionEvent;

    @Before
    public void setup() {
        mConfig = new Config(
                InstrumentationRegistry.getTargetContext(), WIDGET_TYPE, WIDGET_VERSION);
        mLogger = new Logger(mConfig) {
            @Override
            public void writeEvent(SelectionEvent event) {
                mCapturedSelectionEvent = event;
            }
        };
        mCapturedSelectionEvent = null;
    }

    private void startSelectionSession() {
        // A selection started event needs to be fired before any non started event will be logged.
        mLogger.logSelectionStartedEvent(START);
    }

    @Test
    public void testLogger_logSelectionStartedEvent() {
        mLogger.logSelectionStartedEvent(START);
        assertThat(mCapturedSelectionEvent,
                isSelectionEvent(START, START + 1, SelectionEvent.EVENT_SELECTION_STARTED));
    }

    @Test
    public void testLogger_logSelectionModifiedEvent() {
        startSelectionSession();
        mLogger.logSelectionModifiedEvent(START, END);
        assertThat(mCapturedSelectionEvent,
                isSelectionEvent(START, END, SelectionEvent.EVENT_SELECTION_MODIFIED));
    }

    @Test
    public void testLogger_logSelectionActionEvent() {
        startSelectionSession();
        mLogger.logSelectionActionEvent(START, END, SelectionEvent.ACTION_SMART_SHARE);
        assertThat(mCapturedSelectionEvent,
                isSelectionEvent(START, END, SelectionEvent.ACTION_SMART_SHARE));
    }

    @Test
    public void testLoggerConfig() {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String widgetVersion = "v1";

        Config config = new Config(context, Logger.WIDGET_CUSTOM_EDITTEXT, widgetVersion);

        assertEquals(context.getPackageName(), config.getPackageName());
        assertEquals(Logger.WIDGET_CUSTOM_EDITTEXT, config.getWidgetType());
        assertEquals(widgetVersion, config.getWidgetVersion());
    }

    private static Matcher<SelectionEvent> isSelectionEvent(
            final int start, final int end, @EventType int eventType) {
        return new BaseMatcher<SelectionEvent>() {
            @Override
            public boolean matches(Object o) {
                if (o instanceof SelectionEvent) {
                    SelectionEvent event = (SelectionEvent) o;
                    return event.getStart() == start
                            && event.getEnd() == end
                            && event.getEventType() == eventType;
                    // TODO: Test more fields.
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendValue(String.format(Locale.US,
                        "start=%d, end=%d, eventType=%d",
                        start, end, eventType));
            }
        };
    }
}
