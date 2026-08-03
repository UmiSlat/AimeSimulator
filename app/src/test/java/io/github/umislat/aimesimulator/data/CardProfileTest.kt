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
}
