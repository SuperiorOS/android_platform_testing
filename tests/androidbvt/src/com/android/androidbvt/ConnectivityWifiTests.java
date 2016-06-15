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

package com.android.androidbvt;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConnectivityWifiTests extends TestCase {
    private final static String DEFAULT_PING_SITE = "www.google.com";
    private UiDevice mDevice;
    private WifiManager mWifiManager = null;
    private Context mContext = null;
    private AndroidBvtHelper mABvtHelper = null;
    private WifiConfiguration mOriginalConfig = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.setOrientationNatural();
        mContext = InstrumentationRegistry.getTargetContext();
        mABvtHelper = AndroidBvtHelper.getInstance(mDevice, mContext,
                InstrumentationRegistry.getInstrumentation().getUiAutomation());
        mWifiManager = mABvtHelper.getWifiManager();
    }

    @Override
    public void tearDown() throws Exception {
        mDevice.wakeUp();
        mDevice.unfreezeRotation();
        mDevice.pressHome();
        mDevice.waitForIdle();
        super.tearDown();
    }

    /**
     * Test verifies wifi can be disconnected, disabled followed by enable and reconnect. As part of
     * connection check, it pings a site and ensures HTTP_OK return
     */
    @LargeTest
    public void testWifiConnection() throws InterruptedException {
        // Wifi is already connected as part of tradefed device setup, assert that
        assertTrue("Wifi should be connected", isWifiConnected());
        assertNotNull("Wifi manager is null", mWifiManager);
        assertTrue("Wifi isn't enabled", mWifiManager.isWifiEnabled());
        // Disconnect wifi and disable network, save NetId to be used for re-enabling network
        int netId = mWifiManager.getConnectionInfo().getNetworkId();
        disconnectWifi();
        Log.d("MyTestTag", "before sleep");
        Thread.sleep(mABvtHelper.LONG_TIMEOUT);
        Log.d("MyTestTag", "after sleep");
        assertFalse("Wifi shouldn't be connected", isWifiConnected());
        // Network enabled successfully
        assertTrue("Network isn't enabled", mWifiManager.enableNetwork(netId, true));
        // Allow time to settle down
        Thread.sleep(mABvtHelper.LONG_TIMEOUT * 2);
        assertTrue("Wifi should be connected", isWifiConnected());
    }

    /**
     * Test verifies from UI that bunch of AP are listed on enabling Wifi
     */
    @LargeTest
    public void testWifiDiscoveredAPShownUI() throws InterruptedException {
        Intent intent_as = new Intent(
                android.provider.Settings.ACTION_WIFI_SETTINGS);
        mContext.startActivity(intent_as);
        Thread.sleep(mABvtHelper.LONG_TIMEOUT);
        assertNotNull("AP list shouldn't be null",
                mDevice.wait(Until.findObject(By.res("com.android.settings:id/list")),
                        mABvtHelper.LONG_TIMEOUT));
        assertTrue("At least 1 AP should be visible",
                mDevice.wait(Until.findObject(By.res("com.android.settings:id/list")),
                        mABvtHelper.LONG_TIMEOUT)
                        .getChildren().size() > 0);
    }

    /**
     * Checks if wifi connection is active by sending an HTTP request, check for HTTP_OK
     */
    private boolean isWifiConnected() throws InterruptedException {
        int counter = 10;
        while (--counter > 0) {
            try {
                String mPingSite = String.format("http://%s", DEFAULT_PING_SITE);
                URL url = new URL(mPingSite);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(mABvtHelper.LONG_TIMEOUT * 5);
                conn.setReadTimeout(mABvtHelper.LONG_TIMEOUT * 5);
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    return true;
                }
                Thread.sleep(mABvtHelper.SHORT_TIMEOUT);
            } catch (IOException ex) {
                // Wifi being flaky in the lab, test retries 5 times to connect to google.com
                // as IOException is throws connection isn't made and response stream is null
                // so for retrying purpose, exception hasn't been rethrown
                Log.i(mABvtHelper.TEST_TAG, ex.getMessage());
            }
        }
        return false;
    }

    /**
     * Disconnects and disables network
     */
    private void disconnectWifi() {
        assertTrue("Wifi not disconnected", mWifiManager.disconnect());
        mWifiManager.disableNetwork(mWifiManager.getConnectionInfo().getNetworkId());
        mWifiManager.saveConfiguration();
    }
}