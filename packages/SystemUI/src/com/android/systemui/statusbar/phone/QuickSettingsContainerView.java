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

package com.android.systemui.statusbar.phone;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.R;

/**
 *
 */
public class QuickSettingsContainerView extends FrameLayout {

    // The number of columns in the QuickSettings grid
    private int mNumColumns;
    private int mNumFinalColumns;

    // Duplicate number of columns in the QuickSettings grid on landscape view
    private boolean mDuplicateColumnsLandscape;

    // The gap between tiles in the QuickSettings grid
    private float mCellGap;

    private boolean mSingleRow;

    private Context mContext;
    private Resources mResources;

    private boolean mFirstStartUp = true;

    public QuickSettingsContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QuickSettingsContainer, 0, 0);
        mSingleRow = a.getBoolean(R.styleable.QuickSettingsContainer_singleRow, false);
        a.recycle();

        mContext = context;
        mResources = getContext().getResources();

        updateResources();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // TODO: Setup the layout transitions
        LayoutTransition transitions = getLayoutTransition();
    }

    public void updateResources() {
        mCellGap = mResources.getDimension(R.dimen.quick_settings_cell_gap);
        mNumColumns = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUICK_TILES_PER_ROW, 3, UserHandle.USER_CURRENT);
        // do not allow duplication on tablets or any device which do not have
        // flipsettings
        mDuplicateColumnsLandscape = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUICK_TILES_PER_ROW_DUPLICATE_LANDSCAPE,
                1, UserHandle.USER_CURRENT) == 1
                        && mResources.getBoolean(R.bool.config_hasFlipSettingsPanel);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mDuplicateColumnsLandscape && isLandscape()) {
            mNumFinalColumns = mNumColumns * 2;
        } else {
            mNumFinalColumns = mNumColumns;
        }
        // Calculate the cell width dynamically
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int availableWidth = (int) (width - getPaddingLeft() - getPaddingRight() -
                (mNumFinalColumns - 1) * mCellGap);
        float cellWidth = (float) Math.ceil(((float) availableWidth) / mNumFinalColumns);        
        int cellHeight = 0;
        float cellGap = mCellGap;

        if (mSingleRow) {
            cellWidth = MeasureSpec.getSize(heightMeasureSpec);
            cellHeight = (int) cellWidth;
            cellGap /= 2;
        } else {
            cellHeight = (int) getResources().getDimension(R.dimen.quick_settings_cell_height);
        }

        // Update each of the children's widths accordingly to the cell width
        int N = getChildCount();        
        int totalWidth = 0;        
        int cursor = 0;
        for (int i = 0; i < N; ++i) {
            // Update the child's width
            QuickSettingsTileView v = (QuickSettingsTileView) getChildAt(i);
            if (v.getVisibility() != View.GONE) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                int colSpan = v.getColumnSpan();
                lp.width = (int) ((colSpan * cellWidth) + (colSpan - 1) * cellGap);
                if (mSingleRow) {
                lp.height = cellHeight;
                } else if (mNumFinalColumns > 3 && !isLandscape()) {
                   lp.height = (lp.width * mNumFinalColumns - 1) / mNumFinalColumns;
                }
                // Measure the child
                int newWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
                int newHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
                v.measure(newWidthSpec, newHeightSpec);

                // Save the cell height
                if (cellHeight <= 0) {
                    cellHeight = v.getMeasuredHeight();
                }

                cursor += colSpan;
                if (mSingleRow) {                
                totalWidth += v.getMeasuredWidth() + cellGap;
                }
            }
        }

        // Set the measured dimensions.  We always fill the tray width, but wrap to the height of
        // all the tiles.
        int numRows = (int) Math.ceil((float) cursor / mNumColumns);
        int newHeight = (int) ((numRows * cellHeight) + ((numRows - 1) * cellGap)) +
                getPaddingTop() + getPaddingBottom();
        if (mSingleRow) {
            int totalHeight = cellHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(totalWidth, totalHeight);
        } else {
            setMeasuredDimension(width, newHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int N = getChildCount();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int cursor = 0;

        float cellGap = mCellGap;

        if (mSingleRow) {
            cellGap /= 2;
        }

        if (mDuplicateColumnsLandscape && isLandscape()) {
            mNumFinalColumns = mNumColumns * 2;
        } else {
            mNumFinalColumns = mNumColumns;
        }

        // onMeasure is done onLayout called last time isLandscape()
        // so first bootup is done, set it to false
        mFirstStartUp = false;

        for (int i = 0; i < N; ++i) {
            QuickSettingsTileView v = (QuickSettingsTileView) getChildAt(i);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (v.getVisibility() != GONE) {
                int col = cursor % mNumFinalColumns;
                int colSpan = v.getColumnSpan();
                int row = cursor / mNumFinalColumns;

                // Push the item to the next row if it can't fit on this one
                if ((col + colSpan) > mNumFinalColumns && !mSingleRow) {
                    x = getPaddingLeft();
                    y += lp.height + mCellGap;
                    row++;
                }

                // Layout the container
                v.layout(x, y, x + lp.width, y + lp.height);

                // Offset the position by the cell gap or reset the position and cursor when we
                // reach the end of the row
                cursor += v.getColumnSpan();
                if (cursor < (((row + 1) * mNumFinalColumns)) || mSingleRow) {
                    x += lp.width + mCellGap;
                } else if (!mSingleRow) {
                    x = getPaddingLeft();
                    y += lp.height + mCellGap;
                }
            }
        }
    }

    private boolean isLandscape() {
        if (mFirstStartUp) {
            WindowManager wm =
                ((WindowManager) mContext.getSystemService(mContext.WINDOW_SERVICE));
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            return size.x > size.y;
        } else {
            return Resources.getSystem().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
        }
    }

    public int getTileTextSize() {
        // get tile text size based on column count
        switch (mNumColumns) {
            case 5:
                return mResources.getDimensionPixelSize(R.dimen.qs_5_column_text_size);
            case 4:
                return mResources.getDimensionPixelSize(R.dimen.qs_4_column_text_size);
            case 3:
            default:
                return mResources.getDimensionPixelSize(R.dimen.qs_3_column_text_size);
        }
    }

    public int getTileTextPadding() {
        // get tile text padding based on column count
        switch (mNumColumns) {
            case 5:
                return mResources.getDimensionPixelSize(R.dimen.qs_5_column_text_padding);
            case 4:
                return mResources.getDimensionPixelSize(R.dimen.qs_4_column_text_padding);
            case 3:
            default:
                return mResources.getDimensionPixelSize(R.dimen.qs_tile_margin_below_icon);
        }
    }

}
