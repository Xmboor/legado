package io.legado.app.ui.config

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityConfigBinding
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
            GlassBarHelper.bind(
                titleBarGlassView, titleBarShellOverlay, titleBar,
                configFrameLayout, 0f, isTopBar = true
            )
            titleBarContainer.doOnLayout {
                configFrameLayout.setPadding(0, titleBarContainer.height, 0, 0)
            }
        }
    }

    private fun applyHeaderColors() {
        binding.titleBar.setTextColor(primaryTextColor)
        binding.titleBar.setColorFilter(primaryTextColor)
    }

}
