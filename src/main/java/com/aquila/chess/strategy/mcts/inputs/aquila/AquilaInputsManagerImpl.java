package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.utils.Coordinate;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * <h1>Network Input</h1>
 * <ul>
 *     <li>
 *         Positions
 *         <ul>
 *              <li>[0-5] pieces for White</li>
 *              <li>[6-11] pieces for Black</li>
 *         </ul>
 *     </li>
 *     <li>
 *         Attacks
 *         <ul>
 *              <li>[0-5] pieces for White</li>
 *              <li>[6-11] pieces for Black</li>
 *         </ul>
 *     </li>
 *     <li>
 *         Protects
 *         <ul>
 *              <li>[0-5] pieces for White</li>
 *              <li>[6-11] pieces for Black</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * </p>
 */
public class AquilaInputsManagerImpl implements InputsManager {

    static final int SIZE_POSITION = 12;

    public static final int PAWN_INDEX = 0;
    public static final int KNIGHT_INDEX = 1;
    public static final int BISHOP_INDEX = 2;
    public static final int ROOK_INDEX = 3;
    public static final int QUEEN_INDEX = 4;
    public static final int KING_INDEX = 5;

    public static final int FEATURES_PLANES = 46;

    @Override
    public int getNbFeaturesPlanes() {
        return 0;
    }

    @Override
    public InputsFullNN createInputs(Board board, Move move, Alliance color2play) {
        final var inputs = new double[FEATURES_PLANES][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        if (move != null)
            // if we move, the color2play will be the complementary of the player that just moved
            this.createInputs(inputs, move.execute(), move.getMovedPiece().getPieceAllegiance().complementary());
        else
            this.createInputs(inputs, board, color2play);
        return new AquilaInputsFullNN(inputs);
    }

    /**
     * @param board - the board on which we apply the move
     * @return the normalize board for 1 position using board and move. dimensions:
     * [12][NB_COL][NB_COL]
     */
    private void createInputs(double[][][] inputs, Board board, Alliance color2play) {
        board.getAllPieces().parallelStream().forEach(currentPiece -> {
            Player player = switch (currentPiece.getPieceAllegiance()) {
                case WHITE -> board.whitePlayer();
                case BLACK -> board.blackPlayer();
            };
            Coordinate coordinate = new Coordinate(currentPiece);
            int currentPieceIndex = getPlanesIndex(currentPiece);
            // Position 0 (6+6 planes)
            inputs[currentPieceIndex][coordinate.getXInput()][coordinate.getYInput()] = 1;
            // Moves 12 (6+6 planes)
            Collection<Move> moves = currentPiece.calculateLegalMoves(board);
            moves.stream().forEach(move -> {
                Move.MoveStatus status = player.makeMove(move).getMoveStatus();
                if (status == Move.MoveStatus.DONE) {
                    Coordinate movesCoordinate = new Coordinate(move);
                    inputs[12 + currentPieceIndex][movesCoordinate.getXInput()][movesCoordinate.getYInput()] = 1;
                }
            });
            // Attacks 24 (6+6 planes)
            moves.stream().filter(move -> move.isAttack()).forEach(move -> {
                Piece attackingPiece = move.getAttackedPiece();
                Coordinate attackCoordinate = new Coordinate(attackingPiece);
                inputs[24 + getPlanesIndex(attackingPiece)][attackCoordinate.getXInput()][attackCoordinate.getYInput()] = 1;
            });
            // King liberty 36 (1+1 planes)
            if (currentPiece.getPieceType() == Piece.PieceType.KING) {
                int offsetBlack = currentPiece.getPieceAllegiance() == Alliance.BLACK ? 1 : 0;
                moves.stream().forEach(move -> {
                    Move.MoveStatus status = player.makeMove(move).getMoveStatus();
                    if (status == Move.MoveStatus.DONE) {
                        Coordinate coordinateKingMoves = new Coordinate(move);
                        inputs[36 + offsetBlack][coordinateKingMoves.getXInput()][coordinateKingMoves.getYInput()] = 1;
                    }
                });
            }
            // Pawn moves 38 (1+1 planes)
            if (currentPiece.getPieceType() == Piece.PieceType.PAWN) {
                int offsetBlack = currentPiece.getPieceAllegiance() == Alliance.BLACK ? 1 : 0;
                int x = coordinate.getXInput();
                int y = coordinate.getYInput();
                Alliance color = currentPiece.getPieceAllegiance();
                for (int yIndex = y + 1; yIndex < BoardUtils.NUM_TILES_PER_ROW; yIndex++) {
                    if (board.getPiece(new Coordinate(x, yIndex, color).getBoardPosition()) != null) break;
                    inputs[38 + offsetBlack][x][yIndex] = 1;
                }
            }
        });
        int currentIndex = 40;
        List<Move> moveWhites = board.whitePlayer().getLegalMoves();
        Optional<Move> kingSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        List<Move> moveBlacks = board.blackPlayer().getLegalMoves();
        Optional<Move> kingSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        fill(inputs[currentIndex], !queenSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 1], !kingSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 2], !queenSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 3], !kingSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 4], color2play.isBlack() ? 1.0 : 0.0);
        // fill(inputs[109], mctsGame.getNbMoveNoAttackAndNoPawn() >= 50 ? 1.0 : 0.0);
        fill(inputs[currentIndex + 5], 1.0F);
    }

    private void fill(double[][] planes, double value) {
        if (value == 0.0) return;
        for (int i = 0; i < 8; i++) {
            Arrays.fill(planes[i], value);
        }
    }

    /**
     * @formatter:off <pre>
     * [0-6]: Pawn:0, Bishop:1, Knight:2, Rook:3, Queen:4, King:5
     * [0-6] pieces for White
     * [7-12] pieces for Black
     * </pre>
     * @formatter:on
     */
    private int getPlanesIndex(Piece piece) {
        int index = piece.getPieceAllegiance().isWhite() ? 0 : 6;
        if (piece.getPieceType() == Piece.PieceType.PAWN) return index + PAWN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.BISHOP) return index + BISHOP_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KNIGHT) return index + KNIGHT_INDEX;
        if (piece.getPieceType() == Piece.PieceType.ROOK) return index + ROOK_INDEX;
        if (piece.getPieceType() == Piece.PieceType.QUEEN) return index + QUEEN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KING) return index + KING_INDEX;
        return -100; // sure this will failed at least
    }

    @Override
    public void startMCTSStep(final Game game) {

    }

    @Override
    public InputsManager clone() {
        return null;
    }

    @Override
    public long hashCode(Board board, Move move, Alliance color2play) {
        return 0;
    }

    @Override
    public void processPlay(Board board, Move move) {

    }

}
