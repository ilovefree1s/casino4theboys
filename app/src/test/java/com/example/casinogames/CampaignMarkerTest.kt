package com.example.casinogames

import com.example.casinogames.campaign.CAMPAIGN_START
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.DEBT_CEILING
import com.example.casinogames.campaign.Room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The credit line. Unlimited markers made the campaign unlosable — debt piled
 * up past any hope of clearing while the loans kept coming — so the house cuts
 * everyone off at [DEBT_CEILING], and busting past it ends the run.
 */
class CampaignMarkerTest {

    // Campaign is a singleton; every test starts it from a fresh run. Prefs
    // are never initialised here, so nothing touches disk.
    @Before
    fun fresh() = Campaign.restart()

    @Test
    fun `the house signs exactly three markers`() {
        repeat(3) {
            assertTrue("marker ${it + 1} should be signable", Campaign.canTakeMarker)
            Campaign.takeMarker()
        }
        assertEquals(22_500.0, Campaign.debt, 0.001)
        assertTrue(Campaign.debt <= DEBT_CEILING)
        assertFalse("the fourth marker should be refused", Campaign.canTakeMarker)
    }

    @Test
    fun `a refused marker moves no money`() {
        repeat(3) { Campaign.takeMarker() }
        val purse = Campaign.bankroll
        val owed = Campaign.debt
        Campaign.takeMarker()
        assertEquals(purse, Campaign.bankroll, 0.001)
        assertEquals(owed, Campaign.debt, 0.001)
        assertEquals(3, Campaign.markersTaken)
    }

    @Test
    fun `a new campaign buries the debt`() {
        repeat(3) { Campaign.takeMarker() }
        Campaign.restart()
        assertEquals(CAMPAIGN_START, Campaign.bankroll, 0.001)
        assertEquals(0.0, Campaign.debt, 0.001)
        assertEquals(0, Campaign.markersTaken)
        assertEquals(Room.BASEMENT, Campaign.room)
        assertTrue(Campaign.canTakeMarker)
    }

    @Test
    fun `paying the marker down reopens the line`() {
        repeat(3) { Campaign.takeMarker() }
        assertFalse(Campaign.canTakeMarker)
        // Three markers' cash plus the starting purse is 20,000 — short of the
        // 22,500 owed, so the pit rightly refuses until a win covers it.
        Campaign.payMarker()
        assertTrue(Campaign.debt > 0)
        Campaign.payOut(2_500.0)
        Campaign.payMarker()
        assertEquals(0.0, Campaign.debt, 0.001)
        assertTrue(Campaign.canTakeMarker)
    }
}
