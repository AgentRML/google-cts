/**
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package android.app.usage.cts;

import android.app.AppOpsManager;
import android.app.usage.NetworkStatsManager;
import android.app.usage.NetworkStats;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.test.InstrumentationTestCase;
import android.util.Log;

import dalvik.system.SocketTagger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.URL;
import java.text.MessageFormat;
import javax.net.ssl.HttpsURLConnection;

import libcore.io.IoUtils;
import libcore.io.Streams;

public class NetworkUsageStatsTest extends InstrumentationTestCase {
    private static final String LOG_TAG = "NetworkUsageStatsTest";
    private static final String APPOPS_SET_SHELL_COMMAND = "appops set {0} " +
            AppOpsManager.OPSTR_GET_USAGE_STATS + " {1}";

    private static final long MINUTE = 1000 * 60;

    private static final int[] sNetworkTypesToTest = new int[] {
        ConnectivityManager.TYPE_WIFI,
        ConnectivityManager.TYPE_MOBILE,
    };

    // Order corresponds to sNetworkTypesToTest
    private static final int[] sTransportTypesToTest = new int[] {
        NetworkCapabilities.TRANSPORT_WIFI,
        NetworkCapabilities.TRANSPORT_CELLULAR,
    };

    private NetworkStatsManager mNsm;
    private PackageManager mPm;
    private ConnectivityManager mCm;
    private long mStartTime;
    private long mEndTime;

    private long mBytesRead;

    private void exerciseRemoteHost(int transportType) throws Exception {
        final int timeout = 60000;
        mCm.requestNetwork(new NetworkRequest.Builder()
            .addTransportType(transportType)
            .build(), new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    NetworkInfo networkInfo = mCm.getNetworkInfo(network);
                    if (networkInfo == null) {
                        Log.w(LOG_TAG, "Network info is null");
                    } else {
                        Log.w(LOG_TAG, "Network: " + networkInfo.toString());
                    }
                    InputStreamReader in = null;
                    HttpsURLConnection urlc = null;
                    String originalKeepAlive = System.getProperty("http.keepAlive");
                    System.setProperty("http.keepAlive", "false");
                    try {
                        urlc = (HttpsURLConnection) network.openConnection(new URL(
                                "https://www.google.com"));
                        urlc.setConnectTimeout(timeout);
                        urlc.setUseCaches(false);
                        urlc.connect();
                        boolean ping = urlc.getResponseCode() == 200;
                        if (ping) {
                            in = new InputStreamReader(
                                    (InputStream) urlc.getContent());

                            mBytesRead = 0;
                            while (in.read() != -1) ++mBytesRead;
                        }
                    } catch (Exception e) {
                        Log.i(LOG_TAG, "Badness during exercising remote server: " + e);
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                // don't care
                            }
                        }
                        if (urlc != null) {
                            urlc.disconnect();
                        }
                        if (originalKeepAlive == null) {
                            System.clearProperty("http.keepAlive");
                        } else {
                            System.setProperty("http.keepAlive", originalKeepAlive);
                        }
                    }
                }
            });
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
        }
    }

    @Override
    protected void setUp() throws Exception {
        mNsm = (NetworkStatsManager) getInstrumentation().getContext()
                .getSystemService(Context.NETWORK_STATS_SERVICE);

        mPm = getInstrumentation().getContext().getPackageManager();
        mCm = (ConnectivityManager) getInstrumentation().getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void setAppOpsMode(String mode) throws Exception {
        final String command = MessageFormat.format(APPOPS_SET_SHELL_COMMAND,
                getInstrumentation().getContext().getPackageName(), mode);
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try {
            Streams.readFully(new FileInputStream(pfd.getFileDescriptor()));
        } finally {
            IoUtils.closeQuietly(pfd.getFileDescriptor());
        }
    }

    private boolean shouldTestThisNetworkType(int networkTypeIndex) throws Exception {
        NetworkInfo networkInfo = mCm.getNetworkInfo(sNetworkTypesToTest[networkTypeIndex]);
        if (networkInfo == null || !networkInfo.isAvailable()) {
            return false;
        }
        mStartTime = System.currentTimeMillis() - MINUTE/2;
        exerciseRemoteHost(sTransportTypesToTest[networkTypeIndex]);
        mEndTime = System.currentTimeMillis() + MINUTE/2;
        return true;
    }

    public void testDeviceSummary() throws Exception {
        for (int i = 0; i < sNetworkTypesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i)) {
                continue;
            }
            setAppOpsMode("allow");
            NetworkStats.Bucket bucket = null;
            try {
                bucket = mNsm.querySummaryForDevice(
                        sNetworkTypesToTest[i], "", mStartTime, mEndTime);
            } catch (RemoteException | SecurityException e) {
                fail("testDeviceSummary fails with exception: " + e.toString());
            }
            assertTrue(bucket != null);
            setAppOpsMode("deny");
            try {
                bucket = mNsm.querySummaryForDevice(
                        ConnectivityManager.TYPE_WIFI, "", mStartTime, mEndTime);
                fail("negative testDeviceSummary fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testDeviceSummary fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testUserSummary() throws Exception {
        for (int i = 0; i < sNetworkTypesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i)) {
                continue;
            }
            setAppOpsMode("allow");
            NetworkStats.Bucket bucket = null;
            try {
                bucket = mNsm.querySummaryForUser(
                        sNetworkTypesToTest[i], "", mStartTime, mEndTime);
            } catch (RemoteException | SecurityException e) {
                fail("testUserSummary fails with exception: " + e.toString());
            }
            assertTrue(bucket != null);
            setAppOpsMode("deny");
            try {
                bucket = mNsm.querySummaryForUser(
                        ConnectivityManager.TYPE_WIFI, "", mStartTime, mEndTime);
                fail("negative testUserSummary fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testUserSummary fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testAppSummary() throws Exception {
        for (int i = 0; i < sNetworkTypesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i)) {
                continue;
            }
            setAppOpsMode("allow");
            NetworkStats result = null;
            try {
                result = mNsm.querySummary(
                        sNetworkTypesToTest[i], "", mStartTime, mEndTime);
                assertTrue(result != null);
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                long totalTxPackets = 0;
                long totalRxPackets = 0;
                long totalTxBytes = 0;
                long totalRxBytes = 0;
                while (result.getNextBucket(bucket)) {
                    if (bucket.getUid() == Process.myUid()) {
                        totalTxPackets += bucket.getTxPackets();
                        totalRxPackets += bucket.getRxPackets();
                        totalTxBytes += bucket.getTxBytes();
                        totalRxBytes += bucket.getRxBytes();
                    }
                }
                assertTrue("No Rx bytes usage for uid " + Process.myUid(), totalRxBytes > 0);
                assertTrue("No Rx packets usage for uid " + Process.myUid(), totalRxPackets > 0);
                assertTrue("No Tx bytes usage for uid " + Process.myUid(), totalTxBytes > 0);
                assertTrue("No Tx packets usage for uid " + Process.myUid(), totalTxPackets > 0);
            } catch (RemoteException | SecurityException e) {
                fail("testAppSummary fails with exception: " + e.toString());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
            setAppOpsMode("deny");
            try {
                result = mNsm.querySummary(
                        ConnectivityManager.TYPE_WIFI, "", mStartTime, mEndTime);
                fail("negative testAppSummary fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testAppSummary fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testAppDetails() throws Exception {
        for (int i = 0; i < sNetworkTypesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i)) {
                continue;
            }
            setAppOpsMode("allow");
            NetworkStats result = null;
            try {
                result = mNsm.queryDetails(
                        sNetworkTypesToTest[i], "", mStartTime, mEndTime);
                assertTrue(result != null);
            } catch (RemoteException | SecurityException e) {
                fail("testAppDetails fails with exception: " + e.toString());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
            setAppOpsMode("deny");
            try {
                result = mNsm.queryDetails(
                        ConnectivityManager.TYPE_WIFI, "", mStartTime, mEndTime);
                fail("negative testAppDetails fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testAppDetails fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testUidDetails() throws Exception {
        for (int i = 0; i < sNetworkTypesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i)) {
                continue;
            }
            setAppOpsMode("allow");
            NetworkStats result = null;
            try {
                result = mNsm.queryDetailsForUid(
                        sNetworkTypesToTest[i], "", mStartTime, mEndTime, Process.myUid());
                assertTrue(result != null);
            } catch (RemoteException | SecurityException e) {
                fail("testUidDetails fails with exception: " + e.toString());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
            setAppOpsMode("deny");
            try {
                result = mNsm.queryDetailsForUid(
                        ConnectivityManager.TYPE_WIFI, "", mStartTime, mEndTime, Process.myUid());
                fail("negative testUidDetails fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testUidDetails fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }
}
