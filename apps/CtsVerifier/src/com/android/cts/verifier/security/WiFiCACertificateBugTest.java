/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class WiFiCACertificateBugTest extends PassFailButtons.Activity {

    private static final String CERT_ASSET_NAME = "myCA.cer";
    private File certStagingFile = new File("/sdcard/", CERT_ASSET_NAME);
    private KeyguardManager mKeyguardManager;
    private boolean testResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ca_certificate_wifi);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.sec_wifi_ca_cert_test, R.string.sec_wifi_ca_cert_test_info, -1);

        mKeyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

        getPassButton().setEnabled(false);
        Button transferCertificateButton = (Button) findViewById(R.id.transfer_ca_certificate);
        final Button goToSettingsButton = (Button) findViewById(R.id.gotosettings);
        goToSettingsButton.setEnabled(false);
        final Button goToWifiSettingsButton = (Button) findViewById(R.id.gotowifisettings);
        goToWifiSettingsButton.setEnabled(false);
        final Button certificateTrustCheckButton = (Button) findViewById(R.id.check_cert_trust);
        certificateTrustCheckButton.setEnabled(false);

        final TextView finalStepTextView = (TextView) findViewById(R.id.clear_creds_textView);

        goToSettingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //Launch settings of the device
                startActivityForResult(
                        new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS), 0);
                if (testResult) {
                    getPassButton().setEnabled(true);
                    finalStepTextView.setText(getResources().getString(R.string
                            .sec_pass_test_instruction));
                } else {
                    finalStepTextView.setText(getResources().getString(R.string
                            .sec_fail_test_instruction));
                }
            }
        });
        goToWifiSettingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //Launch WiFi settings of the device
                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            }
        });
        transferCertificateButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (transferCertificateToDevice()) {
                    openDialog(getResources().getString(R.string.sec_file_transferred_text));
                    goToSettingsButton.setEnabled(true);
                    goToWifiSettingsButton.setEnabled(true);
                    certificateTrustCheckButton.setEnabled(true);
                } else {
                    openDialog(getResources().getString(R.string.sec_file_transfer_failed));
                }
            }
        });
        certificateTrustCheckButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Certificate caCert = readCertificate(convertFileToByteArray(certStagingFile));
                if (caCert != null) {
                    if (isCaCertificateTrusted(caCert)) {
                        testResult = false;
                        openDialog(getResources().getString(R.string.sec_cert_trusted_text));

                    } else {
                        testResult = true;
                        openDialog(getResources().getString(R.string.sec_cert_nottrusted_text));
                    }
                } else {
                    openDialog(getResources().getString(R.string.sec_read_cert_exception));
                }
            }
        });
    }

    /**
     * Transfers the certificate file to the internal storage i.e. /sdcard/ of the device
     *
     * @return
     */
    private boolean transferCertificateToDevice() {
        InputStream is = null;
        FileOutputStream os = null;
        try {
            try {
                is = getAssets().open(CERT_ASSET_NAME);
                os = new FileOutputStream(certStagingFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } finally {
                if (is != null) is.close();
                if (os != null) os.close();
                certStagingFile.setReadable(true, false);
            }
        } catch (IOException ioe) {
            return false;
        }
        return true;
    }

    /**
     * Converts the certificate file into byte array
     *
     * @param file
     * @return
     */
    private static byte[] convertFileToByteArray(File file) {
        byte[] byteArray = null;
        try {
            InputStream inputStream = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] bytes = new byte[1024 * 8];
            int bytesRead = 0;

            while ((bytesRead = inputStream.read(bytes)) != -1) {
                bos.write(bytes, 0, bytesRead);
            }
            byteArray = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArray;
    }

    /**
     * Opens the dialog with the message passed as an argument
     *
     * @param message
     */
    private void openDialog(String message) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(message);

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string
                .hifi_ultrasound_test_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    /**
     * Checks if the exploit certificate is trusted by the system
     *
     * @param caCert
     * @return
     * @throws GeneralSecurityException
     * @throws CertificateException
     */
    private boolean isCaCertificateTrusted(Certificate caCert) {

        boolean trusted = false;
        TrustManagerFactory tmf = null;
        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            return trusted;
        }
        for (TrustManager trustManager : tmf.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                final X509TrustManager tm = (X509TrustManager) trustManager;
                if (Arrays.asList(tm.getAcceptedIssuers()).contains(caCert)) {
                    trusted = true;
                    break;
                }
            }
        }
        return trusted;
    }

    /**
     * Convert an encoded certificate back into a {@link Certificate}.
     * <p/>
     * Instantiates a fresh CertificateFactory every time for repeatability.
     */
    private static Certificate readCertificate(byte[] certBuffer) {
        CertificateFactory certFactory = null;
        try {
            certFactory = CertificateFactory.getInstance("X.509");
            return certFactory.generateCertificate(new ByteArrayInputStream(certBuffer));
        } catch (CertificateException e) {
            return null;
        }
    }
}