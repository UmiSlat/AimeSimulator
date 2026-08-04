package io.github.umislat.aimesimulator.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
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

    sealed interface AccessCodeRecognition {
        data class FelicaAic(
            val idm: String,
            val systemCode: String,
            val spad0: String,
            val idBlock: String?,
            val accessCode: String
        ) : AccessCodeRecognition

        data class MifareAime(val uid: String, val accessCode: String) : AccessCodeRecognition
        object FelicaNotAmusementIc : AccessCodeRecognition
        object FelicaInvalidAccessCode : AccessCodeRecognition
        object FelicaReadFailed : AccessCodeRecognition
        object MifareAuthenticationFailed : AccessCodeRecognition
        object MifareInvalidAccessCode : AccessCodeRecognition
        object MifareReadFailed : AccessCodeRecognition
        object UnsupportedCardTechnology : AccessCodeRecognition
    }

    fun recognizeAccessCode(tag: Tag): AccessCodeRecognition {
        NfcF.get(tag)?.let { return recognizeAic(tag, it) }
        MifareClassic.get(tag)?.let { return recognizeMifare(tag, it) }
        return AccessCodeRecognition.UnsupportedCardTechnology
    }

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

    private fun recognizeAic(tag: Tag, technology: NfcF): AccessCodeRecognition {
        val idm = tag.id ?: return AccessCodeRecognition.FelicaReadFailed
        val pmm = technology.manufacturer ?: ByteArray(0)
        val systemCode = technology.systemCode ?: ByteArray(0)
        if (!isAicCandidate(idm, pmm, systemCode)) {
            return AccessCodeRecognition.FelicaNotAmusementIc
        }

        return try {
            technology.connect()
            val combined = runCatching {
                read(technology, idm, intArrayOf(AIC_SPAD0_BLOCK, AIC_ID_BLOCK))
            }.getOrNull()
            val spad0: ByteArray
            val idBlock: ByteArray?
            if (combined != null) {
                spad0 = combined[0]
                idBlock = combined[1]
            } else {
                spad0 = read(technology, idm, intArrayOf(AIC_SPAD0_BLOCK))?.singleOrNull()
                    ?: return AccessCodeRecognition.FelicaReadFailed
                idBlock = runCatching {
                    read(technology, idm, intArrayOf(AIC_ID_BLOCK))?.singleOrNull()
                }.getOrNull()
            }
            val accessCode = AicAccessCodeCodec.decodeAccessCode(spad0)
                ?: return AccessCodeRecognition.FelicaInvalidAccessCode
            AccessCodeRecognition.FelicaAic(
                idm = HexCodec.encode(idm),
                systemCode = HexCodec.encode(systemCode),
                spad0 = HexCodec.encode(spad0),
                idBlock = idBlock?.let(HexCodec::encode),
                accessCode = accessCode
            )
        } catch (_: IOException) {
            AccessCodeRecognition.FelicaReadFailed
        } finally {
            runCatching { technology.close() }
        }
    }

    internal fun isAicCandidate(idm: ByteArray, pmm: ByteArray, systemCode: ByteArray): Boolean {
        if (idm.size < 2 || pmm.size < AIC_PMM.size) return false
        val code = if (systemCode.size >= 2) {
            ((systemCode[0].toInt() and 0xFF) shl 8) or
                (systemCode[1].toInt() and 0xFF)
        } else {
            null
        }
        return idm[0] == 0x01.toByte() &&
            idm[1] == 0x2E.toByte() &&
            pmm.copyOf(AIC_PMM.size).contentEquals(AIC_PMM) &&
            (code == null || code == AIC_SYSTEM_CODE || code == 0)
    }

    private fun recognizeMifare(tag: Tag, technology: MifareClassic): AccessCodeRecognition {
        return try {
            technology.connect()
            val sector = technology.blockToSector(AIME_DATA_BLOCK)
            val authenticated = technology.authenticateSectorWithKeyB(sector, AIME_KEY)
            if (!authenticated) {
                AccessCodeRecognition.MifareAuthenticationFailed
            } else {
                val accessCode = MifareAccessCodeCodec.decodeBlock2(
                    technology.readBlock(AIME_DATA_BLOCK)
                )
                if (accessCode == null) {
                    AccessCodeRecognition.MifareInvalidAccessCode
                } else {
                    AccessCodeRecognition.MifareAime(
                        uid = HexCodec.encode(tag.id ?: ByteArray(0)),
                        accessCode = accessCode
                    )
                }
            }
        } catch (_: IOException) {
            AccessCodeRecognition.MifareReadFailed
        } finally {
            runCatching { technology.close() }
        }
    }

    private const val AIME_DATA_BLOCK = 2
    private const val AIC_SPAD0_BLOCK = 0
    private const val AIC_ID_BLOCK = 0x82
    private const val AIC_SYSTEM_CODE = 0x88B4
    private val AIME_KEY = byteArrayOf(0x57, 0x43, 0x43, 0x46, 0x76, 0x32)
    private val AIC_PMM = byteArrayOf(
        0x00,
        0xF1.toByte(),
        0x00,
        0x00,
        0x00,
        0x01,
        0x43,
        0x00
    )
}
