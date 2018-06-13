package ch.deletescape.lawnchair

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import com.google.android.apps.nexuslauncher.NexusAppFilter

open class LawnchairAppFilter(context: Context) : NexusAppFilter(context) {

    private val hideList = HashSet<ComponentName>()

    init {
        hideList.add(ComponentName(context, LawnchairLauncher::class.java.name))
    }

    override fun shouldShowApp(componentName: ComponentName?, user: UserHandle?): Boolean {
        return !hideList.contains(componentName) && super.shouldShowApp(componentName, user)
    }
}