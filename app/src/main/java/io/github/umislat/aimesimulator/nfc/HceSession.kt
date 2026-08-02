package io.github.umislat.aimesimulator.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.NfcFCardEmulation
import android.os.DeadObjectException
import android.util.Log
import io.github.umislat.aimesimulator.data.CardProfile
import io.github.umislat.aimesimulator.data.CardStore

internal class HceSession(private val context: Context) {
    enum class Stage {
        READY, UNSUPPORTED, NFC_DISABLED, SERVICE_RESTARTING, ID, SYSTEM_CODE, ENABLE, EXCEPTION
    }

    data class Report(val stage: Stage, val detail: String = "") {
        val succeeded: Boolean get() = stage == Stage.READY
    }

    private val component = ComponentName(context, AimeHostService::class.java)
    // Keep a known-good adapter across NFC service restarts. Calling isEnabled() on this
    // instance activates Android's built-in dead-service recovery and refreshes the NFC-F
    // Binder, while a fresh getDefaultAdapter() call can remain null through a cached
    // NfcManager on some vendor builds.
    private var adapter: NfcAdapter? = runCatching {
        NfcAdapter.getDefaultAdapter(context)
    }.getOrNull()

    fun activate(activity: Activity, profile: CardProfile, compatibilityMode: Boolean): Report {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)) {
            return report(Stage.UNSUPPORTED, "HCE-F is unavailable")
        }
        val nfcAdapter = resolveAdapter()
            ?: return report(Stage.SERVICE_RESTARTING, "NFC service is restarting")
        try {
            if (!nfcAdapter.isEnabled) return report(Stage.NFC_DISABLED, "NFC is disabled")
        } catch (error: RuntimeException) {
            return runtimeFailure(error)
        }

        val store = CardStore(context)
        val previousId = store.selectedProfile()?.profileId
        if (!store.select(profile.profileId)) return report(Stage.EXCEPTION, "Selection could not be saved")

        return try {
            val manager = NfcFCardEmulation.getInstance(nfcAdapter)
            manager.disableService(activity)
            if (!manager.setNfcid2ForService(component, profile.routedIdm(compatibilityMode))) {
                restore(store, previousId)
                return report(Stage.ID, "NFCID2 registration failed")
            }
            if (!manager.registerSystemCodeForService(component, SYSTEM_CODE)) {
                manager.disableService(activity)
                restore(store, previousId)
                return report(Stage.SYSTEM_CODE, "System-code registration failed")
            }
            if (!manager.enableService(activity, component)) {
                manager.disableService(activity)
                restore(store, previousId)
                return report(Stage.ENABLE, "Foreground service activation failed")
            }
            report(Stage.READY, "Active: ${profile.label}")
        } catch (error: RuntimeException) {
            restore(store, previousId)
            runtimeFailure(error)
        }
    }

    fun deactivate(activity: Activity) {
        val nfcAdapter = resolveAdapter() ?: return
        runCatching { NfcFCardEmulation.getInstance(nfcAdapter).disableService(activity) }
    }

    private fun resolveAdapter(): NfcAdapter? {
        adapter?.let { return it }
        return runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()?.also {
            adapter = it
        }
    }

    private fun restore(store: CardStore, profileId: String?) {
        store.select(profileId)
    }

    private fun report(stage: Stage, detail: String): Report = Report(stage, detail).also {
        CardStore(context).recordHceStatus(detail)
    }

    private fun runtimeFailure(error: RuntimeException): Report {
        val cause = rootCause(error)
        Log.w(TAG, "HCE-F activation failed", error)
        return if (cause is DeadObjectException ||
            cause.javaClass.name == "android.os.DeadSystemException" ||
            cause.message.orEmpty().contains("DeadObjectException", ignoreCase = true)
        ) {
            report(Stage.SERVICE_RESTARTING, "NFC service is restarting")
        } else {
            val detail = cause.message?.takeIf(String::isNotBlank)
                ?.let { "${cause.javaClass.simpleName}: $it" }
                ?: cause.javaClass.simpleName
            report(Stage.EXCEPTION, detail)
        }
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current
    }

    companion object {
        private const val TAG = "AimeHceSession"
        const val SYSTEM_CODE = "88B4"
    }
}
