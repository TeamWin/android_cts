How the test works
==================
ApkVerityTestApp is a test helper app to be installed with fs-verity signature
file (.fsv\_sig). In order for this CTS test to run on a release build across
vendors, the signature needs to be verified against a release certificate loaded
to kernel.

How to modify the test helper app
=================================
Modifying the test helper app will also require to sign the apk with a local debug
key. You will also need to point the test to use your local build.

How to load debug key
---------------------
On debuggable build, it can be done by:

```
adb root
adb shell 'mini-keyctl padd asymmetric fsv-play .fs-verity' < fsverity-debug.x509.der
```

On user build, the keyring is closed and doesn't accept extra key. A workaround
is to copy the .der file to /system/etc/security/fsverity. Upon reboot, the
certificate will be loaded to kernel as usual.

How to use the app built locally
--------------------------------
TODO: provide instruction once the test switches to prebuilt and release signature.
