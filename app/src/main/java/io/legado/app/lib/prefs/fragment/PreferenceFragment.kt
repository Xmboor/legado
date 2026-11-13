package io.legado.app.lib.prefs.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceGroup
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.lib.prefs.EditTextPreferenceDialog
import io.legado.app.lib.prefs.ListPreferenceDialog
import io.legado.app.lib.prefs.MultiSelectListPreferenceDialog
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyNavigationBarPadding

abstract class PreferenceFragment : PreferenceFragmentCompat() {

    private val dialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(Color.TRANSPARENT)
        listView.setBackgroundColor(Color.TRANSPARENT)
        listView.clipToPadding = false
        listView.applyNavigationBarPadding()
        listView.itemAnimator = null
        consumeActivityTargetKey()
    }

    protected fun consumeActivityTargetKey(
        mapTargetKey: (String) -> String = { it }
    ): Boolean {
        val rawTargetKey = activity?.intent?.getStringExtra("targetKey")?.trim().orEmpty()
        if (rawTargetKey.isBlank()) return false
        val targetKey = mapTargetKey(rawTargetKey)
        if (targetKey.isBlank()) return false
        val preference = findPreference<Preference>(targetKey) ?: return false
        listView.post {
            scrollPreferenceToTop(preference)
            highlightPreference(preference)
            activity?.intent?.removeExtra("targetKey")
        }
        return true
    }

    protected fun scrollPreferenceToTop(preference: Preference) {
        val adapter = listView.adapter as? PreferenceGroupAdapter
        if (adapter != null) {
            for (index in 0 until adapter.itemCount) {
                if (adapter.getItem(index) == preference) {
                    (listView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(index, 0)
                        ?: listView.scrollToPosition(index)
                    return
                }
            }
        }
        scrollToPreference(preference)
    }

    private fun highlightPreference(preference: Preference) {
        val adapter = listView.adapter as? PreferenceGroupAdapter ?: return
        val index = (0 until adapter.itemCount).firstOrNull { adapter.getItem(it) == preference }
            ?: return
        val radius = UiCorner.actionRadius(requireContext())
        val tint = ColorUtils.setAlphaComponent(accentColor, 70)
        listView.post {
            val holder = listView.findViewHolderForAdapterPosition(index) ?: return@post
            val itemView = holder.itemView
            val original = itemView.background
            val animator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 1100L
                addUpdateListener { anim ->
                    if (holder.bindingAdapterPosition != index) {
                        anim.cancel()
                        return@addUpdateListener
                    }
                    val alpha = anim.animatedValue as Float
                    itemView.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(ColorUtils.setAlphaComponent(tint, (alpha * 70).toInt()))
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (holder.bindingAdapterPosition == index) itemView.background = original
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        if (holder.bindingAdapterPosition == index) itemView.background = original
                    }
                })
            }
            animator.start()
        }
    }

    /**
     * 按标题/副标题过滤偏好项
     */
    fun filterPreferences(query: String?) {
        val keyword = query?.trim().orEmpty()
        val root = preferenceScreen ?: return
        if (keyword.isBlank()) {
            setPreferenceVisible(root)
            return
        }
        filterPreferenceGroup(root, keyword.lowercase())
    }

    private fun filterPreferenceGroup(group: PreferenceGroup, keyword: String): Boolean {
        var anyVisible = false
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            val visible = when (preference) {
                is PreferenceGroup -> filterPreferenceGroup(preference, keyword) || preference.matches(keyword)
                else -> preference.matches(keyword)
            }
            preference.isVisible = visible
            anyVisible = anyVisible || visible
        }
        group.isVisible = anyVisible || group == preferenceScreen
        return anyVisible
    }

    private fun setPreferenceVisible(group: PreferenceGroup) {
        group.isVisible = true
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            preference.isVisible = true
            if (preference is PreferenceGroup) {
                setPreferenceVisible(preference)
            }
        }
    }

    private fun Preference.matches(keyword: String): Boolean {
        val titleText = title?.toString().orEmpty().lowercase()
        val summaryText = summary?.toString().orEmpty().lowercase()
        val keyText = key?.lowercase().orEmpty()
        return titleText.contains(keyword)
            || summaryText.contains(keyword)
            || keyText.contains(keyword)
    }

    @SuppressLint("RestrictedApi")
    override fun onDisplayPreferenceDialog(preference: Preference) {

        var handled = false
        if (callbackFragment is OnPreferenceDisplayDialogCallback) {
            handled =
                (callbackFragment as OnPreferenceDisplayDialogCallback)
                    .onPreferenceDisplayDialog(this, preference)
        }
        if (!handled && activity is OnPreferenceDisplayDialogCallback) {
            handled = (activity as OnPreferenceDisplayDialogCallback)
                .onPreferenceDisplayDialog(this, preference)
        }

        if (handled) {
            return
        }

        // check if dialog is already showing
        if (parentFragmentManager.findFragmentByTag(dialogFragmentTag) != null) {
            return
        }

        val dialogFragment: DialogFragment = when (preference) {
            is EditTextPreference -> {
                EditTextPreferenceDialog.newInstance(preference.getKey())
            }
            is ListPreference -> {
                ListPreferenceDialog.newInstance(preference.getKey())
            }
            is MultiSelectListPreference -> {
                MultiSelectListPreferenceDialog.newInstance(preference.getKey())
            }
            else -> {
                throw IllegalArgumentException(
                    "Cannot display dialog for an unknown Preference type: "
                            + preference.javaClass.simpleName
                            + ". Make sure to implement onPreferenceDisplayDialog() to handle "
                            + "displaying a custom dialog for this Preference."
                )
            }
        }
        @Suppress("DEPRECATION")
        dialogFragment.setTargetFragment(this, 0)

        dialogFragment.show(parentFragmentManager, dialogFragmentTag)
    }

}
