/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.example.android.cardreader;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import com.example.android.common.logger.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.example.android.cardreader.Utils.BuildSelectAPDU;
import static com.example.android.cardreader.Utils.ByteArrayToHexString;
import static com.example.android.cardreader.Utils.HexStringToByteArray;

/**
 * Callback class, invoked when an NFC card is scanned while the device is running in reader mode.
 * <p>
 * Reader mode can be invoked by calling NfcAdapter
 */
public class LoyaltyCardReader implements NfcAdapter.ReaderCallback {

    private static final String TAG = "LoyaltyCardReader";

    // ISO-DEP command HEADER for selecting an AID.
    // Format: [Class | Instruction | Parameter 1 | Parameter 2]
    private static final String SELECT_APDU_HEADER = "00A40400";

    // AID for our loyalty card service.
    // Application ID (AID)-it defines a way to select applications
    private static final String AID_SAMPLE_LOYALTY_CARD = "F222222222";

    // "OK" status word sent in response to SELECT AID command (0x9000)
    private static final byte[] SELECT_OK_SW = HexStringToByteArray("9000");

    // Weak reference to prevent retain loop. mAccountCallback is responsible for exiting
    // foreground mode before it becomes invalid (e.g. during onPause() or onStop()).
    private WeakReference<AccountCallback> mAccountCallback;

    public interface AccountCallback {
        public void onAccountReceived(String account);
    }

    public LoyaltyCardReader(AccountCallback accountCallback) {
        mAccountCallback = new WeakReference<AccountCallback>(accountCallback);
    }

    /**
     * Callback when a new tag is discovered by the system.
     *
     * <p>Communication with the card should take place here.
     *
     * @param tag Discovered tag
     */
    @Override
    public void onTagDiscovered(Tag tag) {
        Log.i(TAG, "New tag discovered");

        // Android's Host-based Card Emulation (HCE) feature implements the ISO-DEP (ISO 14443-4) protocol.
        // In order to communicate with a device using HCE, the discovered tag should be processed using the IsoDep class.
        IsoDep isoDep = IsoDep.get(tag);

        if (isoDep != null) {
            try {
                // Connect to the remote NFC device
                isoDep.connect();

                /*
                -A step in an application protocol consists of sending a command, processing it in the receiving entity and sending back the response.
                Therefore a specific response corresponds to a specific command, referred to as a command-response pair.
                -An application protocol data unit (APDU) contains either a command message or a response message,
                sent from the interface device to the card or conversely.
                 */

                /*
                -Android uses the Application ID(AID) to determine which HCE service the reader wants to talk to.
                -Typically, the first APDU an NFC reader sends to your device is a "SELECT AID" APDU (this APDU contains the AID that the reader wants to talk to)
                -Android extracts that AID from the APDU, resolves it to an HCE service, then forwards that APDU to the resolved service.
                 */


                // Build SELECT AID command for our loyalty card service.
                // This command tells the remote device which service we wish to communicate with.
                // Send command to remote device
                byte[] APDU_response = isoDep.transceive(BuildSelectAPDU(SELECT_APDU_HEADER, AID_SAMPLE_LOYALTY_CARD));

                // If AID is successfully selected, 0x9000 is returned as the status word (last 2 bytes of the result) by convention.
                // Everything before the status word is optional payload, which is used here to hold the account number.
                int resultLength = APDU_response.length;
                byte[] status = {APDU_response[resultLength - 2], APDU_response[resultLength - 1]};
                byte[] payload = Arrays.copyOf(APDU_response, resultLength - 2);

                if (Arrays.equals(SELECT_OK_SW, status)) {
                    // The remote NFC device will immediately respond with its stored account number
                    String accountNumber = new String(payload, StandardCharsets.UTF_8);
                    Log.i(TAG, "Received: " + accountNumber);
                    // Inform CardReaderFragment of received account number
                    mAccountCallback.get().onAccountReceived(accountNumber);
                }

                isoDep.close();
            } catch (IOException e) {
                Log.e(TAG, "Error communicating with card: " + e.toString());
            }

        }
    }



}
