package lichess.bot;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lichess.bot.chess.Color;
import lichess.bot.model.*;
import lichess.bot.model.Event.Challenge;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * Extend this class to create your Lichess bot.
 */
public abstract class LichessBot {
    private static final String ENDPOINT = "https://lichess.org";
    private static final ObjectMapper mapper = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int MAX_NUMBER_GAMES = 8;

    private final URL server;
    private final String apiToken;
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
        this.server = new URL(ENDPOINT);
        this.apiToken = apiToken;

        if (!validate()) {
            if (registerBot) {
                OkErrorResponse response = post("api/bot/account/upgrade");
                if (response.ok) {
                    System.out.println("Bot account successfully registered");
                } else {
                    throw new IllegalArgumentException("Unable to register bot account: " + response.error);
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
        try (InputStream in = get("api/stream/event")) {
            processStream(in, Event.class, (Event e) -> {
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

    private <T> void processStream(InputStream in, Class<T> valueType, ValueProcessor<T> processor) throws IOException {
        JsonParser parser = new JsonFactory().createParser(in);
        JsonToken token = parser.nextToken();

        // Try find at least one object or array.
        while (!JsonToken.START_ARRAY.equals(token) && token != null && !JsonToken.START_OBJECT.equals(token)) {
            parser.nextToken();
        }

        // No content found
        if (token == null) {
            return;
        }

        boolean scanMore = false;

        while (true) {
            // If the first token is the start of object ->
            // the response contains only one object (no array)
            // do not try to get the first object from array.
            try {
                if (!JsonToken.START_OBJECT.equals(token) || scanMore) {
                    token = parser.nextToken();
                }
                if (!JsonToken.START_OBJECT.equals(token)) {
                    break;
                }
                if (token == null) {
                    break;
                }

                T value = mapper.readValue(parser, valueType);
                processor.process(value);

                scanMore = true;
            } catch (JsonParseException e) {
                throw new IllegalStateException("Could not parse JSON", e);
            }
        }
    }

    private void sendChallengeAccept(Challenge challenge) throws IOException {
        OkErrorResponse response = post(String.format("api/challenge/%s/accept", challenge.id));
        if (response.ok) {
            System.out.println("Challenge " + challenge.id + " accepted.");
        } else {
            System.out.println("Failed to accept challenge " + challenge.id + " because: " + response.error);
        }
    }

    private void sendChallengeDecline(Challenge challenge) throws IOException {
        OkErrorResponse response = post(String.format("api/challenge/%s/decline", challenge.id));
        if (response.ok) {
            System.out.println("Challenge " + challenge.id + " declined.");
        } else {
            System.out.println("Failed to decline challenge " + challenge.id + " because: " + response.error);
        }
    }

    private boolean validate() throws IOException {
        try (InputStream in = get("api/account")) {
            Account account = mapper.readValue(in, Account.class);
            userId = account.id;
            username = account.username;
            return account.title != null && account.title.equalsIgnoreCase("bot");
        }
    }

    private InputStream get(String path) throws IOException {
        HttpURLConnection urlConnection = openConnection(path);
        return urlConnection.getInputStream();
    }

    private OkErrorResponse post(String path) throws IOException {
        return post(path, "");
    }

    private OkErrorResponse post(String path, String formData) throws IOException {
        byte[] postData = formData.getBytes(StandardCharsets.UTF_8);
        int postDataLength = postData.length;

        HttpURLConnection urlConnection = openConnection(path);
        urlConnection.setRequestMethod("POST");
        if (postDataLength > 0) {
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (DataOutputStream dos = new DataOutputStream(urlConnection.getOutputStream())) {
                dos.write(postData);
            }
        }
        
        try (InputStream in = urlConnection.getInputStream()) {
            return mapper.readValue(in, OkErrorResponse.class);
        }
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        URL url = new URL(server, path);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.addRequestProperty("Authorization", "Bearer " + apiToken);
        return urlConnection;
    }

    private interface ValueProcessor<T> {
        void process(T value) throws IOException;
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
            try (InputStream in = get("api/bot/game/stream/" + gameId)) {
                processStream(in, GameEvent.class, (GameEvent ge) -> {
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
            OkErrorResponse response = post(String.format("api/bot/game/%s/chat", gameId), "room=player&text=" + text);
            if (response.ok) {
                System.out.println("Message sent: " + text);
            } else {
                System.out.println("Couldn't send message '" + text + "' because: " + response.error);
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
                OkErrorResponse response = post(String.format("api/bot/game/%s/move/%s", gameId, moveToMake));
                if (response.ok) {
                    System.out.println("Move made successfully: " + moveToMake);
                } else {
                    System.out.println("Couldn't make move because: " + response.error);
                }
            } catch (IOException e) {
                System.out.println("Unable to make move " + moveToMake + ": " + e.getMessage());
            }
        }

        private void resign() throws IOException {
            OkErrorResponse response = post(String.format("api/bot/game/%s/resign", gameId));
            if (response.ok) {
                System.out.println("Game resigned: " + gameId);
            } else {
                System.out.println("Couldn't resign game " + gameId + " because: " + response.error);
            }
        }
    }
}
