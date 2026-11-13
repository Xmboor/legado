package io.legado.app.ui.rss.source.manage

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ItemRssSourceBinding
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.gone
import io.legado.app.utils.showPopupMenu
import io.legado.app.utils.visible
import java.util.Collections


class RssSourceAdapter(
    context: Context,
    val callBack: CallBack,
    private val recyclerView: RecyclerView
) : RecyclerAdapter<RssSource, ItemRssSourceBinding>(context),
    ItemTouchCallback.Callback {

    private val selected = linkedSetOf<RssSource>()
    private val handler = buildMainHandler()
    var showSourceHost = false

    val selection: List<RssSource>
        get() {
            return getItems().filter {
                selected.contains(it)
            }
        }

    val diffItemCallback = object : DiffUtil.ItemCallback<RssSource>() {

        override fun areItemsTheSame(oldItem: RssSource, newItem: RssSource): Boolean {
            return oldItem.sourceUrl == newItem.sourceUrl
        }

        override fun areContentsTheSame(oldItem: RssSource, newItem: RssSource): Boolean {
            return oldItem.sourceName == newItem.sourceName
                    && oldItem.sourceGroup == newItem.sourceGroup
                    && oldItem.enabled == newItem.enabled
        }

        override fun getChangePayload(oldItem: RssSource, newItem: RssSource): Any? {
            val payload = Bundle()
            if (oldItem.sourceName != newItem.sourceName
                || oldItem.sourceGroup != newItem.sourceGroup
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.enabled != newItem.enabled) {
                payload.putBoolean("enabled", newItem.enabled)
            }
            if (payload.isEmpty) {
                return null
            }
            return payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemRssSourceBinding {
        return ItemRssSourceBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemRssSourceBinding,
        item: RssSource,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                tvName.text = item.getDisplayNameGroup()
                swtEnabled.isChecked = item.enabled
                cbSource.isChecked = selected.contains(item)
                upCardBackground(binding, item)
                upSourceHost(binding, holder.layoutPosition)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "upName" -> tvName.text = item.getDisplayNameGroup()
                            "enabled" -> {
                                swtEnabled.isChecked = bundle.getBoolean("enabled")
                                upCardBackground(binding, item)
                            }
                            "selected" -> {
                                cbSource.isChecked = selected.contains(item)
                                upCardBackground(binding, item)
                            }
                            "upSourceHost" -> upSourceHost(binding, holder.layoutPosition)
                        }
                    }
                }
            }
        }
    }

    private fun upCardBackground(binding: ItemRssSourceBinding, item: RssSource) {
        val density = context.resources.displayMetrics.density
        val isSelected = selected.contains(item)
        val strokeColor = if (item.enabled) context.primaryColor else context.secondaryTextColor
        val alphaScale = UiCorner.layoutAlpha()
        val fillColor = when {
            isSelected -> ColorUtils.setAlphaComponent(context.primaryColor, (60 * alphaScale).toInt())
            item.enabled -> ColorUtils.setAlphaComponent(context.primaryColor, (20 * alphaScale).toInt())
            else -> Color.TRANSPARENT
        }
        binding.root.background = GradientDrawable().apply {
            cornerRadius = UiCorner.scaledDp(12f)
            setColor(fillColor)
            setStroke(density.toInt().coerceAtLeast(1), strokeColor)
        }
    }

    fun toggleSelection(item: RssSource) {
        if (selected.contains(item)) {
            selected.remove(item)
        } else {
            selected.add(item)
        }
        val position = getItems().indexOfFirst { it.sourceUrl == item.sourceUrl }
        if (position >= 0) {
            notifyItemChanged(position, bundleOf(Pair("selected", null)))
        }
        callBack.upCountView()
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemRssSourceBinding) {
        binding.apply {
            swtEnabled.setOnUserCheckedChangeListener { checked ->
                getItem(holder.layoutPosition)?.let {
                    it.enabled = checked
                    callBack.update(it)
                }
            }
            cbSource.setOnUserCheckedChangeListener { checked ->
                getItem(holder.layoutPosition)?.let {
                    if (checked) {
                        selected.add(it)
                    } else {
                        selected.remove(it)
                    }
                    callBack.upCountView()
                }
            }
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.edit(it)
                }
            }
            ivMenuMore.setOnClickListener {
                showMenu(ivMenuMore, holder.layoutPosition)
            }
        }
    }

    override fun onCurrentListChanged() {
        callBack.upCountView()
        recyclerView.doOnLayout {
            handler.post {
                notifyItemRangeChanged(0, itemCount, bundleOf("upSourceHost" to null))
            }
        }
    }

    fun selectAll() {
        getItems().forEach {
            selected.add(it)
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun revertSelection() {
        getItems().forEach {
            if (selected.contains(it)) {
                selected.remove(it)
            } else {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun checkSelectedInterval() {
        val selectedPosition = linkedSetOf<Int>()
        getItems().forEachIndexed { index, it ->
            if (selected.contains(it)) {
                selectedPosition.add(index)
            }
        }
        val minPosition = Collections.min(selectedPosition)
        val maxPosition = Collections.max(selectedPosition)
        val itemCount = maxPosition - minPosition + 1
        for (i in minPosition..maxPosition) {
            getItem(i)?.let {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(minPosition, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    private fun showMenu(view: View, position: Int) {
        val source = getItem(position) ?: return
        view.showPopupMenu(
            R.menu.rss_source_item,
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_select -> toggleSelection(source)
                R.id.menu_top -> callBack.toTop(source)
                R.id.menu_bottom -> callBack.toBottom(source)
                R.id.menu_del -> {
                    callBack.del(source)
                    selected.remove(source)
                }
            }
            true
        }
    }

    private fun upSourceHost(binding: ItemRssSourceBinding, position: Int) = binding.run {
        if (showSourceHost && isItemHeader(position)) {
            tvHostText.text = getHeaderText(position)
            tvHostText.visible()
        } else {
            tvHostText.gone()
        }
    }

    fun getHeaderText(position: Int): String {
        val source = getItem(position)!!
        return callBack.getSourceHost(source.sourceUrl)
    }

    fun isItemHeader(position: Int): Boolean {
        if (position == 0) return true
        val lastHost = getHeaderText(position - 1)
        val curHost = getHeaderText(position)
        return lastHost != curHost
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            if (srcItem.customOrder == targetItem.customOrder) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.customOrder
                srcItem.customOrder = targetItem.customOrder
                targetItem.customOrder = srcOrder
                movedItems.add(srcItem)
                movedItems.add(targetItem)
            }
        }
        swapItem(srcPosition, targetPosition)
        return true
    }

    private val movedItems = hashSetOf<RssSource>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<RssSource>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<RssSource> {
                return selected
            }

            override fun getItemId(position: Int): RssSource {
                return getItem(position)!!
            }

            override fun updateSelectState(position: Int, isSelected: Boolean): Boolean {
                getItem(position)?.let {
                    if (isSelected) {
                        selected.add(it)
                    } else {
                        selected.remove(it)
                    }
                    notifyItemChanged(position, bundleOf(Pair("selected", null)))
                    callBack.upCountView()
                    return true
                }
                return false
            }
        }

    interface CallBack {
        fun del(source: RssSource)
        fun edit(source: RssSource)
        fun update(vararg source: RssSource)
        fun toTop(source: RssSource)
        fun toBottom(source: RssSource)
        fun upOrder()
        fun upCountView()
        fun getSourceHost(origin: String): String
    }
}
