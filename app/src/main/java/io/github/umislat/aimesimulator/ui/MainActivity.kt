package io.github.umislat.aimesimulator.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.Menu
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import io.github.umislat.aimesimulator.R
import io.github.umislat.aimesimulator.data.CardProfile
import io.github.umislat.aimesimulator.data.CardStore
import io.github.umislat.aimesimulator.nfc.DefaultNfcAppChecker
import io.github.umislat.aimesimulator.nfc.HceSession

class MainActivity : AppCompatActivity() {
    private lateinit var store: CardStore
    private lateinit var session: HceSession
    private lateinit var list: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var statusView: TextView
    private lateinit var modeSwitch: MaterialSwitch
    private var foreground = false
    private var defaultNfcAppChecked = false
    private var activationRetry: Runnable? = null

    private val reader = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val idm = data.getStringExtra(CardReaderActivity.EXTRA_IDM) ?: return@registerForActivityResult
        showEditor(
            initial = null,
            capturedIdm = idm,
            capturedSpad0 = data.getStringExtra(CardReaderActivity.EXTRA_SPAD0),
            capturedIdBlock = data.getStringExtra(CardReaderActivity.EXTRA_ID_BLOCK)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CardStore(this)
        session = HceSession(this)
        setContentView(buildScreen())
        renderProfiles()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        renderProfiles()
        activateSelected()
        checkDefaultNfcApp()
    }

    override fun onPause() {
        foreground = false
        cancelActivationRetry()
        session.deactivate(this)
        super.onPause()
    }

    private fun buildScreen(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            subtitle = getString(R.string.app_subtitle)
            menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.settings)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
            menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.about)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_SETTINGS -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    MENU_ABOUT -> showAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
        root.addView(toolbar)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = verticalLayout(this)

        modeSwitch = MaterialSwitch(this).apply {
            setText(R.string.compatibility_mode)
            isChecked = store.compatibilityMode()
            setOnCheckedChangeListener { _, enabled ->
                store.setCompatibilityMode(enabled)
                if (this@MainActivity.foreground) activateSelected()
            }
        }
        content.addView(modeSwitch, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            setText(R.string.compatibility_mode_summary)
            textSize = 13f
            alpha = 0.72f
            setPadding(0, 0, 0, dp(12))
        })

        statusView = TextView(this).apply {
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.status_background)
        }
        content.addView(statusView, LinearLayout.LayoutParams(-1, -2))
        content.addView(sectionTitle(R.string.card_profiles))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(MaterialButton(this).apply {
            setText(R.string.add_manually)
            setOnClickListener { showEditor() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        actions.addView(MaterialButton(this).apply {
            setText(R.string.read_physical_card)
            setOnClickListener { reader.launch(Intent(this@MainActivity, CardReaderActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        content.addView(actions)

        emptyView = TextView(this).apply {
            setText(R.string.no_cards)
            gravity = Gravity.CENTER
            alpha = 0.72f
            setPadding(0, dp(40), 0, dp(32))
        }
        content.addView(emptyView)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun renderProfiles() {
        if (!::list.isInitialized) return
        val profiles = store.profiles()
        val selectedId = store.selectedProfile()?.profileId
        list.removeAllViews()
        emptyView.visibility = if (profiles.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        profiles.forEach { profile -> list.addView(cardView(profile, profile.profileId == selectedId)) }
        if (profiles.isEmpty()) {
            statusView.setText(R.string.select_or_add_card)
        }
    }

    private fun cardView(profile: CardProfile, selected: Boolean): MaterialCardView {
        return MaterialCardView(this).apply {
            isCheckable = true
            isChecked = selected
            strokeWidth = if (selected) dp(2) else dp(1)
            radius = dp(18).toFloat()
            useCompatPadding = true
            setOnClickListener {
                store.select(profile.profileId)
                renderProfiles()
                if (this@MainActivity.foreground) activateSelected()
            }

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(12))
            }
            row.addView(TextView(this@MainActivity).apply {
                text = profile.label
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            })
            row.addView(TextView(this@MainActivity).apply {
                text = if (store.showIdm()) getString(R.string.idm_value, profile.idm)
                else getString(R.string.idm_hidden)
                textSize = 14f
                setPadding(0, dp(4), 0, dp(8))
            })
            val buttons = LinearLayout(this@MainActivity).apply { gravity = Gravity.END }
            buttons.addView(MaterialButton(this@MainActivity).apply {
                setText(R.string.edit)
                setOnClickListener { showEditor(profile) }
            })
            buttons.addView(MaterialButton(this@MainActivity).apply {
                setText(R.string.delete)
                setOnClickListener { confirmDelete(profile) }
            })
            row.addView(buttons)
            addView(row)
        }.also { card ->
            card.layoutParams = ViewGroup.MarginLayoutParams(-1, -2).apply {
                topMargin = dp(5)
                bottomMargin = dp(5)
            }
        }
    }

    private fun activateSelected(attempt: Int = 0) {
        cancelActivationRetry()
        val selected = store.selectedProfile()
        if (selected == null) {
            statusView.setText(R.string.select_or_add_card)
            return
        }
        val report = session.activate(this, selected, modeSwitch.isChecked)
        statusView.text = when (report.stage) {
            HceSession.Stage.READY -> getString(R.string.hce_ready, selected.label)
            HceSession.Stage.UNSUPPORTED -> getString(R.string.hce_unsupported)
            HceSession.Stage.NFC_DISABLED -> getString(R.string.nfc_disabled)
            HceSession.Stage.SERVICE_RESTARTING -> {
                if (attempt < MAX_ACTIVATION_RETRIES) {
                    scheduleActivationRetry(attempt + 1)
                    getString(R.string.hce_waiting_for_service)
                } else {
                    getString(R.string.hce_service_restart_timeout)
                }
            }
            else -> getString(R.string.hce_failed, report.detail)
        }
    }

    private fun scheduleActivationRetry(attempt: Int) {
        activationRetry = Runnable {
            activationRetry = null
            if (foreground) activateSelected(attempt)
        }.also { statusView.postDelayed(it, ACTIVATION_RETRY_DELAY_MS) }
    }

    private fun checkDefaultNfcApp() {
        if (defaultNfcAppChecked) return
        when (DefaultNfcAppChecker.checkAndRequest(this)) {
            DefaultNfcAppChecker.Result.NFC_NOT_READY -> Unit
            else -> defaultNfcAppChecked = true
        }
    }

    private fun cancelActivationRetry() {
        activationRetry?.let(statusView::removeCallbacks)
        activationRetry = null
    }

    private fun showEditor(
        initial: CardProfile? = null,
        capturedIdm: String? = null,
        capturedSpad0: String? = null,
        capturedIdBlock: String? = null
    ) {
        val form = verticalLayout(this).apply { setPadding(dp(4), 0, dp(4), 0) }
        val name = editField(R.string.card_name, initial?.label.orEmpty())
        val idm = editField(R.string.idm, capturedIdm ?: initial?.idm ?: CardProfile.DEFAULT_IDM, 16)
        val spad0 = editField(R.string.spad0_optional, capturedSpad0 ?: initial?.spad0.orEmpty(), 32)
        val idBlock = editField(R.string.id_block_optional,
            capturedIdBlock ?: initial?.idBlock.orEmpty(), 32)
        listOf(name, idm, spad0, idBlock).forEach(form::addView)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (initial == null) R.string.add_card else R.string.edit_card)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val profile = CardProfile.create(
                    label = name.text.toString(),
                    idm = idm.text.toString(),
                    spad0 = spad0.text.toString().ifBlank { null },
                    idBlock = idBlock.text.toString().ifBlank { null },
                    profileId = initial?.profileId ?: java.util.UUID.randomUUID().toString()
                )
                if (profile == null) {
                    Snackbar.make(list, R.string.invalid_card_fields, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!store.put(profile)) {
                    Snackbar.make(list, R.string.save_failed, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (initial == null && store.selectedProfile() == null) store.select(profile.profileId)
                dialog.dismiss()
                renderProfiles()
                if (foreground) activateSelected()
            }
        }
        dialog.show()
    }

    private fun editField(label: Int, initial: String, maxLength: Int? = null): EditText = EditText(this).apply {
        hint = getString(label)
        setText(initial)
        isSingleLine = true
        if (maxLength != null) filters = arrayOf(InputFilter.LengthFilter(maxLength))
    }

    private fun confirmDelete(profile: CardProfile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_card)
            .setMessage(getString(R.string.delete_card_message, profile.label))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.remove(profile.profileId)
                renderProfiles()
                if (foreground) activateSelected()
            }
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        private const val MENU_SETTINGS = 100
        private const val MENU_ABOUT = 101
        private const val MAX_ACTIVATION_RETRIES = 30
        private const val ACTIVATION_RETRY_DELAY_MS = 1_000L
    }
}
