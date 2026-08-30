package com.example.safetrack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Helpers for opening the OEM-specific "auto-start" / "background app management"
 * settings screen. Required by MIUI (Xiaomi), ColorOS (Oppo), FuntouchOS (Vivo),
 * EMUI (Huawei), OxygenOS (OnePlus), and others - without this grant, the OS
 * aggressively kills the persistent sync service within minutes.
 *
 * Usage:
 *   AutoStartUtils.getAutoStartIntent(context)?.let { startActivity(it) }
 *      ?: startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
 *          data = Uri.parse("package:${packageName}")
 *      })
 */
object AutoStartUtils {

    private const val TAG = "AutoStartUtils"

    /** Known manufacturer package names / activities for the auto-start screen. */
    private val AUTO_START_INTENTS = listOf(
        // Xiaomi (MIUI) - Security Center
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        },
        // Oppo (ColorOS)
        Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        },
        Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )
        },
        Intent().apply {
            component = ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        },
        // Vivo (FuntouchOS / OriginOS)
        Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
        },
        Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        },
        // Huawei (EMUI / HarmonyOS)
        Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        },
        Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        },
        // OnePlus (OxygenOS)
        Intent().apply {
            component = ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
        },
        // Letv
        Intent().apply {
            component = ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"
            )
        },
        // Asus
        Intent().apply {
            component = ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.entry.FunctionActivity"
            )
        }
    )

    /**
     * Returns an Intent that can be started to open the OEM auto-start screen.
     * Iterates the known list, returning the first one whose target package is installed.
     * Returns null if no OEM intent matches (caller should fall back to default settings).
     */
    fun getAutoStartIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (intent in AUTO_START_INTENTS) {
            try {
                val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo != null) {
                    Log.d(TAG, "Found auto-start activity: ${intent.component}")
                    return Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Auto-start intent not resolvable: ${intent.component} - ${e.message}")
            }
        }
        return null
    }

    /**
     * Returns the device manufacturer in lowercase, useful for branch logic.
     */
    fun getManufacturer(): String = Build.MANUFACTURER.lowercase()

    /**
     * True when the running device is one of the well-known restrictive OEMs.
     * Used to decide whether to even show the auto-start prompt.
     */
    fun isRestrictiveManufacturer(): Boolean {
        return when (getManufacturer()) {
            "xiaomi", "redmi", "poco",
            "oppo", "realme", "oneplus",
            "vivo", "iqoo",
            "huawei", "honor",
            "letv", "asus",
            "samsung" -> true  // Samsung's "deep sleep" is also restrictive on older models
            else -> false
        }
    }
}
