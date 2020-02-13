package soko.ekibun.bangumi.plugins

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.Keep
import androidx.appcompat.view.ContextThemeWrapper
import soko.ekibun.bangumi.plugins.main.MainActivityPlugin
import soko.ekibun.bangumi.plugins.service.DownloadService
import soko.ekibun.bangumi.plugins.subject.SubjectActivityPlugin
import soko.ekibun.bangumi.plugins.util.ReflectUtil

@Keep
object Plugin {
    private val pluginList = mapOf(
        "soko.ekibun.bangumi.ui.subject.SubjectActivity" to SubjectActivityPlugin(),
        "soko.ekibun.bangumi.ui.main.MainActivity" to MainActivityPlugin()
    )

    private var downloadService: DownloadService? = null

    @Keep
    fun setUpPlugins(activity: Activity, context: Context) {
        Log.v("plugin", activity.javaClass.name)
        try {
            val themeContext = object : ContextThemeWrapper(context, R.style.AppTheme) {
                override fun getApplicationContext(): Context {
                    return context
                }

                override fun getSystemService(name: String): Any? {
                    return if (name == Context.WINDOW_SERVICE) activity.getSystemService(name)
                    else super.getSystemService(name)
                }
            }
            themeContext.applyOverrideConfiguration(activity.resources.configuration)
            if (downloadService == null) {
                downloadService = DownloadService(activity.applicationContext, themeContext)
                ReflectUtil.proxyObject(activity.applicationContext, IApplication::class.java)!!.remoteAction =
                    { intent, flags, startId ->
                        downloadService?.onStartCommand(intent, flags, startId)
                    }
            }
            pluginList[activity.javaClass.name]?.setUpPlugins(activity, themeContext)
        } catch (e: Exception) {
            Log.e("plugin", Log.getStackTraceString(e))
        }
    }

    interface IApplication {
        var remoteAction: (intent: Intent?, flags: Int, startId: Int) -> Unit
    }
}
