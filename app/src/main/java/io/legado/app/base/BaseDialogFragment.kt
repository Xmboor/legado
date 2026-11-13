package io.legado.app.base

import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.theme.applyUiBodyTypeface
import io.legado.app.lib.theme.dialogSurfaceBackground
import io.legado.app.utils.applyAdaptiveDim
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setBackgroundKeepPadding
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import splitties.init.appCtx
import kotlin.coroutines.CoroutineContext


abstract class BaseDialogFragment(
    @LayoutRes layoutID: Int,
    private val adaptationSoftKeyboard: Boolean = false
) : DialogFragment(layoutID) {

    private var onDismissListener: OnDismissListener? = null

    fun setOnDismissListener(onDismissListener: OnDismissListener?) {
        this.onDismissListener = onDismissListener
    }

    override fun onStart() {
        super.onStart()
        if (adaptationSoftKeyboard) {
            dialog?.window?.setBackgroundDrawableResource(R.color.transparent)
        } else if (AppConfig.isEInkMode) {
            dialog?.window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val attr = it.attributes
                attr.dimAmount = 0.0f
                attr.windowAnimations = 0
                it.attributes = attr
                it.decorView.setBackgroundKeepPadding(R.color.transparent)
            }
            // 修改gravity的时机一般在子类的onStart方法中, 因此需要在onStart之后执行.
            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    when (dialog?.window?.attributes?.gravity) {
                        Gravity.TOP -> view?.setBackgroundResource(R.drawable.bg_eink_border_bottom)
                        Gravity.BOTTOM -> view?.setBackgroundResource(R.drawable.bg_eink_border_top)
                        else -> {
                            val padding = 2.dpToPx();
                            view?.setPadding(padding, padding, padding, padding)
                            view?.setBackgroundResource(R.drawable.bg_eink_border_dialog)
                        }
                    }
                }
            })
        } else {
            dialog?.window?.setBackgroundDrawableResource(R.color.transparent)
            dialog?.applyAdaptiveDim()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            //不加这个android 5.0对话框顶部会有空白
            setStyle(STYLE_NO_TITLE, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.clipToOutline = true
        view.findViewById<View>(R.id.vw_bg)?.clipToOutline = true
        if (adaptationSoftKeyboard) {
            view.findViewById<View>(R.id.vw_bg)?.setOnClickListener(null)
            view.setOnClickListener { dismiss() }
        } else if (!AppConfig.isEInkMode) {
            view.background = requireContext().dialogSurfaceBackground
        }
        if (!AppConfig.isEInkMode) {
            view.applyUiBodyTypeface(requireContext())
        }
        onFragmentCreated(view, savedInstanceState)
        observeLiveBus()
    }

    abstract fun onFragmentCreated(view: View, savedInstanceState: Bundle?)

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            // 已在显示则不重复添加，避免 "Fragment already added" 异常
            if (isAdded) {
                return@runCatching
            }
            // 同步清理同 tag 的残留实例，消除旧写法异步 remove(this) 与本次 add 的竞争
            val stale = manager.findFragmentByTag(tag)
            if (stale != null && stale !== this) {
                manager.beginTransaction().remove(stale).commitNowAllowingStateLoss()
            }
            if (manager.isStateSaved) {
                // 刚从文件选择器/扫码页返回时状态可能已保存，
                // 用允许状态丢失的提交，避免 IllegalStateException 被静默吞掉导致"不弹框"
                manager.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(this, tag)
                    .commitAllowingStateLoss()
            } else {
                super.show(manager, tag)
            }
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
            appCtx.toastOnUi("显示对话框失败：${it.localizedMessage}")
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    fun <T> execute(
        scope: CoroutineScope = lifecycleScope,
        context: CoroutineContext = Dispatchers.IO,
        block: suspend CoroutineScope.() -> T
    ) = Coroutine.async(scope, context) { block() }

    open fun observeLiveBus() {
    }

    fun findParentTextInputLayout(view: View): TextInputLayout? {
        var parent = view.parent
        while (parent != null && parent !is TextInputLayout) {
            parent = parent.parent
        }
        return parent
    }
}
