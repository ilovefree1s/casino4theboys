package com.example.casinogames.games.shootout

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.holdem.PokerEval

/**
 * Poker categories in ascending strength. Texas Shootout deals from six
 * decks, so the same card can turn up twice and five of a kind is real. The
 * felt ranks it above quads and below a straight flush, and five *identical*
 * cards — suited five of a kind — beats everything.
 */
enum class ShootoutCategory(val label: String) {
    HIGH_CARD("High Card"),
    PAIR("Pair"),
    TWO_PAIR("Two Pair"),
    THREE_KIND("Three of a Kind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("Full House"),
    FOUR_KIND("Four of a Kind"),
    FIVE_KIND("Five of a Kind"),
    STRAIGHT_FLUSH("Straight Flush"),
    ROYAL_FLUSH("Royal Flush"),
    FIVE_KIND_SUITED("Suited Five of a Kind"),
}

/** A scored five-card hand; [tiebreak] separates two of the same category. */
data class ShootoutHandValue(
    val category: ShootoutCategory,
    val tiebreak: List<Int>,
) : Comparable<ShootoutHandValue> {
    override fun compareTo(other: ShootoutHandValue): Int {
        if (category != other.category) return category.ordinal - other.category.ordinal
        for (i in tiebreak.indices) {
            val a = tiebreak[i]
            val b = other.tiebreak.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}

object ShootoutEval {

    fun value(rank: Rank): Int = PokerEval.value(rank)

    /** Best five out of the seven — two hole cards and the board. */
    fun best(cards: List<Card>): ShootoutHandValue {
        require(cards.size >= 5) { "need at least five cards" }
        return combinations(cards, 5).map { score(it) }.max()
    }

    /** Scores exactly five cards. Duplicates are ordinary cards here. */
    fun score(hand: List<Card>): ShootoutHandValue {
        require(hand.size == 5) { "score() takes five cards" }
        val values = hand.map { value(it.rank) }.sortedDescending()
        val flush = hand.all { it.suit == hand[0].suit }
        val straightHigh = straightHigh(values)

        val counts = values.groupingBy { it }.eachCount()
        val grouped = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val shape = grouped.map { it.value }
        val ranks = grouped.map { it.key }

        return when {
            shape.firstOrNull() == 5 && flush ->
                ShootoutHandValue(ShootoutCategory.FIVE_KIND_SUITED, ranks)
            flush && straightHigh == 14 -> ShootoutHandValue(ShootoutCategory.ROYAL_FLUSH, listOf(14))
            flush && straightHigh > 0 -> ShootoutHandValue(ShootoutCategory.STRAIGHT_FLUSH, listOf(straightHigh))
            shape.firstOrNull() == 5 -> ShootoutHandValue(ShootoutCategory.FIVE_KIND, ranks)
            shape.firstOrNull() == 4 -> ShootoutHandValue(ShootoutCategory.FOUR_KIND, ranks)
            shape.take(2) == listOf(3, 2) -> ShootoutHandValue(ShootoutCategory.FULL_HOUSE, ranks)
            flush -> ShootoutHandValue(ShootoutCategory.FLUSH, values)
            straightHigh > 0 -> ShootoutHandValue(ShootoutCategory.STRAIGHT, listOf(straightHigh))
            shape.firstOrNull() == 3 -> ShootoutHandValue(ShootoutCategory.THREE_KIND, ranks)
            shape.take(2) == listOf(2, 2) -> ShootoutHandValue(ShootoutCategory.TWO_PAIR, ranks)
            shape.firstOrNull() == 2 -> ShootoutHandValue(ShootoutCategory.PAIR, ranks)
            else -> ShootoutHandValue(ShootoutCategory.HIGH_CARD, values)
        }
    }

    /** The straight's high card, or 0 when the five are not a run. */
    private fun straightHigh(descending: List<Int>): Int {
        val distinct = descending.distinct()
        if (distinct.size != 5) return 0
        if (distinct[0] - distinct[4] == 4) return distinct[0]
        if (distinct == listOf(14, 5, 4, 3, 2)) return 5
        return 0
    }

    private fun <T> combinations(items: List<T>, choose: Int): List<List<T>> {
        val out = mutableListOf<List<T>>()
        fun walk(start: Int, picked: List<T>) {
            if (picked.size == choose) {
                out.add(picked)
                return
            }
            for (i in start..items.size - (choose - picked.size)) {
                walk(i + 1, picked + items[i])
            }
        }
        walk(0, emptyList())
        return out
    }
}
