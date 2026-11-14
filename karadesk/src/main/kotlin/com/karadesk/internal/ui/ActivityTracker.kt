package com.karadesk.internal.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

internal object ActivityTracker : Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null
    private var installed = false

    var onIntent: ((Activity, Intent?) -> Unit)? = null

    val current: Activity?
        get() = resumed?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    val isForeground: Boolean
        get() = current != null

    fun install(application: Application) {
        if (installed) {
            return
        }

        installed = true
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is ComponentActivity && activity !is MessengerActivity) {
            activity.addOnNewIntentListener { intent -> onIntent?.invoke(activity, intent) }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)

        if (activity !is MessengerActivity) {
            onIntent?.invoke(activity, activity.intent)
            InAppMessages.attach(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) {
            resumed = null
        }

        InAppMessages.detach(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
