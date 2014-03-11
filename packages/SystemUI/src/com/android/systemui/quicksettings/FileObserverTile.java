package com.android.systemui.quicksettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.android.internal.util.cm.QSUtils;

import android.content.Context;
import android.os.FileObserver;
import android.util.Log;
import android.view.View;

import com.android.systemui.statusbar.phone.QuickSettingsController;

public abstract class FileObserverTile extends QuickSettingsTile {
	protected static String TAG = FileObserverTile.class.getSimpleName();
	protected TwoStateTileRes mTileRes;
	protected boolean mFeatureEnabled = false;
	protected FileObserver mObserver;
	protected String mFilePath;

	public FileObserverTile(Context context, QuickSettingsController qsc) {
		super(context, qsc);
		mOnClick = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleState();
				updateResources();
			}
		};
	}

	@Override
	void onPostCreate() {
		mFilePath = getFilePath();
		mFeatureEnabled = isFeatureOn();
		mObserver = new FileObserver(mFilePath, FileObserver.MODIFY) {
			@Override
			public void onEvent(int event, String file) {
				Log.i(TAG, "feature file modified, event:" + event + ", file: "
						+ file);
				updateEnabled();
				updateResources();
			}
		};
		mObserver.startWatching();
		mTileRes = getTileRes();
		updateTile();
		super.onPostCreate();
	}

	@Override
	public void updateResources() {
		updateTile();
		super.updateResources();
	}

	protected abstract String getFilePath();

	protected abstract TwoStateTileRes getTileRes();

	protected void setEnabled(boolean enabled) {
		QSUtils.setKernelFeatureEnabled(mFilePath, enabled);
	}

	protected void updateTile() {
		mLabel = mContext.getString(mFeatureEnabled ? mTileRes.mTileOnLabel
				: mTileRes.mTileOffLabel);
		mDrawable = mFeatureEnabled ? mTileRes.mTileOnDrawable
				: mTileRes.mTileOffDrawable;
	}

	protected void toggleState() {
		updateEnabled();
		setEnabled(!mFeatureEnabled);
	}

	protected void updateEnabled() {
		mFeatureEnabled = isFeatureOn();
	}

	protected boolean isFeatureOn() {
		if (mFilePath == null || mFilePath.isEmpty()) {
			return false;
		}
		File file = new File(mFilePath);
		if (!file.exists()) {
			return false;
		}
		String content = null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			content = reader.readLine();
			Log.i(TAG, "isFeatureOn(): content: " + content);
			return "1".equals(content) || "Y".equalsIgnoreCase(content)
					|| "on".equalsIgnoreCase(content);
		} catch (Exception e) {
			Log.i(TAG, "exception reading feature file", e);
			return false;
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				// ignore
			}
		}
	}

	protected static class TwoStateTileRes {
		int mTileOnLabel;
		int mTileOffLabel;
		int mTileOnDrawable;
		int mTileOffDrawable;

		public TwoStateTileRes(int labelOn, int labelOff, int drawableOn,
				int drawableOff) {
			mTileOnLabel = labelOn;
			mTileOffLabel = labelOff;
			mTileOnDrawable = drawableOn;
			mTileOffDrawable = drawableOff;
		}
	}
}
