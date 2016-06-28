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

package android.print.cts;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.support.annotation.NonNull;

import java.util.ArrayList;

/**
 * Test interface from the application to the print service.
 */
public class AppPrintInterfaceTest extends BasePrintTest {
    private static final String TEST_PRINTER = "Test printer";

    private static final PrintAttributes.Resolution TWO_HUNDRED_DPI = new PrintAttributes.Resolution(
            "200x200", "200dpi", 200,
            200);

    /**
     * @return The print manager
     */
    private @NonNull PrintManager getPrintManager() {
        return (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);
    }

    /**
     * Start printing
     *
     * @param adapter      Adapter supplying data to print
     * @param printJobName The name of the print job
     */
    protected void print(@NonNull PrintDocumentAdapter adapter, @NonNull String printJobName) {
        // Initiate printing as if coming from the app.
        getInstrumentation()
                .runOnMainSync(() -> getPrintManager().print(printJobName, adapter, null));
    }

    /**
     * Create a mock {@link PrintDocumentAdapter} that provides one empty page.
     *
     * @return The mock adapter
     */
    private @NonNull PrintDocumentAdapter createMockPrintDocumentAdapter() {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        return createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    PrintDocumentAdapter.LayoutResultCallback callback =
                            (PrintDocumentAdapter.LayoutResultCallback) invocation
                                    .getArguments()[3];

                    callback.onLayoutFinished(new PrintDocumentInfo.Builder("doc")
                            .setPageCount(1).build(), false);

                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    PrintDocumentAdapter.WriteResultCallback callback = (PrintDocumentAdapter.WriteResultCallback) args[3];

                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    fd.close();
                    callback.onWriteFinished(pages);
                    return null;
                }, invocation -> null);
    }

    /**
     * Create a mock {@link PrinterDiscoverySessionCallbacks} that discovers a simple test printer.
     *
     * @return The mock session callbacks
     */
    private @NonNull PrinterDiscoverySessionCallbacks createFirstMockPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            StubbablePrinterDiscoverySession session = ((PrinterDiscoverySessionCallbacks) invocation
                    .getMock()).getSession();

            if (session.getPrinters().isEmpty()) {
                PrinterId printerId = session.getService().generatePrinterId(TEST_PRINTER);
                PrinterInfo.Builder printer = new PrinterInfo.Builder(
                        session.getService().generatePrinterId(TEST_PRINTER), TEST_PRINTER,
                        PrinterInfo.STATUS_IDLE);

                printer.setCapabilities(new PrinterCapabilitiesInfo.Builder(printerId)
                        .addMediaSize(PrintAttributes.MediaSize.ISO_A5, true)
                        .addResolution(TWO_HUNDRED_DPI, true)
                        .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME,
                                PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0)).build());

                ArrayList<PrinterInfo> printers = new ArrayList<>(1);
                printers.add(printer.build());

                session.addPrinters(printers);
            }
            return null;
        }, null, null, invocation -> null, null, null, invocation -> {
            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }

    /**
     * Create mock service callback for a session. Once the job is queued the test function is
     * called.
     *
     * @param sessionCallbacks The callbacks of the session
     * @param blockAfterState  The state the print services should progress to
     */
    private @NonNull PrintServiceCallbacks createFirstMockPrinterServiceCallbacks(
            final @NonNull PrinterDiscoverySessionCallbacks sessionCallbacks, int blockAfterState) {
        return createMockPrintServiceCallbacks(
                invocation -> sessionCallbacks, invocation -> {
                    android.printservice.PrintJob job = (android.printservice.PrintJob) invocation
                            .getArguments()[0];

                    switch (blockAfterState) {
                        case PrintJobInfo.STATE_CREATED:
                            assertEquals(PrintJobInfo.STATE_CREATED, job.getInfo().getState());
                            break;
                        case PrintJobInfo.STATE_STARTED:
                            job.start();
                            break;
                        case PrintJobInfo.STATE_QUEUED:
                            assertEquals(PrintJobInfo.STATE_QUEUED, job.getInfo().getState());
                            break;
                        case PrintJobInfo.STATE_BLOCKED:
                            job.start();
                            job.block("test block");
                            break;
                        case PrintJobInfo.STATE_FAILED:
                            job.start();
                            job.fail("test fail");
                            break;
                        case PrintJobInfo.STATE_COMPLETED:
                            job.start();
                            job.complete();
                            break;
                        default:
                            throw new Exception("Should not be reached");
                    }

                    return null;
                }, invocation -> {
                    android.printservice.PrintJob job = (android.printservice.PrintJob) invocation
                            .getArguments()[0];

                    job.cancel();

                    return null;
                });
    }

    /**
     * Setup mock print subsystem
     *
     * @param blockAfterState Tell the print service to block all print jobs at this state
     *
     * @return The print document adapter to be used for printing
     */
    private @NonNull PrintDocumentAdapter setupPrint(int blockAfterState) {
        // Create the session of the printers that we will be checking.
        PrinterDiscoverySessionCallbacks sessionCallbacks = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks, blockAfterState);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        return createMockPrintDocumentAdapter();
    }

    /**
     * @param name Name of print job
     *
     * @return The print job for the name
     *
     * @throws Exception If print job could not be found
     */
    private @NonNull PrintJob getPrintJob(@NonNull String name) throws Exception {
        for (PrintJob job : getPrintManager().getPrintJobs()) {
            if (job.getInfo().getLabel().equals(name)) {
                return job;
            }
        }

        throw new Exception(
                "Print job " + name + " not found in " + getPrintManager().getPrintJobs());
    }

    /**
     * Base test for all cancel print job tests
     *
     * @param cancelAfterState The print job state state to progress to canceling
     * @param printJobName     The print job name to use
     *
     * @throws Exception If anything is unexpected
     */
    private void cancelPrintJobBaseTest(int cancelAfterState, @NonNull String printJobName)
            throws Exception {
        PrintDocumentAdapter adapter = setupPrint(cancelAfterState);

        print(adapter, printJobName);
        selectPrinter(TEST_PRINTER);

        clickPrintButton();
        answerPrintServicesWarning(true);

        PrintJob job = getPrintJob(printJobName);

        eventually(() -> assertEquals(cancelAfterState, job.getInfo().getState()));

        job.cancel();

        eventually(() -> assertEquals(PrintJobInfo.STATE_CANCELED, job.getInfo().getState()));

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    public void testAttemptCancelCreatedPrintJob() throws Exception {
        PrintDocumentAdapter adapter = setupPrint(PrintJobInfo.STATE_STARTED);

        print(adapter, "testAttemptCancelCreatedPrintJob");
        selectPrinter(TEST_PRINTER);

        waitForLayoutAdapterCallbackCount(1);

        PrintJob job = getPrintJob("testAttemptCancelCreatedPrintJob");

        // Cancel does not have an effect on created jobs
        job.cancel();

        eventually(() -> assertEquals(PrintJobInfo.STATE_CREATED, job.getInfo().getState()));

        // Cancel printing by exiting print activity
        getUiDevice().pressBack();

        eventually(() -> assertEquals(PrintJobInfo.STATE_CANCELED, job.getInfo().getState()));

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    public void testCancelStartedPrintJob() throws Exception {
        cancelPrintJobBaseTest(PrintJobInfo.STATE_STARTED, "testCancelStartedPrintJob");
    }

    public void testCancelBlockedPrintJob() throws Exception {
        cancelPrintJobBaseTest(PrintJobInfo.STATE_BLOCKED, "testCancelBlockedPrintJob");
    }

    public void testCancelFailedPrintJob() throws Exception {
        cancelPrintJobBaseTest(PrintJobInfo.STATE_FAILED, "testCancelFailedPrintJob");
    }

    public void testRestartFailedPrintJob() throws Exception {
        PrintDocumentAdapter adapter = setupPrint(PrintJobInfo.STATE_FAILED);

        print(adapter, "testRestartFailedPrintJob");
        selectPrinter(TEST_PRINTER);

        clickPrintButton();
        answerPrintServicesWarning(true);

        PrintJob job = getPrintJob("testRestartFailedPrintJob");

        eventually(() -> assertEquals(PrintJobInfo.STATE_FAILED, job.getInfo().getState()));

        // Restart goes from failed right to queued, so stop the print job at "queued" now
        setupPrint(PrintJobInfo.STATE_QUEUED);

        job.restart();
        eventually(() -> assertEquals(PrintJobInfo.STATE_QUEUED, job.getInfo().getState()));

        job.cancel();
        eventually(() -> assertEquals(PrintJobInfo.STATE_CANCELED, job.getInfo().getState()));

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    public void testGetTwoPrintJobStates() throws Exception {
        PrintDocumentAdapter adapter = setupPrint(PrintJobInfo.STATE_BLOCKED);
        print(adapter, "testGetTwoPrintJobStates-block");
        selectPrinter(TEST_PRINTER);
        clickPrintButton();
        answerPrintServicesWarning(true);

        PrintJob job1 = getPrintJob("testGetTwoPrintJobStates-block");
        eventually(() -> assertEquals(PrintJobInfo.STATE_BLOCKED, job1.getInfo().getState()));

        adapter = setupPrint(PrintJobInfo.STATE_COMPLETED);
        print(adapter, "testGetTwoPrintJobStates-complete");
        clickPrintButton();

        PrintJob job2 = getPrintJob("testGetTwoPrintJobStates-complete");
        eventually(() -> assertEquals(PrintJobInfo.STATE_COMPLETED, job2.getInfo().getState()));

        // First print job should still be there
        PrintJob job1again = getPrintJob("testGetTwoPrintJobStates-block");
        assertEquals(PrintJobInfo.STATE_BLOCKED, job1again.getInfo().getState());

        // Check attributes that should have been applied
        assertEquals(PrintAttributes.MediaSize.ISO_A5,
                job1again.getInfo().getAttributes().getMediaSize());
        assertEquals(PrintAttributes.COLOR_MODE_MONOCHROME,
                job1again.getInfo().getAttributes().getColorMode());
        assertEquals(TWO_HUNDRED_DPI, job1again.getInfo().getAttributes().getResolution());

        waitForPrinterDiscoverySessionDestroyCallbackCalled(2);
    }
}
