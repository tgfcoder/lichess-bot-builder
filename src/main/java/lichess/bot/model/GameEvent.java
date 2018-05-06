package lichess.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GameEvent {
    public GameEventType type;

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
    
    public enum GameEventType {
        @JsonProperty("gameFull") FULL,
        @JsonProperty("gameState") STATE,
        @JsonProperty("chatLine") CHAT;
    }
}
