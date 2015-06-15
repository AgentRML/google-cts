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

import android.content.Intent;
import android.os.Binder;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class MockInCallService extends InCallService {
    private ArrayList<Call> mCalls = new ArrayList<>();
    private static InCallServiceCallbacks sCallbacks;
    private Map<Call, MockVideoCallCallback> mVideoCallCallbacks =
            new ArrayMap<Call, MockVideoCallCallback>();

    private static final Object sLock = new Object();

    public static abstract class InCallServiceCallbacks {
        private MockInCallService mService;
        public Semaphore lock = new Semaphore(0);

        public void onCallAdded(Call call, int numCalls) {};
        public void onCallRemoved(Call call, int numCalls) {};
        public void onCallStateChanged(Call call, int state) {};

        final public MockInCallService getService() {
            return mService;
        }

        final public void setService(MockInCallService service) {
            mService = service;
        }
    }

    private Call.Callback mCallCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (getCallbacks() != null) {
                getCallbacks().onCallStateChanged(call, state);
            }
        }

        @Override
        public void onVideoCallChanged(Call call, InCallService.VideoCall videoCall) {
            saveVideoCall(call, videoCall);
        }
    };

    private void saveVideoCall(Call call, VideoCall videoCall) {
        if (videoCall != null) {
            if (!mVideoCallCallbacks.containsKey(call)) {
                MockVideoCallCallback listener = new MockVideoCallCallback(call);
                videoCall.registerCallback(listener);
                mVideoCallCallbacks.put(call, listener);
            }
        } else {
            mVideoCallCallbacks.remove(call);
        }
    }

    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        if (getCallbacks() != null) {
            getCallbacks().setService(this);
        }
        return super.onBind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        if (!mCalls.contains(call)) {
            mCalls.add(call);
            call.registerCallback(mCallCallback);

            VideoCall videoCall = call.getVideoCall();
            if (videoCall != null) {
                saveVideoCall(call, videoCall);
            }
        }
        if (getCallbacks() != null) {
            getCallbacks().onCallAdded(call, mCalls.size());
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        mCalls.remove(call);
        if (getCallbacks() != null) {
            getCallbacks().onCallRemoved(call, mCalls.size());
            saveVideoCall(call, null /* remove videoCall */);
        }
    }

    /**
     * @return the number of calls currently added to the {@code InCallService}.
     */
    public int getCallCount() {
        return mCalls.size();
    }

    /**
     * @return the most recently added call that exists inside the {@code InCallService}
     */
    public Call getLastCall() {
        if (mCalls.size() >= 1) {
            return mCalls.get(mCalls.size() - 1);
        }
        return null;
    }

    public void disconnectLastCall() {
        final Call call = getLastCall();
        if (call != null) {
            call.disconnect();
        }
    }

    public static void setCallbacks(InCallServiceCallbacks callbacks) {
        synchronized (sLock) {
            sCallbacks = callbacks;
        }
    }

    private InCallServiceCallbacks getCallbacks() {
        synchronized (sLock) {
            if (sCallbacks != null) {
                sCallbacks.setService(this);
            }
            return sCallbacks;
        }
    }

    /**
     * Determines if a video callback has been registered for the passed in call.
     *
     * @param call The call.
     * @return {@code true} if a video callback has been registered.
     */
    public boolean isVideoCallbackRegistered(Call call) {
        return mVideoCallCallbacks.containsKey(call);
    }

    /**
     * Retrieves the video callbacks associated with a call.
     * @param call The call.
     * @return The {@link MockVideoCallCallback} instance associated with the call.
     */
    public MockVideoCallCallback getVideoCallCallback(Call call) {
        return mVideoCallCallbacks.get(call);
    }
}
