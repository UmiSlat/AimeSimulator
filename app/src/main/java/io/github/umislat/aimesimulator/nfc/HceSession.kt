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
import io.github.umislat.aimesimulator.data.IdmRouteMode

internal class HceSession(private val context: Context) {
    enum class Stage {
        READY, UNSUPPORTED, NFC_DISABLED, SERVICE_RESTARTING, STORAGE, ID, SYSTEM_CODE, ENABLE,
        EXCEPTION
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
        routeMode: IdmRouteMode,
        systemCode: String = SYSTEM_CODE
    ): Report = activateProfile(
        activity,
        profile.profileId,
        profile.routedIdm(routeMode),
        systemCode
    )

    private fun activateProfile(
        activity: Activity,
        profileId: String,
        nfcid2: String,
        systemCode: String
    ): Report {
        val store by lazy(LazyThreadSafetyMode.NONE) { CardStore(context) }
        val workflow = HceActivationWorkflow(
            backend = androidBackend(activity, component),
            selection = object : HceActivationWorkflow.Selection {
                override fun selectedProfileId(): String? = store.selectedProfile()?.profileId

                override fun select(profileId: String?): Boolean = store.select(profileId)
            },
            failureReporter = ::runtimeFailure
        )
        return workflow.activate(profileId, nfcid2, systemCode)
    }

    private fun androidBackend(
        activity: Activity,
        serviceComponent: ComponentName
    ): HceActivationWorkflow.Backend = object : HceActivationWorkflow.Backend {
        private var activeAdapter: NfcAdapter? = null
        private var cachedManager: NfcFCardEmulation? = null

        override fun isSupported(): Boolean = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF
        )

        override fun availability(): HceActivationWorkflow.Availability {
            val nfcAdapter = resolveAdapter()
                ?: return HceActivationWorkflow.Availability.SERVICE_RESTARTING
            activeAdapter = nfcAdapter
            return if (nfcAdapter.isEnabled) {
                HceActivationWorkflow.Availability.READY
            } else {
                HceActivationWorkflow.Availability.NFC_DISABLED
            }
        }

        override fun disable() {
            manager().disableService(activity)
        }

        override fun setNfcid2(nfcid2: String): Boolean =
            manager().setNfcid2ForService(serviceComponent, nfcid2)

        override fun registerSystemCode(systemCode: String): Boolean =
            manager().registerSystemCodeForService(serviceComponent, systemCode)

        override fun enable(): Boolean = manager().enableService(activity, serviceComponent)

        private fun manager(): NfcFCardEmulation = cachedManager ?: NfcFCardEmulation.getInstance(
            checkNotNull(activeAdapter) { "NFC adapter was not prepared" }
        ).also { cachedManager = it }
    }

    fun deactivate(activity: Activity) {
        val nfcAdapter = resolveAdapter() ?: return
        runCatching { NfcFCardEmulation.getInstance(nfcAdapter).disableService(activity) }
    }

    fun activateStaticAimeDiagnostic(activity: Activity): Report {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)) {
            return report(Stage.UNSUPPORTED)
        }
        val nfcAdapter = resolveAdapter()
            ?: return report(Stage.SERVICE_RESTARTING)
        try {
            if (!nfcAdapter.isEnabled) return report(Stage.NFC_DISABLED)
        } catch (error: RuntimeException) {
            return runtimeFailure(error)
        }

        return try {
            val manager = NfcFCardEmulation.getInstance(nfcAdapter)
            manager.disableService(activity)
            val parsedIdm = manager.getNfcid2ForService(staticAimeComponent)
            if (!STATIC_AIME_IDM.equals(parsedIdm, ignoreCase = true)) {
                return report(Stage.ID, parsedIdm.orEmpty())
            }
            val parsedSystemCode = manager.getSystemCodeForService(staticAimeComponent)
            if (!SYSTEM_CODE.equals(parsedSystemCode, ignoreCase = true)) {
                return report(
                    Stage.SYSTEM_CODE,
                    parsedSystemCode.orEmpty()
                )
            }
            if (!manager.enableService(activity, staticAimeComponent)) {
                return report(Stage.ENABLE)
            }
            report(Stage.READY)
        } catch (error: RuntimeException) {
            runtimeFailure(error)
        }
    }

    fun activateDefaultHcefCard(activity: Activity): Report {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)) {
            return report(Stage.UNSUPPORTED)
        }
        val nfcAdapter = resolveAdapter()
            ?: return report(Stage.SERVICE_RESTARTING)
        try {
            if (!nfcAdapter.isEnabled) return report(Stage.NFC_DISABLED)
        } catch (error: RuntimeException) {
            return runtimeFailure(error)
        }

        return try {
            val manager = NfcFCardEmulation.getInstance(nfcAdapter)
            manager.disableService(activity)
            val parsedIdm = manager.getNfcid2ForService(defaultHcefComponent)
            if (!DEFAULT_HCEF_IDM.equals(parsedIdm, ignoreCase = true)) {
                return report(Stage.ID, parsedIdm.orEmpty())
            }
            val parsedSystemCode = manager.getSystemCodeForService(defaultHcefComponent)
            if (!GENERIC_SYSTEM_CODE.equals(parsedSystemCode, ignoreCase = true)) {
                return report(
                    Stage.SYSTEM_CODE,
                    parsedSystemCode.orEmpty()
                )
            }
            if (!manager.enableService(activity, defaultHcefComponent)) {
                return report(Stage.ENABLE)
            }
            report(Stage.READY)
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

    private fun report(stage: Stage, detail: String = ""): Report = Report(stage, detail)

    private fun runtimeFailure(error: RuntimeException): Report {
        val cause = rootCause(error)
        Log.w(TAG, "HCE-F activation failed", error)
        return if (cause is DeadObjectException ||
            cause.javaClass.name == "android.os.DeadSystemException" ||
            cause.message.orEmpty().contains("DeadObjectException", ignoreCase = true)
        ) {
            report(Stage.SERVICE_RESTARTING)
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
