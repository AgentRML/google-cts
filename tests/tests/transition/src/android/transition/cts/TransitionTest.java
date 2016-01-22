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
package android.transition.cts;

import android.transition.cts.R;

import android.animation.ObjectAnimator;
import android.transition.AutoTransition;
import android.transition.Scene;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.concurrent.TimeUnit;

public class TransitionTest extends BaseTransitionTest {

    public TransitionTest() {
    }

    public void testAddListener() throws Throwable {
        startTransition(R.layout.scene1);
        waitForStart();

        final SimpleTransitionListener listener2 = new SimpleTransitionListener();

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                AutoTransition autoTransition = new AutoTransition();
                autoTransition.setDuration(100);
                autoTransition.addListener(listener2);
                Scene scene = Scene.getSceneForLayout(mSceneRoot, R.layout.scene2, mActivity);
                TransitionManager.go(scene, autoTransition);
            }
        });

        waitForStart(listener2);

        assertEquals(0, mListener.pauseLatch.getCount());
        assertEquals(0, mListener.resumeLatch.getCount());
        assertEquals(1, mListener.cancelLatch.getCount());
        assertEquals(1, mListener.endLatch.getCount());
        assertEquals(0, mListener.startLatch.getCount());

        assertEquals(1, listener2.pauseLatch.getCount());
        assertEquals(1, listener2.resumeLatch.getCount());
        assertEquals(1, listener2.cancelLatch.getCount());
        assertEquals(1, listener2.endLatch.getCount());
        assertEquals(0, listener2.startLatch.getCount());
        endTransition();
    }

    public void testRemoveListener() throws Throwable {
        startTransition(R.layout.scene1);
        waitForStart();

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTransition.removeListener(mListener);
            }
        });

        assertFalse(mListener.endLatch.await(250, TimeUnit.MILLISECONDS));
    }

    public void testAddTargetId() throws Throwable {
        enterScene(R.layout.scene4);
        mTransition.addTarget(R.id.holder);
        mTransition.addTarget(R.id.hello);
        assertEquals(2, mTransition.getTargetIds().size());
        startTransition(R.layout.scene1);
        assertEquals(1, mTargets.size());
        assertEquals(R.id.hello, mTargets.get(0).getId());
        endTransition();
    }

    public void testRemoveTargetId() throws Throwable {
        enterScene(R.layout.scene4);
        mTransition.addTarget(R.id.holder);
        mTransition.addTarget(R.id.hello);
        mTransition.addTarget(R.id.redSquare);
        assertEquals(3, mTransition.getTargetIds().size());
        mTransition.removeTarget(0); // nothing should happen
        mTransition.removeTarget(R.id.redSquare);
        assertEquals(2, mTransition.getTargetIds().size());

        startTransition(R.layout.scene1);
        assertEquals(1, mTargets.size());
        assertEquals(R.id.hello, mTargets.get(0).getId());
        endTransition();
    }

    public void testAddTargetClass() throws Throwable {
        enterScene(R.layout.scene4);
        mTransition.addTarget(RelativeLayout.class);
        mTransition.addTarget(TextView.class);
        assertEquals(2, mTransition.getTargetTypes().size());
        startTransition(R.layout.scene1);
        assertEquals(1, mTargets.size());
        assertTrue(mTargets.get(0) instanceof TextView);
        endTransition();
    }

    public void testRemoveTargetClass() throws Throwable {
        enterScene(R.layout.scene4);
        mTransition.addTarget(TextView.class);
        mTransition.addTarget(View.class);
        mTransition.addTarget(RelativeLayout.class);
        assertEquals(3, mTransition.getTargetTypes().size());
        mTransition.removeTarget(ImageView.class); // should do nothing
        mTransition.removeTarget(View.class);
        assertEquals(2, mTransition.getTargetTypes().size());
        startTransition(R.layout.scene1);
        assertEquals(1, mTargets.size());
        assertTrue(mTargets.get(0) instanceof TextView);
        endTransition();
    }

    public void testAddTargetView() throws Throwable {
        enterScene(R.layout.scene1);

        final View[] target = new View[1];
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                target[0] = mActivity.findViewById(R.id.hello);
            }
        });
        mTransition.addTarget(target[0]);
        assertEquals(1, mTransition.getTargets().size());
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
                target[0].setVisibility(View.GONE);
            }
        });
        waitForStart();
        assertEquals(1, mTargets.size());
        assertEquals(target[0], mTargets.get(0));
        endTransition();
    }

    public void testRemoveTargetView() throws Throwable {
        enterScene(R.layout.scene1);

        final View[] target = new View[3];
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                target[0] = mActivity.findViewById(R.id.hello);
                target[1] = mActivity.findViewById(R.id.greenSquare);
                target[2] = mActivity.findViewById(R.id.redSquare);
            }
        });

        mTransition.addTarget(target[0]);
        mTransition.addTarget(target[1]);
        assertEquals(2, mTransition.getTargets().size());
        mTransition.removeTarget(target[2]); // should do nothing
        mTransition.removeTarget(target[1]);
        assertEquals(1, mTransition.getTargets().size());
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
                target[0].setVisibility(View.GONE);
            }
        });
        waitForStart();
        assertEquals(1, mTargets.size());
        assertEquals(target[0], mTargets.get(0));
        endTransition();
    }

    public void testAddTargetName() throws Throwable {
        enterScene(R.layout.scene4);
        mTransition.addTarget("red");
        mTransition.addTarget("holder");
        assertEquals(2, mTransition.getTargetNames().size());
        assertEquals(0, mTargets.size());
        startTransition(R.layout.scene2);
        assertEquals(1, mTargets.size());
        assertEquals(R.id.redSquare, mTargets.get(0).getId());
        endTransition();
    }

    public void testRemoveTargetName() throws Throwable {
        enterScene(R.layout.scene4);
        mTransition.addTarget("holder");
        mTransition.addTarget("red");
        mTransition.addTarget("green");
        assertEquals(3, mTransition.getTargetNames().size());
        mTransition.removeTarget("purple"); // should do nothing
        // try to force a different String instance
        String greenName = new StringBuilder("gre").append("en").toString();
        mTransition.removeTarget(greenName);
        assertEquals(2, mTransition.getTargetNames().size());
        startTransition(R.layout.scene1);
        assertEquals(1, mTargets.size());
        assertEquals(R.id.redSquare, mTargets.get(0).getId());
        endTransition();
    }

    public void testIsTransitionRequired() throws Throwable {
        enterScene(R.layout.scene1);
        mTransition = new NotRequiredTransition();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                TransitionManager.beginDelayedTransition(mSceneRoot, mTransition);
                mActivity.findViewById(R.id.hello).setVisibility(View.GONE);
            }
        });
        waitForStart();
        assertEquals(0, mTargets.size());
        endTransition();
    }

    private class NotRequiredTransition extends TestTransition {
        @Override
        public boolean isTransitionRequired(TransitionValues startValues,
                TransitionValues newValues) {
            return false;
        }
    }
}

