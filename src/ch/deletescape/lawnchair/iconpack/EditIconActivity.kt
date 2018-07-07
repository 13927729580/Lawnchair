package ch.deletescape.lawnchair.iconpack

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.LauncherModel
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.LooperExecutor
import java.lang.ref.WeakReference

class EditIconActivity : SettingsBaseActivity() {

    private val originalIcon by lazy { findViewById<ImageView>(R.id.originalIcon) }
    private val divider by lazy { findViewById<View>(R.id.divider) }
    private val iconRecyclerView by lazy { findViewById<RecyclerView>(R.id.iconRecyclerView) }
    private val iconPackRecyclerView by lazy { findViewById<RecyclerView>(R.id.iconPackRecyclerView) }
    private val iconPackManager = IconPackManager.getInstance(this)
    private val component by lazy {
        if (intent.hasExtra(EXTRA_COMPONENT)) {
            ComponentKey(intent.getParcelableExtra<ComponentName>(EXTRA_COMPONENT), intent.getParcelableExtra(EXTRA_USER))
        } else null
    }
    private val iconPacks by lazy {
        listOf(IconPackInfo("")) + iconPackManager.getPackProviders()
                .map { IconPackInfo(it) }.sortedBy { it.title }
    }
    private val icons by lazy {
        component?.let { iconPacks.mapNotNull { it.getEntryForComponent(component!!) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_icon)

        title = intent.getStringExtra(EXTRA_TITLE)

        LooperExecutor(LauncherModel.getWorkerLooper()).execute {
            iconPacks
            runOnUiThread(::bindPacks)
            if (component != null) icons
            runOnUiThread(::bindIcons)
        }
    }

    private fun bindPacks() {
        originalIcon.setImageDrawable(LawnchairLauncher.currentEditIcon)
        originalIcon.setOnClickListener { onSelectIcon(null) }

        iconPackRecyclerView.adapter = IconPackAdapter()
        iconPackRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun bindIcons() {
        if (component != null) {
            iconRecyclerView.adapter = IconAdapter()
            iconRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        } else {
            divider.visibility = View.GONE
            iconRecyclerView.visibility = View.GONE
        }

        findViewById<View>(R.id.loadingProgressBar).visibility = View.GONE
    }

    fun onSelectIcon(entry: IconPack.Entry?) {
        val customEntry = entry?.toCustomEntry()
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ENTRY, customEntry?.toPackString()))
        finish()
    }

    fun onSelectIconPack(packageName: String) {
        startActivityForResult(IconPickerActivity.newIntent(this, packageName), CODE_PICK_ICON)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CODE_PICK_ICON && resultCode == Activity.RESULT_OK) {
            val entryString = data?.getStringExtra(EditIconActivity.EXTRA_ENTRY) ?: return
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ENTRY, entryString))
            finish()
        }
    }

    inner class IconAdapter : RecyclerView.Adapter<IconAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.icon_item, parent, false))
        }

        override fun getItemCount() = icons?.size ?: 0

        override fun onBindViewHolder(holder: Holder, position: Int) {
            icons?.get(position)?.let { holder.bind(it) }
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(entry: IconPack.Entry) {
                try {
                    itemView.visibility = View.VISIBLE
                    (itemView as ImageView).setImageDrawable(entry.drawable)
                } catch (e: Exception) {
                    itemView.visibility = View.GONE
                }
            }

            override fun onClick(v: View) {
                onSelectIcon(icons!![adapterPosition])
            }
        }
    }

    inner class IconPackAdapter : RecyclerView.Adapter<IconPackAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.icon_pack_item, parent, false))
        }

        override fun getItemCount() = iconPacks.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(iconPacks[position])
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            private val icon = itemView.findViewById<ImageView>(android.R.id.icon)
            private val title = itemView.findViewById<TextView>(android.R.id.title)

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(info: IconPackInfo) {
                icon.setImageDrawable(info.icon)
                title.text = info.title
            }

            override fun onClick(v: View) {
                onSelectIconPack(iconPacks[adapterPosition].packageName)
            }
        }
    }

    inner class IconPackInfo(val name: String) {

        val icon = getIconPack().displayIcon
        val title = getIconPack().displayName
        val packageName = getIconPack().packPackageName

        var packRef: WeakReference<IconPack>? = null

        private fun getIconPack(): IconPack {
            if (packRef?.get() == null) {
                packRef = WeakReference(iconPackManager.getIconPack(name, false, false))
            }
            return packRef!!.get()!!
        }

        fun getEntryForComponent(key: ComponentKey): IconPack.Entry? {
            return getIconPack().run {
                ensureInitialLoadComplete()
                getIconPack().getEntryForComponent(key)
            }
        }
    }

    companion object {

        const val CODE_PICK_ICON = 0
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COMPONENT = "component"
        const val EXTRA_USER = "user"

        fun newIntent(context: Context, title: String, componentKey: ComponentKey? = null): Intent {
            return Intent(context, EditIconActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                componentKey?.run {
                    putExtra(EXTRA_COMPONENT, componentName)
                    putExtra(EXTRA_USER, user)
                }
            }
        }
    }
}
