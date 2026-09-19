package com.example.casinogames.games.miniuth

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.holdem.PokerEval

/**
 * Three-card poker categories in ascending strength. With only three cards a
 * straight is harder to make than a flush, and trips harder than either, so
 * the order is not the five-card one. The mini royal is suited Q-K-A.
 */
enum class MiniCategory(val label: String) {
    HIGH_CARD("High Card"),
    PAIR("Pair"),
    FLUSH("Flush"),
    STRAIGHT("Straight"),
    THREE_KIND("Three of a Kind"),
    STRAIGHT_FLUSH("Straight Flush"),
    MINI_ROYAL("Mini Royal"),
}

/** A scored three-card hand; [tiebreak] separates two of the same category. */
data class MiniHandValue(
    val category: MiniCategory,
    val tiebreak: List<Int>,
) : Comparable<MiniHandValue> {
    override fun compareTo(other: MiniHandValue): Int {
        if (category != other.category) return category.ordinal - other.category.ordinal
        for (i in tiebreak.indices) {
            val a = tiebreak[i]
            val b = other.tiebreak.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}

object MiniEval {

    /** Scores exactly three cards. */
    fun score(hand: List<Card>): MiniHandValue {
        require(hand.size == 3) { "score() takes three cards" }
        val v = hand.map { PokerEval.value(it.rank) }.sortedDescending()
        val flush = hand.all { it.suit == hand[0].suit }
        val distinct = v.distinct().size == 3
        // Ace plays high over Q-K and low under 2-3; the wheel is three-high.
        val straightHigh = when {
            !distinct -> 0
            v[0] - v[2] == 2 -> v[0]
            v == listOf(14, 3, 2) -> 3
            else -> 0
        }
        return when {
            flush && straightHigh == 14 -> MiniHandValue(MiniCategory.MINI_ROYAL, listOf(14))
            flush && straightHigh > 0 -> MiniHandValue(MiniCategory.STRAIGHT_FLUSH, listOf(straightHigh))
            v[0] == v[2] -> MiniHandValue(MiniCategory.THREE_KIND, listOf(v[0]))
            straightHigh > 0 -> MiniHandValue(MiniCategory.STRAIGHT, listOf(straightHigh))
            flush -> MiniHandValue(MiniCategory.FLUSH, v)
            v[0] == v[1] -> MiniHandValue(MiniCategory.PAIR, listOf(v[0], v[2]))
            v[1] == v[2] -> MiniHandValue(MiniCategory.PAIR, listOf(v[1], v[0]))
            else -> MiniHandValue(MiniCategory.HIGH_CARD, v)
        }
    }

    /**
     * Every hand must use its single hole card: the hole card plus the best
     * two of the community cards. With only the two flop cards out there is
     * just the one hand to make.
     */
    fun bestCards(hole: Card, community: List<Card>): List<Card> {
        require(community.size >= 2) { "need at least the flop" }
        var best: List<Card>? = null
        var bestValue: MiniHandValue? = null
        for (i in community.indices) for (j in i + 1 until community.size) {
            val three = listOf(hole, community[i], community[j])
            val value = score(three)
            if (bestValue == null || value > bestValue) {
                best = three
                bestValue = value
            }
        }
        return best!!
    }

    fun best(hole: Card, community: List<Card>): MiniHandValue = score(bestCards(hole, community))
}
