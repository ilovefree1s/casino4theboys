package com.example.casinogames.games.djwild

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit

/**
 * DJ Wild's deck: the ordinary 52 plus one joker, with every deuce and the
 * joker playing wild — five wild cards in all.
 */
object DjWildDeck {
    const val DECKS = 1
    const val JOKERS = 1
    fun isWild(card: Card): Boolean = card.isJoker || card.rank == Rank.TWO
    /** How many of the five cards are playing wild. */
    fun wildCount(cards: List<Card>): Int = cards.count(::isWild)
}

/**
 * Wild poker ranks differently from the hold'em ladder, so it keeps its own
 * enum rather than bending [com.example.casinogames.games.holdem.HandCategory]
 * which Ultimate Texas Hold'em pays off.
 *
 * Ascending strength, so the ordinal ranks them. The order below is the
 * conventional wild-poker one; the paytable art settles what each rung pays.
 */
enum class WildCategory(val label: String) {
    HIGH_CARD("High Card"),
    PAIR("Pair"),
    TWO_PAIR("Two Pair"),
    THREE_KIND("Three of a Kind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("Full House"),
    FOUR_KIND("Four of a Kind"),
    STRAIGHT_FLUSH("Straight Flush"),
    FIVE_KIND("Five of a Kind"),
    ROYAL_FLUSH("Royal Flush"),

    /** All four deuces and the joker in one hand — the top of the board. */
    FIVE_WILDS("Five Wilds"),
}

/** A scored wild hand. [tiebreak] separates two hands of the same category. */
data class WildHandValue(
    val category: WildCategory,
    val tiebreak: List<Int> = emptyList(),
) : Comparable<WildHandValue> {
    override fun compareTo(other: WildHandValue): Int {
        if (category != other.category) return category.ordinal - other.category.ordinal
        for (i in tiebreak.indices) {
            val a = tiebreak[i]
            val b = other.tiebreak.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}

/**
 * Scores five cards with wilds. A wild becomes whatever card makes the hand
 * best, so the score is the best of every substitution — worked out
 * exhaustively rather than by rule, which is short enough to be obviously
 * right and slow only in hands that almost never come out (four wilds in five
 * cards happens about once in twelve thousand).
 */
object DjWildEval {

    /** Aces are high; the wheel handles the low ace as a special case. */
    private fun value(rank: Rank): Int = if (rank == Rank.ACE) 14 else rank.ordinal + 1

    private val DECK: List<Card> =
        Suit.entries.flatMap { suit -> Rank.STANDARD.map { Card(it, suit) } }

    fun score(hand: List<Card>): WildHandValue {
        require(hand.size == 5) { "score() takes five cards" }
        val wilds = hand.count(DjWildDeck::isWild)
        if (wilds == 5) return WildHandValue(WildCategory.FIVE_WILDS)
        val naturals = hand.filterNot(DjWildDeck::isWild)
        if (wilds == 0) return scoreNatural(naturals)

        var best: WildHandValue? = null
        fillWilds(naturals, wilds, 0) { filled ->
            val v = scoreNatural(filled)
            if (best == null || v > best!!) best = v
        }
        return best!!
    }

    /**
     * The hand read with every card playing as itself — a deuce counting two,
     * not wild. The felt is explicit about this: "hands containing a '2' NOT
     * used as a wild are considered natural", and Trips pays far more for a
     * natural than a wild one.
     *
     * Null when the hand holds the joker, which has no face of its own and so
     * can never be part of a natural hand.
     */
    fun naturalScore(hand: List<Card>): WildHandValue? {
        require(hand.size == 5) { "naturalScore() takes five cards" }
        if (hand.any { it.isJoker }) return null
        return scoreNatural(hand)
    }

    /**
     * Walks every way the wilds could be filled. Substitutions are taken in
     * non-decreasing deck order so the same multiset is never tried twice —
     * wilds are interchangeable, and without that the four-wild case would be
     * fifty times slower for no different answer.
     */
    private fun fillWilds(
        naturals: List<Card>,
        left: Int,
        from: Int,
        emit: (List<Card>) -> Unit,
    ) {
        if (left == 0) {
            emit(naturals)
            return
        }
        for (i in from until DECK.size) {
            fillWilds(naturals + DECK[i], left - 1, i, emit)
        }
    }

    /** Scores five cards that are all playing as themselves. */
    private fun scoreNatural(hand: List<Card>): WildHandValue {
        val values = hand.map { value(it.rank) }.sortedDescending()
        val flush = hand.all { it.suit == hand[0].suit }
        val straightHigh = straightHigh(values)

        val counts = values.groupingBy { it }.eachCount()
        val grouped = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val shape = grouped.map { it.value }
        val ranks = grouped.map { it.key }

        return when {
            shape.firstOrNull() == 5 -> WildHandValue(WildCategory.FIVE_KIND, ranks)
            flush && straightHigh == 14 -> WildHandValue(WildCategory.ROYAL_FLUSH, listOf(14))
            flush && straightHigh > 0 -> WildHandValue(WildCategory.STRAIGHT_FLUSH, listOf(straightHigh))
            shape.firstOrNull() == 4 -> WildHandValue(WildCategory.FOUR_KIND, ranks)
            shape.take(2) == listOf(3, 2) -> WildHandValue(WildCategory.FULL_HOUSE, ranks)
            flush -> WildHandValue(WildCategory.FLUSH, values)
            straightHigh > 0 -> WildHandValue(WildCategory.STRAIGHT, listOf(straightHigh))
            shape.firstOrNull() == 3 -> WildHandValue(WildCategory.THREE_KIND, ranks)
            shape.take(2) == listOf(2, 2) -> WildHandValue(WildCategory.TWO_PAIR, ranks)
            shape.firstOrNull() == 2 -> WildHandValue(WildCategory.PAIR, ranks)
            else -> WildHandValue(WildCategory.HIGH_CARD, values)
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
}
