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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
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
import android.printservice.CustomPrinterIconCallback;
import android.printservice.PrintJob;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;

import java.util.ArrayList;
import java.util.List;

import static android.print.cts.Utils.assertException;
import static android.print.cts.Utils.eventually;
import static android.print.cts.Utils.runOnMainThread;

/**
 * Test the interface from a print service to the print manager
 */
public class PrintServicesTest extends BasePrintTest {
    private static final String PRINTER_NAME = "Test printer";
    private static final int NUM_PAGES = 2;

    /** The print job processed in the test */
    private static PrintJob sPrintJob;

    /** Printer under test */
    private static PrinterInfo sPrinter;

    /** The printer discovery session used in this test */
    private static StubbablePrinterDiscoverySession sDiscoverySession;

    /** The custom printer icon to use */
    private Icon mIcon;

    /**
     * Create a mock {@link PrintDocumentAdapter} that provides {@link #NUM_PAGES} empty pages.
     *
     * @return The mock adapter
     */
    private PrintDocumentAdapter createMockPrintDocumentAdapter() {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        return createMockPrintDocumentAdapter(
                invocation -> {
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
                }, invocation -> {
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
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });
    }

    /**
     * Create a mock {@link PrinterDiscoverySessionCallbacks} that discovers a single printer with
     * minimal capabilities.
     *
     * @return The mock session callbacks
     */
    private PrinterDiscoverySessionCallbacks createFirstMockPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            // Get the session.
            sDiscoverySession = ((PrinterDiscoverySessionCallbacks) invocation.getMock())
                    .getSession();

            if (sDiscoverySession.getPrinters().isEmpty()) {
                List<PrinterInfo> printers = new ArrayList<>();

                // Add the printer.
                PrinterId printerId = sDiscoverySession.getService()
                        .generatePrinterId(PRINTER_NAME);

                PrinterCapabilitiesInfo capabilities = new PrinterCapabilitiesInfo.Builder(
                        printerId)
                        .setMinMargins(new Margins(200, 200, 200, 200))
                        .addMediaSize(MediaSize.ISO_A4, true)
                        .addResolution(new Resolution("300x300", "300x300", 300, 300),
                                true)
                        .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                PrintAttributes.COLOR_MODE_COLOR)
                        .build();

                Intent infoIntent = new Intent(getActivity(), Activity.class);
                PendingIntent infoPendingIntent = PendingIntent.getActivity(getActivity(), 0,
                        infoIntent, PendingIntent.FLAG_IMMUTABLE);

                sPrinter = new PrinterInfo.Builder(printerId, PRINTER_NAME,
                        PrinterInfo.STATUS_IDLE)
                        .setCapabilities(capabilities)
                        .setDescription("Minimal capabilities")
                        .setInfoIntent(infoPendingIntent)
                        .build();
                printers.add(sPrinter);

                sDiscoverySession.addPrinters(printers);
            }
            return null;
        }, null, null, invocation -> null, invocation -> {
            CustomPrinterIconCallback callback = (CustomPrinterIconCallback) invocation
                    .getArguments()[2];

            if (mIcon != null) {
                callback.onCustomPrinterIconLoaded(mIcon);
            }
            return null;
        }, null, invocation -> {
            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }

    /**
     * Get the current progress of #sPrintJob
     *
     * @return The current progress
     *
     * @throws InterruptedException If the thread was interrupted while setting the progress
     * @throws Throwable            If anything is unexpected.
     */
    private float getProgress() throws Throwable {
        float[] printProgress = new float[1];
        runOnMainThread(() -> printProgress[0] = sPrintJob.getInfo().getProgress());

        return printProgress[0];
    }

    /**
     * Get the current status of #sPrintJob
     *
     * @return The current status
     *
     * @throws InterruptedException If the thread was interrupted while getting the status
     * @throws Throwable            If anything is unexpected.
     */
    private CharSequence getStatus() throws Throwable {
        CharSequence[] printStatus = new CharSequence[1];
        runOnMainThread(() -> printStatus[0] = sPrintJob.getInfo().getStatus(getActivity()
                .getPackageManager()));

        return printStatus[0];
    }

    /**
     * Check if a print progress is correct.
     *
     * @param desiredProgress The expected @{link PrintProgresses}
     *
     * @throws Throwable If anything goes wrong or this takes more than 5 seconds
     */
    private void checkNotification(float desiredProgress, CharSequence desiredStatus)
            throws Throwable {
        eventually(() -> assertEquals(desiredProgress, getProgress()));
        eventually(() -> assertEquals(desiredStatus.toString(), getStatus().toString()));
    }

    /**
     * Set a new progress and status for #sPrintJob
     *
     * @param progress The new progress to set
     * @param status   The new status to set
     *
     * @throws InterruptedException If the thread was interrupted while setting
     * @throws Throwable            If anything is unexpected.
     */
    private void setProgressAndStatus(final float progress, final CharSequence status)
            throws Throwable {
        runOnMainThread(() -> {
            sPrintJob.setProgress(progress);
            sPrintJob.setStatus(status);
        });
    }

    /**
     * Progress print job and check the print job state.
     *
     * @param progress How much to progress
     * @param status   The status to set
     *
     * @throws Throwable If anything goes wrong.
     */
    private void progress(float progress, CharSequence status) throws Throwable {
        setProgressAndStatus(progress, status);

        // Check that progress of job is correct
        checkNotification(progress, status);
    }

    /**
     * Create mock service callback for a session.
     *
     * @param sessionCallbacks The callbacks of the sessopm
     */
    private PrintServiceCallbacks createFirstMockPrinterServiceCallbacks(
            final PrinterDiscoverySessionCallbacks sessionCallbacks) {
        return createMockPrintServiceCallbacks(
                invocation -> sessionCallbacks,
                invocation -> {
                    sPrintJob = (PrintJob) invocation.getArguments()[0];
                    sPrintJob.start();
                    onPrintJobQueuedCalled();

                    return null;
                }, null);
    }

    /**
     * Test that the progress and status is propagated correctly.
     *
     * @throws Throwable If anything is unexpected.
     */
    public void testProgress() throws Throwable {
        if (!supportsPrinting()) {
            return;
        }
        // Create the session callbacks that we will be checking.
        PrinterDiscoverySessionCallbacks sessionCallbacks
                = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter();

        // Start printing.
        print(adapter);

        // Wait for write of the first page.
        waitForWriteAdapterCallback(1);

        // Select the printer.
        selectPrinter(PRINTER_NAME);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait until the print job is queued and #sPrintJob is set
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Progress print job and check for appropriate notifications
        progress(0, "printed 0");
        progress(0.5f, "printed 50");
        progress(1, "printed 100");

        // Call complete from the main thread
        runOnMainThread(sPrintJob::complete);

        // Wait for all print jobs to be handled after which the session destroyed.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    /**
     * Render a {@link Drawable} into a {@link Bitmap}.
     *
     * @param d the drawable to be rendered
     *
     * @return the rendered bitmap
     */
    private static Bitmap renderDrawable(Drawable d) {
        Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);

        return bitmap;
    }

    /**
     * Update the printer
     *
     * @param printer the new printer to use
     *
     * @throws InterruptedException If we were interrupted while the printer was updated.
     * @throws Throwable            If anything is unexpected.
     */
    private void updatePrinter(final PrinterInfo printer) throws Throwable {
        runOnMainThread(() -> {
            ArrayList<PrinterInfo> printers = new ArrayList<>(1);
            printers.add(printer);
            sDiscoverySession.addPrinters(printers);
        });

        // Update local copy of printer
        sPrinter = printer;
    }

    /**
     * Assert is the printer icon does not match the bitmap. As the icon update might take some time
     * we try up to 5 seconds.
     *
     * @param bitmap The bitmap to match
     *
     * @throws Throwable If anything is unexpected.
     */
    private void assertThatIconIs(Bitmap bitmap) throws Throwable {
        eventually(
                () -> assertTrue(bitmap.sameAs(renderDrawable(sPrinter.loadIcon(getActivity())))));
    }

    /**
     * Test that the icon get be updated.
     *
     * @throws Throwable If anything is unexpected.
     */
    public void testUpdateIcon() throws Throwable {
        if (!supportsPrinting()) {
            return;
        }
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks sessionCallbacks
                = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter();

        // Start printing.
        print(adapter);

        // Open printer selection dropdown list to display icon on screen
        UiObject destinationSpinner = UiDevice.getInstance(getInstrumentation())
                .findObject(new UiSelector().resourceId(
                        "com.android.printspooler:id/destination_spinner"));
        destinationSpinner.click();

        // Get the print service's icon
        PackageManager packageManager = getActivity().getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(
                new ComponentName(getActivity(), FirstPrintService.class).getPackageName(), 0);
        ApplicationInfo appInfo = packageInfo.applicationInfo;
        Drawable printServiceIcon = appInfo.loadIcon(packageManager);

        assertThatIconIs(renderDrawable(printServiceIcon));

        // Update icon to resource
        updatePrinter((new PrinterInfo.Builder(sPrinter)).setIconResourceId(R.drawable.red_printer)
                .build());

        assertThatIconIs(renderDrawable(getActivity().getDrawable(R.drawable.red_printer)));

        // Update icon to bitmap
        Bitmap bm = BitmapFactory.decodeResource(getActivity().getResources(),
                R.raw.yellow_printer);
        // Icon will be picked up from the discovery session once setHasCustomPrinterIcon is set
        mIcon = Icon.createWithBitmap(bm);
        updatePrinter((new PrinterInfo.Builder(sPrinter)).setHasCustomPrinterIcon(true).build());

        assertThatIconIs(renderDrawable(mIcon.loadDrawable(getActivity())));
    }

    /**
     * Test that we cannot call attachBaseContext
     *
     * @throws Throwable If anything is unexpected.
     */
    public void testCannotUseAttachBaseContext() throws Throwable {
        if (!supportsPrinting()) {
            return;
        }
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks sessionCallbacks
                = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter();

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Start printing to set serviceCallbacks.getService()
        print(adapter);
        eventually(() -> assertNotNull(serviceCallbacks.getService()));

        // attachBaseContext should always throw an exception no matter what input value
        assertException(() -> serviceCallbacks.getService().callAttachBaseContext(null),
                IllegalStateException.class);
        assertException(() -> serviceCallbacks.getService().callAttachBaseContext(getActivity()),
                IllegalStateException.class);
    }
}
