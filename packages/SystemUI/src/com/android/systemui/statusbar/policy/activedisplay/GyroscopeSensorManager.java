/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2014 ParanoidAndroid Project.
 * This code has been modified. Portions copyright (C) 2014, AOSB Project.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.activedisplay;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import javax.annotation.concurrent.GuardedBy;

/**
 * Manages the proximity sensor and notifies a listener when enabled.
 */
public class GyroscopeSensorManager {
    /**
     * Listener of the state of the proximity sensor.
     * <p>
     * This interface abstracts two possible states for the proximity sensor, near and far.
     * <p>
     * The actual meaning of these states depends on the actual sensor.
     */
    public interface GyroscopeListener {
        /** Called when the proximity sensor transitions from the far to the near state. */
        public void onNear();
        /** Called when the proximity sensor transitions from the near to the far state. */
        public void onFar();
        /** Called when .. */
        public abstract void onTableModeChanged(boolean onTable);
    }

    public static enum State {
        NEAR, FAR
    }

    private final GyroscopeSensorEventListener mGyroscopeSensorListener;

    /**
     * The current state of the manager, i.e., whether it is currently tracking the state of the
     * sensor.
     */
    private boolean mManagerEnabled;

    /**
     * The listener to the state of the sensor.
     * <p>
     * Contains most of the logic concerning tracking of the sensor.
     * <p>
     * After creating an instance of this object, one should call {@link #register()} and
     * {@link #unregister()} to enable and disable the notifications.
     * <p>
     * Instead of calling unregister, one can call {@link #unregisterWhenFar()} to unregister the
     * listener the next time the sensor reaches the {@link State#FAR} state if currently in the
     * {@link State#NEAR} state.
     */
    private static class GyroscopeSensorEventListener implements SensorEventListener {

        private final static int INCREMENTS_TO_DISABLE = 5;
        private final static float NOISE_THRESHOLD = 0.5f;

        private final SensorManager mSensorManager;
        private final Sensor mGyroscopeSensor;
        private final GyroscopeListener mListener;

        //private boolean mGyroscopeRegistered;
        private boolean mHasInitialValues;
        private float mLastX = 0, mLastY = 0, mLastZ = 0;
        private int mSensorIncrement = 0;
        private boolean mOnTable;

        /**
         * The last state of the sensor.
         * <p>
         * Before registering and after unregistering we are always in the {@link State#FAR} state.
         */
        @GuardedBy("this") private State mLastState;
        /**
         * If this flag is set to true, we are waiting to reach the {@link State#FAR} state and
         * should notify the listener and unregister when that happens.
         */
        @GuardedBy("this") private boolean mWaitingForMovement;

        public GyroscopeSensorEventListener(SensorManager sensorManager, Sensor gyroscopeSensor, GyroscopeListener listener) {

            mSensorManager = sensorManager;
            mGyroscopeSensor = gyroscopeSensor;

            mListener = listener;
            // Initialize at far state.
            mLastState = State.FAR;
            mWaitingForMovement = false;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Make sure we have a valid value.
            if (event.values == null) return;

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[1];
            boolean storeValues = false;
            if(mHasInitialValues) {
                float dX = Math.abs(mLastX - x);
                float dY = Math.abs(mLastY - y);
                float dZ = Math.abs(mLastY - z);
                if(dX >= NOISE_THRESHOLD ||
                        dY >= NOISE_THRESHOLD || dZ >= NOISE_THRESHOLD) {
                    if (mWaitingForMovement) {
                        //Log.d(TAG, "On table: false");
                        mOnTable = false;
                        mListener.onTableModeChanged(mOnTable);
                        //registerEventListeners();
                        mWaitingForMovement = false;
                        mSensorIncrement = 0;
                    }
                    storeValues = true;
                } else {
                    if (mSensorIncrement < INCREMENTS_TO_DISABLE) {
                        mSensorIncrement ++;
                        if (mSensorIncrement == INCREMENTS_TO_DISABLE) {
                            //Log.d(TAG, "On table: true");
                            mOnTable = true;
                            mListener.onTableModeChanged(mOnTable);
                            mWaitingForMovement = true;
                        }
                    }
                }
            }

            if(!mHasInitialValues || storeValues) {
                mHasInitialValues = true;
                mLastX = x;
                mLastY = y;
                mLastZ = z;
            }

            State state = getStateFromValue(mOnTable);

            synchronized (this) {
                // No change in state, do nothing.
                if (state == mLastState) return;
                // Keep track of the current state.
                mLastState = state;
                // If we are waiting to reach the far state and we are now in it, unregister.
                if (mWaitingForMovement && mLastState == State.FAR) {
                    unregisterWithoutNotification();
                }
            }
            // Notify the listener of the state change.
            switch (state) {
                case NEAR:
                    mListener.onNear();
                    break;

                case FAR:
                    mListener.onFar();
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Nothing to do here.
        }

        /** Returns the state of the sensor given its current value. */
        private State getStateFromValue(boolean value) {
            // Determine if the current value corresponds to the NEAR or FAR state.
            // Take case of the case where the proximity sensor is binary: if the current value is
            // equal to the maximum, we are always in the FAR state.
            return (value) ? State.FAR : State.NEAR;
        }

        /**
         * Unregister the next time the sensor reaches the {@link State#FAR} state.
         */
        public synchronized void unregisterWhenFar() {
            if (mLastState == State.FAR) {
                // We are already in the far state, just unregister now.
                unregisterWithoutNotification();
            } else {
                mWaitingForMovement = true;
            }
        }

        /** Register the listener and call the listener as necessary. */
        public synchronized void register() {
            // It is okay to register multiple times.
            mSensorManager.registerListener(this, mGyroscopeSensor, SensorManager.SENSOR_DELAY_UI);
            // We should no longer be waiting for the far state if we are registering again.
            mWaitingForMovement = false;
            //mGyroscopeRegistered = true;
        }

        public void unregister() {
            State lastState;
            synchronized (this) {
                unregisterWithoutNotification();
                lastState = mLastState;
                // Always go back to the FAR state. That way, when we register again we will get a
                // transition when the sensor gets into the NEAR state.
                mLastState = State.FAR;
                mLastX = mLastY = mLastZ = 0;
                mSensorIncrement = 0;
                //mGyroscopeRegistered = false;
                mHasInitialValues = false;
            }
            // Notify the listener if we changed the state to FAR while unregistering.
            if (lastState != State.FAR) {
                mListener.onFar();
            }
        }

        @GuardedBy("this")
        private void unregisterWithoutNotification() {
            mSensorManager.unregisterListener(this);
            mWaitingForMovement = false;
        }
    }

    public GyroscopeSensorManager(Context context, GyroscopeListener listener) {
        SensorManager sensorManager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscopeSensor == null) {
            // If there is no sensor, we should not do anything.
            mGyroscopeSensorListener = null;
        } else {
            mGyroscopeSensorListener =
                    new GyroscopeSensorEventListener(sensorManager, gyroscopeSensor, listener);
        }
    }

    /**
     * Enables the proximity manager.
     * <p>
     * The listener will start getting notifications of events.
     * <p>
     * This method is idempotent.
     */
    public void enable() {
        if (mGyroscopeSensorListener != null && !mManagerEnabled) {
            mGyroscopeSensorListener.register();
            mManagerEnabled = true;
        }
    }

    /**
     * Disables the proximity manager.
     * <p>
     * The listener will stop receiving notifications of events, possibly after receiving a last
     * {@link Listener#onFar()} callback.
     * <p>
     * If {@code waitForFarState} is true, if the sensor is not currently in the {@link State#FAR}
     * state, the listener will receive a {@link Listener#onFar()} callback the next time the sensor
     * actually reaches the {@link State#FAR} state.
     * <p>
     * If {@code waitForFarState} is false, the listener will receive a {@link Listener#onFar()}
     * callback immediately if the sensor is currently not in the {@link State#FAR} state.
     * <p>
     * This method is idempotent.
     */
    public void disable(boolean waitForFarState) {
        if (mGyroscopeSensorListener != null && mManagerEnabled) {
            if (waitForFarState) {
                mGyroscopeSensorListener.unregisterWhenFar();
            } else {
                mGyroscopeSensorListener.unregister();
            }
            mManagerEnabled = false;
        }
    }
}
