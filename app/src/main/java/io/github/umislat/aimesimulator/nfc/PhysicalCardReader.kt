package io.github.umislat.aimesimulator.nfc

import android.nfc.Tag
import android.nfc.tech.NfcF
import io.github.umislat.aimesimulator.data.HexCodec
import java.io.IOException

internal object PhysicalCardReader {
    data class Capture(
        val idm: String,
        val systemCode: String,
        val spad0: String?,
        val idBlock: String?
    )

    fun capture(tag: Tag): Capture? {
        val technology = NfcF.get(tag) ?: return null
        val idmBytes = tag.id ?: return null
        val systemCode = technology.systemCode?.copyOf() ?: ByteArray(0)
        val found = HashMap<Int, ByteArray>()
        try {
            technology.connect()
            val requested = intArrayOf(0x00, 0x82)
            val combined = runCatching { read(technology, idmBytes, requested) }.getOrNull()
            if (combined != null) {
                requested.forEachIndexed { index, block -> found[block] = combined[index] }
            } else {
                requested.forEach { block ->
                    runCatching { read(technology, idmBytes, intArrayOf(block))?.singleOrNull() }
                        .getOrNull()?.let { found[block] = it }
                }
            }
        } catch (_: IOException) {
            // IDm-only capture remains useful.
        } finally {
            runCatching { technology.close() }
        }

        return Capture(
            idm = HexCodec.encode(idmBytes),
            systemCode = HexCodec.encode(systemCode),
            spad0 = found[0x00]?.let(HexCodec::encode),
            idBlock = found[0x82]?.let(HexCodec::encode)
        )
    }

    private fun read(technology: NfcF, idm: ByteArray, blocks: IntArray): List<ByteArray>? {
        val result = FelicaCodec.decodeReadResponse(
            technology.transceive(FelicaCodec.readRequest(idm, blocks))
        ) ?: return null
        if (!result.succeeded || !result.nfcid2.contentEquals(idm) || result.blocks.size != blocks.size) {
            return null
        }
        return result.blocks
    }
}
