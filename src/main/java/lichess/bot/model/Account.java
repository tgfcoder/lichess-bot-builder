package lichess.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Account {
    public String id;
    public String username;
    public String title;
    
    @JsonIgnore
    public boolean isBot() {
        return "bot".equalsIgnoreCase(title);
    }
}
