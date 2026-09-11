package com.example.casinogames.games.shootout

import com.example.casinogames.games.core.Card

/**
 * Texas Shootout. Four cards each from a six-deck shoe; the player keeps two
 * (or splits into two hands for a second poker bet), the dealer keeps two by
 * house way, and five community cards decide it. Best five of seven, player
 * wins even money, the dealer takes every tie. There is no play bet and no
 * qualifying — the choice of two is the whole game.
 */
object ShootoutRules {

    const val DECKS = 6

    /**
     * The Bonus, on the player's own best five, win or lose. This is Galaxy's
     * top-heavy pay table, the one the user picked for its big numbers:
     *
     *   Suited Five of a Kind .. 5,000 to 1      Five of a Kind ...... 50 to 1
     *   Royal Flush ........... 1,000 to 1      Four of a Kind ....... 5 to 1
     *   Suited Four of a Kind ... 500 to 1      Full House ........... 3 to 1
     *   Straight Flush .......... 100 to 1       Flush ................ 2 to 1
     *                                            Straight ............. 1 to 1
     *
     * Three of a kind and under lose on this table; the common table pushes
     * trips and pays quads 7, but its ceiling is a fifth of this one's.
     * Suited quads is the house's own rung, ranked between the royal and
     * the straight flush by rarity, and priced a rung under the royal.
     */
    enum class BonusPay(val label: String, val payout: Int) {
        FIVE_KIND_SUITED("Suited Five of a Kind", 5_000),
        ROYAL_FLUSH("Royal Flush", 1_000),
        FOUR_KIND_SUITED("Suited Four of a Kind", 500),
        STRAIGHT_FLUSH("Straight Flush", 100),
        FIVE_KIND("Five of a Kind", 50),
        FOUR_KIND("Four of a Kind", 5),
        FULL_HOUSE("Full House", 3),
        FLUSH("Flush", 2),
        STRAIGHT("Straight", 1),
    }

    fun bonusRung(hand: ShootoutHandValue): BonusPay? = when (hand.category) {
        ShootoutCategory.FIVE_KIND_SUITED -> BonusPay.FIVE_KIND_SUITED
        ShootoutCategory.ROYAL_FLUSH -> BonusPay.ROYAL_FLUSH
        ShootoutCategory.FOUR_KIND_SUITED -> BonusPay.FOUR_KIND_SUITED
        ShootoutCategory.STRAIGHT_FLUSH -> BonusPay.STRAIGHT_FLUSH
        ShootoutCategory.FIVE_KIND -> BonusPay.FIVE_KIND
        ShootoutCategory.FOUR_KIND -> BonusPay.FOUR_KIND
        ShootoutCategory.FULL_HOUSE -> BonusPay.FULL_HOUSE
        ShootoutCategory.FLUSH -> BonusPay.FLUSH
        ShootoutCategory.STRAIGHT -> BonusPay.STRAIGHT
        else -> null
    }

    /**
     * The dealer's house way, Galaxy's list top to bottom. Every two-card
     * combination out of the four is graded and the best grade is kept; within
     * a grade the higher cards win. Suited means the same suit, which from a
     * six-deck shoe can also mean the very same card twice.
     *
     *    1. Pair of eights or better       8. Ace high, unsuited
     *    2. Ace-jack or better             9. Face card high, suited
     *    3. Suited pair, twos to sevens   10. Face card high, unsuited
     *    4. Unsuited pair, twos to sevens 11. Connected, suited
     *    5. Ace high, suited              12. Connected, unsuited
     *    6. Both ten or better, suited    13. Two highest, suited
     *    7. Both ten or better, unsuited  14. Two highest, unsuited
     */
    fun houseWay(four: List<Card>): List<Card> {
        require(four.size == 4) { "the dealer holds four cards" }
        var best: List<Card>? = null
        var bestGrade: List<Int>? = null
        for (i in 0 until 4) for (j in i + 1 until 4) {
            val pick = orderPair(four[i], four[j])
            val grade = grade(pick[0], pick[1])
            if (bestGrade == null || compareGrades(grade, bestGrade) < 0) {
                best = pick
                bestGrade = grade
            }
        }
        return best!!
    }

    /** High card first; between equal ranks, suits in enum order so it is stable. */
    private fun orderPair(a: Card, b: Card): List<Card> {
        val va = ShootoutEval.value(a.rank)
        val vb = ShootoutEval.value(b.rank)
        return when {
            va > vb -> listOf(a, b)
            va < vb -> listOf(b, a)
            a.suit.ordinal <= b.suit.ordinal -> listOf(a, b)
            else -> listOf(b, a)
        }
    }

    /**
     * A grade is the tier, then the cards to break ties inside it — negated so
     * that a plain ascending comparison prefers the higher card.
     */
    private fun grade(high: Card, low: Card): List<Int> {
        val h = ShootoutEval.value(high.rank)
        val l = ShootoutEval.value(low.rank)
        val suited = high.suit == low.suit
        val pair = h == l
        val tier = when {
            pair && h >= 8 -> 1
            h == 14 && l >= 11 -> 2
            pair && suited -> 3
            pair -> 4
            h == 14 && suited -> 5
            l >= 10 && suited -> 6
            l >= 10 -> 7
            h == 14 -> 8
            h >= 11 && suited -> 9
            h >= 11 -> 10
            h - l == 1 && suited -> 11
            h - l == 1 -> 12
            suited -> 13
            else -> 14
        }
        return listOf(tier, -h, -l)
    }

    private fun compareGrades(a: List<Int>, b: List<Int>): Int {
        for (i in a.indices) {
            if (a[i] != b[i]) return a[i] - b[i]
        }
        return 0
    }

    enum class Outcome { WIN, LOSE }

    /**
     * The Bad Beat, DJ Wild's ladder brought over: it pays off whichever hand
     * *lost* the showdown, when that hand was trips or better — the point
     * being that a big hand went down. The shoe-only hands slot in by rank:
     * suited quads beside the straight flush, five of a kind between that
     * and quads.
     *
     *   Suited Five of a Kind .. 10,000 to 1     Four of a Kind ...... 500 to 1
     *   Royal Flush ............ 10,000 to 1     Full House .......... 400 to 1
     *   Suited Four of a Kind ... 5,000 to 1     Flush ............... 300 to 1
     *   Straight Flush .......... 5,000 to 1     Straight ............ 100 to 1
     *   Five of a Kind .......... 1,000 to 1     Three of a Kind ....... 9 to 1
     *
     * The dealer takes ties here, so a tied player hand counts as beaten.
     */
    enum class BadBeatPay(val label: String, val payout: Int) {
        FIVE_KIND_SUITED("Suited Five of a Kind", 10_000),
        ROYAL_FLUSH("Royal Flush", 10_000),
        FOUR_KIND_SUITED("Suited Four of a Kind", 5_000),
        STRAIGHT_FLUSH("Straight Flush", 5_000),
        FIVE_KIND("Five of a Kind", 1_000),
        FOUR_KIND("Four of a Kind", 500),
        FULL_HOUSE("Full House", 400),
        FLUSH("Flush", 300),
        STRAIGHT("Straight", 100),
        THREE_KIND("Three of a Kind", 9),
    }

    fun badBeatRung(hand: ShootoutHandValue): BadBeatPay? = when (hand.category) {
        ShootoutCategory.FIVE_KIND_SUITED -> BadBeatPay.FIVE_KIND_SUITED
        ShootoutCategory.ROYAL_FLUSH -> BadBeatPay.ROYAL_FLUSH
        ShootoutCategory.FOUR_KIND_SUITED -> BadBeatPay.FOUR_KIND_SUITED
        ShootoutCategory.STRAIGHT_FLUSH -> BadBeatPay.STRAIGHT_FLUSH
        ShootoutCategory.FIVE_KIND -> BadBeatPay.FIVE_KIND
        ShootoutCategory.FOUR_KIND -> BadBeatPay.FOUR_KIND
        ShootoutCategory.FULL_HOUSE -> BadBeatPay.FULL_HOUSE
        ShootoutCategory.FLUSH -> BadBeatPay.FLUSH
        ShootoutCategory.STRAIGHT -> BadBeatPay.STRAIGHT
        ShootoutCategory.THREE_KIND -> BadBeatPay.THREE_KIND
        else -> null
    }

    /** What the Bad Beat found, and on whose hand. */
    data class BadBeat(val rung: BadBeatPay, val onDealer: Boolean) {
        val label: String get() = if (onDealer) "Dealer ${rung.label}" else rung.label
    }

    fun badBeat(playerHand: ShootoutHandValue, dealerHand: ShootoutHandValue, outcome: Outcome): BadBeat? {
        val onDealer = outcome == Outcome.WIN
        val losing = if (onDealer) dealerHand else playerHand
        return badBeatRung(losing)?.let { BadBeat(it, onDealer) }
    }

    /** One hand, settled. Returns are gross — stake plus winnings. */
    data class HandSettlement(
        val playerHand: ShootoutHandValue,
        val dealerHand: ShootoutHandValue,
        val outcome: Outcome,
        val pokerReturn: Double,
        val bonusReturn: Double,
        val bonusWin: BonusPay?,
        val badBeatReturn: Double = 0.0,
        val badBeatWin: BadBeat? = null,
    ) {
        val totalReturn: Double get() = pokerReturn + bonusReturn + badBeatReturn
    }

    /**
     * Settles one player hand against the dealer's kept two on the board. The
     * dealer wins ties, so a push does not exist here. The bonus rides the
     * player's own five and pays whichever way the showdown went; the Bad
     * Beat reads the hand that lost, whichever side that was.
     */
    fun settle(
        playerHole: List<Card>,
        dealerHole: List<Card>,
        board: List<Card>,
        poker: Double,
        bonus: Double,
        badBeat: Double = 0.0,
    ): HandSettlement {
        require(playerHole.size == 2 && dealerHole.size == 2 && board.size == 5)
        val playerHand = ShootoutEval.best(playerHole + board)
        val dealerHand = ShootoutEval.best(dealerHole + board)
        val outcome = if (playerHand > dealerHand) Outcome.WIN else Outcome.LOSE

        val bonusWin = if (bonus > 0) bonusRung(playerHand) else null
        val bonusReturn = if (bonusWin != null) bonus * (bonusWin.payout + 1) else 0.0

        val badBeatWin = if (badBeat > 0) badBeat(playerHand, dealerHand, outcome) else null
        val badBeatReturn = if (badBeatWin != null) badBeat * (badBeatWin.rung.payout + 1) else 0.0

        return HandSettlement(
            playerHand, dealerHand, outcome,
            pokerReturn = if (outcome == Outcome.WIN) poker * 2 else 0.0,
            bonusReturn = bonusReturn,
            bonusWin = bonusWin,
            badBeatReturn = badBeatReturn,
            badBeatWin = badBeatWin,
        )
    }
}
