package ch.deletescape.lawnchair.iconpack

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import ch.deletescape.lawnchair.iconpack.EditIconActivity.Companion.EXTRA_ENTRY
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R

class IconPickerActivity : SettingsBaseActivity(), View.OnLayoutChangeListener {

    private val iconPackManager = IconPackManager.getInstance(this)
    private val iconGrid by lazy { findViewById<RecyclerView>(R.id.iconGrid) }
    private val iconPack by lazy { iconPackManager.getIconPack(intent.getStringExtra(EXTRA_ICON_PACK), false) }
    private val icons = ArrayList<CachedIconEntry>()
    private val adapter = IconGridAdapter()
    private val layoutManager = GridLayoutManager(this, 1)
    private var canceled = false

    private var dynamicPadding = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_picker)

        title = iconPack.displayName

        getContentFrame().addOnLayoutChangeListener(this)

        supportActionBar?.run {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        LoadIconTask().execute(iconPack)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        canceled = true
    }

    override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        getContentFrame().removeOnLayoutChangeListener(this)
        calculateDynamicGrid(iconGrid.width)
        iconGrid.adapter = adapter
        iconGrid.layoutManager = layoutManager
    }

    private fun calculateDynamicGrid(width: Int) {
        val iconPadding = resources.getDimensionPixelSize(R.dimen.icon_preview_padding)
        val iconSize = resources.getDimensionPixelSize(R.dimen.icon_preview_size)
        val iconSizeWithPadding = iconSize + iconPadding + iconPadding
        val maxWidth = width - iconPadding - iconPadding
        val columnCount = maxWidth / iconSizeWithPadding
        val usedWidth = iconSize * columnCount
        dynamicPadding = (width - usedWidth) / (columnCount + 1) / 2
        layoutManager.spanCount = columnCount
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = getItemSpan(position)
        }
        iconGrid.setPadding(dynamicPadding, iconPadding, dynamicPadding, iconPadding)
    }

    private fun getItemSpan(position: Int)
            = if (adapter.getItemViewType(position) == adapter.TYPE_ITEM) 1 else layoutManager.spanCount

    fun onSelectIcon(entry: IconPack.Entry) {
        val customEntry = entry.toCustomEntry()
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ENTRY, customEntry.toString()))
        finish()
    }

    inner class IconGridAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        val TYPE_LOADING = 0
        val TYPE_ITEM = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            return if (getItemViewType(0) == TYPE_ITEM) {
                IconHolder(layoutInflater.inflate(R.layout.icon_item, parent, false))
            } else {
                LoadingHolder(layoutInflater.inflate(R.layout.adapter_loading, parent, false))
            }
        }

        override fun getItemCount() = Math.max(icons.size, 1)

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is IconHolder) {
                holder.bind(icons[position])
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (icons.size != 0)
                TYPE_ITEM
            else
                TYPE_LOADING
        }

        inner class IconHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            init {
                itemView.setOnClickListener(this)
                (itemView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    leftMargin = dynamicPadding
                    rightMargin = dynamicPadding
                }
            }

            fun bind(cachedEntry: CachedIconEntry) {
                (itemView as ImageView).setImageDrawable(cachedEntry.drawable)
            }

            override fun onClick(v: View) {
                onSelectIcon(icons[adapterPosition].entry)
            }
        }

        inner class LoadingHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }

    inner class LoadIconTask : AsyncTask<IconPack, Void, List<CachedIconEntry>>() {

        override fun doInBackground(vararg params: IconPack): List<CachedIconEntry> {
            val iconPack = params[0]
            iconPack.ensureInitialLoadComplete()

            val icons = ArrayList<CachedIconEntry>()
            iconPack.entries.forEach { if (!canceled) icons.add(CachedIconEntry(it, it.drawable)) }
            return icons
        }

        override fun onPostExecute(result: List<CachedIconEntry>) {
            if (canceled) return
            icons.addAll(result.sortedBy { it.entry.displayName })
            adapter.notifyDataSetChanged()
        }
    }

    class CachedIconEntry(val entry: IconPack.Entry, val drawable: Drawable)

    companion object {

        private const val EXTRA_ICON_PACK = "pack"

        fun newIntent(context: Context, packageName: String): Intent {
            return Intent(context, IconPickerActivity::class.java).apply {
                putExtra(EXTRA_ICON_PACK, packageName)
            }
        }
    }
}
