package lichess.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Event {
    public static String EVENT_TYPE_CHALLENGE = "challenge";
    public static String EVENT_TYPE_GAME_START = "gameStart";

    public String type;
    public Challenge challenge;
    public GameStart gameStart;

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

        public static class User {
            public String id;
            public String name;
            public String title;
            public int rating;
            public boolean provisional;
            public boolean patron;
            public boolean online;
            public int lag;
        }

        public static class Variant {
            public String key;
            public String name;
            @JsonProperty("short")
            public String shortName;
        }

        public static class TimeControl {
            public String type;
            public int limit;
            public int increment;
            public String show;
        }

        public static class Perf {
            public String icon;
            public String name;
        }
    }

    public static class GameStart {
        public String id;
    }
}
