package dev.migs.scan.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class ShareTarget(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Returns every installed app that declares it can handle ACTION_SEND with
 * either application/pdf, image/jpeg or image/png — i.e. apps that will accept
 * a scan via a preset. De-duped by package name, sorted by label.
 */
fun loadShareTargets(context: Context): List<ShareTarget> {
    val pm = context.packageManager
    val mimes = listOf("application/pdf", "image/jpeg", "image/png")
    val byPackage = mutableMapOf<String, ShareTarget>()
    mimes.forEach { mime ->
        val probe = Intent(Intent.ACTION_SEND).apply { type = mime }
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(probe, 0)
        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (byPackage.containsKey(pkg)) continue
            byPackage[pkg] = ShareTarget(
                packageName = pkg,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
            )
        }
    }
    return byPackage.values.sortedBy { it.label.lowercase() }
}
