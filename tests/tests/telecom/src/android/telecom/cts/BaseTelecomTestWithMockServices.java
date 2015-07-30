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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.*;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.InCallService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConnection;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.MockInCallService.InCallServiceCallbacks;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Telecom CTS tests that require a {@link CtsConnectionService} and
 * {@link MockInCallService} to verify Telecom functionality.
 */
public class BaseTelecomTestWithMockServices extends InstrumentationTestCase {

    public static final int FLAG_REGISTER = 0x1;
    public static final int FLAG_ENABLE = 0x2;

    public static final PhoneAccountHandle TEST_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, COMPONENT), ACCOUNT_ID);

    public static final PhoneAccount TEST_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_VIDEO_CALLING |
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
            .build();

    public static final PhoneAccountHandle TEST_REMOTE_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, REMOTE_COMPONENT), REMOTE_ACCOUNT_ID);
    public static final String TEST_REMOTE_PHONE_ACCOUNT_ADDRESS = "tel:666-TEST";

    private static int sCounter = 0;

    Context mContext;
    TelecomManager mTelecomManager;
    InCallServiceCallbacks mInCallCallbacks;
    String mPreviousDefaultDialer = null;
    MockConnectionService connectionService = null;
    MockConnectionService remoteConnectionService = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        if (shouldTestTelecom(mContext)) {
            mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
            TestUtils.setDefaultDialer(getInstrumentation(), PACKAGE);
            setupCallbacks();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (shouldTestTelecom(mContext)) {
            cleanupCalls();
            if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
                TestUtils.setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
            }
            // Disconnect the remote phone account if the test had set it up.
            if (remoteConnectionService != null) {
                tearDownConnectionServices(TEST_PHONE_ACCOUNT_HANDLE,
                        TEST_REMOTE_PHONE_ACCOUNT_HANDLE);
            } else {
                tearDownConnectionService(TEST_PHONE_ACCOUNT_HANDLE);
            }
        }
        super.tearDown();
    }

    protected PhoneAccount setupConnectionService(MockConnectionService connectionService,
            int flags)
            throws Exception {
        if (connectionService != null) {
            this.connectionService = connectionService;
        } else {
            // Generate a vanilla mock connection service, if not provided.
            this.connectionService = new MockConnectionService();
        }
        CtsConnectionService.setUp(TEST_PHONE_ACCOUNT_HANDLE, this.connectionService);

        if ((flags & FLAG_REGISTER) != 0) {
            mTelecomManager.registerPhoneAccount(TEST_PHONE_ACCOUNT);
        }
        if ((flags & FLAG_ENABLE) != 0) {
            TestUtils.enablePhoneAccount(getInstrumentation(), TEST_PHONE_ACCOUNT_HANDLE);
        }

        return TEST_PHONE_ACCOUNT;
    }

    protected void setupConnectionServices(MockConnectionService connectionService,
            MockConnectionService remoteConnectionService, int flags)
            throws Exception {
        // Setup the primary connection service first
        setupConnectionService(connectionService, flags);

        if (remoteConnectionService != null) {
            this.remoteConnectionService = remoteConnectionService;
        } else {
            // Generate a vanilla mock connection service, if not provided.
            this.remoteConnectionService = new MockConnectionService();
        }
        CtsRemoteConnectionService.setUp(TEST_REMOTE_PHONE_ACCOUNT_HANDLE,
                this.remoteConnectionService);

        if ((flags & FLAG_REGISTER) != 0) {
            // This needs SIM subscription, so register via adb commands to get system permission.
            TestUtils.registerSimPhoneAccount(getInstrumentation(),
                    TEST_REMOTE_PHONE_ACCOUNT_HANDLE,
                    REMOTE_ACCOUNT_LABEL,
                    TEST_REMOTE_PHONE_ACCOUNT_ADDRESS);
        }
        if ((flags & FLAG_ENABLE) != 0) {
            TestUtils.enablePhoneAccount(getInstrumentation(), TEST_REMOTE_PHONE_ACCOUNT_HANDLE);
        }
    }

    protected void tearDownConnectionService(PhoneAccountHandle accountHandle) throws Exception {
        mTelecomManager.unregisterPhoneAccount(accountHandle);
        CtsConnectionService.tearDown();
        this.connectionService = null;
    }

    protected void tearDownConnectionServices(PhoneAccountHandle accountHandle,
            PhoneAccountHandle remoteAccountHandle) throws Exception {
        // Teardown the primary connection service first
        tearDownConnectionService(accountHandle);

        mTelecomManager.unregisterPhoneAccount(remoteAccountHandle);
        CtsRemoteConnectionService.tearDown();
        this.remoteConnectionService = null;
    }

    protected void startCallTo(Uri address, PhoneAccountHandle accountHandle) {
        final Intent intent = new Intent(Intent.ACTION_CALL, address);
        if (accountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private void setupCallbacks() {
        mInCallCallbacks = new InCallServiceCallbacks() {
            @Override
            public void onCallAdded(Call call, int numCalls) {
                Log.i(TAG, "onCallAdded, Call: " + call + "Num Calls: " + numCalls);
                this.lock.release();
            }
            @Override
            public void onCallRemoved(Call call, int numCalls) {
                Log.i(TAG, "onCallRemoved, Call: " + call + "Num Calls: " + numCalls);
            }
            @Override
            public void onParentChanged(Call call, Call parent) {
                Log.i(TAG, "onParentChanged, Call: " + call + "Parent: " + parent);
                this.lock.release();
            }
            @Override
            public void onChildrenChanged(Call call, List<Call> children) {
                Log.i(TAG, "onChildrenChanged, Call: " + call + "Children: " + children);
                this.lock.release();
            }
            @Override
            public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
                Log.i(TAG, "onConferenceableCallsChanged, Call: " + call + "Conferenceables: " +
                        conferenceableCalls);
                this.lock.release();
            }
            @Override
            public void onDetailsChanged(Call call, Call.Details details) {
                Log.i(TAG, "onDetailsChanged, Call: " + call + "Details: " + details);
            }
            @Override
            public void onCallDestroyed(Call call) {
                Log.i(TAG, "onCallDestroyed, Call: " + call);
            }
            @Override
            public void onCallStateChanged(Call call, int newState) {
                Log.i(TAG, "onCallStateChanged, Call: " + call + "New State: " + newState);
            }
        };

        MockInCallService.setCallbacks(mInCallCallbacks);
    }

    /**
     * Puts Telecom in a state where there is an incoming call provided by the
     * {@link CtsConnectionService} which can be tested.
     */
    void addAndVerifyNewIncomingCall(Uri incomingHandle, Bundle extras) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentCallCount = mInCallCallbacks.getService().getCallCount();
        }

        if (extras == null) {
            extras = new Bundle();
        }
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, incomingHandle);
        mTelecomManager.addNewIncomingCall(TEST_PHONE_ACCOUNT_HANDLE, extras);

        try {
            if (!mInCallCallbacks.lock.tryAcquire(3, TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a call.",
                currentCallCount + 1,
                mInCallCallbacks.getService().getCallCount());
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall() {
        placeAndVerifyCall(null);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     *
     *  @param videoState the video state of the call.
     */
    void placeAndVerifyCall(int videoState) {
        placeAndVerifyCall(null, videoState);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall(Bundle extras) {
        placeAndVerifyCall(extras, VideoProfile.STATE_AUDIO_ONLY);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall(Bundle extras, int videoState) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentCallCount = mInCallCallbacks.getService().getCallCount();
        }
        placeNewCallWithPhoneAccount(extras, videoState);

        try {
            if (!mInCallCallbacks.lock.tryAcquire(3, TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a call.",
                currentCallCount + 1,
                mInCallCallbacks.getService().getCallCount());
    }

    MockConnection verifyConnectionForOutgoingCall() {
        // Assuming only 1 connection present
        return verifyConnectionForOutgoingCall(0);
    }

    MockConnection verifyConnectionForOutgoingCall(int connectionIndex) {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create outgoing connection for outgoing call",
                connectionService.outgoingConnections.size(), not(equalTo(0)));
        assertEquals("Telecom should not create incoming connections for outgoing calls",
                0, connectionService.incomingConnections.size());
        MockConnection connection = connectionService.outgoingConnections.get(connectionIndex);
        setAndverifyConnectionForOutgoingCall(connection);
        return connection;
    }

    MockConnection verifyRemoteConnectionForOutgoingCall() {
        // Assuming only 1 connection present
        return verifyRemoteConnectionForOutgoingCall(0);
    }

    MockConnection verifyRemoteConnectionForOutgoingCall(int connectionIndex) {
        try {
            if (!remoteConnectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create outgoing connection for remote outgoing call",
                remoteConnectionService.outgoingConnections.size(), not(equalTo(0)));
        assertEquals("Telecom should not create incoming connections for remote outgoing calls",
                0, remoteConnectionService.incomingConnections.size());
        MockConnection connection = remoteConnectionService.outgoingConnections.get(connectionIndex);
        setAndverifyConnectionForOutgoingCall(connection);
        return connection;
    }

    void setAndverifyConnectionForOutgoingCall(MockConnection connection) {
        connection.setDialing();
        connection.setActive();
        assertEquals(Connection.STATE_ACTIVE, connection.getState());
    }

    MockConnection verifyConnectionForIncomingCall() {
        // Assuming only 1 connection present
        return verifyConnectionForIncomingCall(0);
    }

    MockConnection verifyConnectionForIncomingCall(int connectionIndex) {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create incoming connections for incoming calls",
                connectionService.incomingConnections.size(), not(equalTo(0)));
        assertEquals("Telecom should not create outgoing connections for incoming calls",
                0, connectionService.outgoingConnections.size());
        MockConnection connection = connectionService.incomingConnections.get(connectionIndex);
        setAndverifyConnectionForIncomingCall(connection);
        return connection;
    }

    MockConnection verifyRemoteConnectionForIncomingCall() {
        // Assuming only 1 connection present
        return verifyRemoteConnectionForIncomingCall(0);
    }

    MockConnection verifyRemoteConnectionForIncomingCall(int connectionIndex) {
        try {
            if (!remoteConnectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create incoming connections for remote incoming calls",
                remoteConnectionService.incomingConnections.size(), not(equalTo(0)));
        assertEquals("Telecom should not create outgoing connections for remote incoming calls",
                0, remoteConnectionService.outgoingConnections.size());
        MockConnection connection = remoteConnectionService.incomingConnections.get(connectionIndex);
        setAndverifyConnectionForIncomingCall(connection);
        return connection;
    }

    void setAndverifyConnectionForIncomingCall(MockConnection connection) {
        connection.setRinging();
        assertEquals(Connection.STATE_RINGING, connection.getState());
    }

    void setAndVerifyConferenceablesForOutgoingConnection(int connectionIndex) {
        /**
         * Make all other outgoing connections as conferenceable with this
         * new connection.
         */
        MockConnection connection = connectionService.outgoingConnections.get(connectionIndex);
        List<Connection> confConnections = new ArrayList<>(connectionService.outgoingConnections.size());
        for (Connection c : connectionService.outgoingConnections) {
            if (c != connection) {
                confConnections.add(c);
            }
        }
        connection.setConferenceableConnections(confConnections);

        try {
            if (!mInCallCallbacks.lock.tryAcquire(3, TimeUnit.SECONDS)) {
                fail("No call added to the conferenceables list.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
        assertEquals(connection.getConferenceables(), confConnections);
    }

    MockConference addAndVerifyConferenceCall(Call call1, Call call2) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentConfCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentConfCallCount = mInCallCallbacks.getService().getConferenceCallCount();
        }
        List<Call> call1ConfList = call1.getConferenceableCalls();
        List<Call> call2ConfList = call2.getConferenceableCalls();
        if (call1ConfList.contains(call2) && call2ConfList.contains(call1)) {
            call1.conference(call2);
        } else {
            fail("Calls cannot be conferenced!");
        }

        /**
         * We should have 1 onCallAdded, 2 onChildrenChanged and 2 onParentChanged invoked, so
         * we should have 5 available permits on the incallService lock.
         */
        try {
            if (!mInCallCallbacks.lock.tryAcquire(5, 3, TimeUnit.SECONDS)) {
                fail("Conference addition failed.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a conf call.",
                currentConfCallCount + 1,
                mInCallCallbacks.getService().getConferenceCallCount());
        // Return the newly created conference object to the caller
        return connectionService.conferences.get(currentConfCallCount);
    }

    void splitFromConferenceCall(Call call1) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());

        call1.splitFromConference();
        /**
         * We should have 1 onChildrenChanged and 1 onParentChanged invoked, so
         * we should have 2 available permits on the incallService lock.
         */
        try {
            if (!mInCallCallbacks.lock.tryAcquire(2, 3, TimeUnit.SECONDS)) {
                fail("Conference split failed");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
    }

    /**
     * Disconnect the created test call and verify that Telecom has cleared all calls.
     */
    void cleanupCalls() {
        if (mInCallCallbacks != null && mInCallCallbacks.getService() != null) {
            mInCallCallbacks.getService().disconnectAllConferenceCalls();
            mInCallCallbacks.getService().disconnectAllCalls();
            assertNumConferenceCalls(mInCallCallbacks.getService(), 0);
            assertNumCalls(mInCallCallbacks.getService(), 0);
        }
    }

    /**
     * Place a new outgoing call via the {@link CtsConnectionService}
     */
    private void placeNewCallWithPhoneAccount(Bundle extras, int videoState) {
        if (extras == null) {
            extras = new Bundle();
        }
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEST_PHONE_ACCOUNT_HANDLE);

        if (!VideoProfile.isAudioOnly(videoState)) {
            extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
        }

        mTelecomManager.placeCall(createTestNumber(), extras);
    }

    /**
     * Create a new number each time for a new test. Telecom has special logic to reuse certain
     * calls if multiple calls to the same number are placed within a short period of time which
     * can cause certain tests to fail.
     */
    Uri createTestNumber() {
        return Uri.fromParts("tel", String.valueOf(++sCounter), null);
    }

    public static Uri getTestNumber() {
        return Uri.fromParts("tel", String.valueOf(sCounter), null);
    }

    void assertNumCalls(final MockInCallService inCallService, final int numCalls) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return numCalls;
            }
            @Override
            public Object actual() {
                return inCallService.getCallCount();
            }
        },
        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
        "InCallService should contain " + numCalls + " calls."
    );
    }

    void assertNumConferenceCalls(final MockInCallService inCallService, final int numCalls) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return numCalls;
            }
            @Override
            public Object actual() {
                return inCallService.getConferenceCallCount();
            }
        },
        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
        "InCallService should contain " + numCalls + " conference calls."
    );
    }


    void assertMuteState(final InCallService incallService, final boolean isMuted) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isMuted;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return state == null ? null : state.isMuted();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's mute state should be: " + isMuted
        );
    }

    void assertMuteState(final MockConnection connection, final boolean isMuted) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isMuted;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = connection.getCallAudioState();
                        return state == null ? null : state.isMuted();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection's mute state should be: " + isMuted
        );
    }

    void assertAudioRoute(final InCallService incallService, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return route;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return state == null ? null : state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's audio route should be: " + route
        );
    }

    void assertAudioRoute(final MockConnection connection, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return route;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = connection.getCallAudioState();
                        return state == null ? null : state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection's audio route should be: " + route
        );
    }

    void assertConnectionState(final Connection connection, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return connection.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection should be in state " + state
        );
    }

    void assertCallState(final Call call, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return call.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should be in state " + state
        );
    }

    void assertDtmfString(final MockConnection connection, final String dtmfString) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                @Override
                public Object expected() {
                    return dtmfString;
                }

                @Override
                public Object actual() {
                    return connection.getDtmfString();
                }
            },
            WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
            "DTMF string should be equivalent to entered DTMF characters: " + dtmfString
        );
    }

    void assertCallDisplayName(final Call call, final String name) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return name;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getCallerDisplayName();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have display name: " + name
        );
    }

    void assertRemoteConnectionState(final RemoteConnection connection, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return connection.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Remote Connection should be in state " + state
        );
    }

    void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    /**
     * Performs some work, and waits for the condition to be met.  If the condition is not met in
     * each step of the loop, the work is performed again.
     *
     * @param work The work to perform.
     * @param condition The condition.
     * @param timeout The timeout.
     * @param description Description of the work being performed.
     */
    void doWorkAndWaitUntilConditionIsTrueOrTimeout(Work work, Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        work.doWork();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
            work.doWork();
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual();
    }

    protected interface Work {
        void doWork();
    }
}
