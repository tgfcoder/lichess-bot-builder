package lichess.bot;

/**
 * Chess engine that does nothing but resign on it's turn.
 */
public class ResigningChessEngine implements Engine {
    @Override
    public String onChatMessage(String username, String text) {
        return null;
    }

    @Override
    public void initializeBoardState(String initialFen) {

    }

    @Override
    public void updateGameState(String moves, long wtime, long btime, long winc, long binc) {

    }

    @Override
    public String makeMove() {
        return "resign";
    }
}
