package io.legado.app.ui.config

import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityConfigBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.read.config.MoreConfigDialog
import io.legado.app.ui.book.read.config.ReadAloudConfigDialog
import io.legado.app.ui.widget.glass.GlassBarHelper
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ConfigActivity : VMBaseActivity<ActivityConfigBinding, ConfigViewModel>() {

    override val binding by viewBinding(ActivityConfigBinding::inflate)
    override val viewModel by viewModels<ConfigViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        applyHeaderColors()
        setupGlass()
        when (val configTag = intent.getStringExtra("configTag")) {
            ConfigTag.OTHER_CONFIG -> replaceFragment(configTag, OtherConfigFragment::class.java)
            ConfigTag.THEME_CONFIG -> replaceFragment(configTag, ThemeConfigFragment::class.java)
            ConfigTag.BACKUP_CONFIG -> replaceFragment(configTag, BackupConfigFragment::class.java)
            ConfigTag.AI_CONFIG -> replaceFragment(configTag, AiConfigFragment::class.java)
            ConfigTag.COVER_CONFIG -> replaceFragment(configTag, CoverConfigFragment::class.java)
            ConfigTag.WELCOME_CONFIG -> replaceFragment(configTag, WelcomeConfigFragment::class.java)
            ConfigTag.DISCOVERY_SUBSCRIPTION_CONFIG ->
                replaceFragment(configTag, DiscoverySubscriptionConfigFragment::class.java)
            ConfigTag.DISCOVERY_CONFIG -> replaceFragment(configTag, DiscoveryConfigFragment::class.java)
            ConfigTag.SUBSCRIPTION_CONFIG -> replaceFragment(configTag, SubscriptionConfigFragment::class.java)
            ConfigTag.READ_CONFIG -> {
                setTitle(R.string.read_config)
                replaceFragment(configTag, MoreConfigDialog.ReadPreferenceFragment::class.java)
            }
            ConfigTag.ALOUD_CONFIG -> {
                setTitle(R.string.aloud_config)
                replaceFragment(configTag, ReadAloudConfigDialog.ReadAloudPreferenceFragment::class.java)
            }
            else -> finish()
        }
    }

    override fun setTitle(resId: Int) {
        super.setTitle(resId)
        binding.titleBar.setTitle(resId)
        applyHeaderColors()
    }

    override fun onResume() {
        super.onResume()
        applyHeaderColors()
        setupGlass()
    }

    fun <T : Fragment> replaceFragment(configTag: String, fragmentClass: Class<T>) {
        intent.putExtra("configTag", configTag)
        val configFragment = supportFragmentManager.findFragmentByTag(configTag)
            ?: fragmentClass.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.configFrameLayout, configFragment, configTag)
            .commit()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NAVIGATION_BAR_CHANGED) { setupGlass() }
    }

    private fun setupGlass() {
        binding.run {
            // 修复设置页空白：此 fork 的液态玻璃会把 configFrameLayout（Fragment 内容容器）
            // 作为 RenderEffect 渲染源，在部分机型/系统版本上导致内容区绘制为空白。
            // 设置页统一改用实色标题栏，彻底规避该问题（功能不受影响）。
            titleBarGlassView.visibility = android.view.View.GONE
            titleBarShellOverlay.visibility = android.view.View.GONE
            titleBar.setBackgroundColor(primaryColor)
        }
    }

    private fun applyHeaderColors() {
        binding.titleBar.setTextColor(primaryTextColor)
        binding.titleBar.setColorFilter(primaryTextColor)
    }

}
