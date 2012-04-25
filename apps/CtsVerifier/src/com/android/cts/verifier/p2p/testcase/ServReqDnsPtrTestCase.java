/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.cts.verifier.p2p.testcase;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;

/**
 * Service discovery requester test case to search Bonjour domain.
 */
public class ServReqDnsPtrTestCase extends ServReqTestCase {

    public ServReqDnsPtrTestCase(Context context) {
        super(context);
    }

    @Override
    protected boolean executeTest() throws InterruptedException {

        /*
         * create request to search bonjour ipp PTR.
         */
        List<WifiP2pServiceRequest> reqList = new ArrayList<WifiP2pServiceRequest>();
        reqList.add(WifiP2pDnsSdServiceRequest.newInstance("_ipp._tcp"));

        /*
         * search and check the callback function.
         *
         * DNS PTR: IPP service.
         * DNS TXT: No services.
         * UPnP: No services.
         */
        return searchTest(mTargetAddress, reqList,
                DnsSdResponseListenerTest.IPP_DNS_PTR,
                DnsSdTxtRecordListenerTest.NO_DNS_TXT,
                UPnPServiceResponseListenerTest.NO_UPNP_SERVICES);
    }

    @Override
    public String getTestName() {
        return "Request DNS PTR service test";
    }
}
