package org.levimc.launcher.core.minecraft

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.levimc.launcher.core.crash.CrashReporter
import org.levimc.launcher.core.news.NewsNotificationHelper
import org.levimc.launcher.settings.FeatureSettings
import org.levimc.launcher.ui.dialogs.LogcatOverlayManager

class LauncherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        try {
            FeatureSettings.init(applicationContext)
            FeatureSettings.getInstance()
        } catch (error: Throwable) {
            Log.e("LauncherApplication", "Feature settings init failed", error)
        }
        try {
            CrashReporter.init(this)
        } catch (error: Throwable) {
            Log.e("LauncherApplication", "Crash reporter init failed", error)
        }
        val processName = Application.getProcessName()
        if (processName.endsWith(":crash")) return

        try {
            NewsNotificationHelper.initialize(this)
        } catch (error: Throwable) {
            Log.e("LauncherApplication", "News notifications init failed", error)
        }
        try {
            LogcatOverlayManager.init(this)
        } catch (error: Throwable) {
            Log.e("LauncherApplication", "Logcat overlay init failed", error)
        }

        try {
            preferences = PreferenceManager.getDefaultSharedPreferences(this)
        } catch (error: Throwable) {
            Log.e("LauncherApplication", "Preferences init failed", error)
        }
    }

    companion object {
        @JvmStatic
        lateinit var context: Context
            private set

        @JvmStatic
        lateinit var preferences: SharedPreferences
            private set
    }
}
