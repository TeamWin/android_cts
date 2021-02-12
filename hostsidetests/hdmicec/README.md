# CEC CTS testing for Android TV devices

NOTE: CTS has two meanings here. HDMI defines a set of tests in the
**Compliance Test Specification** of HDMI 1.4b
__HDMI Compliance Test Specification 1.4b__ and
**Android Compatibility Test Suite**.

The Android Compatibility Test Suite includes specific tests from the HDMI
Compliance Test Specification as well as other Android specific tests.

## Setup

### Playback devices (aka Set Top Boxes)

Running these CTS tests requires a specific HDMI layout with a CEC adapter.

*   Android TV playback device
*   CEC adapter, see [External CEC Adapter instructions](cec_adapter.md)
*   Install `cec-client` binary, see [install instructions](cec_adapter.md#software)
*   HDMI Display (aka a TV) or an HDMI fake plug

![drawing](setup.png)

### Automation

Given the setup described above you can run all of these tests with the command

```
atest CtsHdmiCecHostTestCases
```

To shard the test (distribute and run the tests on multiple devices), use this
command -
```
./cts-tradefed run commandAndExit cts --enable-token-sharding --shard-count 3 -m CtsHdmiCecHostTestCases
```

The shard count corresponds to the number of DUTs connected to the host.
