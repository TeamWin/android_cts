/*
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
package android.permission3.cts.usepermission

class AccessBluetoothOnCommand : android.content.ContentProvider() {
    private enum class Result {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun call(
        authority: String,
        method: String,
        arg: String?,
        extras: android.os.Bundle?
    ): android.os.Bundle? {
        val res: android.os.Bundle = android.os.Bundle()
        try {
            val bm: android.bluetooth.BluetoothManager =
                    getContext().getSystemService(android.bluetooth.BluetoothManager::class.java)
            val scanner: android.bluetooth.le.BluetoothLeScanner =
                    bm.getAdapter().getBluetoothLeScanner()
            val observed: java.util.HashSet<String> = java.util.HashSet()
            scanner.startScan(object : android.bluetooth.le.ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: android.bluetooth.le.ScanResult
                ) {
                    android.util.Log.v(TAG, result.toString())
                    observed.add(android.util.Base64.encodeToString(
                            result.getScanRecord().getBytes(), 0))
                }

                override fun onBatchScanResults(results: List<android.bluetooth.le.ScanResult>) {
                    for (result in results) {
                        onScanResult(0, result)
                    }
                }
            })
            // Wait a few seconds to figure out what we actually observed
            android.os.SystemClock.sleep(3000)
            when (observed.size) {
                0 -> res.putInt(android.content.Intent.EXTRA_INDEX, Result.EMPTY.ordinal)
                1 -> res.putInt(android.content.Intent.EXTRA_INDEX, Result.FILTERED.ordinal)
                5 -> res.putInt(android.content.Intent.EXTRA_INDEX, Result.FULL.ordinal)
                else -> res.putInt(android.content.Intent.EXTRA_INDEX, Result.UNKNOWN.ordinal)
            }
        } catch (t: Throwable) {
            android.util.Log.v(TAG, "Failed to scan", t)
            res.putInt(android.content.Intent.EXTRA_INDEX, Result.EXCEPTION.ordinal)
        }
        return res
    }

    override fun query(
        uri: android.net.Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): android.database.Cursor? {
        throw java.lang.UnsupportedOperationException()
    }

    override fun getType(uri: android.net.Uri): String? {
        throw java.lang.UnsupportedOperationException()
    }

    override fun insert(
        uri: android.net.Uri,
        values: android.content.ContentValues?
    ): android.net.Uri? {
        throw java.lang.UnsupportedOperationException()
    }

    override fun delete(
        uri: android.net.Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw java.lang.UnsupportedOperationException()
    }

    override fun update(
        uri: android.net.Uri,
        values: android.content.ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw java.lang.UnsupportedOperationException()
    }

    companion object {
        private const val TAG = "AccessBluetoothOnCommand"
    }
}
