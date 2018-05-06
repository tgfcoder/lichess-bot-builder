package lichess.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Event {
    public EventType type;
    public Challenge challenge;
    public GameStart game;

    public static class Challenge {
        public String id;
        public String status;
        public User challenger;
        public User destUser;
        public Variant variant;
        public boolean rated;
        public TimeControl timeControl;
        public String color;
        public Perf perf;

    }

    public static class GameStart {
        public String id;
    }

    public enum EventType {
        @JsonProperty("challenge") CHALLENGE,
        @JsonProperty("gameStart") GAME_START;
    }
}
