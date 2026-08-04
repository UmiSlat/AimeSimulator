package io.github.umislat.aimesimulator.nfc

import io.github.umislat.aimesimulator.data.HexCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AicAccessCodeCodecTest {
    @Test fun decryptsHinataSpad0Vector() {
        val encrypted = hex("000102030405060708090A0B0C0D0E0F")

        assertEquals(
            "A22CFA2A6084D652935A7BC550F8C8ED",
            HexCodec.encode(requireNotNull(AicAccessCodeCodec.decryptSpad0(encrypted)))
        )
    }

    @Test fun extractsAccessCodeFromEncryptedAicBlock() {
        val encrypted = hex("FE27165A9396EEC9E43AA991FD3D8CAF")

        assertEquals(
            "50123456789012345678",
            AicAccessCodeCodec.decodeAccessCode(encrypted)
        )
    }

    @Test fun rejectsEmptyAndUnexpectedBlocks() {
        assertNull(AicAccessCodeCodec.decodeAccessCode(ByteArray(16)))
        assertNull(AicAccessCodeCodec.decryptSpad0(ByteArray(15)))
    }

    @Test fun matchesHinataAmusementIcFingerprint() {
        val idm = hex("012E112233445566")
        val pmm = hex("00F1000000014300")

        assertTrue(PhysicalCardReader.isAicCandidate(idm, pmm, hex("88B4")))
        assertTrue(PhysicalCardReader.isAicCandidate(idm, pmm, ByteArray(0)))
        assertFalse(PhysicalCardReader.isAicCandidate(idm, pmm, hex("0003")))
        assertFalse(PhysicalCardReader.isAicCandidate(idm, hex("FFFFFFFFFFFFFFFF"), hex("88B4")))
    }

    private fun hex(value: String): ByteArray = requireNotNull(HexCodec.decode(value))
}
