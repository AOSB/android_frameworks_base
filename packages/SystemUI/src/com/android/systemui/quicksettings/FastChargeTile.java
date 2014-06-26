package com.android.systemui.quicksettings;

import android.content.Context;
import android.os.SystemProperties;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.internal.util.cm.QSUtils;

public class FastChargeTile extends FileObserverTile {
	protected static String TAG = FastChargeTile.class.getSimpleName();
	public static final String FFC_PATH = "/sys/kernel/fast_charge/force_fast_charge";
	private boolean mNeedsWriteOverride = false;

	public FastChargeTile(Context context, QuickSettingsController qsc) {
		super(context, qsc);
	}

	@Override
	protected String getFilePath() {
		return FFC_PATH;
	}

	@Override
	void onPostCreate() {
		String device = SystemProperties.get("ro.product.device", "none");
		mNeedsWriteOverride = "m8".equals(device);
		super.onPostCreate();
	}

	@Override
	protected TwoStateTileRes getTileRes() {
		return new TwoStateTileRes(R.string.quick_settings_fcharge_on_label
				,R.string.quick_settings_fcharge_off_label
				,R.drawable.ic_qs_fcharge_on
				,R.drawable.ic_qs_fcharge_off);
	}

	protected void setEnabled(boolean enabled) {
		if (mNeedsWriteOverride) {
		     QSUtils.setKernelFeatureEnabled(FFC_PATH, enabled);
		} else {
		    super.setEnabled(enabled);
		}
	}
}
