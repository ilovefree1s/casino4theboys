package com.example.casinogames.games.baccarat

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank

enum class Outcome { PLAYER, BANKER, TIE }

enum class BetType(val displayName: String) {
    PLAYER("Player"),
    BANKER("Banker"),
    TIE("Tie"),
    PLAYER_PAIR("Player Pair"),
    BANKER_PAIR("Banker Pair"),
    FORTUNE_7("Fortune 7"),
    GOLDEN_8("Golden 8"),
    HEAVENLY_9("Heavenly 9"),
    BLAZING_7S("Blazing 7s"),
    COVER_ALL("Cover All"),
}

data class BaccaratHand(
    val player: List<Card>,
    val banker: List<Card>,
    val outcome: Outcome,
    val natural: Boolean,
) {
    val playerTotal: Int get() = BaccaratEngine.handTotal(player)
    val bankerTotal: Int get() = BaccaratEngine.handTotal(banker)
}

/**
 * Pure punto banco rules and payouts. Payouts mirror the felt:
 * Player 1:1 · Banker 1:1 less 5% commission · Tie 8:1 (P/B push) · Pairs 11:1
 * Fortune 7 40:1 · Golden 8 25:1 · Heavenly 9 75:1 both / 10:1 either ·
 * Blazing 7s 200:1 both / 75:1 either.
 */
object BaccaratEngine {

    fun handTotal(cards: List<Card>): Int = cards.sumOf { it.rank.baccaratValue } % 10

    fun playHand(draw: () -> Card): BaccaratHand {
        val player = mutableListOf(draw(), draw())
        val banker = mutableListOf(draw(), draw())
        val p = handTotal(player)
        val b = handTotal(banker)

        val natural = p >= 8 || b >= 8
        if (!natural) {
            var playerThird: Card? = null
            if (p <= 5) {
                playerThird = draw()
                player.add(playerThird)
            }
            if (playerThird == null) {
                if (b <= 5) banker.add(draw())
            } else {
                val t = playerThird.rank.baccaratValue
                val bankerDraws =
                    b <= 2 ||
                        (b == 3 && t != 8) ||
                        (b == 4 && t in 2..7) ||
                        (b == 5 && t in 4..7) ||
                        (b == 6 && (t == 6 || t == 7))
                if (bankerDraws) banker.add(draw())
            }
        }

        val pFinal = handTotal(player)
        val bFinal = handTotal(banker)
        val outcome = when {
            pFinal > bFinal -> Outcome.PLAYER
            bFinal > pFinal -> Outcome.BANKER
            else -> Outcome.TIE
        }
        return BaccaratHand(player, banker, outcome, natural)
    }

    /**
     * Amount returned per bet spot (stake included on wins/pushes). Spots that
     * were not staked or lost are absent. A push shows up as exactly the stake.
     */
    fun settleBreakdown(hand: BaccaratHand, bets: Map<BetType, Int>): Map<BetType, Double> {
        val returns = linkedMapOf<BetType, Double>()
        fun pay(type: BetType, multiplier: Double) {
            val stake = bets[type] ?: 0
            if (stake > 0) returns[type] = stake * multiplier
        }

        val pT = hand.playerTotal
        val bT = hand.bankerTotal
        val p3 = hand.player.size == 3
        val b3 = hand.banker.size == 3

        when (hand.outcome) {
            Outcome.PLAYER -> pay(BetType.PLAYER, 2.0)
            Outcome.BANKER -> pay(BetType.BANKER, 1.95)
            Outcome.TIE -> {
                pay(BetType.TIE, 9.0)
                pay(BetType.PLAYER, 1.0)
                pay(BetType.BANKER, 1.0)
            }
        }

        if (hand.player[0].rank == hand.player[1].rank) pay(BetType.PLAYER_PAIR, 12.0)
        if (hand.banker[0].rank == hand.banker[1].rank) pay(BetType.BANKER_PAIR, 12.0)

        val fortune7 = hand.outcome == Outcome.BANKER && b3 && bT == 7
        val golden8 = hand.outcome == Outcome.PLAYER && p3 && pT == 8
        if (fortune7) pay(BetType.FORTUNE_7, 41.0)
        if (golden8) pay(BetType.GOLDEN_8, 26.0)

        val p9 = p3 && pT == 9
        val b9 = b3 && bT == 9
        if (p9 && b9) pay(BetType.HEAVENLY_9, 76.0)
        else if (p9 || b9) pay(BetType.HEAVENLY_9, 11.0)

        val p7 = p3 && pT == 7
        val b7 = b3 && bT == 7
        if (p7 && b7) pay(BetType.BLAZING_7S, 201.0)
        else if (p7 || b7) pay(BetType.BLAZING_7S, 76.0)

        // Cover All: 6 to 1 when any of the four fortune events occurs.
        if (fortune7 || golden8 || p9 || b9 || p7 || b7) pay(BetType.COVER_ALL, 7.0)

        return returns
    }

    /** Total returned to the player (stake included on wins/pushes). */
    fun settle(hand: BaccaratHand, bets: Map<BetType, Int>): Double =
        settleBreakdown(hand, bets).values.sum()

    /** Which bet spots hit this hand, for highlighting on the felt. */
    fun winningSpots(hand: BaccaratHand): Set<BetType> {
        val pT = hand.playerTotal
        val bT = hand.bankerTotal
        val p3 = hand.player.size == 3
        val b3 = hand.banker.size == 3
        return buildSet {
            when (hand.outcome) {
                Outcome.PLAYER -> add(BetType.PLAYER)
                Outcome.BANKER -> add(BetType.BANKER)
                Outcome.TIE -> add(BetType.TIE)
            }
            if (hand.player[0].rank == hand.player[1].rank) add(BetType.PLAYER_PAIR)
            if (hand.banker[0].rank == hand.banker[1].rank) add(BetType.BANKER_PAIR)
            if (hand.outcome == Outcome.BANKER && b3 && bT == 7) add(BetType.FORTUNE_7)
            if (hand.outcome == Outcome.PLAYER && p3 && pT == 8) add(BetType.GOLDEN_8)
            if ((p3 && pT == 9) || (b3 && bT == 9)) add(BetType.HEAVENLY_9)
            if ((p3 && pT == 7) || (b3 && bT == 7)) add(BetType.BLAZING_7S)
            if (BetType.FORTUNE_7 in this || BetType.GOLDEN_8 in this ||
                BetType.HEAVENLY_9 in this || BetType.BLAZING_7S in this
            ) add(BetType.COVER_ALL)
        }
    }
}
