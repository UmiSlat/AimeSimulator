package io.github.umislat.aimesimulator.nfc

import android.nfc.cardemulation.HostNfcFService
import android.os.Bundle
import android.util.Log
import io.github.umislat.aimesimulator.BuildConfig
import io.github.umislat.aimesimulator.data.CardProfile
import io.github.umislat.aimesimulator.data.CardStore

open class AimeHostService : HostNfcFService() {
    override fun processNfcFPacket(commandPacket: ByteArray, extras: Bundle?): ByteArray? {
        val request = FelicaCodec.decodeRequest(commandPacket) ?: return null
        val profile = CardStore(this).selectedProfile() ?: CardProfile.fallback()
        val image = CardImage(profile)

        val response = when (request.command) {
            FelicaCodec.READ_COMMAND -> {
                val validServices = request.blocks.all { block ->
                    request.services.getOrNull(block.serviceIndex) == FelicaCodec.READ_ONLY_SERVICE
                }
                if (!validServices) {
                    FelicaCodec.readResponse(request.nfcid2, emptyList(), 0x01, 0xA2)
                } else {
                    FelicaCodec.readResponse(
                        request.nfcid2,
                        request.blocks.map { image.read(it.blockNumber) }
                    )
                }
            }
            FelicaCodec.WRITE_COMMAND -> FelicaCodec.writeResponse(request.nfcid2)
            else -> FelicaCodec.UNKNOWN_RESPONSE.copyOf()
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "command=%02X request=%d response=%d".format(request.command, commandPacket.size, response.size))
        }
        return response
    }

    override fun onDeactivated(reason: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "deactivated reason=$reason")
    }

    companion object {
        private const val TAG = "AimeHostService"
    }
}

class StaticAimeHostService : AimeHostService()
