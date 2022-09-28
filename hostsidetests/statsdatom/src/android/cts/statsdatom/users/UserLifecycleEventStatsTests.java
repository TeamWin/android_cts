/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.cts.statsdatom.users;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto.UserLifecycleEventOccurred;
import com.android.os.StatsLog.EventMetricData;

import java.util.ArrayList;
import java.util.List;

public class UserLifecycleEventStatsTests extends UserStatsTests<UserLifecycleEventOccurred> {
    static final int USER_LIFECYCLE_EVENT_OCCURRED = 265;

    public UserLifecycleEventStatsTests() {
        super(USER_LIFECYCLE_EVENT_OCCURRED);
    }

    public void testCreateGuestUser() throws Exception {
        String userName = "TestUser_" + System.currentTimeMillis();
        int userId = userCreate(userName, true);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertExpectedEvents(data, userId, "CREATE_USER");
    }

    public void testRemoveGuestUser() throws Exception {
        String userName = "TestUser_" + System.currentTimeMillis();
        int userId = removeGuestUser(userName, true);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertExpectedEvents(data, userId, "REMOVE_USER");
    }

    // Failing due to issue b/246283671
//    public void testSwitchToGuestUser() throws Exception {
//        String userName = "TestUser_" + System.currentTimeMillis();
//        int userId = switchUSer(userName, true);
//        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
//        assertExpectedEvents(data, userId, "SWITCH_USER");
//    }

    public void testCreateFullUser() throws Exception {
        String userName = "FullUser_" + System.currentTimeMillis();
        int userId = userCreate(userName, false);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertExpectedEvents(data, userId, "CREATE_USER");
    }

    public void testRemoveFullUser() throws Exception {
        String userName = "FullUser_" + System.currentTimeMillis();
        int userId = removeGuestUser(userName, false);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertExpectedEvents(data, userId, "REMOVE_USER");
    }

    // Failing due to issue b/246283671
//    public void testSwitchToFullUser() throws Exception {
//        String userName = "FullUser_" + System.currentTimeMillis();
//        int userId = switchUSer(userName, false);
//        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
//        assertExpectedEvents(data, userId, "SWITCH_USER");
//    }

    @Override
    protected UserLifecycleEventOccurred getAtom(EventMetricData data) {
        return data.getAtom().getUserLifecycleEventOccurred();
    }

    protected void assertExpectedEvents(List<EventMetricData> data, int userId, String eventName) {
        List<String> expectedData = prepareUserExpectedEvents(eventName);
        assertThat(expectedData.size()).isEqualTo(data.size());
        for (EventMetricData eventMetricData : data) {
            UserLifecycleEventOccurred atom = getAtom(eventMetricData);
            String expectedName = atom.getEvent() + "_" + atom.getState();
            long id = atom.getUserId();
            assertThat(expectedData).contains(expectedName);
            expectedData.remove(expectedName);
            assertThat(id).isEqualTo(userId);
        }
        assertThat(expectedData).isEmpty();
    }

    private List<String> prepareUserExpectedEvents(String event) {
        List<String> expectedData = new ArrayList<>();
        switch (event) {
            case "CREATE_USER":
                expectedData.add("CREATE_USER_BEGIN");
                expectedData.add("CREATE_USER_FINISH");
                break;
            case "REMOVE_USER":
                expectedData.add("REMOVE_USER_BEGIN");
                expectedData.add("REMOVE_USER_FINISH");
                break;
            case "SWITCH_USER":
                expectedData.add("SWITCH_USER_BEGIN");
                expectedData.add("SWITCH_USER_FINISH");
                expectedData.add("START_USER_BEGIN");
                expectedData.add("START_USER_FINISH");
                expectedData.add("USER_RUNNING_LOCKED_NONE");
                expectedData.add("UNLOCKING_USER_BEGIN");
                expectedData.add("UNLOCKING_USER_FINISH");
                expectedData.add("UNLOCKED_USER_BEGIN");
                expectedData.add("UNLOCKED_USER_FINISH");
                break;
        }
        return expectedData;
    }
}
