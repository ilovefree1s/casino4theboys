package com.example.casinogames.games.djwild

import com.example.casinogames.games.core.Card

/** Rules specific to DJ Wild. */
object DjWildRules {

    /**
     * The Bad Beat side bet, read straight off the felt:
     *
     *   Royal Flush .... 10,000 to 1      Full House ..... 400 to 1
     *   Quints ......... 10,000 to 1      Flush .......... 300 to 1
     *   Straight Flush .. 5,000 to 1      Straight ....... 100 to 1
     *   Quads ............. 500 to 1      Trips ............ 9 to 1
     *
     * "Quints" is five of a kind, which only a wild hand can reach. Anything
     * under trips pays nothing.
     */
    enum class BadBeatPay(val label: String, val payout: Int) {
        ROYAL_FLUSH("Royal Flush", 10_000),
        QUINTS("Quints", 10_000),
        STRAIGHT_FLUSH("Straight Flush", 5_000),
        QUADS("Quads", 500),
        FULL_HOUSE("Full House", 400),
        FLUSH("Flush", 300),
        STRAIGHT("Straight", 100),
        TRIPS("Trips", 9),
    }

    /**
     * The Trips side bet, read straight off the felt. It pays two ways — a
     * hand made with a wild pays one ladder, a hand that stands up on its own
     * pays a far richer one:
     *
     *                        Wild      Natural
     *   Five Wilds .......  2,000        —
     *   Royal Flush ......     90      1,000
     *   Quints ...........     70        —
     *   Straight Flush ...     25        200
     *   Quads ............      6         60
     *   Full House .......      5         30
     *   Flush ............      4         25
     *   Straight .........      3         20
     *   Three of a Kind ..      1          6
     *
     * Quints and five wilds have no natural column: only a wild can make them.
     */
    enum class TripsPay(val label: String, val wild: Int, val natural: Int?) {
        FIVE_WILDS("Five Wilds", 2_000, null),
        ROYAL_FLUSH("Royal Flush", 90, 1_000),
        QUINTS("Quints", 70, null),
        STRAIGHT_FLUSH("Straight Flush", 25, 200),
        QUADS("Quads", 6, 60),
        FULL_HOUSE("Full House", 5, 30),
        FLUSH("Flush", 4, 25),
        STRAIGHT("Straight", 3, 20),
        TRIPS("Three of a Kind", 1, 6),
    }

    /** What Trips found: the rung, whether it stood up naturally, and the odds. */
    data class TripsHit(val rung: TripsPay, val natural: Boolean, val payout: Int) {
        val label: String get() = if (natural) "${rung.label} (natural)" else rung.label
    }

    private fun tripsRung(category: WildCategory): TripsPay? = when (category) {
        WildCategory.FIVE_WILDS -> TripsPay.FIVE_WILDS
        WildCategory.ROYAL_FLUSH -> TripsPay.ROYAL_FLUSH
        WildCategory.FIVE_KIND -> TripsPay.QUINTS
        WildCategory.STRAIGHT_FLUSH -> TripsPay.STRAIGHT_FLUSH
        WildCategory.FOUR_KIND -> TripsPay.QUADS
        WildCategory.FULL_HOUSE -> TripsPay.FULL_HOUSE
        WildCategory.FLUSH -> TripsPay.FLUSH
        WildCategory.STRAIGHT -> TripsPay.STRAIGHT
        WildCategory.THREE_KIND -> TripsPay.TRIPS
        else -> null
    }

    /**
     * The better of the two ladders. A hand can qualify twice over — 2-3-4-5-6
     * of spades is a natural straight flush at 200, and the deuce could
     * instead play wild for a seven-high straight flush at 25 — so the hand is
     * paid the way that pays it best, which is how a player would call it.
     */
    fun trips(hand: List<Card>): TripsHit? {
        val wildHit = tripsRung(DjWildEval.score(hand).category)
            ?.let { TripsHit(it, natural = false, payout = it.wild) }
        val naturalHit = DjWildEval.naturalScore(hand)
            ?.let { tripsRung(it.category) }
            ?.let { rung -> rung.natural?.let { TripsHit(rung, natural = true, payout = it) } }
        return listOfNotNull(wildHit, naturalHit).maxByOrNull { it.payout }
    }

    /** What comes back on Trips, stake included. */
    fun settleTrips(hand: List<Card>, stake: Int): Double {
        if (stake <= 0) return 0.0
        val hit = trips(hand) ?: return 0.0
        return stake * (hit.payout + 1.0)
    }

    /** What the Bad Beat found, and on whose hand. */
    data class BadBeat(val rung: BadBeatPay, val onDealer: Boolean) {
        val label: String get() = if (onDealer) "Dealer ${rung.label}" else rung.label
    }

    /**
     * The Bad Beat pays off whichever hand *lost* the showdown, when that hand
     * was trips or better — the point being that a big hand went down. A push
     * pays nothing, since nothing was beaten.
     *
     * A folded hand counts as the player's loss: the cards were dealt and the
     * dealer's are turned over, so a flush thrown away still lost.
     */
    fun badBeat(player: List<Card>, dealer: List<Card>, folded: Boolean): BadBeat? {
        val loserIsDealer = when {
            folded -> false
            else -> {
                val cmp = DjWildEval.score(player).compareTo(DjWildEval.score(dealer))
                if (cmp == 0) return null
                cmp > 0
            }
        }
        val losing = if (loserIsDealer) dealer else player
        return badBeatRung(losing)?.let { BadBeat(it, onDealer = loserIsDealer) }
    }

    /** What comes back on the Bad Beat, stake included. */
    fun settleBadBeat(
        player: List<Card>,
        dealer: List<Card>,
        folded: Boolean,
        stake: Int,
    ): Double {
        if (stake <= 0) return 0.0
        val hit = badBeat(player, dealer, folded) ?: return 0.0
        return stake * (hit.rung.payout + 1.0)
    }

    /** Which rung a five-card hand reaches, or null when it pays nothing. */
    fun badBeatRung(hand: List<Card>): BadBeatPay? = when (DjWildEval.score(hand).category) {
        // Five wilds is five of a kind made of deuces; the felt has no separate
        // rung for it, so it pays as quints.
        WildCategory.FIVE_WILDS -> BadBeatPay.QUINTS
        WildCategory.ROYAL_FLUSH -> BadBeatPay.ROYAL_FLUSH
        WildCategory.FIVE_KIND -> BadBeatPay.QUINTS
        WildCategory.STRAIGHT_FLUSH -> BadBeatPay.STRAIGHT_FLUSH
        WildCategory.FOUR_KIND -> BadBeatPay.QUADS
        WildCategory.FULL_HOUSE -> BadBeatPay.FULL_HOUSE
        WildCategory.FLUSH -> BadBeatPay.FLUSH
        WildCategory.STRAIGHT -> BadBeatPay.STRAIGHT
        WildCategory.THREE_KIND -> BadBeatPay.TRIPS
        else -> null
    }
}
