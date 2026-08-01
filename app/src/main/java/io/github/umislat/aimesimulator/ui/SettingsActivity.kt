package io.github.umislat.aimesimulator.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.umislat.aimesimulator.R
import io.github.umislat.aimesimulator.data.CardStore
import io.github.umislat.aimesimulator.root.PmmManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var store: CardStore
    private lateinit var pmmSwitch: MaterialSwitch
    private lateinit var pmmStatus: TextView
    private lateinit var progress: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CardStore(this)
        setContentView(buildScreen())
        refreshPmm()
    }

    override fun onResume() {
        super.onResume()
        if (::pmmStatus.isInitialized) refreshPmm()
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

    private fun refreshPmm() = runPmm { PmmManager.inspect() }

    private fun changePmm(enabled: Boolean) = runPmm { PmmManager.setEnabled(enabled) }

    private fun runPmm(action: () -> PmmManager.Snapshot) {
        pmmSwitch.isEnabled = false
        progress.show()
        Thread {
            val snapshot = action()
            runOnUiThread {
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
        }.apply { name = "aimesim-pmm-state"; start() }
    }
}
