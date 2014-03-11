package com.android.systemui.quicksettings;

import android.content.Context;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class FastChargeTile extends FileObserverTile {
	protected static String TAG = FastChargeTile.class.getSimpleName();
    public static final String FFC_PATH = "/sys/kernel/fast_charge/force_fast_charge";

	public FastChargeTile(Context context, QuickSettingsController qsc) {
		super(context, qsc);
	}

	@Override
	protected String getFilePath() {
		return FFC_PATH;
	}

	@Override
	protected TwoStateTileRes getTileRes() {
		return new TwoStateTileRes(R.string.quick_settings_fcharge_on_label
				,R.string.quick_settings_fcharge_off_label
				,R.drawable.ic_qs_fcharge_on
				,R.drawable.ic_qs_fcharge_off);
	}
}
