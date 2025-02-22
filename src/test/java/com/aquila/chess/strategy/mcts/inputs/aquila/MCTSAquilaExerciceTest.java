package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.Game;
import com.aquila.chess.Helper;
import com.aquila.chess.config.MCTSConfig;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.StaticStrategy;
import com.aquila.chess.strategy.Strategy;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.utils.DotGenerator;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.aquila.chess.Game.GameStatus.*;
import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class MCTSAquilaExerciceTest {

    public static int NB_THREAD = 8;

    final UpdateCpuct updateCpuct = (nbStep, nbLegalMoves) -> {
        return 0.000025; // Math.exp(-0.04 * nbStep) / 2;
    };

    private static final Dirichlet dirichlet = game -> false;

    DeepLearningAGZ deepLearningWhite;
    DeepLearningAGZ deepLearningBlack;
    NNSimul nnBlack;
    NNSimul nnWhite;

    AquilaInputsManagerImpl inputsManager;

    @BeforeEach
    public void initMockDeepLearning() {
        nnWhite = new NNSimul(2);
        nnBlack = new NNSimul(1);
        inputsManager = new AquilaInputsManagerImpl();
        deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnWhite)
                .inputsManager(inputsManager)
                .batchSize(16)
                .train(false)
                .build();
        deepLearningBlack = DeepLearningAGZ.builder()
                .nn(nnBlack)
                .inputsManager(inputsManager)
                .batchSize(16)
                .train(false)
                .build();
        nnBlack.clearIndexOffset();
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  p-B --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @ValueSource(ints = {20, 100, 200, 400, 800})
    @ParameterizedTest
    @DisplayName("detect black promotion")
    void testSimulationDetectPossibleBlackPromotion(int nbStep) throws Exception {
        final Board board = Board.createBoard("kh1", "pa3,kg3", BLACK);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final StaticStrategy whiteStrategy = new StaticStrategy(WHITE, "H1-G1;G1-H1;H1-G1;G1-H1");
        if (nbStep <= 10) deepLearningBlack.getServiceNN().setBatchSize(2);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withDirichlet((steps) -> {
                    return true;
                })
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        game.setup(whiteStrategy, blackStrategy);
        nnBlack.addIndexOffset(1.0F, "a3-a2;a2-a1", Piece.PieceType.PAWN);
        for (int i = 0; i < 3; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("[{}] move: {} class:{}", move.getAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(blackStrategy);
            if (status != IN_PROGRESS) {
                if (move instanceof Move.PawnPromotion) {
                    log.info("GAME:\n{}\n", game.toPGN());
                    return;
                }
                log.error("{}", game);
                assertFalse(true, "End of game not expected:" + status);
            }
            if (move.getAllegiance().isBlack()) {
                if (log.isInfoEnabled()) log.info(blackStrategy.mctsTree4log(true, 50));
            }
        }
        log.info("GAME:\n{}\n", game.toPGN());
        assertTrue(false, "We should have get promoted");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  P-B --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {10, 100, 200, 300, 400, 800})
    @DisplayName("white chessmate with black promotion")
    void testEndWithBlackPromotion(int nbStep) throws Exception {
        final Board board = Board.createBoard("kh1", "pa3,kg3", BLACK);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final StaticStrategy whiteStrategy = new StaticStrategy(WHITE, "H1-G1;G1-H1;H1-G1;G1-H1");
        if (nbStep <= 10) deepLearningBlack.getServiceNN().setBatchSize(2);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        game.setup(whiteStrategy, blackStrategy);
        nnBlack.addIndexOffset(20.0F, "a3-a2", Piece.PieceType.PAWN);
        nnBlack.addIndexOffset(10.0F, "a2-a1", Piece.PieceType.PAWN);
        log.info("TWEAK NN: a3-a2:{} - a2-a1:{}",
                PolicyUtils.indexFromMove(Piece.PieceType.PAWN, "a3", "a2"),
                PolicyUtils.indexFromMove(Piece.PieceType.PAWN, "a2", "a1")
        );
        for (int i = 0; i < 5; i++) {
            log.info(game.toString());
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            if (status == Game.GameStatus.WHITE_CHESSMATE) {
                log.info("GAME:\n{}\n", game.toPGN());
                return;
            }
            assertEquals(IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
            if (move.getAllegiance().isBlack()) {
                if (log.isInfoEnabled() && nbStep <= 200) log.info(blackStrategy.mctsTree4log(true, 50));
            }
            Helper.checkMCTSTree(blackStrategy);
        }
        log.info("GAME:\n{}\n", game.toPGN());
        assertTrue(false, "We should have got a black chessmate");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  P-B --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testOneShotBlackChessMate() throws Exception {
        final Board board = Board.createBoard("kh1", "pa2,kg3", BLACK);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final StaticStrategy whiteStrategy = new StaticStrategy(WHITE, "H1-G1;G1-H1;H1-G1;G1-H1");
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);

        for (int i = 0; i < 2; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(blackStrategy);
            if (status == Game.GameStatus.WHITE_CHESSMATE) {
                log.info("GAME:\n{}\n", game.toPGN());
                return;
            }
            assertEquals(IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
            if (move.getAllegiance().isBlack()) {
                if (log.isInfoEnabled()) log.info(blackStrategy.mctsTree4log(true, 50));
            }
        }
        if (log.isInfoEnabled()) log.info(blackStrategy.mctsTree4log(true, 50));
        log.info("GAME:\n{}\n", game.toPGN());
        assertTrue(false, "We should have got a white chessmate");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- K-B  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  P-W --- --- --- --- --- K-W ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {100, 200, 400, 800})
    void testEndWithWhitePromotion(int nbStep) throws Exception {
        final Board board = Board.createBoard("pa6,kg6", "kh8", WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final Strategy blackStrategy = new RandomStrategy(BLACK, 1);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        game.setup(whiteStrategy, blackStrategy);
        nnBlack.addIndexOffset(2.0F, "a6-a7", Piece.PieceType.PAWN);
        nnBlack.addIndexOffset(1.0F, "a7-a8", Piece.PieceType.PAWN);
        log.info("TWEAK NN: a6-a7:{} - a7-a8:{}",
                PolicyUtils.indexFromMove(Piece.PieceType.PAWN, "a6", "a7"),
                PolicyUtils.indexFromMove(Piece.PieceType.PAWN, "a7", "a8")
        );
        Move move = null;
        for (int i = 0; i < 3; i++) {
            Game.GameStatus status = game.play();
            if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(true, 50));
            move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(whiteStrategy);
            if (status == BLACK_CHESSMATE) {
                log.info("GAME:\n{}\n", game.toPGN());
                return;
            }
            assertEquals(IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
        }
        log.info("GAME:\n{}\n", game.toPGN());
        assertTrue(false, "We should have a chessmate");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- R-B --- K-B --- ---  3
     * 2  P-B --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- K-W --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {100, 200, 400, 800})
    void testAvoidEndWithBlackPromotion(int nbStep) throws Exception {
        final Board board = Board.createBoard("kf1", "pa2,rd3,kf3", WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(800);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbSearchCalls(nbStep);
        game.setup(whiteStrategy, blackStrategy);
        nnBlack.addIndexOffset(2.0F, "d3-d2", Piece.PieceType.ROOK);
        nnBlack.addIndexOffset(1.0F, "a2-a1", Piece.PieceType.PAWN);
        log.info("TWEAK NN: d3-d2:{} - a2-a1:{}",
                PolicyUtils.indexFromMove(Piece.PieceType.ROOK, "d3", "d2"),
                PolicyUtils.indexFromMove(Piece.PieceType.PAWN, "a2", "a1")
        );
        Move move = null;
        for (int i = 0; i < 3; i++) {
            Game.GameStatus status = game.play();
            move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            switch (move.getAllegiance()) {
                case WHITE:
                    log.info(whiteStrategy.mctsTree4log(false, 3));
                    Helper.checkMCTSTree(whiteStrategy);
                    List<MCTSNode> looses = whiteStrategy.getDirectRoot().search(MCTSNode.State.LOOSE);
                    log.info("[WHITE] Looses Nodes:{}", looses.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
                    assertTrue(looses.size() > 0);
                    break;
                case BLACK:
                    Helper.checkMCTSTree(blackStrategy);
                    List<MCTSNode> wins = blackStrategy.getDirectRoot().search(MCTSNode.State.WIN);
                    log.info("[BLACK] Win Nodes:{}", wins.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
                    assertTrue(wins.size() > 0);
                    break;
            }
            if (status == Game.GameStatus.WHITE_CHESSMATE) {
                log.info("Game:{}", game);
                assertNotEquals(Game.GameStatus.WHITE_CHESSMATE, status, "We should not have a white chessmate");
            }
            assertEquals(IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
        }
        log.info("GAME:\n{}\n", game.toPGN());
    }

    /**
     * <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- K-B --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  P-W --- --- R-W --- K-W --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     */
    @ParameterizedTest
    @ValueSource(ints = {50, 100, 200, 300, 400})
    void testEndWithWhite1Step(int nbSearch) throws Exception {
        final Board board = Board.createBoard("pa6,rd6,kf6", "kf8", WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbSearch);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbSearch);
        game.setup(whiteStrategy, blackStrategy);
        Move move;
        Game.GameStatus status = game.play();
        move = game.getLastMove();
        log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
        log.info(DotGenerator.toString(whiteStrategy.getDirectRoot(), 5));
        List<MCTSNode> wins = whiteStrategy.getDirectRoot().search(MCTSNode.State.WIN);
        log.info("[WHITE] Wins Nodes:{}", wins.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
        assertTrue(wins.size() > 0);
        Helper.checkMCTSTree(whiteStrategy);
        assertEquals("Rd8", move.toString(), "Move should be Rd8 but is: " + move);
        assertEquals(BLACK_CHESSMATE, status, "We should not have a black chessmate:\n" + game);
    }

    /**
     * @formatter:off <pre>
         *    [a] [b] [c] [d] [e] [f] [g] [h]
         * 8  R-B --- --- --- --- --- --- ---  8
         * 7  --- --- --- --- --- --- --- ---  7
         * 6  --- --- --- --- --- --- --- ---  6
         * 5  --- --- --- --- --- --- --- ---  5
         * 4  --- --- --- --- --- --- --- ---  4
         * 3  --- --- --- --- --- --- --- ---  3
         * 2  --- --- --- --- K-W --- K-B R-B  2
         * 1  --- --- --- --- --- --- --- ---  1
         *    [a] [b] [c] [d] [e] [f] [g] [h]
         * </pre>
         * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {300, 400, 800})
    void testAvoidWhiteChessMate(int nbSearchCalls) throws Exception {
        final Board board = Board.createBoard("ke2", "ra8,kg2,rh2", WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbSearchCalls);
        final StaticStrategy blackStrategy = new StaticStrategy(BLACK, "G2-G3;A8-A1");
        game.setup(whiteStrategy, blackStrategy);
        Move move = null;
        for (int i = 0; i < 4; i++) {
            Game.GameStatus status = game.play();
            move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            switch (move.getAllegiance()) {
                case WHITE:
                    if (Utils.isDebuggerPresent()) {
                        log.info("CURRENT GRAPH:\n{}", DotGenerator.toString(whiteStrategy.getDirectRoot(), 20, false));
                    }
                    List<MCTSNode> lossNodes = whiteStrategy.getDirectRoot().search(MCTSNode.State.LOOSE);
                    log.info("[WHITE] loss EndNodes: {}", lossNodes.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
                    if (i == 0) assertTrue(lossNodes.size() > 0, "WHITE should have detect a loss nodes to avoid them");
                    // Helper.checkMCTSTree(whiteStrategy);
                    break;
                case BLACK:
                    break;
            }
            assertNotEquals(Game.GameStatus.WHITE_CHESSMATE, status, "We should not have a white chessmate");
            assertEquals(IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
        }
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(false, 5));
        log.info("GAME:\n{}\n", game.toPGN());
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  R-B --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- K-W --- K-B R-B  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {100, 200, 400, 800})
    @DisplayName("white chessmate in 2 (a8-a3,*,g2-g3,*,a3-a1)")
    void testMakeWhiteChessMateIn2(int nbStep) throws Exception {
        final Board board = Board.createBoard("ke2", "ra8,kg2,rh2", BLACK);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        game.setup(whiteStrategy, blackStrategy);
        nnBlack.addIndexOffset(3F, "a8-a3", Piece.PieceType.ROOK);
        nnBlack.addIndexOffset(2F, "g2-g3", Piece.PieceType.KING);
        nnBlack.addIndexOffset(1F, "a3-a1", Piece.PieceType.ROOK);
        log.info("TWEAK NN: a8-a3:{} - g2-g3:{} - a3-a2:{}",
                PolicyUtils.indexFromMove(Piece.PieceType.ROOK, "a8", "a3"),
                PolicyUtils.indexFromMove(Piece.PieceType.KING, "g2", "g3"),
                PolicyUtils.indexFromMove(Piece.PieceType.ROOK, "a3", "a1")
        );
        Game.GameStatus status = null;
        Move move;
        for (int i = 0; i < 10; i++) {
            status = game.play();
            move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            switch (move.getAllegiance()) {
                case WHITE:
                    List<MCTSNode> winLoss1 = whiteStrategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
                    log.info("[WHITE] STEP:{} Wins/loss EndNodes: {}", i, winLoss1.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
                    if (log.isInfoEnabled())
                        log.info("WHITE GRAPH:\n{}", whiteStrategy.mctsTree4log(nbStep <= 100, 50));
                    Helper.checkMCTSTree(whiteStrategy);
                    break;
                case BLACK:
                    List<MCTSNode> winLoss2 = blackStrategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
                    log.info("[BLACK] STEP:{} Wins/loss EndNodes: {}", i, winLoss2.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
                    if (log.isInfoEnabled())
                        log.info("BLACK GRAPH:\n{}", blackStrategy.mctsTree4log(nbStep <= 100, 50));
                    if (winLoss2.size() == 0) {
                        assertTrue(false, "WIN nodes should have been detected");
                    }
                    Helper.checkMCTSTree(blackStrategy);
                    break;
            }
            switch (i) {
                // case 0 -> assertEquals("Ra3", move.toString());
                // case 4 -> assertEquals("Ra1", move.toString());
            }
            if (status == WHITE_CHESSMATE) break;
            assertEquals(IN_PROGRESS, status, "wrong status: only WHITE_CHESSMATE or in progress is allow");
        }
        if (log.isInfoEnabled()) log.info(blackStrategy.mctsTree4log(false, 50));
        log.warn("board:{}", game.getBoard());
        log.warn("game:{}", game.toPGN());
        assertEquals(WHITE_CHESSMATE, status, "WHITE_CHESSMATE should have been detected");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- K-B --- --- ---  8
     * 7  --- --- --- --- --- --- K-W R-W  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  R-W --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * Kf3 Kd1 2.Ra1
     * </pre>
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {100, 200, 400, 800})
    @DisplayName("black chessmate in 2 (g7-g6,*,a1-a8)")
    void testMakeBlackChessMate(int nbStep) throws Exception {
        final Board board = Board.createBoard("ra1,kg7,rh7", "ke8", WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                10,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                2,
                updateCpuct,
                -1)
                .withDirichlet(dirichlet)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        game.setup(whiteStrategy, blackStrategy);
        nnWhite.addIndexOffset(2F, "g7-g6", Piece.PieceType.KING);
        nnWhite.addIndexOffset(1F, "a1-a8", Piece.PieceType.ROOK);
        log.info("TWEAK NN: g7-g6:{} - a1-a8:{}",
                PolicyUtils.indexFromMove(Piece.PieceType.ROOK, "g7", "g6"),
                PolicyUtils.indexFromMove(Piece.PieceType.KING, "a1", "a8")
        );
        Move move;
        Game.GameStatus status = null;
        for (int i = 0; i < 4; i++) {
            status = game.play();
            move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            switch (move.getAllegiance()) {
                case WHITE:
                    List<MCTSNode> winLoss1 = whiteStrategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
                    log.info("[WHITE] Wins/loss EndNodes: {}", winLoss1.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
                    Helper.checkMCTSTree(whiteStrategy);
                    break;
                case BLACK:
                    List<MCTSNode> winLoss2 = blackStrategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
                    log.info("[BLACK] Wins/loss EndNodes: {}", winLoss2.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
                    Helper.checkMCTSTree(blackStrategy);
                    break;
            }
            if (status == BLACK_CHESSMATE) break;
            assertEquals(IN_PROGRESS, status, "wrong status: only BLACK_CHESSMATE or in progress is allow");
        }
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(false, 50));
        log.warn("game:{}", game.toPGN());
        assertEquals(BLACK_CHESSMATE, status, "BLACK_CHESSMATE should have been detected");
    }

    /**
     * * @formatter:off
     * <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- Q-W --- --- ---  7
     * 6  --- p-B --- --- p-B --- p-B K-B  6
     * 5  p-B --- --- --- --- --- N-W Q-B  5
     * 4  --- --- --- p-W p-B --- --- ---  4
     * 3  --- --- --- --- --- --- p-W ---  3
     * 2  p-W p-W --- --- --- p-W K-W ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     *
     * </pre>
     *
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {800})
    void testBlackChessMate2Move(int nbStep) throws Exception {
        final Board board = Board.createBoard(
                "PA2,PB2,PD4,QE7,PF2,KG2,PG3,NG5",
                "PA5,PB6,PE4,PE6,PG6,QH5,KH6",
                WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                10,
                updateCpuct,
                -1)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        final RandomStrategy blackStrategy = new RandomStrategy(BLACK, 10);
        game.setup(whiteStrategy, blackStrategy);
        nnWhite.addIndexOffset(0.5F, "f2-f4", board);
        Move move;
        Game.GameStatus status = null;
        for (int i = 0; i < 8; i++) {
            status = game.play();
            move = game.getLastMove();
            switch (move.getAllegiance()) {
                case WHITE:
                    List<MCTSNode> winLoss = whiteStrategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
                    log.info("[WHITE] Wins/loss EndNodes: {}", winLoss.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
                    // Helper.checkMCTSTree(whiteStrategy);
                    break;
            }
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getAllegiance(), move, move.getClass().getSimpleName());
            if (status == BLACK_CHESSMATE) break;
        }
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(false, 50));
        log.warn("game:{}", game.toPGN());
        assertEquals(BLACK_CHESSMATE, status, "BLACK_CHESSMATE should have been detected");
    }

    /**
     * @throws Exception
     * @formatter:off [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- R-B --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- K-W ---  1
     * [a] [b] [c] [d] [e] [f] [g] [h]
     * <p>
     * PGN format to use with -> https://lichess.org/paste
     * @formatter:on
     */
    @Test
    void testChessMateBlack1Move() throws Exception {
        final Board board = Board.createBoard(
                "kg1",
                "re8,kg3",
                BLACK);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                10,
                updateCpuct,
                -1)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(800);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                10,
                updateCpuct,
                -1)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);
        final Game.GameStatus status = game.play();
        final List<MCTSNode> win = blackStrategy.getDirectRoot().search(MCTSNode.State.WIN);
        if (log.isInfoEnabled()) log.info(blackStrategy.mctsTree4log(false, 50));
        log.info("[BLACK] win EndNodes: {}", win.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
        assertTrue(win.size() > 0, "White should have detect loss nodes");
        assertEquals(WHITE_CHESSMATE, status, "we should be in progress mode");
    }

    /**
     * @throws Exception
     * @formatter:off [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- R-B --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- K-W ---  1
     * [a] [b] [c] [d] [e] [f] [g] [h]
     * <p>
     * PGN format to use with -> https://lichess.org/paste
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {10, 50, 100, 800})
    void testAvoidWhiteChessMate1Move(int nbStep) throws Exception {
        final Board board = Board.createBoard(
                "kg1",
                "re8,kg3",
                WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                10,
                updateCpuct,
                -1)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(800);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                10,
                updateCpuct,
                -1)
                .withNbThread(NB_THREAD)
                .withNbSearchCalls(nbStep);
        game.setup(whiteStrategy, blackStrategy);
        nnWhite.addIndexOffset(1F, "e8-e1", Piece.PieceType.ROOK);
        Game.GameStatus status = null;
        status = game.play();
        assertEquals(IN_PROGRESS, status);
        List<MCTSNode> winLossNodes = traceMCTS(whiteStrategy, true);
        assertTrue(winLossNodes.size() > 0, "White should have detect loss nodes");
        status = game.play();
        winLossNodes = traceMCTS(blackStrategy, false);
        log.warn("game:{}", game.toPGN());
        assertEquals(IN_PROGRESS, status, "we should be in progress mode");
    }

    /**
     * @throws Exception
     * @formatter:off [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- K-B ---  8
     * 7  R-W --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- K-W ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     * [a] [b] [c] [d] [e] [f] [g] [h]
     * <p>
     * PGN format to use with -> https://lichess.org/paste
     * ------------ P G N -------------
     * [Event "Test Aquila Chess Player"]
     * [Site "MOUGINS 06250"]
     * [Date "2021.08.23"]
     * [Round "1"]
     * [White "MCTSPLAYER MCTS -> Move: null visit:2 win:0 parent:false childs:1 SubNodes:21"]
     * [Black "RandomPlayer:100"]
     * [Result "*"]
     * 1.Ra8
     * @formatter:on
     */
    @Test
    void testMCTSChessMateWhite1Move() throws IOException {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(100)
//                .withDirichlet(dirichlet);
//        final ChessPlayer blackPlayer = new RandomPlayer(100);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//
//        final Rook rW = new Rook(Color.WHITE, Location.get("A7"));
//        final King kW = new King(Color.WHITE, Location.get("G6"));
//        final King kB = new King(Color.BLACK, Location.get("G8"));
//
//        whitePlayer.addPieces(rW, kW);
//        blackPlayer.addPieces(kB);
//        game.init();
//        log.info(game.toString());
//        log.info("### Black Possibles Moves:");
//        final Moves moves = blackPlayer.getPossibleLegalMoves();
//        moves.forEach((move) -> log.info("### {}", move.getAlgebricNotation()));
//
//        // WHITE MOVE
//        assertThrows(EndOfGameException.class, () -> {
//            log.info("move white: {}", game.play());
//        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1})
    @Disabled
    void testStopDoubleGame(final int seed) throws IOException {
//        Board board = new Board();
//        MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, 200)
//                .withDirichlet(dirichlet);
//        ChessPlayer blackPlayer = new RandomPlayerFirstLevel(seed);
//        Game game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        log.info("BOARD before play:\n" + game);
//        try {
//            do {
//                game.play(false);
//            } while (true);
//        } catch (final EndOfGameException e) {
//            log.warn("END OF game:\n{}\n{}", e.getLocalizedMessage(), game);
//        }
//        log.warn("END OF game:\n{}", game);
//        whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, 200);
//        blackPlayer = new RandomPlayer(seed);
//        board = new Board();
//        game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        log.warn("BOARD before play:\n" + game);
//        try {
//            do {
//                game.play(false);
//            } while (true);
//        } catch (final EndOfGameException e) {
//            log.warn("END OF game:\n{}\n{}", e.getLocalizedMessage(), game);
//        }
//        log.warn("END OF game:\n{}", game);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    @Disabled
    void testStopGame(final int seed) {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, 100).withDirichlet(dirichlet);
//        final ChessPlayer blackPlayer = new RandomPlayerFirstLevel(seed + 1000);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        log.info("BOARD before play:\n" + game);
//        try {
//            do {
//                game.play(false);
//            } while (true);
//        } catch (final EndOfGameException e) {
//            log.info("END OF game:\n{}\n{}", e.getLocalizedMessage(), game);
//        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    public void testAvoidChessIn1(int nbThreads) {
//        INN nnWhite = new Lc0NNTest();
//        DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true, 20);
//        INN nnBlack = new Lc0NNTest();
//        DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, true, 20);
//
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(100)
//                .withNbThread(nbThreads)
//                .withDirichlet(dirichlet);
//        final MCTSPlayer blackPlayer = new MCTSPlayer(deepLearningBlack, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(100)
//                .withNbThread(nbThreads)
//                .withDirichlet(dirichlet);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        final String pgn = "1.e3 f5 2.Nh3"; // 3.g5 4.Qh5 ";
//        game.playPGN(pgn);
//        log.warn(game.toString());
//        assertEquals(Color.BLACK, game.getColorToPlay());
//        Move move;
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, blackPlayer.getStatistic());
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, whitePlayer.getStatistic());
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, blackPlayer.getStatistic());
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, whitePlayer.getStatistic());
    }

    /**
     * count the number of win loss, display the graph if interesting, check the tree and return the list of win/loss nodes
     *
     * @param strategy
     * @param b
     * @return
     */
    private List<MCTSNode> traceMCTS(final MCTSStrategy strategy, boolean forceGraph) {
        List<MCTSNode> winLoss = strategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
        log.info("[{}}] Wins/loss EndNodes ({}): {}", strategy.getAlliance(), winLoss.size(), winLoss.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
        if (forceGraph || winLoss.size() > 0)
            log.info("[{}] graph:\n############################\n{}\n############################", strategy.getAlliance(), DotGenerator.toString(strategy.getDirectRoot(), 20, false));
        Helper.checkMCTSTree(strategy);
        return winLoss;
    }
}
