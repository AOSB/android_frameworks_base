package com.android.systemui.quicksettings;

import android.content.Context;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class OtouchTile extends FileObserverTile {
	protected static String TAG = OtouchTile.class.getSimpleName();
    public static final String OT_PATH = "/proc/touchpad/enable";

	public OtouchTile(Context context, QuickSettingsController qsc) {
		super(context, qsc);
	}

	@Override
	protected String getFilePath() {
		return OT_PATH;
	}

	@Override
	protected TwoStateTileRes getTileRes() {
		return new TwoStateTileRes(R.string.quick_settings_otouch_on_label
				,R.string.quick_settings_otouch_off_label
				,R.drawable.ic_qs_otouch_on
				,R.drawable.ic_qs_otouch_off);
	}
}
