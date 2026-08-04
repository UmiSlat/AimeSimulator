package io.github.umislat.aimesimulator.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class RootlessAssessmentTest {
    @Test fun acceptsCompletedAimeRegistration() {
        val assessment = RootlessAssessment.from(
            HceSession.Report(HceSession.Stage.READY),
            compatibilityMode = false,
            hasProfile = true
        )

        assertEquals(RootlessAssessment.Outcome.REGISTRATION_ACCEPTED, assessment.outcome)
    }

    @Test fun distinguishesDynamicAndCompatibilityIdentifierFailures() {
        val report = HceSession.Report(HceSession.Stage.ID)

        assertEquals(
            RootlessAssessment.Outcome.DYNAMIC_ID_REJECTED,
            RootlessAssessment.from(report, compatibilityMode = false, hasProfile = true).outcome
        )
        assertEquals(
            RootlessAssessment.Outcome.COMPATIBILITY_ID_REJECTED,
            RootlessAssessment.from(report, compatibilityMode = true, hasProfile = true).outcome
        )
    }

    @Test fun reportsSystemCodeAsItsOwnBlocker() {
        val assessment = RootlessAssessment.from(
            HceSession.Report(HceSession.Stage.SYSTEM_CODE),
            compatibilityMode = true,
            hasProfile = true
        )

        assertEquals(RootlessAssessment.Outcome.SYSTEM_CODE_REJECTED, assessment.outcome)
    }

    @Test fun requiresAProfileBeforeTesting() {
        val assessment = RootlessAssessment.from(
            report = null,
            compatibilityMode = false,
            hasProfile = false
        )

        assertEquals(RootlessAssessment.Outcome.PROFILE_REQUIRED, assessment.outcome)
    }
}
