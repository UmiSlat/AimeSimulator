package io.github.umislat.aimesimulator.nfc

import io.github.umislat.aimesimulator.data.CardProfile
import io.github.umislat.aimesimulator.data.HexCodec

internal class CardImage(profile: CardProfile) {
    private val blocks = HashMap<Int, ByteArray>()

    init {
        for (number in 0x00..0x0D) blocks[number] = ByteArray(16)
        blocks[0x0E] = ByteArray(16) { 0xFF.toByte() }
        blocks[0x80] = ByteArray(16)
        blocks[0x81] = ByteArray(16)
        blocks[0x83] = fixedBlock("000000000000000000F1000000014300")
        blocks[0x84] = fixedBlock("0000")
        blocks[0x85] = fixedBlock("88B4")
        blocks[0x86] = fixedBlock("0001")
        blocks[0x87] = ByteArray(16)
        blocks[0x88] = fixedBlock("FE7F000007011E00FF41FF4101")
        blocks[0x90] = fixedBlock("000000")
        blocks[0x91] = ByteArray(16)
        blocks[0x92] = fixedBlock("0000")

        blocks[0x00] = profile.spad0?.let { fixedBlock(it) } ?: ByteArray(16)
        val idBlock = profile.idBlock?.let { fixedBlock(it) } ?: ByteArray(16)
        HexCodec.decode(profile.idm, 8)?.copyInto(idBlock, 0)
        blocks[0x82] = idBlock
    }

    fun read(number: Int): ByteArray = blocks[number]?.copyOf() ?: ByteArray(16)

    private fun fixedBlock(value: String): ByteArray {
        val source = HexCodec.decode(value) ?: return ByteArray(16)
        return ByteArray(16).also { block ->
            source.copyInto(block, endIndex = minOf(block.size, source.size))
        }
    }
}
