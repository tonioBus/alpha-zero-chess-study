package com.aquila.chess.strategy.mcts.utils;

import lombok.Getter;
import lombok.ToString;

import static com.chess.engine.classic.board.BoardUtils.NUM_TILES_PER_ROW;

@Getter
@ToString
public class Coordinate2D {
    int x, y;

    public Coordinate2D(int coordinate1D) {
        this.x = coordinate1D % NUM_TILES_PER_ROW;
        this.y = NUM_TILES_PER_ROW - 1 - coordinate1D / NUM_TILES_PER_ROW;
    }
}
