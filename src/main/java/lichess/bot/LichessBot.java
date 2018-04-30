package lichess.bot;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lichess.bot.model.Account;
import lichess.bot.model.Event;
import lichess.bot.model.OkErrorResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * Extend this class to create your Lichess bot.
 */
public class LichessBot {
    private static final String ENDPOINT = "https://lichess.org";
    private static final ObjectMapper mapper = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final URL server;
    private final String apiToken;

    /**
     * Creates the bot client with the given apiToken. The account this token is tied to must already be registered
     * as a bot account; see {@link lichess.bot.LichessBot#LichessBot(java.lang.String, boolean)} to register programmatically
     * @param apiToken      Personal API token of bot account
     */
    public LichessBot(String apiToken) throws IOException {
        this(apiToken, false);
    }

    /**
     * Creates the bot client with the given apiToken and specifying whether to register the account as a bot on lichess.
     * @param apiToken      Personal API token of bot account
     * @param registerBot   true if you need the account upgraded to BOT status (can only be done if no games played)
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

    /**
     * Start listening for challenges and start of game events.
     *
     * Much code lifted from https://sohlich.github.io/post/jackson/
     */
    public void listen() throws IOException {
        try (InputStream in = get("api/stream/event")) {
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

                    Event event = mapper.readValue(parser, Event.class);

                    if (event.type.equals(Event.EVENT_TYPE_CHALLENGE)) {
                        if (acceptChallenge(event.challenge)) {
                            // accept
                        } else {
                            declineChallenge(event.challenge);
                        }
                    }

                    scanMore = true;
                } catch (JsonParseException e) {
                    throw new IllegalStateException("Could not parse JSON", e);
                }
            }
        }
    }

    private void declineChallenge(Event.Challenge challenge) throws IOException {
        try (InputStream in = post(String.format("api/challenge/%s/decline", challenge.id))) {
            OkErrorResponse response = mapper.readValue(in, OkErrorResponse.class);
            if (response.ok) {
                return;
            } else {
                System.out.println("Failed to decline challenge " + challenge.id + " because: " + response.error);
            }
        }
    }

    /**
     * Whether or not to accept this incoming challenge.
     * @param challenge Challenge information.
     */
    private boolean acceptChallenge(Event.Challenge challenge) {
        return false;
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
}
