package lichess.bot.model;

public class Event {
    public static String EVENT_TYPE_CHALLENGE = "challenge";
    public static String EVENT_TYPE_GAME_START = "gameStart";

    public String type;
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

}
