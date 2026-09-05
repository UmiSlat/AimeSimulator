package io.github.umislat.aimesimulator.nfc

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

internal object DefaultNfcAppChecker {
    enum class Result {
        ALREADY_DEFAULT,
        NOT_DEFAULT,
        REQUESTED,
        NFC_NOT_READY,
        UNSUPPORTED,
        FAILED
    }

    fun check(activity: Activity): Result {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                walletRoleManager(activity)?.let { roleManager ->
                    return if (roleManager.isRoleHeld(RoleManager.ROLE_WALLET)) {
                        Result.ALREADY_DEFAULT
                    } else {
                        Result.NOT_DEFAULT
                    }
                }
            }
            val manager = CardEmulation.getInstance(adapter)
            val service = ComponentName(activity, DefaultNfcService::class.java)
            if (manager.isDefaultServiceForCategory(service, CardEmulation.CATEGORY_PAYMENT)) {
                Result.ALREADY_DEFAULT
            } else {
                Result.NOT_DEFAULT
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to check the default NFC application", error)
            Result.FAILED
        }
    }

    fun request(activity: Activity): Result {
        val current = check(activity)
        if (current != Result.NOT_DEFAULT) return current
        return try {
            val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                walletRoleManager(activity)?.createRequestRoleIntent(RoleManager.ROLE_WALLET)
                    ?: createLegacyRequest(activity)
            } else {
                createLegacyRequest(activity)
            }
            activity.startActivity(request)
            Result.REQUESTED
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to request the default NFC application", error)
            Result.FAILED
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun walletRoleManager(activity: Activity): RoleManager? =
        activity.getSystemService(RoleManager::class.java)
            ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_WALLET) }

    @Suppress("DEPRECATION")
    private fun createLegacyRequest(activity: Activity): Intent {
        val service = ComponentName(activity, DefaultNfcService::class.java)
        return Intent(CardEmulation.ACTION_CHANGE_DEFAULT)
            .putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
            .putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, service)
    }

    private const val TAG = "AimeDefaultNfc"
}
