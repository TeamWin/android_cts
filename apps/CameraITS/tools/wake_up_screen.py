# Copyright 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import re
import subprocess
import sys
import time

def main():
    """Power up and unlock screen as needed."""
    screen_id = None
    for s in sys.argv[1:]:
        if s[:7] == 'screen=' and len(s) > 7:
            screen_id = s[7:]
    cmd = ('adb -s %s shell dumpsys display | egrep "mScreenState"'
           % screen_id)
    process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE)
    cmd_ret = process.stdout.read()
    screen_state = re.split(r'[s|=]', cmd_ret)[-1]
    if 'OFF' in screen_state:
        print 'Screen OFF. Turning ON.'
        wakeup = ('adb -s %s shell input keyevent POWER' % screen_id)
        subprocess.Popen(wakeup.split())
        time.sleep(0.5)  # some screens need pause for next command
    unlock = ('adb -s %s wait-for-device shell wm dismiss-keyguard'
              % screen_id)
    subprocess.Popen(unlock.split())
    time.sleep(0.5)  # some screens need time for command to take effect

if __name__ == '__main__':
    main()
