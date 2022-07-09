How to add a test for a new HID device
======================================

1. Connect the device of interest to Android.
2. Open `adb shell`.
3. Ensure debugfs is mounted by checking that you have access to the `/sys/kernel/debug/hid`
directory. If not, it can be mounted using `mount -t debugfs none /sys/kernel/debug`.
4. Go to `/sys/kernel/debug/hid/0005:0B05:4500.000F`.
Here "0005:0B05:4500.000F" is just an example. This identifier will be different for each device,
and will change each time you reconnect the same physical device to Android. Use
`ls /sys/kernel/debug/hid` to look in the directory to see the identifiers for connected HID
devices.
5. Use `cat rdesc` to print the HID descriptor of this device.
6. Use `cat events` to print the events that the device is producing.
Once you cat the events, generate some events (by hand) on the device.
This will show you the HID reports that the device produces.

To observe the MotionEvents that Android receives in response to the hid reports, write a small
app that would override `dispatchGenericMotionEvent` and `dispatchKeyEvent` of an activity.
There, print all of the event data that has changed. For MotionEvents, ensure to look at the
historical data as well, since multiple reports could get batched into a single MotionEvent.
