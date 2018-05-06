package lichess.bot;

import lichess.bot.model.Account;
import lichess.bot.model.Event;
import lichess.bot.model.Event.Challenge;
import lichess.client.LichessRestClient;
import lichess.client.LichessServiceException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Extend this class to create your Lichess bot.
 */
public abstract class LichessBot {
    private static final int MAX_NUMBER_GAMES = 8;
    public static final Pattern VALID_MOVE_PATTERN = Pattern.compile("[a-h][1-8][a-h][1-8][knbrq]?");

    private final LichessRestClient client;
    private final ExecutorService gameExecutorService = Executors.newFixedThreadPool(MAX_NUMBER_GAMES);

    private final Account account;

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
        this.account = client.get("api/account", Account.class);

        if (!account.isBot()) {
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

        System.out.println("Session open with username " + account.username);
    }

    /**
     * Start listening for challenges and start of game events.
     * <p>
     * Much code lifted from https://sohlich.github.io/post/jackson/
     */
    public void listen() throws IOException {
        client.stream("api/stream/event", Event.class, this::processEvent);
    }
    
    private void processEvent(Event e) throws IOException {
        switch (e.type) {
        case CHALLENGE:
            if (acceptChallenge(e.challenge)) {
                sendChallengeAccept(e.challenge);
            } else {
                sendChallengeDecline(e.challenge);
            }
            break;
            
        case GAME_START:
            gameExecutorService.submit(new LichessGameRunner(account, e.game.id, client, newEngineInstance()));
            break;
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
}
