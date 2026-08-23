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
    const val ROW_PITCH = 79.5f
    const val ROWS = 12

    /** The grid's three columns, and the dozens rail beside them. */
    const val GRID_LEFT = 22f
    const val GRID_RIGHT = 669f
    val COLUMN_EDGES = floatArrayOf(22f, 237f, 453f, 669f)
    const val DOZENS_LEFT = 680f
    const val DOZENS_RIGHT = 816f
    /** Each dozen's band, level with its third of the grid. */
    val DOZEN_BANDS = arrayOf(284f to 690f, 700f to 1100f, 1110f to 1500f)

    /** The 2-to-1 column bets, then the even-money row. */
    const val COLUMN_BETS_TOP = 1337f
    const val COLUMN_BETS_BOTTOM = 1405f
    const val EVEN_MONEY_TOP = 1414f
    const val EVEN_MONEY_BOTTOM = 1487f

    /** The six even-money cells, edge to edge. */
    val EVEN_MONEY_EDGES = floatArrayOf(22f, 153f, 286f, 422f, 551f, 683f, 816f)

    /** The chip rack and the three buttons, as the art painted them. */
    const val CHIPS_TOP = 1512f
    const val CHIPS_BOTTOM = 1611f
    const val CHIP_FIRST_MID = 162f
    const val CHIP_PITCH = 127.5f
    const val CHIP_DIAMETER = 104f
    const val BUTTONS_TOP = 1630f
    const val BUTTONS_BOTTOM = 1716f
    /** Left, centre and right button spans: undo, spin, rebet. */
    val BUTTON_EDGES = arrayOf(22f to 250f, 259f to 587f, 603f to 816f)

    /** The back arrow the art paints at the top left. */
    const val BACK_RIGHT = 200f
    const val BACK_BOTTOM = 110f
}
