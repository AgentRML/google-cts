/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.media.cts;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.VolumeProvider;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.Set;

public class MediaSessionTest extends AndroidTestCase {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 5000L;
    private static final String SESSION_TAG = "test-session";
    private static final String EXTRAS_KEY = "test-key";
    private static final String EXTRAS_VALUE = "test-val";
    private static final String SESSION_EVENT = "test-session-event";

    private AudioManager mAudioManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Tests that a session can be created and that all the fields are
     * initialized correctly.
     */
    public void testCreateSession() throws Exception {
        MediaSession session = new MediaSession(getContext(), SESSION_TAG);
        assertNotNull(session.getSessionToken());
        assertFalse("New session should not be active", session.isActive());

        // Verify by getting the controller and checking all its fields
        MediaController controller = session.getController();
        assertNotNull(controller);
        verifyNewSession(controller, SESSION_TAG);
    }

    /**
     * Tests MediaSession.Token created in the constructor of MediaSession.
     */
    public void testSessionToken() throws Exception {
        MediaSession session = new MediaSession(getContext(), SESSION_TAG);
        MediaSession.Token sessionToken = session.getSessionToken();

        assertNotNull(sessionToken);
        assertEquals(0, sessionToken.describeContents());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        sessionToken.writeToParcel(p, 0);
        p.setDataPosition(0);
        MediaSession.Token token = MediaSession.Token.CREATOR.createFromParcel(p);
        assertEquals(token, sessionToken);
        p.recycle();
    }

    /**
     * Tests that the various configuration bits on a session get passed to the
     * controller.
     */
    public void testConfigureSession() throws Exception {
        MediaSession session = new MediaSession(getContext(), SESSION_TAG);
        MediaController controller = session.getController();

        // test setExtras
        Bundle extras = new Bundle();
        extras.putString(EXTRAS_KEY, EXTRAS_VALUE);
        session.setExtras(extras);
        Bundle extrasOut = controller.getExtras();
        assertNotNull(extrasOut);
        assertEquals(EXTRAS_VALUE, extrasOut.get(EXTRAS_KEY));

        // test setFlags
        session.setFlags(5);
        assertEquals(5, controller.getFlags());

        // test setMetadata
        MediaMetadata metadata =
                new MediaMetadata.Builder().putString(EXTRAS_KEY, EXTRAS_VALUE).build();
        session.setMetadata(metadata);
        MediaMetadata metadataOut = controller.getMetadata();
        assertNotNull(metadataOut);
        assertEquals(EXTRAS_VALUE, metadataOut.getString(EXTRAS_KEY));

        // test setPlaybackState
        PlaybackState state = new PlaybackState.Builder().setActions(55).build();
        session.setPlaybackState(state);
        PlaybackState stateOut = controller.getPlaybackState();
        assertNotNull(stateOut);
        assertEquals(55L, stateOut.getActions());

        // test setPlaybackToRemote, do this before testing setPlaybackToLocal
        // to ensure it switches correctly.
        try {
            session.setPlaybackToRemote(null);
            fail("Expected IAE for setPlaybackToRemote(null)");
        } catch (IllegalArgumentException e) {
            // expected
        }
        VolumeProvider vp = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_FIXED, 11, 11) {};
        session.setPlaybackToRemote(vp);
        MediaController.PlaybackInfo info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE, info.getPlaybackType());
        assertEquals(11, info.getMaxVolume());
        assertEquals(11, info.getCurrentVolume());
        assertEquals(VolumeProvider.VOLUME_CONTROL_FIXED, info.getVolumeControl());

        // test setPlaybackToLocal
        AudioAttributes attrs = new AudioAttributes.Builder().addTag(EXTRAS_VALUE).build();
        session.setPlaybackToLocal(attrs);
        info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL, info.getPlaybackType());
        Set<String> tags = info.getAudioAttributes().getTags();
        assertNotNull(tags);
        assertTrue(tags.contains(EXTRAS_VALUE));

        // test setQueue and setQueueTitle
        ArrayList<MediaSession.QueueItem> queue = new ArrayList<MediaSession.QueueItem>();
        MediaSession.QueueItem item = new MediaSession.QueueItem(new MediaDescription.Builder()
                .setMediaId(EXTRAS_VALUE).setTitle("title").build(), 11);
        queue.add(item);
        session.setQueue(queue);
        session.setQueueTitle(EXTRAS_VALUE);

        assertEquals(EXTRAS_VALUE, controller.getQueueTitle());
        assertEquals(1, controller.getQueue().size());
        assertEquals(11, controller.getQueue().get(0).getQueueId());
        assertEquals(EXTRAS_VALUE, controller.getQueue().get(0).getDescription().getMediaId());

        session.setQueue(null);
        session.setQueueTitle(null);

        assertNull(controller.getQueueTitle());
        assertNull(controller.getQueue());

        // test setSessionActivity
        Intent intent = new Intent("cts.MEDIA_SESSION_ACTION");
        PendingIntent pi = PendingIntent.getActivity(getContext(), 555, intent, 0);
        session.setSessionActivity(pi);
        assertEquals(pi, controller.getSessionActivity());

        // test setActivity
        session.setActive(true);
        assertTrue(session.isActive());
    }

    public void testSendSessionEvent() throws Exception {
        MediaSession session = new MediaSession(getContext(), SESSION_TAG);
        MediaController controller = new MediaController(getContext(), session.getSessionToken());
        Object lock = new Object();
        MediaControllerCallback callback = new MediaControllerCallback(lock);
        controller.registerCallback(callback, mHandler);

        Bundle extras = new Bundle();
        extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

        synchronized (lock) {
            session.sendSessionEvent(SESSION_EVENT, extras);
            lock.wait(TIME_OUT_MS);
            assertEquals(SESSION_EVENT, callback.mEvent);
            assertEquals(EXTRAS_VALUE, callback.mExtras.getString(EXTRAS_KEY));
        }
    }

    /**
     * Verifies that a new session hasn't had any configuration bits set yet.
     *
     * @param controller The controller for the session
     */
    private void verifyNewSession(MediaController controller, String tag) {
        assertEquals("New session has unexpected configuration", 0L, controller.getFlags());
        assertNull("New session has unexpected configuration", controller.getExtras());
        assertNull("New session has unexpected configuration", controller.getMetadata());
        assertEquals("New session has unexpected configuration",
                getContext().getPackageName(), controller.getPackageName());
        assertNull("New session has unexpected configuration", controller.getPlaybackState());
        assertNull("New session has unexpected configuration", controller.getQueue());
        assertNull("New session has unexpected configuration", controller.getQueueTitle());
        assertEquals("New session has unexpected configuration", Rating.RATING_NONE,
                controller.getRatingType());
        assertNull("New session has unexpected configuration", controller.getSessionActivity());

        assertNotNull(controller.getSessionToken());
        assertNotNull(controller.getTransportControls());
        assertEquals(tag, controller.getTag());

        MediaController.PlaybackInfo info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL, info.getPlaybackType());
        AudioAttributes attrs = info.getAudioAttributes();
        assertNotNull(attrs);
        assertEquals(AudioAttributes.USAGE_MEDIA, attrs.getUsage());
        assertEquals(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                info.getCurrentVolume());
    }

    private class MediaControllerCallback extends MediaController.Callback {
        private Object mLock;
        private String mEvent;
        private Bundle mExtras;

        MediaControllerCallback(Object lock) {
            mLock = lock;
        }

        @Override
        public void onSessionEvent(String event, Bundle extras) {
            synchronized (mLock) {
                mEvent = event;
                mExtras = (Bundle) extras.clone();
                mLock.notify();
            }
        }
    }
}
