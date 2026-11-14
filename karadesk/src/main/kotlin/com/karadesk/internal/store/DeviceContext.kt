package com.karadesk.internal.store

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.karadesk.BuildConfig
import com.karadesk.internal.net.DeviceInfo
import java.util.Locale
import java.util.TimeZone

internal object DeviceContext {
    fun current(context: Context): DeviceInfo = DeviceInfo(
        os = "Android",
        osVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
        deviceModel = model(),
        appVersion = appVersion(context),
        sdkVersion = BuildConfig.SDK_VERSION,
        language = Locale.getDefault().toLanguageTag(),
        timezone = TimeZone.getDefault().id,
    )

    fun userAgent(context: Context): String {
        val label = runCatching {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "App"
        val version = appVersion(context) ?: "unknown"

        return "KaraDesk-Android/${BuildConfig.SDK_VERSION} ($label $version; Android ${Build.VERSION.RELEASE}; ${model()})"
            .filter { it.code in 0x20..0x7e }
    }

    fun model(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()

        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }

    @Suppress("DEPRECATION")
    fun appVersion(context: Context): String? = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val name = info.versionName
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()

        when {
            name != null && name != code -> "$name ($code)"
            name != null -> name
            else -> code
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
