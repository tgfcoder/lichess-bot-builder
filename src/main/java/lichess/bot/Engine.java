package lichess.bot;

public interface Engine {
    /**
     * Allows the engine to respond to chat messages in the game.
     * @param username  Who the message is from.
     * @param text      Message text.
     */
    String onChatMessage(String username, String text);

    /**
     * Set up the initial state of the chessboard with FEN notation.
     */
    void initializeBoardState(String initialFen);

    /**
     * Update the game state after a move.
     * @param moves Move history string e.g. "e2e4 c7c5 f2f4 d7d6 g1f3 b8c6 f1c4 g8f6 d2d3 g7g6 e1g1 f8g7 b1c3"
     * @param wtime Amount of time white has remaining (millis)
     * @param btime Amount of time black has remaining (millis)
     * @param winc  Amount of time white's time will increment after their move (millis)
     * @param binc  Amount of time black's time will increment after their move (millis)
     */
    void updateGameState(String moves, long wtime, long btime, long winc, long binc);

    /**
     * Compute a move based on the current game state. This will be called on the bot's turn.
     * @return A move in UCI format such as "e2e4". All moves are of the form (piece to move's position)(destination).
     *         "resign" is also accepted.
     */
    String makeMove();
}
