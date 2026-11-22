package com.example.sidebuttonhelper.ui;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.sidebuttonhelper.R;
import com.example.sidebuttonhelper.databinding.ActivityOemGuidanceBinding;

public class OemGuidanceActivity extends AppCompatActivity {

    // realme UI / ColorOS / OxygenOS are all ColorOS descendants and ship one of these
    // startup managers, depending on version and region. First one that resolves wins.
    private static final String[][] COLOROS_AUTOSTART = {
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oplus.battery", "com.oplus.battery.ui.activity.PowerConsumeRankActivity"},
    };

    private static final String[][] XIAOMI_AUTOSTART = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
    };

    private static final String[][] VIVO_AUTOSTART = {
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
    };

    private static final String[][] HUAWEI_AUTOSTART = {
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
    };

    private static final String[][] ASUS_AUTOSTART = {
            {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"},
    };

    private ActivityOemGuidanceBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOemGuidanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String manufacturer = Build.MANUFACTURER.toLowerCase();
        binding.textManufacturerDetected.setText(
                getString(R.string.oem_detected_device, Build.MANUFACTURER, Build.MODEL));
        binding.textInstructions.setText(instructionsFor(manufacturer));

        binding.btnOpenAutostartSettings.setOnClickListener(v -> openAutostartSettings());
    }

    private String instructionsFor(String manufacturer) {
        if (manufacturer.contains("realme")) {
            return getString(R.string.oem_instructions_realme);
        } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
                || manufacturer.contains("poco")) {
            return getString(R.string.oem_instructions_xiaomi);
        } else if (manufacturer.contains("oppo") || manufacturer.contains("oneplus")
                || manufacturer.contains("oplus")) {
            return getString(R.string.oem_instructions_oppo);
        } else if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return getString(R.string.oem_instructions_vivo);
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            return getString(R.string.oem_instructions_huawei);
        } else if (manufacturer.contains("asus")) {
            return getString(R.string.oem_instructions_asus);
        } else {
            return getString(R.string.oem_instructions_default, Build.MANUFACTURER);
        }
    }

    private String[][] candidatesFor(String manufacturer) {
        if (manufacturer.contains("realme")) {
            return COLOROS_AUTOSTART;
        } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
                || manufacturer.contains("poco")) {
            return XIAOMI_AUTOSTART;
        } else if (manufacturer.contains("oppo") || manufacturer.contains("oneplus")
                || manufacturer.contains("oplus")) {
            return COLOROS_AUTOSTART;
        } else if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return VIVO_AUTOSTART;
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            return HUAWEI_AUTOSTART;
        } else if (manufacturer.contains("asus")) {
            return ASUS_AUTOSTART;
        }
        return new String[0][];
    }

    private void openAutostartSettings() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        for (String[] candidate : candidatesFor(manufacturer)) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(candidate[0], candidate[1]));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                // Try the next known screen; OEMs rename these between versions.
            }
        }
        openAppInfoFallback();
    }

    private void openAppInfoFallback() {
        Toast.makeText(this, R.string.oem_fallback_toast, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
