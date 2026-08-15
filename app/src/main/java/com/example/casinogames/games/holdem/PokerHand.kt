package com.example.casinogames.games.holdem

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank

/** Poker categories in ascending strength, so the enum ordinal ranks them. */
enum class HandCategory(val label: String) {
    HIGH_CARD("High Card"),
    PAIR("Pair"),
    TWO_PAIR("Two Pair"),
    THREE_KIND("Three of a Kind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("Full House"),
    FOUR_KIND("Four of a Kind"),
    STRAIGHT_FLUSH("Straight Flush"),
    ROYAL_FLUSH("Royal Flush"),
}

/**
 * A scored five-card hand. [tiebreak] holds the ranks that separate two hands of
 * the same category, most significant first.
 */
data class HandValue(
    val category: HandCategory,
    val tiebreak: List<Int>,
) : Comparable<HandValue> {
    override fun compareTo(other: HandValue): Int {
        if (category != other.category) return category.ordinal - other.category.ordinal
        for (i in tiebreak.indices) {
            val a = tiebreak[i]
            val b = other.tiebreak.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}

object PokerEval {

    /** Aces are high here; the wheel handles the low ace as a special case. */
    fun value(rank: Rank): Int = if (rank == Rank.ACE) 14 else rank.ordinal + 1

    /** Best five-card hand out of any number of cards (seven, in hold'em). */
    fun best(cards: List<Card>): HandValue {
        require(cards.size >= 5) { "need at least five cards" }
        return combinations(cards, 5).map { score(it) }.max()
    }

    /** The five cards that make the best hand, for showing which ones played. */
    fun bestCards(cards: List<Card>): List<Card> {
        require(cards.size >= 5) { "need at least five cards" }
        return combinations(cards, 5).maxBy { score(it) }
    }

    /** Scores exactly five cards. */
    fun score(hand: List<Card>): HandValue {
        require(hand.size == 5) { "score() takes five cards" }
        val values = hand.map { value(it.rank) }.sortedDescending()
        val flush = hand.all { it.suit == hand[0].suit }
        val straightHigh = straightHigh(values)

        // Rank -> how many of it, ordered by count then rank so pairs read off cleanly.
        val counts = values.groupingBy { it }.eachCount()
        val grouped = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val shape = grouped.map { it.value }

        return when {
            flush && straightHigh == 14 -> HandValue(HandCategory.ROYAL_FLUSH, listOf(14))
            flush && straightHigh > 0 -> HandValue(HandCategory.STRAIGHT_FLUSH, listOf(straightHigh))
            shape.firstOrNull() == 4 ->
                HandValue(HandCategory.FOUR_KIND, grouped.map { it.key })
            shape.take(2) == listOf(3, 2) ->
                HandValue(HandCategory.FULL_HOUSE, grouped.map { it.key })
            flush -> HandValue(HandCategory.FLUSH, values)
            straightHigh > 0 -> HandValue(HandCategory.STRAIGHT, listOf(straightHigh))
            shape.firstOrNull() == 3 ->
                HandValue(HandCategory.THREE_KIND, grouped.map { it.key })
            shape.take(2) == listOf(2, 2) ->
                HandValue(HandCategory.TWO_PAIR, grouped.map { it.key })
            shape.firstOrNull() == 2 ->
                HandValue(HandCategory.PAIR, grouped.map { it.key })
            else -> HandValue(HandCategory.HIGH_CARD, values)
        }
    }

    /** Returns the straight's high card, or 0 if the five cards aren't a run. */
    private fun straightHigh(descending: List<Int>): Int {
        val distinct = descending.distinct()
        if (distinct.size != 5) return 0
        if (distinct[0] - distinct[4] == 4) return distinct[0]
        // The wheel: A-2-3-4-5 counts as a five-high straight.
        if (distinct == listOf(14, 5, 4, 3, 2)) return 5
        return 0
    }

    private fun <T> combinations(items: List<T>, choose: Int): List<List<T>> {
        if (choose == 0) return listOf(emptyList())
        if (items.size < choose) return emptyList()
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
