package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.campaign.CAMPAIGN_GOAL
import com.example.casinogames.campaign.FreePlayLimits
import com.example.casinogames.campaign.Room
import com.example.casinogames.campaign.limitsFor
import com.example.casinogames.games.djwild.DjWildDeck
import com.example.casinogames.games.djwild.DjWildEval
import com.example.casinogames.games.djwild.DjWildRules
import com.example.casinogames.games.djwild.DjWildRules.BadBeatPay
import com.example.casinogames.games.djwild.DjWildRules.BlindPay
import com.example.casinogames.games.djwild.DjWildRules.TripsPay
import com.example.casinogames.games.djwild.WildCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DJ Wild's deck and its wild-card hand ranking. */
class DjWildHandTest {

    private fun c(spec: String): Card {
        val rank = when (spec[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO; 'W' -> Rank.JOKER
            else -> error("bad rank in $spec")
        }
        val suit = when (spec[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; 'c' -> Suit.CLUBS
            else -> error("bad suit in $spec")
        }
        return Card(rank, suit)
    }

    private fun hand(vararg specs: String) = specs.map(::c)

    private fun cat(vararg specs: String) = DjWildEval.score(hand(*specs)).category

    // ---- the deck ----

    @Test
    fun `every deuce and the joker play wild`() {
        assertTrue(DjWildDeck.isWild(c("2s")))
        assertTrue(DjWildDeck.isWild(c("2h")))
        assertTrue(DjWildDeck.isWild(c("2d")))
        assertTrue(DjWildDeck.isWild(c("2c")))
        assertTrue(DjWildDeck.isWild(c("Ws")))
        assertFalse(DjWildDeck.isWild(c("3s")))
        assertFalse(DjWildDeck.isWild(c("As")))
    }

    @Test
    fun `the shoe deals fifty three cards and only one joker`() {
        val shoe = com.example.casinogames.games.core.Shoe(
            decks = DjWildDeck.DECKS,
            jokers = DjWildDeck.JOKERS,
        )
        assertEquals(53, shoe.cardsRemaining)
        val all = (1..53).map { shoe.draw() }
        assertEquals(1, all.count { it.isJoker })
        assertEquals(4, all.count { it.rank == Rank.TWO })
        // Five wilds in the deck: the four deuces and the joker.
        assertEquals(5, all.count(DjWildDeck::isWild))
        assertEquals(53, all.distinct().size)
    }

    @Test
    fun `an ordinary shoe still has no jokers`() {
        val shoe = com.example.casinogames.games.core.Shoe(decks = 1)
        assertEquals(52, shoe.cardsRemaining)
        assertEquals(0, (1..52).map { shoe.draw() }.count { it.isJoker })
    }

    // ---- natural hands rank as they always did ----

    @Test
    fun `hands without a wild score straight`() {
        assertEquals(WildCategory.HIGH_CARD, cat("As", "Kh", "9d", "7c", "5s"))
        assertEquals(WildCategory.PAIR, cat("As", "Ah", "9d", "7c", "5s"))
        assertEquals(WildCategory.TWO_PAIR, cat("As", "Ah", "9d", "9c", "5s"))
        assertEquals(WildCategory.THREE_KIND, cat("As", "Ah", "Ad", "9c", "5s"))
        assertEquals(WildCategory.STRAIGHT, cat("9s", "8h", "7d", "6c", "5s"))
        assertEquals(WildCategory.FLUSH, cat("As", "Js", "9s", "7s", "5s"))
        assertEquals(WildCategory.FULL_HOUSE, cat("As", "Ah", "Ad", "9c", "9s"))
        assertEquals(WildCategory.FOUR_KIND, cat("As", "Ah", "Ad", "Ac", "9s"))
        assertEquals(WildCategory.STRAIGHT_FLUSH, cat("9s", "8s", "7s", "6s", "5s"))
        assertEquals(WildCategory.ROYAL_FLUSH, cat("As", "Ks", "Qs", "Js", "Ts"))
    }

    // ---- a wild becomes whatever helps most ----

    @Test
    fun `one wild fills a pair into trips`() {
        assertEquals(WildCategory.THREE_KIND, cat("As", "Ah", "9d", "7c", "2s"))
    }

    @Test
    fun `one wild completes a straight`() {
        // 9-8-6-5 plus a wild is the seven.
        assertEquals(WildCategory.STRAIGHT, cat("9s", "8h", "6d", "5c", "2s"))
    }

    @Test
    fun `one wild completes a flush over a straight`() {
        // Four spades and a wild: a flush beats the straight it could also make.
        assertEquals(WildCategory.FLUSH, cat("As", "Js", "9s", "7s", "2h"))
    }

    @Test
    fun `one wild completes a royal flush`() {
        assertEquals(WildCategory.ROYAL_FLUSH, cat("As", "Ks", "Qs", "Js", "2h"))
    }

    @Test
    fun `a wild never plays as itself when something beats it`() {
        // The wild is a deuce, but reading it as one would leave only a pair.
        assertEquals(WildCategory.THREE_KIND, cat("2s", "As", "Ah", "9d", "7c"))
    }

    @Test
    fun `two wilds make four of a kind out of a pair`() {
        assertEquals(WildCategory.FOUR_KIND, cat("As", "Ah", "2d", "2c", "9s"))
    }

    @Test
    fun `three wilds and a pair make five of a kind`() {
        assertEquals(WildCategory.FIVE_KIND, cat("As", "Ah", "2d", "2c", "Ws"))
    }

    @Test
    fun `four wilds beat everything below the top`() {
        // Four wilds plus any card is five of a kind at worst.
        val v = DjWildEval.score(hand("2s", "2h", "2d", "Ws", "Ac"))
        assertTrue(
            "four wilds should reach five of a kind or better, got ${v.category}",
            v.category.ordinal >= WildCategory.FIVE_KIND.ordinal,
        )
    }

    @Test
    fun `all four deuces and the joker are five wilds`() {
        assertEquals(WildCategory.FIVE_WILDS, cat("2s", "2h", "2d", "2c", "Ws"))
    }

    // ---- comparing hands ----

    @Test
    fun `a wild trips loses to natural quads`() {
        val wildTrips = DjWildEval.score(hand("As", "Ah", "9d", "7c", "2s"))
        val quads = DjWildEval.score(hand("9s", "9h", "9d", "9c", "5s"))
        assertTrue(quads > wildTrips)
    }

    @Test
    fun `same category splits on the higher rank`() {
        val aces = DjWildEval.score(hand("As", "Ah", "Ad", "9c", "5s"))
        val kings = DjWildEval.score(hand("Ks", "Kh", "Kd", "9c", "5s"))
        assertTrue(aces > kings)
        assertEquals(0, aces.compareTo(DjWildEval.score(hand("Ac", "Ad", "Ah", "9s", "5h"))))
    }

    @Test
    fun `five wilds outrank a royal flush`() {
        val fiveWilds = DjWildEval.score(hand("2s", "2h", "2d", "2c", "Ws"))
        val royal = DjWildEval.score(hand("As", "Ks", "Qs", "Js", "Ts"))
        assertTrue(fiveWilds > royal)
    }
}

/** The Bad Beat side bet, read off the felt the user supplied. */
class DjWildBadBeatTest {

    private fun c(spec: String): Card {
        val rank = when (spec[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO; 'W' -> Rank.JOKER
            else -> error("bad rank in $spec")
        }
        val suit = when (spec[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; 'c' -> Suit.CLUBS
            else -> error("bad suit in $spec")
        }
        return Card(rank, suit)
    }

    private fun hand(vararg specs: String) = specs.map(::c)

    // ---- the ladder, rung by rung ----

    @Test
    fun `every rung pays what the felt says`() {
        assertEquals(10_000, BadBeatPay.ROYAL_FLUSH.payout)
        assertEquals(10_000, BadBeatPay.QUINTS.payout)
        assertEquals(5_000, BadBeatPay.STRAIGHT_FLUSH.payout)
        assertEquals(500, BadBeatPay.QUADS.payout)
        assertEquals(400, BadBeatPay.FULL_HOUSE.payout)
        assertEquals(300, BadBeatPay.FLUSH.payout)
        assertEquals(100, BadBeatPay.STRAIGHT.payout)
        assertEquals(9, BadBeatPay.TRIPS.payout)
    }

    @Test
    fun `hands map onto the right rung`() {
        assertEquals(BadBeatPay.ROYAL_FLUSH, DjWildRules.badBeatRung(hand("As", "Ks", "Qs", "Js", "Ts")))
        assertEquals(BadBeatPay.STRAIGHT_FLUSH, DjWildRules.badBeatRung(hand("9s", "8s", "7s", "6s", "5s")))
        assertEquals(BadBeatPay.QUADS, DjWildRules.badBeatRung(hand("As", "Ah", "Ad", "Ac", "9s")))
        assertEquals(BadBeatPay.FULL_HOUSE, DjWildRules.badBeatRung(hand("As", "Ah", "Ad", "9c", "9s")))
        assertEquals(BadBeatPay.FLUSH, DjWildRules.badBeatRung(hand("As", "Js", "9s", "7s", "5s")))
        assertEquals(BadBeatPay.STRAIGHT, DjWildRules.badBeatRung(hand("9s", "8h", "7d", "6c", "5s")))
        assertEquals(BadBeatPay.TRIPS, DjWildRules.badBeatRung(hand("As", "Ah", "Ad", "9c", "5s")))
    }

    @Test
    fun `quints pay the same as a royal`() {
        val quints = DjWildRules.badBeatRung(hand("As", "Ah", "2d", "2c", "Ws"))
        assertEquals(DjWildRules.BadBeatPay.QUINTS, quints)
        assertEquals(10_000, quints?.payout)
    }

    @Test
    fun `five wilds pay as quints, the felt having no rung of its own for them`() {
        assertEquals(
            DjWildRules.BadBeatPay.QUINTS,
            DjWildRules.badBeatRung(hand("2s", "2h", "2d", "2c", "Ws")),
        )
    }

    @Test
    fun `two pair and below pay nothing`() {
        assertNull(DjWildRules.badBeatRung(hand("As", "Ah", "9d", "9c", "5s")))
        assertNull(DjWildRules.badBeatRung(hand("As", "Ah", "9d", "7c", "5s")))
        assertNull(DjWildRules.badBeatRung(hand("As", "Kh", "9d", "7c", "5s")))
    }

    // ---- it pays the hand that lost ----

    @Test
    fun `the losing hand is the one that pays`() {
        // Player's flush goes down to the dealer's full house: the flush pays.
        val player = hand("As", "Js", "9s", "7s", "5s")
        val dealer = hand("Kh", "Kd", "Kc", "9h", "9d")
        val hit = DjWildRules.badBeat(player, dealer, folded = false)
        assertEquals(DjWildRules.BadBeatPay.FLUSH, hit?.rung)
        assertEquals(false, hit?.onDealer)
        assertEquals(300 * 100.0 + 100, DjWildRules.settleBadBeat(player, dealer, false, 100), 0.001)
    }

    @Test
    fun `a dealer hand that loses big pays too`() {
        // The dealer's straight flush loses to five wilds; the dealer's hand pays.
        val player = hand("2s", "2h", "2d", "2c", "Ws")
        val dealer = hand("9s", "8s", "7s", "6s", "5s")
        val hit = DjWildRules.badBeat(player, dealer, folded = false)
        assertEquals(DjWildRules.BadBeatPay.STRAIGHT_FLUSH, hit?.rung)
        assertEquals(true, hit?.onDealer)
        assertEquals("Dealer Straight Flush", hit?.label)
    }

    @Test
    fun `the winning hand never pays`() {
        // Player's royal beats the dealer's rags: nothing was beaten.
        val player = hand("As", "Ks", "Qs", "Js", "Ts")
        val dealer = hand("9h", "7d", "5c", "4s", "3h")
        assertNull(DjWildRules.badBeat(player, dealer, folded = false))
        assertEquals(0.0, DjWildRules.settleBadBeat(player, dealer, false, 100), 0.001)
    }

    @Test
    fun `a push pays nothing, nothing having been beaten`() {
        val player = hand("As", "Ah", "Ad", "9c", "5s")
        val dealer = hand("Ac", "Ah", "Ad", "9s", "5h")
        assertNull(DjWildRules.badBeat(player, dealer, folded = false))
    }

    @Test
    fun `a folded hand still counts as the player losing`() {
        val player = hand("As", "Js", "9s", "7s", "5s")
        val dealer = hand("9h", "7d", "5c", "4s", "3h")
        val hit = DjWildRules.badBeat(player, dealer, folded = true)
        assertEquals(DjWildRules.BadBeatPay.FLUSH, hit?.rung)
        assertEquals(false, hit?.onDealer)
    }

    @Test
    fun `no stake, no payout`() {
        val player = hand("As", "Js", "9s", "7s", "5s")
        val dealer = hand("Kh", "Kd", "Kc", "9h", "9d")
        assertEquals(0.0, DjWildRules.settleBadBeat(player, dealer, false, 0), 0.001)
    }

    // ---- the cap that keeps it off the campaign ----

    @Test
    fun `the bad beat is capped far below the ordinary side bets`() {
        Room.entries.forEach { room ->
            assertTrue(
                "${room.roomName} should cap the bad beat below its side bets",
                room.badBeatMax < room.sideMax,
            )
        }
        // Every rung is a whole $25 chip, so the house never takes a clipped bet.
        Room.entries.forEach { room ->
            assertEquals(
                "${room.roomName} should cap the bad beat on a whole chip",
                0, room.badBeatMax % 25,
            )
        }
        // The point of the cap: a royal on the cheapest table is a great night,
        // not the whole million-dollar campaign in one hand.
        val basementRoyal = Room.BASEMENT.badBeatMax * DjWildRules.BadBeatPay.ROYAL_FLUSH.payout
        assertEquals(250_000, basementRoyal)
        assertTrue("a Basement royal must not win the campaign", basementRoyal < CAMPAIGN_GOAL)
    }

    @Test
    fun `the table will not take more than the bad beat cap`() {
        val basement = limitsFor(campaign = true)
        // limitsFor reads the live campaign, which starts in the Basement.
        assertEquals(25, basement.badBeatMax)
        assertEquals(25, basement.allowBadBeat(wanted = 25, alreadyOn = 0))
        assertEquals(0, basement.allowBadBeat(wanted = 25, alreadyOn = 25))
        // Play testing has no house behind it, so nothing is capped.
        assertEquals(25, FreePlayLimits.allowBadBeat(wanted = 25, alreadyOn = 500))
    }
}

/**
 * The Trips side bet, which pays two ways: a hand made with a wild pays one
 * ladder, one that stands up on its own pays a far richer one.
 */
class DjWildTripsTest {

    private fun c(spec: String): Card {
        val rank = when (spec[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO; 'W' -> Rank.JOKER
            else -> error("bad rank in $spec")
        }
        val suit = when (spec[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; 'c' -> Suit.CLUBS
            else -> error("bad suit in $spec")
        }
        return Card(rank, suit)
    }

    private fun hand(vararg specs: String) = specs.map(::c)

    @Test
    fun `both ladders read the way the felt does`() {
        assertEquals(2_000, TripsPay.FIVE_WILDS.wild)
        assertNull("only a wild can make five wilds", TripsPay.FIVE_WILDS.natural)
        assertEquals(90, TripsPay.ROYAL_FLUSH.wild)
        assertEquals(1_000, TripsPay.ROYAL_FLUSH.natural)
        assertEquals(70, TripsPay.QUINTS.wild)
        assertNull("only a wild can make quints", TripsPay.QUINTS.natural)
        assertEquals(25, TripsPay.STRAIGHT_FLUSH.wild)
        assertEquals(200, TripsPay.STRAIGHT_FLUSH.natural)
        assertEquals(6, TripsPay.QUADS.wild)
        assertEquals(60, TripsPay.QUADS.natural)
        assertEquals(5, TripsPay.FULL_HOUSE.wild)
        assertEquals(30, TripsPay.FULL_HOUSE.natural)
        assertEquals(4, TripsPay.FLUSH.wild)
        assertEquals(25, TripsPay.FLUSH.natural)
        assertEquals(3, TripsPay.STRAIGHT.wild)
        assertEquals(20, TripsPay.STRAIGHT.natural)
        assertEquals(1, TripsPay.TRIPS.wild)
        assertEquals(6, TripsPay.TRIPS.natural)
    }

    @Test
    fun `a hand with no wild in it pays the natural ladder`() {
        val hit = DjWildRules.trips(hand("As", "Ks", "Qs", "Js", "Ts"))
        assertEquals(TripsPay.ROYAL_FLUSH, hit?.rung)
        assertTrue(hit!!.natural)
        assertEquals(1_000, hit.payout)
        assertEquals("Royal Flush (natural)", hit.label)
    }

    @Test
    fun `a hand leaning on a wild pays the wild ladder`() {
        // The deuce has to become the ten for this to be a royal.
        val hit = DjWildRules.trips(hand("As", "Ks", "Qs", "Js", "2h"))
        assertEquals(TripsPay.ROYAL_FLUSH, hit?.rung)
        assertFalse(hit!!.natural)
        assertEquals(90, hit.payout)
    }

    @Test
    fun `a deuce not used as a wild is still natural`() {
        // The felt is explicit about this one: 2-3-4-5-6 of spades stands up
        // as a straight flush with the deuce playing two, so it pays the
        // natural 200 rather than the wild 25 it could also be called.
        val hit = DjWildRules.trips(hand("2s", "3s", "4s", "5s", "6s"))
        assertEquals(TripsPay.STRAIGHT_FLUSH, hit?.rung)
        assertTrue("a deuce playing two keeps the hand natural", hit!!.natural)
        assertEquals(200, hit.payout)
    }

    @Test
    fun `a deuce that only makes trips by going wild pays the wild rung`() {
        val hit = DjWildRules.trips(hand("As", "Ah", "2d", "9c", "7s"))
        assertEquals(TripsPay.TRIPS, hit?.rung)
        assertFalse(hit!!.natural)
        assertEquals(1, hit.payout)
    }

    @Test
    fun `the joker can never be natural`() {
        assertNull(DjWildEval.naturalScore(hand("As", "Ks", "Qs", "Js", "Ws")))
        val hit = DjWildRules.trips(hand("As", "Ks", "Qs", "Js", "Ws"))
        assertEquals(TripsPay.ROYAL_FLUSH, hit?.rung)
        assertFalse(hit!!.natural)
        assertEquals(90, hit.payout)
    }

    @Test
    fun `quints and five wilds pay only the wild column`() {
        val quints = DjWildRules.trips(hand("As", "Ah", "2d", "2c", "Ws"))
        assertEquals(TripsPay.QUINTS, quints?.rung)
        assertEquals(70, quints?.payout)

        val fiveWilds = DjWildRules.trips(hand("2s", "2h", "2d", "2c", "Ws"))
        assertEquals(TripsPay.FIVE_WILDS, fiveWilds?.rung)
        assertEquals(2_000, fiveWilds?.payout)
    }

    @Test
    fun `two pair and below pay nothing`() {
        assertNull(DjWildRules.trips(hand("As", "Ah", "9d", "9c", "5s")))
        assertNull(DjWildRules.trips(hand("As", "Kh", "9d", "7c", "5s")))
    }

    @Test
    fun `the stake comes back with the win`() {
        // Natural quads at 60 to 1 on 25 returns the 25 plus 1,500.
        assertEquals(1_525.0, DjWildRules.settleTrips(hand("As", "Ah", "Ad", "Ac", "9s"), 25), 0.001)
        assertEquals(0.0, DjWildRules.settleTrips(hand("As", "Kh", "9d", "7c", "5s"), 25), 0.001)
        assertEquals(0.0, DjWildRules.settleTrips(hand("As", "Ah", "Ad", "Ac", "9s"), 0), 0.001)
    }
}

/** The Blind ladder and the way a whole hand settles. */
class DjWildSettleTest {

    private fun c(spec: String): Card {
        val rank = when (spec[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO; 'W' -> Rank.JOKER
            else -> error("bad rank in $spec")
        }
        val suit = when (spec[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; 'c' -> Suit.CLUBS
            else -> error("bad suit in $spec")
        }
        return Card(rank, suit)
    }

    private fun hand(vararg specs: String) = specs.map(::c)

    /** A plain hand with nothing in it, for the other side of a showdown. */
    private val rags = hand("9h", "7d", "5c", "4s", "3h")
    private val betterRags = hand("Kh", "7d", "5c", "4s", "3h")

    @Test
    fun `the blind ladder reads the way the felt does`() {
        assertEquals(1_000, BlindPay.FIVE_WILDS.payout)
        assertEquals(50, BlindPay.ROYAL_FLUSH.payout)
        assertEquals(10, BlindPay.QUINTS.payout)
        assertEquals(9, BlindPay.STRAIGHT_FLUSH.payout)
        assertEquals(4, BlindPay.QUADS.payout)
        assertEquals(3, BlindPay.FULL_HOUSE.payout)
        // The felt pays a flush the same three as a full house. It stays that way.
        assertEquals(3, BlindPay.FLUSH.payout)
        assertEquals(1, BlindPay.STRAIGHT.payout)
    }

    @Test
    fun `trips or less pushes the blind`() {
        assertNull(DjWildRules.blindRung(hand("As", "Ah", "Ad", "9c", "5s")))
        assertNull(DjWildRules.blindRung(hand("As", "Ah", "9d", "9c", "5s")))
        assertNull(DjWildRules.blindRung(hand("As", "Kh", "9d", "7c", "5s")))
    }

    @Test
    fun `a winning straight pays the blind one to one`() {
        val player = hand("9s", "8h", "7d", "6c", "5s")
        val s = DjWildRules.settle(player, rags, 25.0, 25.0, 50.0, 0.0, 0.0, folded = false)
        assertEquals(DjWildRules.DjOutcome.WIN, s.outcome)
        assertEquals(BlindPay.STRAIGHT, s.blindWin)
        assertEquals(50.0, s.anteReturn, 0.001)   // ante 25 paid 1:1
        assertEquals(50.0, s.blindReturn, 0.001)  // blind 25 paid 1:1
        assertEquals(100.0, s.playReturn, 0.001)  // play 50 paid 1:1
        assertEquals(200.0, s.totalReturn, 0.001)
    }

    @Test
    fun `a winning hand under a straight pushes the blind but still wins the rest`() {
        val player = hand("As", "Ah", "Ad", "9c", "5s")
        val s = DjWildRules.settle(player, rags, 25.0, 25.0, 50.0, 0.0, 0.0, folded = false)
        assertEquals(DjWildRules.DjOutcome.WIN, s.outcome)
        assertNull("trips or less is a push", s.blindWin)
        assertEquals(50.0, s.anteReturn, 0.001)
        assertEquals(25.0, s.blindReturn, 0.001)  // pushed: exactly what went up
        assertEquals(100.0, s.playReturn, 0.001)
    }

    @Test
    fun `a losing hand takes the blind down with it`() {
        val player = rags
        val dealer = hand("As", "Ah", "Ad", "Ac", "9s")
        val s = DjWildRules.settle(player, dealer, 25.0, 25.0, 50.0, 0.0, 0.0, folded = false)
        assertEquals(DjWildRules.DjOutcome.LOSE, s.outcome)
        assertEquals(0.0, s.totalReturn, 0.001)
    }

    @Test
    fun `a tie pushes everything`() {
        val player = hand("As", "Ah", "Ad", "9c", "5s")
        val dealer = hand("Ac", "Ad", "Ah", "9s", "5h")
        val s = DjWildRules.settle(player, dealer, 25.0, 25.0, 50.0, 0.0, 0.0, folded = false)
        assertEquals(DjWildRules.DjOutcome.PUSH, s.outcome)
        assertEquals(100.0, s.totalReturn, 0.001)  // 25 + 25 + 50 back
    }

    @Test
    fun `folding forfeits the ante and blind`() {
        val player = hand("9s", "8h", "7d", "6c", "5s")
        val s = DjWildRules.settle(player, betterRags, 25.0, 25.0, 0.0, 0.0, 0.0, folded = true)
        assertEquals(DjWildRules.DjOutcome.FOLD, s.outcome)
        assertEquals(0.0, s.totalReturn, 0.001)
    }

    @Test
    fun `the side bets ride whatever the hand does`() {
        // A folded natural straight flush still collects Trips at 200 to 1,
        // and the Bad Beat too, the folded hand having lost.
        val player = hand("2s", "3s", "4s", "5s", "6s")
        val s = DjWildRules.settle(player, rags, 25.0, 25.0, 0.0, 10.0, 5.0, folded = true)
        assertEquals(DjWildRules.DjOutcome.FOLD, s.outcome)
        assertEquals(TripsPay.STRAIGHT_FLUSH, s.tripsWin?.rung)
        assertTrue("a deuce playing two keeps it natural", s.tripsWin!!.natural)
        assertEquals(2_010.0, s.tripsReturn, 0.001)          // 10 at 200:1, stake back
        assertEquals(BadBeatPay.STRAIGHT_FLUSH, s.badBeatWin?.rung)
        assertEquals(25_005.0, s.badBeatReturn, 0.001)       // 5 at 5,000:1, stake back
    }

    @Test
    fun `five wilds pays every ladder it touches`() {
        val player = hand("2s", "2h", "2d", "2c", "Ws")
        val s = DjWildRules.settle(player, rags, 25.0, 25.0, 50.0, 10.0, 5.0, folded = false)
        assertEquals(DjWildRules.DjOutcome.WIN, s.outcome)
        assertEquals(BlindPay.FIVE_WILDS, s.blindWin)
        assertEquals(25_025.0, s.blindReturn, 0.001)   // 25 at 1,000:1
        assertEquals(TripsPay.FIVE_WILDS, s.tripsWin?.rung)
        assertEquals(20_010.0, s.tripsReturn, 0.001)   // 10 at 2,000:1
        // The dealer's rags never lost big, so the Bad Beat finds nothing.
        assertNull(s.badBeatWin)
    }

    @Test
    fun `the play bet is twice the ante`() {
        assertEquals(2, DjWildRules.PLAY_MULTIPLE)
    }
}
