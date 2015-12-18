/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.print.cts;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.printservice.PrintJob;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Test the interface from a print service to the print manager
 */
public class PrintServicesTest extends BasePrintTest {
    private static final String PRINTER_NAME = "Test printer";
    private static final int NUM_PAGES = 2;

    /** The print job processed in the test */
    private static PrintJob mPrintJob;

    /** The current progress of #mPrintJob once read from the system */
    private static float mPrintProgress;

    /** The current status of #mPrintJob once read from the system */
    private static CharSequence mPrintStatus;

    /**
     * Create a mock {@link PrintDocumentAdapter} that provides {@link #NUM_PAGES} empty pages.
     *
     * @return The mock adapter
     */
    private PrintDocumentAdapter createMockPrintDocumentAdapter() {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        return createMockPrintDocumentAdapter(
                new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                        LayoutResultCallback callback = (LayoutResultCallback) invocation
                                .getArguments()[3];

                        PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                .setPageCount(NUM_PAGES)
                                .build();

                        callback.onLayoutFinished(info, false);

                        // Mark layout was called.
                        onLayoutCalled();
                        return null;
                    }
                }, new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        PageRange[] pages = (PageRange[]) args[0];
                        ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                        WriteResultCallback callback = (WriteResultCallback) args[3];

                        writeBlankPages(printAttributes[0], fd, pages[0].getStart(),
                                pages[0].getEnd());
                        fd.close();
                        callback.onWriteFinished(pages);

                        // Mark write was called.
                        onWriteCalled();
                        return null;
                    }
                }, new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        // Mark finish was called.
                        onFinishCalled();
                        return null;
                    }
                });
    }

    /**
     * Create a mock {@link PrinterDiscoverySessionCallbacks} that discovers a single printer with
     * minimal capabilities.
     *
     * @return The mock session callbacks
     */
    private PrinterDiscoverySessionCallbacks createFirstMockPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                // Get the session.
                StubbablePrinterDiscoverySession session = ((PrinterDiscoverySessionCallbacks) invocation
                        .getMock()).getSession();

                if (session.getPrinters().isEmpty()) {
                    List<PrinterInfo> printers = new ArrayList<PrinterInfo>();

                    // Add the printer.
                    PrinterId printerId = session.getService().generatePrinterId(PRINTER_NAME);

                    PrinterCapabilitiesInfo capabilities = new PrinterCapabilitiesInfo.Builder(
                            printerId)
                                    .setMinMargins(new Margins(200, 200, 200, 200))
                                    .addMediaSize(MediaSize.ISO_A4, true)
                                    .addResolution(new Resolution("300x300", "300x300", 300, 300),
                                            true)
                                    .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                            PrintAttributes.COLOR_MODE_COLOR)
                                    .build();

                    PrinterInfo printer = new PrinterInfo.Builder(printerId, PRINTER_NAME,
                            PrinterInfo.STATUS_IDLE)
                                    .setCapabilities(capabilities)
                                    .build();
                    printers.add(printer);

                    session.addPrinters(printers);
                }
                return null;
            }
        }, null, null, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                return null;
            }
        }, null, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // Take a note onDestroy was called.
                onPrinterDiscoverySessionDestroyCalled();
                return null;
            }
        });
    }

    /**
     * Get the current progress of #mPrintJob
     *
     * @return The current progress
     * @throws InterruptedException If the thread was interrupted while setting the progress
     */
    private float getProgress() throws InterruptedException {
        final PrintServicesTest synchronizer = PrintServicesTest.this;

        synchronized (synchronizer) {
            Runnable getter = new Runnable() {
                @Override
                public void run() {
                    synchronized (synchronizer) {
                        mPrintProgress = mPrintJob.getInfo().getProgress();

                        synchronizer.notify();
                    }
                }
            };

            (new Handler(Looper.getMainLooper())).post(getter);

            synchronizer.wait();
        }

        return mPrintProgress;
    }

    /**
     * Get the current status of #mPrintJob
     *
     * @return The current status
     * @throws InterruptedException If the thread was interrupted while getting the status
     */
    private CharSequence getStatus() throws InterruptedException {
        final PrintServicesTest synchronizer = PrintServicesTest.this;

        synchronized (synchronizer) {
            Runnable getter = new Runnable() {
                @Override
                public void run() {
                    synchronized (synchronizer) {
                        mPrintStatus = mPrintJob.getInfo().getStatus();

                        synchronizer.notify();
                    }
                }
            };

            (new Handler(Looper.getMainLooper())).post(getter);

            synchronizer.wait();
        }

        return mPrintStatus;
    }

    /**
     * Check if a print progress is correct.
     *
     * @param desiredProgress The expected @{link PrintProgresses}
     * @throws Exception If anything goes wrong or this takes more than 5 seconds
     */
    private void checkNotification(float desiredProgress,
            CharSequence desiredStatus) throws Exception {
        final long TIMEOUT = 5000;
        final Date start = new Date();

        while ((new Date()).getTime() - start.getTime() < TIMEOUT) {
            if (desiredProgress == getProgress()
                && desiredStatus.toString().equals(getStatus().toString())) {
                return;
            }

            Thread.sleep(200);
        }

        throw new TimeoutException("Progress or status not updated in " + TIMEOUT + " ms");
    }

    /**
     * Set a new progress and status for #mPrintJob
     *
     * @param progress The new progress to set
     * @param status The new status to set
     * @throws InterruptedException If the thread was interrupted while setting
     */
    private void setProgressAndStatus(final float progress, final CharSequence status)
            throws InterruptedException {
        final PrintServicesTest synchronizer = PrintServicesTest.this;

        synchronized (synchronizer) {
            Runnable completer = new Runnable() {
                @Override
                public void run() {
                    synchronized (synchronizer) {
                        mPrintJob.setProgress(progress);
                        mPrintJob.setStatus(status);

                        synchronizer.notify();
                    }
                }
            };

            (new Handler(Looper.getMainLooper())).post(completer);

            synchronizer.wait();
        }
    }

    /**
     * Progress print job and check the print job state.
     *
     * @param progress How much to progress
     * @param status The status to set
     * @throws Exception If anything goes wrong.
     */
    private void progress(float progress, CharSequence status) throws Exception {
        setProgressAndStatus(progress, status);

        // Check that progress of job is correct
        checkNotification(progress, status);
    }

    /**
     * Test that the progress and status is propagated correctly.
     *
     * @throws Exception If anything is unexpected.
     */
    public void testProgress()
            throws Exception {
        if (!supportsPrinting()) {
            return;
        }
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks sessionCallbacks = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createMockPrintServiceCallbacks(
                new Answer<PrinterDiscoverySessionCallbacks>() {
                    @Override
                    public PrinterDiscoverySessionCallbacks answer(InvocationOnMock invocation) {
                        return sessionCallbacks;
                    }
                },
                new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) {
                        mPrintJob = (PrintJob) invocation.getArguments()[0];
                        mPrintJob.start();
                        onPrintJobQueuedCalled();

                        return null;
                    }
                }, null);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter();

        // Start printing.
        print(adapter);

        // Wait for write of the first page.
        waitForWriteAdapterCallback();

        // Select the printer.
        selectPrinter(PRINTER_NAME);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait until the print job is queued and #mPrintJob is set
        waitForServiceOnPrintJobQueuedCallbackCalled();

        // Progress print job and check for appropriate notifications
        progress(0, "printed 0");
        progress(0.5f, "printed 50");
        progress(1, "printed 100");

        // Call complete from the main thread
        Handler handler = new Handler(Looper.getMainLooper());

        Runnable completer = new Runnable() {
            @Override
            public void run() {
                mPrintJob.complete();
            }
        };

        handler.post(completer);

        // Wait for all print jobs to be handled after which the session destroyed.
        waitForPrinterDiscoverySessionDestroyCallbackCalled();
    }
}
