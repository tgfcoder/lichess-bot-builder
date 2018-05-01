package lichess.bot;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lichess.bot.model.Account;
import lichess.bot.model.Event;
import lichess.bot.model.GameEvent;
import lichess.bot.model.OkErrorResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * Extend this class to create your Lichess bot.
 */
public class LichessBot {
    private static final String ENDPOINT = "https://lichess.org";
    private static final ObjectMapper mapper = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final URL server;
    private final String apiToken;
    private final ExecutorService gameExecutorService = Executors.newFixedThreadPool(1);

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
                try (InputStream in = post("api/bot/account/upgrade")) {
                    OkErrorResponse response = mapper.readValue(in, OkErrorResponse.class);
                    if (response.ok) {
                        System.out.println("Bot account successfully registered");
                    } else {
                        throw new IllegalArgumentException("Unable to register bot account: " + response.error);
                    }
                }
            } else {
                throw new IllegalArgumentException("apiToken does not point to a BOT account. If you want to register, pass true in bot constructor.");
            }
        }
    }

    public void setMaxGameLimit(int maxGameLimit) {
        ((ThreadPoolExecutor) this.gameExecutorService).setCorePoolSize(maxGameLimit);
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
                    gameExecutorService.submit(new GameRunner(e.game.id));
                }
            });
        }
    }

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

    private interface ValueProcessor<T> {
        void process(T value) throws IOException;
    }

    private void sendChallengeAccept(Event.Challenge challenge) throws IOException {
        try (InputStream in = post(String.format("api/challenge/%s/accept", challenge.id))) {
            OkErrorResponse response = mapper.readValue(in, OkErrorResponse.class);
            if (response.ok) {
                System.out.println("Challenge " + challenge.id + " accepted.");
                return;
            } else {
                System.out.println("Failed to accept challenge " + challenge.id + " because: " + response.error);
            }
        }
    }

    private void sendChallengeDecline(Event.Challenge challenge) throws IOException {
        try (InputStream in = post(String.format("api/challenge/%s/decline", challenge.id))) {
            OkErrorResponse response = mapper.readValue(in, OkErrorResponse.class);
            if (response.ok) {
                System.out.println("Challenge " + challenge.id + " declined.");
                return;
            } else {
                System.out.println("Failed to decline challenge " + challenge.id + " because: " + response.error);
            }
        }
    }

    /**
     * Whether or not to accept this incoming challenge.
     *
     * @param challenge Challenge information.
     */
    private boolean acceptChallenge(Event.Challenge challenge) {
        return true;
    }

    private boolean validate() throws IOException {
        try (InputStream in = get("api/account")) {
            Account account = mapper.readValue(in, Account.class);
            return account.title != null && account.title.toLowerCase().equals("bot");
        }
    }

    private InputStream get(String path) throws IOException {
        URL url = new URL(server, path);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.addRequestProperty("Authorization", "Bearer " + apiToken);
        return urlConnection.getInputStream();
    }

    private InputStream post(String path) throws IOException {
        URL url = new URL(server, path);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.addRequestProperty("Authorization", "Bearer " + apiToken);
        return urlConnection.getInputStream();
    }

    private class GameRunner implements Runnable {
        private final String gameId;

        public GameRunner(String gameId) {
            this.gameId = gameId;
        }

        @Override
        public void run() {
            try (InputStream in = get("api/bot/game/stream/" + gameId)) {
                processStream(in, GameEvent.class, (GameEvent ge) -> {
                    if (GameEvent.GAME_EVENT_TYPE_CHAT.equals(ge.type)) {
                        makeMove(ge.text.trim().substring(0, 4));
                    }
                });
            } catch (IOException e) {
                System.out.println("IOException during game");
                throw new RuntimeException("IOException occurred during game", e);
            }
        }

        private void makeMove(String moveToMake) {
            try (InputStream moveIs = post(String.format("api/bot/game/%s/move/%s", gameId, moveToMake))) {
                OkErrorResponse response = mapper.readValue(moveIs, OkErrorResponse.class);
                if (response.ok) {
                    System.out.println("Move made: " + moveToMake);
                } else {
                    System.out.println("Couldn't make move because: " + response.error);
                }
            } catch (IOException e) {
                System.out.println("Couldn't make move");
            }
        }
    }
}
