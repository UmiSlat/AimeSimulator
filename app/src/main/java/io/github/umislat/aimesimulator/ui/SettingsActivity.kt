package io.github.umislat.aimesimulator.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.umislat.aimesimulator.R
import io.github.umislat.aimesimulator.ThemeSettings
import io.github.umislat.aimesimulator.data.CardStore
import io.github.umislat.aimesimulator.root.PmmManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var store: CardStore
    private lateinit var pmmSwitch: MaterialSwitch
    private lateinit var pmmStatus: TextView
    private lateinit var progress: LinearProgressIndicator
    private var lastPmmSnapshot: PmmManager.Snapshot? = null
    private var hasResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CardStore(this)
        setContentView(buildScreen())
        restorePmmSnapshot(savedInstanceState)?.let(::renderPmm) ?: refreshPmm()
    }

    override fun onResume() {
        super.onResume()
        if (hasResumed && ::pmmStatus.isInitialized) refreshPmm()
        hasResumed = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        lastPmmSnapshot?.let { snapshot ->
            outState.putString(STATE_PMM_STATUS, snapshot.status.name)
            outState.putString(STATE_PMM_DETAIL, snapshot.detail)
            outState.putBoolean(STATE_PMM_MODERN, snapshot.modernModule)
        }
        super.onSaveInstanceState(outState)
    }

    private fun buildScreen(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(MaterialToolbar(this).apply {
            title = getString(R.string.settings)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        })
        progress = LinearProgressIndicator(this).apply { isIndeterminate = true; hide() }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        val scroll = ScrollView(this)
        val content = verticalLayout(this)
        content.addView(sectionTitle(R.string.appearance))
        content.addView(TextView(this).apply {
            setText(R.string.theme_mode)
            textSize = 16f
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(buildThemeSelector())

        val dynamicAvailable = DynamicColors.isDynamicColorAvailable()
        content.addView(MaterialSwitch(this).apply {
            setText(R.string.dynamic_colors)
            isChecked = dynamicAvailable && ThemeSettings.dynamicColorsEnabled(this@SettingsActivity)
            isEnabled = dynamicAvailable
            setOnCheckedChangeListener { button, enabled ->
                if (button.isPressed && ThemeSettings.setDynamicColorsEnabled(
                        this@SettingsActivity,
                        enabled
                    )) {
                    recreate()
                }
            }
        })
        content.addView(TextView(this).apply {
            setText(
                if (dynamicAvailable) R.string.dynamic_colors_summary
                else R.string.dynamic_colors_unavailable
            )
            textSize = 13f
            alpha = 0.72f
            setPadding(0, 0, 0, dp(8))
        })

        content.addView(sectionTitle(R.string.privacy))
        content.addView(MaterialSwitch(this).apply {
            setText(R.string.show_idm)
            isChecked = store.showIdm()
            setOnCheckedChangeListener { _, value -> store.setShowIdm(value) }
        })

        content.addView(sectionTitle(R.string.pmm_patch))
        pmmSwitch = MaterialSwitch(this).apply {
            setText(R.string.enable_pmm_patch)
            isEnabled = false
            setOnCheckedChangeListener { _, enabled ->
                if (isPressed) changePmm(enabled)
            }
        }
        content.addView(pmmSwitch)
        pmmStatus = TextView(this).apply {
            setText(R.string.checking_status)
            textSize = 14f
            setPadding(0, dp(6), 0, dp(8))
        }
        content.addView(pmmStatus)
        content.addView(TextView(this).apply {
            setText(R.string.pmm_explanation)
            textSize = 13f
            alpha = 0.72f
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildThemeSelector(): MaterialButtonToggleGroup {
        val systemId = View.generateViewId()
        val lightId = View.generateViewId()
        val darkId = View.generateViewId()
        return MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            addView(themeButton(systemId, R.string.theme_follow_system))
            addView(themeButton(lightId, R.string.theme_light))
            addView(themeButton(darkId, R.string.theme_dark))
            check(
                when (ThemeSettings.mode(this@SettingsActivity)) {
                    ThemeSettings.Mode.SYSTEM -> systemId
                    ThemeSettings.Mode.LIGHT -> lightId
                    ThemeSettings.Mode.DARK -> darkId
                }
            )
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val mode = when (checkedId) {
                    lightId -> ThemeSettings.Mode.LIGHT
                    darkId -> ThemeSettings.Mode.DARK
                    else -> ThemeSettings.Mode.SYSTEM
                }
                if (mode != ThemeSettings.mode(this@SettingsActivity) &&
                    ThemeSettings.setMode(this@SettingsActivity, mode)) {
                    AppCompatDelegate.setDefaultNightMode(mode.nightMode)
                }
            }
        }
    }

    private fun themeButton(id: Int, text: Int): MaterialButton = MaterialButton(this).apply {
        this.id = id
        setText(text)
        isCheckable = true
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    private fun refreshPmm() = runPmm { PmmManager.inspect() }

    private fun changePmm(enabled: Boolean) = runPmm { PmmManager.setEnabled(enabled) }

    private fun runPmm(action: () -> PmmManager.Snapshot) {
        pmmSwitch.isEnabled = false
        progress.show()
        Thread {
            val snapshot = action()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) renderPmm(snapshot)
            }
        }.apply { name = "aimesim-pmm-state"; start() }
    }

    private fun renderPmm(snapshot: PmmManager.Snapshot) {
        lastPmmSnapshot = snapshot
        progress.hide()
        pmmSwitch.setOnCheckedChangeListener(null)
        pmmSwitch.isChecked = snapshot.status == PmmManager.Status.ACTIVE ||
            snapshot.status == PmmManager.Status.WAITING
        pmmSwitch.isEnabled = snapshot.status != PmmManager.Status.UNAVAILABLE
        pmmSwitch.setOnCheckedChangeListener { button, enabled ->
            if (button.isPressed) changePmm(enabled)
        }
        val mode = if (snapshot.modernModule) getString(R.string.pmm_mode_module)
            else getString(R.string.pmm_mode_framework)
        pmmStatus.text = getString(R.string.pmm_status, mode, snapshot.detail)
    }

    private fun restorePmmSnapshot(state: Bundle?): PmmManager.Snapshot? {
        val statusName = state?.getString(STATE_PMM_STATUS) ?: return null
        val status = runCatching { PmmManager.Status.valueOf(statusName) }.getOrNull() ?: return null
        val detail = state.getString(STATE_PMM_DETAIL) ?: return null
        return PmmManager.Snapshot(status, detail, state.getBoolean(STATE_PMM_MODERN))
    }

    private companion object {
        const val STATE_PMM_STATUS = "settings.pmm.status"
        const val STATE_PMM_DETAIL = "settings.pmm.detail"
        const val STATE_PMM_MODERN = "settings.pmm.modern"
    }
}
