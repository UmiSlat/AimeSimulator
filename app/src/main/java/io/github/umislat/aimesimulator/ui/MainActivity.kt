package io.github.umislat.aimesimulator.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.umislat.aimesimulator.R
import io.github.umislat.aimesimulator.ThemeSettings
import io.github.umislat.aimesimulator.data.CardProfile
import io.github.umislat.aimesimulator.data.CardStore
import io.github.umislat.aimesimulator.nfc.DefaultNfcAppChecker
import io.github.umislat.aimesimulator.nfc.HceSession
import io.github.umislat.aimesimulator.root.PmmManager

class MainActivity : AppCompatActivity() {
    private lateinit var store: CardStore
    private lateinit var session: HceSession
    private lateinit var toolbar: MaterialToolbar
    private lateinit var contentHost: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView

    private var selectedTab = TAB_CARDS
    private var foreground = false
    private var hasResumed = false
    private var defaultNfcAppChecked = false
    private var activationRetry: Runnable? = null
    private var hceStatusText: CharSequence = ""
    private var cardPageStatusView: TextView? = null
    private var statusPageStatusView: TextView? = null

    private var pmmSnapshot: PmmManager.Snapshot? = null
    private var pmmSwitch: MaterialSwitch? = null
    private var pmmStatus: TextView? = null
    private var pmmProgress: LinearProgressIndicator? = null

    private val reader = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val idm = data.getStringExtra(CardReaderActivity.EXTRA_IDM)
        val accessCode = data.getStringExtra(CardReaderActivity.EXTRA_ACCESS_CODE)
        if (idm == null && accessCode == null) return@registerForActivityResult
        showEditor(
            capturedIdm = idm,
            capturedSpad0 = data.getStringExtra(CardReaderActivity.EXTRA_SPAD0),
            capturedIdBlock = data.getStringExtra(CardReaderActivity.EXTRA_ID_BLOCK),
            capturedAccessCode = accessCode
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CardStore(this)
        session = HceSession(this)
        selectedTab = savedInstanceState?.getInt(STATE_SELECTED_TAB, TAB_CARDS) ?: TAB_CARDS
        pmmSnapshot = restorePmmSnapshot(savedInstanceState)
        hceStatusText = if (store.selectedProfile() == null) {
            getString(R.string.select_or_add_card)
        } else {
            getString(R.string.checking_hce_status)
        }
        setContentView(buildShell())
        bottomNavigation.menu.findItem(selectedTab).isChecked = true
        showPage(selectedTab, refreshPmm = selectedTab == TAB_STATUS && pmmSnapshot == null)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        activateSelected()
        if (hasResumed && selectedTab == TAB_STATUS) refreshPmm()
        hasResumed = true
        checkDefaultNfcApp()
    }

    override fun onPause() {
        foreground = false
        cancelActivationRetry()
        session.deactivate(this)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
        pmmSnapshot?.let { snapshot ->
            outState.putString(STATE_PMM_STATUS, snapshot.status.name)
            outState.putString(STATE_PMM_DETAIL, snapshot.detail)
            outState.putBoolean(STATE_PMM_MODERN, snapshot.modernModule)
        }
        super.onSaveInstanceState(outState)
    }

    private fun buildShell(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        toolbar = MaterialToolbar(this@MainActivity).apply {
            title = getString(R.string.app_name)
            setTitleTextAppearance(this@MainActivity, R.style.TextAppearance_AimeSimulator_Toolbar)
        }
        addView(toolbar)

        contentHost = FrameLayout(this@MainActivity)
        addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomNavigation = BottomNavigationView(this@MainActivity).apply {
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            menu.add(Menu.NONE, TAB_CARDS, Menu.NONE, R.string.nav_cards)
                .setIcon(R.drawable.ic_nav_cards)
            menu.add(Menu.NONE, TAB_STATUS, Menu.NONE, R.string.nav_status)
                .setIcon(R.drawable.ic_nav_status)
            menu.add(Menu.NONE, TAB_SETTINGS, Menu.NONE, R.string.settings)
                .setIcon(R.drawable.ic_nav_settings)
            setOnItemSelectedListener { item ->
                showPage(item.itemId, refreshPmm = item.itemId == TAB_STATUS)
                true
            }
        }
        addView(bottomNavigation)
    }

    private fun showPage(tab: Int, refreshPmm: Boolean) {
        selectedTab = tab
        cardPageStatusView = null
        statusPageStatusView = null
        pmmSwitch = null
        pmmStatus = null
        pmmProgress = null
        toolbar.title = getString(
            when (tab) {
                TAB_STATUS -> R.string.nav_status
                TAB_SETTINGS -> R.string.settings
                else -> R.string.app_name
            }
        )
        contentHost.removeAllViews()
        contentHost.addView(
            when (tab) {
                TAB_STATUS -> buildStatusPage()
                TAB_SETTINGS -> buildSettingsPage()
                else -> buildCardsPage()
            },
            FrameLayout.LayoutParams(-1, -1)
        )
        updateHceStatusViews()
        if (tab == TAB_STATUS) {
            pmmSnapshot?.let(::renderPmm)
            if (refreshPmm) refreshPmm()
        }
    }

    private fun buildCardsPage(): View {
        val page = FrameLayout(this)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = verticalLayout(this)
        content.addView(statusSummaryCard())

        val profiles = store.profiles()
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(sectionTitle(R.string.card_profiles), LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply {
                text = resources.getQuantityString(
                    R.plurals.card_profile_count,
                    profiles.size,
                    profiles.size
                )
                textSize = 13f
                alpha = 0.68f
            })
        })
        if (profiles.isEmpty()) {
            content.addView(TextView(this).apply {
                setText(R.string.no_cards)
                gravity = Gravity.CENTER
                alpha = 0.72f
                setPadding(dp(12), dp(48), dp(12), dp(96))
            })
        } else {
            val selectedId = store.selectedProfile()?.profileId
            profiles.forEach { profile ->
                content.addView(profileCard(profile, profile.profileId == selectedId))
            }
            content.setPadding(dp(20), dp(8), dp(20), dp(104))
        }
        scroll.addView(content)
        page.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        page.addView(ExtendedFloatingActionButton(this).apply {
            setText(R.string.add_card_action)
            setIconResource(android.R.drawable.ic_input_add)
            setOnClickListener { showAddOptions() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(20)
            bottomMargin = dp(18)
        })
        return page
    }

    private fun statusSummaryCard(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(22).toFloat()
        strokeWidth = dp(1)
        isClickable = true
        isFocusable = true
        setOnClickListener { bottomNavigation.selectedItemId = TAB_STATUS }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(TextView(this@MainActivity).apply {
                setText(R.string.simulation_status)
                textSize = 13f
                alpha = 0.7f
            })
            cardPageStatusView = TextView(this@MainActivity).apply {
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(6), 0, 0)
            }
            addView(cardPageStatusView)
            store.selectedProfile()?.let { profile ->
                addView(TextView(this@MainActivity).apply {
                    text = getString(
                        if (store.compatibilityMode()) R.string.status_profile_compatibility
                        else R.string.status_profile_normal,
                        profile.label
                    )
                    textSize = 13f
                    alpha = 0.72f
                    setPadding(0, dp(8), 0, 0)
                })
            }
        })
    }

    private fun profileCard(profile: CardProfile, selected: Boolean): MaterialCardView =
        MaterialCardView(this).apply {
            strokeWidth = if (selected) dp(2) else dp(1)
            strokeColor = MaterialColors.getColor(
                this,
                if (selected) com.google.android.material.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOutlineVariant
            )
            radius = dp(20).toFloat()
            setOnClickListener {
                store.select(profile.profileId)
                activateSelected()
                showPage(TAB_CARDS, refreshPmm = false)
            }
            layoutParams = ViewGroup.MarginLayoutParams(-1, -2).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(15), dp(14), dp(12))
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@MainActivity).apply {
                        text = profile.label
                        textSize = 18f
                        setTypeface(typeface, Typeface.BOLD)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    if (selected) addView(Chip(this@MainActivity).apply {
                        setText(R.string.selected_profile)
                        isClickable = false
                        isCheckable = false
                        setEnsureMinTouchTargetSize(false)
                        chipMinHeight = dp(32).toFloat()
                        textSize = 12f
                    })
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (store.showIdm()) getString(R.string.idm_value, profile.idm)
                    else getString(R.string.idm_hidden)
                    textSize = 14f
                    alpha = 0.76f
                    setPadding(0, dp(4), 0, if (profile.accessCode == null) dp(6) else 0)
                })
                profile.accessCode?.let {
                    addView(TextView(this@MainActivity).apply {
                        text = getString(
                            R.string.access_code_value,
                            if (store.showAccessCode()) profile.formattedAccessCode()
                            else getString(R.string.access_code_hidden)
                        )
                        textSize = 14f
                        alpha = 0.76f
                        setPadding(0, dp(3), 0, dp(6))
                    })
                }
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.END
                    addView(compactTextButton(R.string.edit).apply {
                        setOnClickListener { showEditor(profile) }
                    })
                    addView(compactTextButton(R.string.delete).apply {
                        setOnClickListener { confirmDelete(profile) }
                    })
                })
            })
        }

    private fun buildStatusPage(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = verticalLayout(this)
        content.addView(sectionTitle(R.string.simulation_status))
        content.addView(MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            strokeWidth = dp(1)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                statusPageStatusView = TextView(this@MainActivity).apply {
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                }
                addView(statusPageStatusView)
                addView(TextView(this@MainActivity).apply {
                    setText(R.string.hce_status_summary)
                    textSize = 13f
                    alpha = 0.7f
                    setPadding(0, dp(6), 0, 0)
                })
            })
        })
        content.addView(MaterialSwitch(this).apply {
            setText(R.string.compatibility_mode)
            isChecked = store.compatibilityMode()
            setOnCheckedChangeListener { button, enabled ->
                if (!button.isPressed) return@setOnCheckedChangeListener
                store.setCompatibilityMode(enabled)
                activateSelected()
                showPage(TAB_STATUS, refreshPmm = false)
            }
        })
        content.addView(TextView(this).apply {
            setText(R.string.compatibility_mode_summary)
            textSize = 13f
            alpha = 0.72f
            setPadding(0, 0, 0, dp(8))
        })

        content.addView(sectionTitle(R.string.current_profile))
        content.addView(currentProfileCard())
        content.addView(sectionTitle(R.string.pmm_patch))
        content.addView(pmmCard())
        scroll.addView(content)
        return scroll
    }

    private fun currentProfileCard(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        strokeWidth = dp(1)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            val profile = store.selectedProfile()
            if (profile == null) {
                addView(bodyText(R.string.select_or_add_card))
            } else {
                addView(detailLine(R.string.card_name, profile.label))
                addView(detailLine(
                    R.string.idm_label,
                    if (store.showIdm()) profile.idm else getString(R.string.hidden_value)
                ))
                addView(detailLine(
                    R.string.access_code,
                    when {
                        profile.accessCode == null -> getString(R.string.access_code_not_set)
                        store.showAccessCode() -> profile.formattedAccessCode().orEmpty()
                        else -> getString(R.string.access_code_hidden)
                    }
                ))
                addView(detailLine(
                    R.string.route_mode,
                    getString(if (store.compatibilityMode()) R.string.compatibility_mode
                        else R.string.normal_mode)
                ))
                addView(detailLine(R.string.system_code_label, HceSession.SYSTEM_CODE))
                addView(detailLine(R.string.pmm_value_label, STANDARD_PMM_DISPLAY))
            }
        })
    }

    private fun detailLine(@androidx.annotation.StringRes label: Int, value: String): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(TextView(this@MainActivity).apply {
                setText(label)
                textSize = 14f
                alpha = 0.68f
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 14f
                gravity = Gravity.END
                setTypeface(typeface, Typeface.BOLD)
            })
        }

    private fun pmmCard(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        strokeWidth = dp(1)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
            pmmProgress = LinearProgressIndicator(this@MainActivity).apply {
                isIndeterminate = true
                hide()
            }
            addView(pmmProgress, LinearLayout.LayoutParams(-1, dp(4)))
            pmmSwitch = MaterialSwitch(this@MainActivity).apply {
                setText(R.string.enable_pmm_patch)
                isEnabled = false
                setOnCheckedChangeListener { button, enabled ->
                    if (button.isPressed) changePmm(enabled)
                }
            }
            addView(pmmSwitch)
            pmmStatus = TextView(this@MainActivity).apply {
                setText(R.string.checking_status)
                textSize = 14f
                setPadding(0, dp(4), 0, dp(8))
            }
            addView(pmmStatus)
            addView(TextView(this@MainActivity).apply {
                setText(R.string.pmm_explanation)
                textSize = 13f
                alpha = 0.72f
            })
        })
    }

    private fun buildSettingsPage(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
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
            isChecked = dynamicAvailable && ThemeSettings.dynamicColorsEnabled(this@MainActivity)
            isEnabled = dynamicAvailable
            setOnCheckedChangeListener { button, enabled ->
                if (button.isPressed && ThemeSettings.setDynamicColorsEnabled(
                        this@MainActivity,
                        enabled
                    )) {
                    recreate()
                }
            }
        })
        content.addView(TextView(this).apply {
            setText(if (dynamicAvailable) R.string.dynamic_colors_summary
                else R.string.dynamic_colors_unavailable)
            textSize = 13f
            alpha = 0.72f
        })

        content.addView(sectionTitle(R.string.privacy))
        content.addView(MaterialSwitch(this).apply {
            setText(R.string.show_idm)
            isChecked = store.showIdm()
            setOnCheckedChangeListener { button, value ->
                if (button.isPressed) store.setShowIdm(value)
            }
        })
        content.addView(MaterialSwitch(this).apply {
            setText(R.string.show_access_code)
            isChecked = store.showAccessCode()
            setOnCheckedChangeListener { button, value ->
                if (button.isPressed) store.setShowAccessCode(value)
            }
        })

        content.addView(sectionTitle(R.string.application_section))
        content.addView(MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            setText(R.string.open_about)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(content)
        return scroll
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
            check(when (ThemeSettings.mode(this@MainActivity)) {
                ThemeSettings.Mode.SYSTEM -> systemId
                ThemeSettings.Mode.LIGHT -> lightId
                ThemeSettings.Mode.DARK -> darkId
            })
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val mode = when (checkedId) {
                    lightId -> ThemeSettings.Mode.LIGHT
                    darkId -> ThemeSettings.Mode.DARK
                    else -> ThemeSettings.Mode.SYSTEM
                }
                if (mode != ThemeSettings.mode(this@MainActivity) &&
                    ThemeSettings.setMode(this@MainActivity, mode)) {
                    AppCompatDelegate.setDefaultNightMode(mode.nightMode)
                }
            }
        }
    }

    private fun themeButton(id: Int, text: Int): MaterialButton = MaterialButton(
        this,
        null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        this.id = id
        setText(text)
        isCheckable = true
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    private fun compactTextButton(text: Int): MaterialButton = MaterialButton(
        this,
        null,
        com.google.android.material.R.attr.materialButtonStyle
    ).apply {
        setText(text)
        backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
    }

    private fun showAddOptions() {
        val dialog = BottomSheetDialog(this)
        val content = verticalLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), dp(28))
            addView(sheetHandle(), LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(20)
            })
            addView(sheetTitle(R.string.add_card_action))
            addView(sheetDescription(R.string.add_card_method_summary).apply {
                setPadding(0, dp(6), 0, dp(18))
            })
            addView(addMethodCard(
                R.drawable.ic_add_manual,
                R.string.add_manually,
                R.string.add_manually_summary
            ) {
                dialog.dismiss()
                showEditor()
            })
            addView(addMethodCard(
                R.drawable.ic_read_nfc,
                R.string.read_physical_card,
                R.string.read_physical_card_summary
            ) {
                dialog.dismiss()
                reader.launch(Intent(this@MainActivity, CardReaderActivity::class.java))
            }.apply { applyMargins(vertical = 6) })
        }
        dialog.setContentView(content)
        dialog.behavior.skipCollapsed = true
        dialog.setOnShowListener { dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        dialog.show()
    }

    private fun addMethodCard(
        icon: Int,
        title: Int,
        summary: Int,
        action: () -> Unit
    ): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        strokeWidth = dp(1)
        cardElevation = 0f
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(16), dp(16))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
                )
                contentDescription = getString(title)
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(18) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    setText(title)
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(sheetDescription(summary).apply { setPadding(0, dp(3), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

    private fun activateSelected(attempt: Int = 0) {
        cancelActivationRetry()
        val selected = store.selectedProfile()
        if (selected == null) {
            setHceStatus(getString(R.string.select_or_add_card))
            return
        }
        val report = session.activate(this, selected, store.compatibilityMode())
        val message = when (report.stage) {
            HceSession.Stage.READY -> getString(R.string.hce_ready, selected.label)
            HceSession.Stage.UNSUPPORTED -> getString(R.string.hce_unsupported)
            HceSession.Stage.NFC_DISABLED -> getString(R.string.nfc_disabled)
            HceSession.Stage.SERVICE_RESTARTING -> {
                if (attempt < MAX_ACTIVATION_RETRIES) {
                    scheduleActivationRetry(attempt + 1)
                    getString(R.string.hce_waiting_for_service)
                } else getString(R.string.hce_service_restart_timeout)
            }
            else -> getString(R.string.hce_failed, report.detail)
        }
        setHceStatus(message)
    }

    private fun setHceStatus(message: CharSequence) {
        hceStatusText = message
        updateHceStatusViews()
    }

    private fun updateHceStatusViews() {
        cardPageStatusView?.text = hceStatusText
        statusPageStatusView?.text = hceStatusText
    }

    private fun scheduleActivationRetry(attempt: Int) {
        activationRetry = Runnable {
            activationRetry = null
            if (foreground) activateSelected(attempt)
        }.also { contentHost.postDelayed(it, ACTIVATION_RETRY_DELAY_MS) }
    }

    private fun cancelActivationRetry() {
        activationRetry?.let(contentHost::removeCallbacks)
        activationRetry = null
    }

    private fun checkDefaultNfcApp() {
        if (defaultNfcAppChecked) return
        when (DefaultNfcAppChecker.checkAndRequest(this)) {
            DefaultNfcAppChecker.Result.NFC_NOT_READY -> Unit
            else -> defaultNfcAppChecked = true
        }
    }

    private fun refreshPmm() = runPmm { PmmManager.inspect() }

    private fun changePmm(enabled: Boolean) = runPmm { PmmManager.setEnabled(enabled) }

    private fun runPmm(action: () -> PmmManager.Snapshot) {
        pmmSwitch?.isEnabled = false
        pmmProgress?.show()
        Thread {
            val snapshot = action()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                pmmSnapshot = snapshot
                if (selectedTab == TAB_STATUS) renderPmm(snapshot)
            }
        }.apply { name = "aimesim-pmm-state"; start() }
    }

    private fun renderPmm(snapshot: PmmManager.Snapshot) {
        pmmSnapshot = snapshot
        pmmProgress?.hide()
        pmmSwitch?.apply {
            setOnCheckedChangeListener(null)
            isChecked = snapshot.status == PmmManager.Status.ACTIVE ||
                snapshot.status == PmmManager.Status.WAITING
            isEnabled = snapshot.status != PmmManager.Status.UNAVAILABLE
            setOnCheckedChangeListener { button, enabled ->
                if (button.isPressed) changePmm(enabled)
            }
        }
        val mode = if (snapshot.modernModule) getString(R.string.pmm_mode_module)
            else getString(R.string.pmm_mode_framework)
        pmmStatus?.text = getString(R.string.pmm_status, mode, snapshot.detail)
    }

    private fun restorePmmSnapshot(state: Bundle?): PmmManager.Snapshot? {
        val statusName = state?.getString(STATE_PMM_STATUS) ?: return null
        val status = runCatching { PmmManager.Status.valueOf(statusName) }.getOrNull() ?: return null
        val detail = state.getString(STATE_PMM_DETAIL) ?: return null
        return PmmManager.Snapshot(status, detail, state.getBoolean(STATE_PMM_MODERN))
    }

    private fun showEditor(
        initial: CardProfile? = null,
        capturedIdm: String? = null,
        capturedSpad0: String? = null,
        capturedIdBlock: String? = null,
        capturedAccessCode: String? = null
    ) {
        val name = editorField(
            R.string.card_name,
            R.string.card_name_helper,
            initial?.label.orEmpty(),
            hex = false
        )
        val idm = editorField(
            R.string.idm,
            R.string.idm_helper,
            capturedIdm ?: initial?.idm ?: CardProfile.DEFAULT_IDM
        )
        val spad0 = editorField(
            R.string.spad0_optional,
            R.string.optional_block_helper,
            capturedSpad0 ?: initial?.spad0.orEmpty()
        )
        val idBlock = editorField(
            R.string.id_block_optional,
            R.string.optional_block_helper,
            capturedIdBlock ?: initial?.idBlock.orEmpty()
        )
        val accessCode = editorField(
            R.string.access_code,
            R.string.access_code_helper,
            capturedAccessCode?.chunked(4)?.joinToString(" ")
                ?: initial?.formattedAccessCode().orEmpty(),
            hex = false,
            numeric = true
        )
        val form = verticalLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), dp(12))
            listOf(name, accessCode, idm, spad0, idBlock).forEach { field ->
                addView(field.layout, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
            }
        }
        val save = MaterialButton(this).apply { setText(R.string.save) }
        val cancel = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply { setText(android.R.string.cancel) }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(8), dp(20), dp(20))
            addView(cancel, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            addView(save, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        }
        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            isFillViewport = true
            addView(form)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setPadding(0, dp(10), 0, 0)
            addView(sheetHandle(), LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18)
            })
            addView(sheetTitle(if (initial == null) R.string.add_card else R.string.edit_card).apply {
                setPadding(dp(20), 0, dp(20), 0)
            })
            addView(sheetDescription(R.string.card_editor_summary).apply {
                setPadding(dp(20), dp(6), dp(20), dp(8))
            })
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(actions)
        }
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(root)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val idmValid = validateHexField(idm, 16, optional = false)
            val spad0Valid = validateHexField(spad0, 32, optional = true)
            val idBlockValid = validateHexField(idBlock, 32, optional = true)
            val accessCodeValid = validateAccessCodeField(accessCode)
            if (idmValid && spad0Valid && idBlockValid && accessCodeValid) {
                val profile = CardProfile.create(
                    label = name.input.text.toString(),
                    idm = idm.input.text.toString(),
                    spad0 = spad0.input.text.toString().ifBlank { null },
                    idBlock = idBlock.input.text.toString().ifBlank { null },
                    accessCode = accessCode.input.text.toString().ifBlank { null },
                    profileId = initial?.profileId ?: java.util.UUID.randomUUID().toString()
                )
                if (profile == null) {
                    Snackbar.make(root, R.string.invalid_card_fields, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!store.put(profile)) {
                    Snackbar.make(root, R.string.save_failed, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (initial == null && store.selectedProfile() == null) store.select(profile.profileId)
                dialog.dismiss()
                activateSelected()
                showPage(TAB_CARDS, refreshPmm = false)
                bottomNavigation.selectedItemId = TAB_CARDS
            }
        }
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.layoutParams = sheet.layoutParams.apply {
                    height = (resources.displayMetrics.heightPixels * 0.90f).toInt()
                }
            }
            dialog.behavior.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun editorField(
        label: Int,
        helper: Int,
        initial: String,
        hex: Boolean = true,
        numeric: Boolean = false
    ): EditorField {
        val input = TextInputEditText(this).apply {
            setText(initial)
            isSingleLine = true
            setHorizontallyScrolling(true)
            if (hex) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                typeface = Typeface.MONOSPACE
            } else if (numeric) {
                inputType = InputType.TYPE_CLASS_NUMBER
                typeface = Typeface.MONOSPACE
            }
        }
        val layout = TextInputLayout(
            this,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = getString(label)
            helperText = getString(helper)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        return EditorField(layout, input)
    }

    private fun validateHexField(field: EditorField, expectedLength: Int, optional: Boolean): Boolean {
        val compact = field.input.text.toString().filterNot(Char::isWhitespace)
        val valid = (optional && compact.isEmpty()) ||
            (compact.length == expectedLength && compact.all { it.digitToIntOrNull(16) != null })
        field.layout.error = if (valid) null else getString(
            if (expectedLength == 16) R.string.idm_field_error else R.string.block_field_error
        )
        return valid
    }

    private fun validateAccessCodeField(field: EditorField): Boolean {
        val compact = field.input.text.toString().filterNot { it.isWhitespace() || it == '-' }
        val valid = compact.isEmpty() || (compact.length == 20 && compact.all(Char::isDigit))
        field.layout.error = if (valid) null else getString(R.string.access_code_field_error)
        return valid
    }

    private fun sheetHandle(): View = View(this).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(2).toFloat()
            setColor(MaterialColors.getColor(contentHost, com.google.android.material.R.attr.colorOutline))
        }
    }

    private fun sheetTitle(text: Int): TextView = TextView(this).apply {
        setText(text)
        textSize = 24f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    }

    private fun sheetDescription(text: Int): TextView = TextView(this).apply {
        setText(text)
        textSize = 14f
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
    }

    private fun confirmDelete(profile: CardProfile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_card)
            .setMessage(getString(R.string.delete_card_message, profile.label))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.remove(profile.profileId)
                activateSelected()
                showPage(TAB_CARDS, refreshPmm = false)
            }
            .show()
    }

    private companion object {
        const val TAB_CARDS = 1001
        const val TAB_STATUS = 1002
        const val TAB_SETTINGS = 1003
        const val MAX_ACTIVATION_RETRIES = 30
        const val ACTIVATION_RETRY_DELAY_MS = 1_000L
        const val STANDARD_PMM_DISPLAY = "00F1 0000 0001 4300"
        const val STATE_SELECTED_TAB = "main.selected.tab"
        const val STATE_PMM_STATUS = "main.pmm.status"
        const val STATE_PMM_DETAIL = "main.pmm.detail"
        const val STATE_PMM_MODERN = "main.pmm.modern"
    }

    private data class EditorField(
        val layout: TextInputLayout,
        val input: TextInputEditText
    )
}
