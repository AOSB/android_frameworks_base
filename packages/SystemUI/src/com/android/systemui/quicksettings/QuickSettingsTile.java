/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 *
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

package com.android.systemui.quicksettings;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class QuickSettingsTile implements View.OnClickListener {
    protected final Context mContext;
    protected QuickSettingsContainerView mContainer;
    protected QuickSettingsTileView mTile;
    protected View.OnClickListener mOnClick;
    protected View.OnLongClickListener mOnLongClick;
    protected final int mTileLayout;
    protected int mDrawable;
    protected String mLabel;
    protected int mTileTextSize;
    protected int mTileTextPadding;
    protected PhoneStatusBar mStatusbarService;
    protected QuickSettingsController mQsc;
    protected SharedPreferences mPrefs;

    private final boolean mFlipRight;

    // Gesture
    protected final GestureDetector mGestureDetector;
    protected final View.OnTouchListener mGestureListener;

    // Flip
    protected final static int FLIP_RIGHT = 0;
    protected final static int FLIP_LEFT = 1;
    protected final static int FLIP_UP = 2;
    protected final static int FLIP_DOWN = 3;

    public QuickSettingsTile(Context context, QuickSettingsController qsc) {
        this(context, qsc, R.layout.quick_settings_tile_basic);
    }

    public QuickSettingsTile(Context context, QuickSettingsController qsc, int layout) {
        this(context, qsc, layout, true);
    }

    public QuickSettingsTile(Context context, QuickSettingsController qsc, int layout,
                             boolean flipRight) {
        mContext = context;
        mDrawable = R.drawable.ic_notifications;
        mLabel = mContext.getString(R.string.quick_settings_label_enabled);
        mStatusbarService = qsc.mStatusBarService;
        mQsc = qsc;
        mTileLayout = layout;
        mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);
        mHandler = new Handler();
        if (mObserver == null) {
            mObserver = new SettingsObserver(context, mHandler);
        }
        mGestureDetector = new GestureDetector(mContext, new QuickTileGestureDetector());
        mGestureListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return mGestureDetector.onTouchEvent(motionEvent);
            }
        };
        mFlipRight = flipRight;
    }

    public void setupQuickSettingsTile(LayoutInflater inflater,
            QuickSettingsContainerView container) {
        container.updateResources();
        mTileTextSize = container.getTileTextSize();
        mTileTextPadding = container.getTileTextPadding();

        mTile = (QuickSettingsTileView) inflater.inflate(
                R.layout.quick_settings_tile, container, false);
        mTile.setContent(mTileLayout, inflater);
        mContainer = container;
        mContainer.addView(mTile);
        onPostCreate();
        updateQuickSettings();
        mTile.setOnTouchListener(mGestureListener);
    }

    public void switchToRibbonMode() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        if (tv != null) {
            tv.setVisibility(View.GONE);
        }
        View image = mTile.findViewById(R.id.image);
        if (image != null) {
            MarginLayoutParams params = (MarginLayoutParams) image.getLayoutParams();
            int margin = mContext.getResources().getDimensionPixelSize(
                    R.dimen.qs_tile_ribbon_icon_margin);
            params.topMargin = params.bottomMargin = margin;
            image.setLayoutParams(params);
        }
    }

    void onPostCreate() {}

    public void onDestroy() {}

    public void onReceive(Context context, Intent intent) {
    }

    public void onChangeUri(ContentResolver resolver, Uri uri) {
    }

    public void updateResources() {
        if (mTile != null) {
            updateQuickSettings();
        }
    }

    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        if (tv != null) {
            tv.setText(mLabel);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            tv.setPadding(0, mTileTextPadding, 0, 0);
        }
        View image = mTile.findViewById(R.id.image);
        if (image != null && image instanceof ImageView) {
            ((ImageView) image).setImageResource(mDrawable);
        }
    }

    public boolean isFlipTilesEnabled() {
        return mObserver.getFlip();
    }

    public void flipTile(int delay, int flipId) {
        if (!isFlipTilesEnabled()) {
            return;
        }
        int animId;
        switch (flipId) {
            default:
            case FLIP_RIGHT:
                animId = R.anim.flip_right;
                break;
            case FLIP_LEFT:
                animId = R.anim.flip_left;
                break;
            case FLIP_UP:
                animId = R.anim.flip_up;
                break;
            case FLIP_DOWN:
                animId = R.anim.flip_down;
                break;
        }
        final AnimatorSet anim = (AnimatorSet) AnimatorInflater.loadAnimator(
                mContext, animId);
        anim.setTarget(mTile);
        anim.setDuration(200);
        anim.addListener(new AnimatorListener() {

            @Override
            public void onAnimationEnd(Animator animation) {
            }

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }

        });

        Runnable doAnimation = new Runnable() {
            @Override
            public void run() {
                anim.start();
            }
        };

        mHandler.postDelayed(doAnimation, delay);
    }

    void startSettingsActivity(String action) {
        Intent intent = new Intent(action);
        startSettingsActivity(intent);
    }

    void startSettingsActivity(Intent intent) {
        startSettingsActivity(intent, true);
    }

    private void startSettingsActivity(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !mStatusbarService.isDeviceProvisioned()) {
            return;
        }
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
            // ignored
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        mStatusbarService.animateCollapsePanels();
    }

    @Override
    public void onClick(View v) {
        if (mOnClick != null) {
            mOnClick.onClick(mTile);
        }

        ContentResolver resolver = mContext.getContentResolver();
        boolean shouldCollapse = Settings.System.getIntForUser(resolver,
                Settings.System.QS_COLLAPSE_PANEL, 0, UserHandle.USER_CURRENT) == 1;
        if (shouldCollapse) {
            mQsc.mBar.collapseAllPanels(true);
        }

        flipTile(0, FLIP_DOWN);
    }

    public void onFlingRight() {
        flipTile(0, FLIP_RIGHT);
    }

    public void onFlingLeft() {
        flipTile(0, FLIP_LEFT);
    }

    private class QuickTileGestureDetector extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_MIN_DISTANCE = 120;
        private static final int SWIPE_MAX_OFF_PATH = 250;
        private static final int SWIPE_THRESHOLD_VELOCITY = 200;

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            QuickSettingsTile.this.onClick(mTile);
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (mOnLongClick != null) {
                mOnLongClick.onLongClick(mTile);
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
                    return false;
                }
                // right to left swipe
                if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE
                        && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    onFlingLeft();
                } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE
                        && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    onFlingRight();
                }
            } catch (Exception e) {
                // nothing
            }
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }
}
