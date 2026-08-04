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
    private val staticAimeComponent = ComponentName(context, StaticAimeHostService::class.java)
    private val defaultHcefComponent = ComponentName(context, DefaultHcefCardService::class.java)
    // Keep a known-good adapter across NFC service restarts. Calling isEnabled() on this
    // instance activates Android's built-in dead-service recovery and refreshes the NFC-F
    // Binder, while a fresh getDefaultAdapter() call can remain null through a cached
    // NfcManager on some vendor builds.
    private var adapter: NfcAdapter? = runCatching {
        NfcAdapter.getDefaultAdapter(context)
    }.getOrNull()

    fun activate(
        activity: Activity,
        profile: CardProfile,
        compatibilityMode: Boolean,
        systemCode: String = SYSTEM_CODE
    ): Report {
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
            if (!manager.registerSystemCodeForService(component, systemCode)) {
                manager.disableService(activity)
                restore(store, previousId)
                return report(Stage.SYSTEM_CODE, "System-code $systemCode registration failed")
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

    fun activateStaticAimeDiagnostic(activity: Activity): Report {
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

        return try {
            val manager = NfcFCardEmulation.getInstance(nfcAdapter)
            manager.disableService(activity)
            val parsedIdm = manager.getNfcid2ForService(staticAimeComponent)
            if (!STATIC_AIME_IDM.equals(parsedIdm, ignoreCase = true)) {
                return report(Stage.ID, "Static NFCID2 parsed as ${parsedIdm ?: "none"}")
            }
            val parsedSystemCode = manager.getSystemCodeForService(staticAimeComponent)
            if (!SYSTEM_CODE.equals(parsedSystemCode, ignoreCase = true)) {
                return report(
                    Stage.SYSTEM_CODE,
                    "Static 88B4 parsed as ${parsedSystemCode ?: "none"}"
                )
            }
            if (!manager.enableService(activity, staticAimeComponent)) {
                return report(Stage.ENABLE, "Static 88B4 foreground activation failed")
            }
            report(Stage.READY, "Static 88B4 service enabled")
        } catch (error: RuntimeException) {
            runtimeFailure(error)
        }
    }

    fun activateDefaultHcefCard(activity: Activity): Report {
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

        return try {
            val manager = NfcFCardEmulation.getInstance(nfcAdapter)
            manager.disableService(activity)
            val parsedIdm = manager.getNfcid2ForService(defaultHcefComponent)
            if (!DEFAULT_HCEF_IDM.equals(parsedIdm, ignoreCase = true)) {
                return report(Stage.ID, "Default NFCID2 parsed as ${parsedIdm ?: "none"}")
            }
            val parsedSystemCode = manager.getSystemCodeForService(defaultHcefComponent)
            if (!GENERIC_SYSTEM_CODE.equals(parsedSystemCode, ignoreCase = true)) {
                return report(
                    Stage.SYSTEM_CODE,
                    "Default 4000 parsed as ${parsedSystemCode ?: "none"}"
                )
            }
            if (!manager.enableService(activity, defaultHcefComponent)) {
                return report(Stage.ENABLE, "Default HCE-F card activation failed")
            }
            report(Stage.READY, "Default HCE-F card service enabled")
        } catch (error: RuntimeException) {
            runtimeFailure(error)
        }
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
        const val GENERIC_SYSTEM_CODE = "4000"
        const val STATIC_AIME_IDM = CardProfile.COMPATIBILITY_IDM
        const val DEFAULT_HCEF_IDM = CardProfile.COMPATIBILITY_IDM
    }
}
