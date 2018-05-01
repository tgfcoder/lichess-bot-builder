package lichess.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Variant {
    public String key;
    public String name;
    @JsonProperty("short")
    public String shortName;
}
