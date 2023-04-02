package com.aquila.chess.strategy.mcts.utils;

public class ConvertValueOutput {

    /**
     * convert sigmoid output range [0,-1] to [-1,1] range
     * @param value
     * @return
     */
    public static float convertFromSigmoid(float value) {
        return value * 2 - 1;
    }

    /**
     * convert [-1,1] range to sigmoid output range [0,-1]
     * @param value
     * @return
     */
    public static float convertToSigmoid(float value) {
        return (value + 1) / 2;
    }
}
