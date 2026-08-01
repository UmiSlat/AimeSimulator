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
}
