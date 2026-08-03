package io.github.umislat.aimesimulator.ui

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import io.github.umislat.aimesimulator.R
import io.github.umislat.aimesimulator.nfc.PhysicalCardReader

class CardReaderActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private var adapter: NfcAdapter? = null
    private lateinit var status: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.read_physical_card)
        adapter = NfcAdapter.getDefaultAdapter(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(MaterialToolbar(this).apply {
            title = getString(R.string.read_physical_card)
            setTitleTextAppearance(this@CardReaderActivity, R.style.TextAppearance_AimeSimulator_Toolbar)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        })
        val content = verticalLayout(this).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            addView(CircularProgressIndicator(this@CardReaderActivity).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { topMargin = dp(56) }
            })
            status = bodyText(R.string.hold_card_near_phone).apply {
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            }
            addView(status)
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter
        if (nfc == null) {
            status.setText(R.string.nfc_unavailable)
            return
        }
        if (!nfc.isEnabled) {
            status.setText(R.string.nfc_disabled)
            return
        }
        nfc.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        )
    }

    override fun onPause() {
        adapter?.disableReaderMode(this)
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        val capture = PhysicalCardReader.capture(tag)
        runOnUiThread {
            if (capture == null) {
                status.setText(R.string.card_read_failed)
            } else {
                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra(EXTRA_IDM, capture.idm)
                    putExtra(EXTRA_SYSTEM_CODE, capture.systemCode)
                    putExtra(EXTRA_SPAD0, capture.spad0)
                    putExtra(EXTRA_ID_BLOCK, capture.idBlock)
                })
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_IDM = "capture.idm"
        const val EXTRA_SYSTEM_CODE = "capture.systemCode"
        const val EXTRA_SPAD0 = "capture.spad0"
        const val EXTRA_ID_BLOCK = "capture.idBlock"
    }
}
