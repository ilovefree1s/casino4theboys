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
}

data class Card(val rank: Rank, val suit: Suit)

/** A multi-deck dealing shoe shared by shoe-based games (baccarat, blackjack, …). */
class Shoe(
    private val decks: Int = 8,
    private val random: Random = Random.Default,
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
                for (suit in Suit.entries) for (rank in Rank.entries) add(Card(rank, suit))
            }
        }.shuffled(random).toMutableList()
}
