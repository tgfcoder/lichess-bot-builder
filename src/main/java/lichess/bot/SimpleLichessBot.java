package lichess.bot;

import lichess.bot.model.Event;

import java.io.IOException;

/**
 * Basic implementation of LichessBot
 */
public class SimpleLichessBot extends LichessBot {
    public SimpleLichessBot(String apiToken) throws IOException {
        super(apiToken);
    }

    public SimpleLichessBot(String apiToken, boolean registerBot) throws IOException {
        super(apiToken, registerBot);
    }

    @Override
    protected Engine newEngineInstance() {
        return new ResigningChessEngine();
    }

    @Override
    protected boolean acceptChallenge(Event.Challenge challenge) {
        return true;
    }
}
