package com.example.casinogames.games.core

import kotlin.random.Random

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠", false),
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    CLUBS("♣", false),
}

enum class Rank(val label: String, val baccaratValue: Int) {
    ACE("A", 1),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 0),
    JACK("J", 0),
    QUEEN("Q", 0),
    KING("K", 0),

    /**
     * Only ever dealt from a shoe asked for jokers — DJ Wild is the one table
     * that wants them. Kept last so no other rank's ordinal moves, and left
     * out of [STANDARD] so the ordinary 52 is unchanged.
     */
    JOKER("★", 0);

    companion object {
        /** The ordinary 52-card deck's ranks: everything but the joker. */
        val STANDARD: List<Rank> = entries.filter { it != JOKER }
    }
}

data class Card(val rank: Rank, val suit: Suit) {
    val isJoker: Boolean get() = rank == Rank.JOKER
}

/**
 * A multi-deck dealing shoe shared by shoe-based games (baccarat, blackjack, …).
 * [jokers] are added per deck and default to none, so every existing table
 * keeps the plain 52.
 */
class Shoe(
    private val decks: Int = 8,
    private val random: Random = Random.Default,
    private val jokers: Int = 0,
) {
    private var cards = freshCards()

    val cardsRemaining: Int get() = cards.size

    fun draw(): Card = cards.removeAt(cards.lastIndex)

    fun reshuffleIfBelow(threshold: Int) {
        if (cards.size < threshold) cards = freshCards()
    }

    /** Test hook: places [next] on top of the shoe so they deal in order. */
    fun stack(next: List<Card>) {
        cards.addAll(next.reversed())
    }

    private fun freshCards(): MutableList<Card> =
        buildList {
            repeat(decks) {
                for (suit in Suit.entries) for (rank in Rank.STANDARD) add(Card(rank, suit))
                // Jokers carry a suit only because Card wants one; nothing reads it.
                repeat(jokers) { i -> add(Card(Rank.JOKER, Suit.entries[i % Suit.entries.size])) }
            }
        }.shuffled(random).toMutableList()
}
