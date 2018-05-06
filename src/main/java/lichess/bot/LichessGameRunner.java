package lichess.bot;

import java.io.IOException;

import lichess.bot.chess.Color;
import lichess.bot.model.Account;
import lichess.bot.model.GameEvent;
import lichess.bot.model.User;
import lichess.client.LichessRestClient;
import lichess.client.LichessServiceException;

class LichessGameRunner implements Runnable {
    private final Account account;
    private final String gameId;
    private Color myColor = Color.UNKNOWN;
    
    private final LichessRestClient client;
    private final Engine engine;

    public LichessGameRunner(Account account, String gameId, LichessRestClient client, Engine engine) {
        this.account = account;
        this.gameId = gameId;
        this.client = client;
        this.engine = engine;
    }

    @Override
    public void run() {
        try {
            client.stream("api/bot/game/stream/" + gameId, GameEvent.class, this::processEvent);
        } catch (Exception e) {
            System.out.println("Exception occurred during game " + gameId + ". Game processing stopped.");
            e.printStackTrace();
            throw new RuntimeException("Exception occurred during game", e);
        }
    }
    
    private void processEvent(GameEvent ge) throws IOException {
        switch (ge.type) {
        case CHAT:
            if (ge.room.equals("player") && !ge.username.equals(account.username)) {
                String reply = engine.onChatMessage(ge.username, ge.text);
                if (reply != null && reply.trim().length() > 0) {
                    sendMessage(reply);
                }
            }
            break;
            
        case FULL:
            setMyColor(ge.white, ge.black);
            engine.initializeBoardState(ge.initialFen, myColor == Color.WHITE);
            engine.updateGameState(ge.state.moves, ge.state.wtime, ge.state.btime, ge.state.winc, ge.state.binc);
            if (isMyMove(ge.state.moves)) {
                makeMove(engine.makeMove());
            }
            break;
            
        case STATE:
            engine.updateGameState(ge.moves, ge.wtime, ge.btime, ge.winc, ge.binc);
            if (isMyMove(ge.moves)) {
                makeMove(engine.makeMove());
            }
            break;
        }
    }

    private void sendMessage(String text) throws IOException {
        try {
            client.post(String.format("api/bot/game/%s/chat", gameId), "room=player&text=" + text);
            System.out.println("Message sent: " + text);
        } catch (LichessServiceException e) {
            System.out.println("Couldn't send message '" + text + "' because: " + e.getMessage());
        }
    }

    private void setMyColor(User white, User black) {
        if (white.id != null && white.id.equals(account.id)) {
            myColor = Color.WHITE;
        } else if (black.id != null && black.id.equals(account.id)) {
            myColor = Color.BLACK;
        } else {
            throw new IllegalStateException("In a game where neither player's ID matches my own. White: " + white.id + ", Black: " + black.id + ", Me: " + account.id);
        }
    }

    private boolean isMyMove(String moves) {
        if (moves == null || moves.trim().isEmpty()) {
            return myColor == Color.WHITE;
        }

        String[] moveArray = moves.trim().split(" ");

        if (moveArray.length % 2 == 0) {
            // White's turn
            return myColor == Color.WHITE;
        } else {
            // Black's turn
            return myColor == Color.BLACK;
        }
    }

    private void makeMove(String moveToMake) throws IOException {
        boolean invalidMove = false;

        if (moveToMake == null) {
            invalidMove = true;
        } else {
            moveToMake = moveToMake.toLowerCase().trim();

            if (moveToMake.equals("resign")) {
                resign();
                return;
            } else if (!validMoveFormat(moveToMake)) {
                invalidMove = true;
            }
        }

        if (invalidMove) {
            throw new IllegalArgumentException("Invalid move: " + moveToMake);
        }

        System.out.println("Making move " + moveToMake);
        try {
            try {
                client.post(String.format("api/bot/game/%s/move/%s", gameId, moveToMake));
                System.out.println("Move made successfully: " + moveToMake);
            } catch (LichessServiceException e) {
                System.out.println("Couldn't make move because: " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("Unable to make move " + moveToMake + ": " + e.getMessage());
        }
    }

    private boolean validMoveFormat(String move) {
        return LichessBot.VALID_MOVE_PATTERN.matcher(move).matches();
    }

    private void resign() throws IOException {
        try {
            client.post(String.format("api/bot/game/%s/resign", gameId));
            System.out.println("Game resigned: " + gameId);
        } catch (LichessServiceException e) {
            System.out.println("Couldn't resign game " + gameId + " because: " + e.getMessage());
        }
    }
}
