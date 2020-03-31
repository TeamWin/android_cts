/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.helpers;

/** Helper methods for interacting with the print system UI. */
public interface ICtsPrintHelper extends ICtsDeviceInteractionHelper {

    /**
     * Submit a print job based on the current settings.
     *
     * <p>This is the equivalent of the user clicking the print button; the test app must have
     * already called PrintManager.print() to create the print job and start the UI.
     */
    void submitPrintJob();

    /**
     * Select a specific printer from the list of available printers.
     *
     * @param printerName name of the printer
     * @param timeout timeout in milliseconds
     */
    void selectPrinter(String printerName, long timeout);

    /**
     * Set the page orientation to portrait or landscape.
     *
     * @param orientation "Portrait" or "Landscape"
     */
    void setPageOrientation(String orientation);

    /**
     * Get the current page orientation.
     *
     * @return current orientation as "Portrait" or "Landscape"
     */
    String getPageOrientation();

    /**
     * Set the media size.
     *
     * @param mediaSize human-readable label matching one of the PrintAttributes.MediaSize values
     */
    void setMediaSize(String mediaSize);

    /**
     * Set the color mode to Color or Black &amp; White.
     *
     * @param color "Color" or "Black &amp; White"
     */
    void setColorMode(String color);

    /**
     * Get the current color mode.
     *
     * @return "Color" or "Black &amp; White"
     */
    String getColorMode();

    /**
     * Set the duplex mode.
     *
     * @param duplex human-readable label matching one of the DUPLEX_MODE constants from
     *               PrintAttributes
     */
    void setDuplexMode(String duplex);

    /**
     * Set the number of copies to print.
     *
     * @param copies the new number of copies
     */
    void setCopies(int copies);

    /**
     * Get the current number of copies to print.
     *
     * @return the current number of copies
     */
    int getCopies();
}
