package lichess.bot.model;

public class GameEvent {
    public static String GAME_EVENT_TYPE_FULL = "gameFull";
    public static String GAME_EVENT_TYPE_STATE = "gameState";
    public static String GAME_EVENT_TYPE_CHAT = "chatLine";

    public String type;

    // full
    public String id;
    public boolean rated;
    public Variant variant;
    public Clock clock;
    public String speed;
    public Perf perf;
    public long createdAt;
    public User white;
    public User black;
    public String initialFen;
    public GameState state;

    // game state
    public String moves;
    public long wtime;
    public long btime;
    public long winc;
    public long binc;

    // chat line
    public String username;
    public String text;
    public String room;
}
