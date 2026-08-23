package com.example.casinogames.games.roulette

/**
 * Where everything sits on roulette_felt.png (836 x 1722), measured off the
 * art itself. The screen draws the table as it was made and puts the live
 * pieces — the money, the belt, the chips and every tap target — on these
 * marks, so nothing has to be redrawn in code.
 */
object RouletteArt {
    const val W = 836f
    const val H = 1722f

    /** The money the art leaves blank, right-aligned to this edge. */
    const val MONEY_RIGHT = 800f
    const val BANKROLL_MID = 50f
    const val STAKED_MID = 98f

    /**
     * The badge the header wears, sized to clear the back arrow on one side
     * and the money on the other, and to leave the belt its strip below.
     */
    const val LOGO_MID_X = 418f
    const val LOGO_MID_Y = 76f
    const val LOGO_SIZE = 132f

    /**
     * The strip opened up between the header and the zero row. It runs the
     * table's own margins, so the wheel reads as another rail of the layout;
     * the top edge sits just under where the logo's glow dies out.
     */
    const val BELT_LEFT = 22f
    const val BELT_RIGHT = 816f
    const val BELT_TOP = 150f
    const val BELT_BOTTOM = 282f

    /** The zero row, then twelve rows of three below it. */
    const val ZERO_TOP = 284f
    const val ZERO_BOTTOM = 367f
    const val GRID_TOP = 371f
    /** Measured off the twelve seams: the last row's floor is 1330. */
    const val ROW_PITCH = 79.92f
    const val ROWS = 12

    /** The grid's three columns, and the dozens rail beside them. */
    const val GRID_LEFT = 22f
    const val GRID_RIGHT = 669f
    val COLUMN_EDGES = floatArrayOf(22f, 237f, 453f, 669f)
    const val DOZENS_LEFT = 680f
    const val DOZENS_RIGHT = 816f
    /**
     * Each dozen takes exactly the four rows it pays on, seam to seam, so a
     * press anywhere beside 1-12 is the first dozen and never the second.
     */
    val DOZEN_BANDS = arrayOf(
        GRID_TOP to GRID_TOP + 4 * ROW_PITCH,
        GRID_TOP + 4 * ROW_PITCH to GRID_TOP + 8 * ROW_PITCH,
        GRID_TOP + 8 * ROW_PITCH to GRID_TOP + 12 * ROW_PITCH,
    )

    /** The 2-to-1 column bets, then the even-money row. */
    const val COLUMN_BETS_TOP = 1337f
    const val COLUMN_BETS_BOTTOM = 1405f
    const val EVEN_MONEY_TOP = 1414f
    const val EVEN_MONEY_BOTTOM = 1487f

    /** The six even-money cells, edge to edge. */
    val EVEN_MONEY_EDGES = floatArrayOf(22f, 153f, 286f, 422f, 551f, 683f, 816f)

    /** The chip rack, as the art painted it. */
    const val CHIPS_TOP = 1512f
    const val CHIPS_BOTTOM = 1611f
    const val CHIP_FIRST_MID = 162f
    const val CHIP_PITCH = 127.5f
    const val CHIP_DIAMETER = 104f

    /**
     * The art leaves this band empty: the buttons are drawn in code so they
     * can grey out while the wheel is running. Even spans, evenly spaced.
     */
    const val BUTTONS_TOP = 1630f
    const val BUTTONS_BOTTOM = 1716f
    /** Left, centre and right button spans: undo, spin, rebet. */
    val BUTTON_EDGES = arrayOf(22f to 250f, 268f to 570f, 588f to 816f)

    /** The back arrow the art paints at the top left. */
    const val BACK_RIGHT = 200f
    const val BACK_BOTTOM = 110f
}
