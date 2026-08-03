package io.github.umislat.aimesimulator.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.util.Log

internal object DefaultNfcAppChecker {
    enum class Result {
        ALREADY_DEFAULT,
        REQUESTED,
        NFC_NOT_READY,
        UNSUPPORTED,
        FAILED
    }

    fun checkAndRequest(activity: Activity): Result {
        if (!activity.packageManager.hasSystemFeature(
                PackageManager.FEATURE_NFC_HOST_CARD_EMULATION
            )) {
            return Result.UNSUPPORTED
        }
        val adapter = runCatching { NfcAdapter.getDefaultAdapter(activity) }.getOrNull()
            ?: return Result.NFC_NOT_READY
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!enabled) return Result.NFC_NOT_READY

        return try {
            val manager = CardEmulation.getInstance(adapter)
            val service = ComponentName(activity, DefaultNfcService::class.java)
            if (manager.isDefaultServiceForCategory(service, CardEmulation.CATEGORY_PAYMENT)) {
                Result.ALREADY_DEFAULT
            } else {
                val request = Intent(CardEmulation.ACTION_CHANGE_DEFAULT)
                    .putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
                    .putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, service)
                activity.startActivity(request)
                Result.REQUESTED
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to check the default NFC application", error)
            Result.FAILED
        }
    }

    private const val TAG = "AimeDefaultNfc"
}
