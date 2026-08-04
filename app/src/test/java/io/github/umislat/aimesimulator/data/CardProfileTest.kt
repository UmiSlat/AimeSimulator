package io.github.umislat.aimesimulator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardProfileTest {
    @Test fun routesOnlyCompatibilityIdentifier() {
        val profile = requireNotNull(CardProfile.create("Test", "4D494D494245414D"))
        assertEquals("4D494D494245414D", profile.routedIdm(false))
        assertEquals(CardProfile.COMPATIBILITY_IDM, profile.routedIdm(true))
    }

    @Test fun validatesCapturedBlocks() {
        assertNull(CardProfile.create("Invalid", "02FE123456789ABC", spad0 = "00"))
    }

    @Test fun normalizesAndFormatsPrintedAccessCode() {
        val profile = requireNotNull(CardProfile.create(
            "Test",
            "02FE123456789ABC",
            accessCode = "1234 5678-9012 3456 7890"
        ))
        assertEquals("12345678901234567890", profile.accessCode)
        assertEquals("1234 5678 9012 3456 7890", profile.formattedAccessCode())
    }

    @Test fun rejectsInvalidPrintedAccessCode() {
        assertNull(CardProfile.create(
            "Invalid",
            "02FE123456789ABC",
            accessCode = "1234567890123456789X"
        ))
    }

    @Test fun acceptsRecognizedAmusementIcFields() {
        val profile = requireNotNull(CardProfile.create(
            label = "",
            idm = "012E112233445566",
            spad0 = "FE27165A9396EEC9E43AA991FD3D8CAF",
            accessCode = "5012 3456 7890 1234 5678"
        ))

        assertEquals("Card 5566", profile.label)
        assertEquals("012E112233445566", profile.idm)
        assertEquals("FE27165A9396EEC9E43AA991FD3D8CAF", profile.spad0)
        assertEquals("50123456789012345678", profile.accessCode)
    }
}
