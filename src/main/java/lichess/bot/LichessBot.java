package lichess.bot;

import lichess.bot.chess.Color;
import lichess.bot.model.*;
import lichess.bot.model.Event.Challenge;
import lichess.client.LichessRestClient;
import lichess.client.LichessServiceException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Extend this class to create your Lichess bot.
 */
public abstract class LichessBot {
    private static final int MAX_NUMBER_GAMES = 8;

    private final LichessRestClient client;
    private final ExecutorService gameExecutorService = Executors.newFixedThreadPool(MAX_NUMBER_GAMES);

    private String userId = "?";
    private String username = "?";

    /**
     * Creates the bot client with the given apiToken. The account this token is tied to must already be registered
     * as a bot account; see {@link lichess.bot.LichessBot#LichessBot(java.lang.String, boolean)} to register programmatically
     *
     * @param apiToken Personal API token of bot account
     */
    public LichessBot(String apiToken) throws IOException {
        this(apiToken, false);
    }

    /**
     * Creates the bot client with the given apiToken and specifying whether to register the account as a bot on lichess.
     *
     * @param apiToken    Personal API token of bot account
     * @param registerBot true if you need the account upgraded to BOT status (can only be done if no games played)
     */
    public LichessBot(String apiToken, boolean registerBot) throws IOException {
        this.client = new LichessRestClient(apiToken);

        if (!validate()) {
            if (registerBot) {
                try {
                    client.post("api/bot/account/upgrade");
                    System.out.println("Bot account successfully registered");
                } catch (LichessServiceException e) {
                    throw new IllegalArgumentException("Unable to register bot account", e);
                }
            } else {
                throw new IllegalArgumentException("apiToken does not point to a BOT account. If you want to register, pass true in bot constructor.");
            }
        }

        System.out.println("Session open with username " + username);
    }

    /**
     * Start listening for challenges and start of game events.
     * <p>
     * Much code lifted from https://sohlich.github.io/post/jackson/
     */
    public void listen() throws IOException {
        client.stream("api/stream/event", Event.class, (Event e) -> {
            if (Event.EVENT_TYPE_CHALLENGE.equals(e.type)) {
                if (acceptChallenge(e.challenge)) {
                    sendChallengeAccept(e.challenge);
                } else {
                    sendChallengeDecline(e.challenge);
                }
            } else if (Event.EVENT_TYPE_GAME_START.equals(e.type)) {
                gameExecutorService.submit(new GameRunner(e.game.id, newEngineInstance()));
            }
        });
    }

    /**
     * Create a new engine instance to handle one chess game.
     */
    protected abstract Engine newEngineInstance();

    /**
     * Whether or not to accept this incoming challenge.
     *
     * @param challenge Challenge information.
     */
    protected abstract boolean acceptChallenge(Challenge challenge);

    private void sendChallengeAccept(Challenge challenge) throws IOException {
        try {
            client.post(String.format("api/challenge/%s/accept", challenge.id));
            System.out.println("Challenge " + challenge.id + " accepted.");
        } catch (LichessServiceException e) {
            System.out.println("Failed to accept challenge " + challenge.id + " because: " + e.getMessage());
        }
    }

    private void sendChallengeDecline(Challenge challenge) throws IOException {
        try {
            client.post(String.format("api/challenge/%s/decline", challenge.id));
            System.out.println("Challenge " + challenge.id + " declined.");
        } catch (LichessServiceException e) {
            System.out.println("Failed to decline challenge " + challenge.id + " because: " + e.getMessage());
        }
    }

    private boolean validate() throws IOException {
        Account account = client.get("api/account", Account.class);
        userId = account.id;
        username = account.username;
        return account.title != null && account.title.equalsIgnoreCase("bot");
    }

    private class GameRunner implements Runnable {
        private final String gameId;
        private final Engine engine;
        private Color myColor = Color.UNKNOWN;

        public GameRunner(String gameId, Engine engine) {
            this.gameId = gameId;
            this.engine = engine;
        }

        @Override
        public void run() {
            try {
                client.stream("api/bot/game/stream/" + gameId, GameEvent.class, (GameEvent ge) -> {
                    if (GameEvent.GAME_EVENT_TYPE_CHAT.equals(ge.type)) {
                        if (ge.room.equals("player") && !ge.username.equals(username)) {
                            String reply = engine.onChatMessage(ge.username, ge.text);
                            if (reply != null && reply.trim().length() > 0) {
                                sendMessage(reply);
                            }
                        }
                    } else if (GameEvent.GAME_EVENT_TYPE_FULL.equals(ge.type)) {
                        setMyColor(ge.white, ge.black);
                        engine.initializeBoardState(ge.initialFen);
                        engine.updateGameState(ge.state.moves, ge.state.wtime, ge.state.btime, ge.state.winc, ge.state.binc);
                        if (isMyMove(ge.state.moves)) {
                            makeMove(engine.makeMove());
                        }
                    } else if (GameEvent.GAME_EVENT_TYPE_STATE.equals(ge.type)) {
                        engine.updateGameState(ge.moves, ge.wtime, ge.btime, ge.winc, ge.binc);
                        if (isMyMove(ge.moves)) {
                            makeMove(engine.makeMove());
                        }
                    }
                });
            } catch (Exception e) {
                System.out.println("Exception occurred during game " + gameId + ". Game processing stopped.");
                e.printStackTrace();
                throw new RuntimeException("Exception occurred during game", e);
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
            if (white.id.equals(userId)) {
                myColor = Color.WHITE;
            } else if (black.id.equals(userId)) {
                myColor = Color.BLACK;
            } else {
                throw new IllegalStateException("In a game where neither player's ID matches my own. White: " + white.id + ", Black: " + black.id + ", Me: " + userId);
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
                if (moveToMake.toLowerCase().equals("resign")) {
                    resign();
                    return;
                } else if (moveToMake.length() != 4) {
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

        private void resign() throws IOException {
            try {
                client.post(String.format("api/bot/game/%s/resign", gameId));
                System.out.println("Game resigned: " + gameId);
            } catch (LichessServiceException e) {
                System.out.println("Couldn't resign game " + gameId + " because: " + e.getMessage());
            }
        }
    }
}
