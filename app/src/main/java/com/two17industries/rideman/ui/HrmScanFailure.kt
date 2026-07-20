package com.two17industries.rideman.ui

import android.bluetooth.le.ScanCallback

/**
 * Rider-facing copy for a `startScan` that never started.
 *
 * The failure this replaces was a silent one: `onScanFailed` was not overridden, so the picker
 * sat on "Searching… the strap must be worn and damp to transmit." indefinitely — blaming the
 * rider's strap for a scan that the Bluetooth stack had already refused. Every message here
 * therefore names Bluetooth and never the strap, and each says what the rider can actually do.
 */
fun scanFailureMessage(errorCode: Int): String = when (errorCode) {
    ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
        "Bluetooth is already running this search. Close this dialog and open it again."

    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
        "Bluetooth refused to register the search. Turn Bluetooth off and on again."

    ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
        "Bluetooth hit an internal error. Turn Bluetooth off and on again."

    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
        "This phone's Bluetooth does not support the kind of search this needs."

    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
        "Bluetooth has no scan slots left. Close other Bluetooth apps and try again."

    ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
        "Bluetooth has been asked to search too many times in a row. " +
            "Wait about half a minute, then try again."

    else ->
        "Bluetooth could not start the search (error $errorCode). " +
            "Turn Bluetooth off and on again."
}

/** Shown when `startScan` throws outright rather than reporting through the callback. */
const val SCAN_START_REFUSED_MESSAGE =
    "Bluetooth refused to start the search. Check that Bluetooth is on and try again."
