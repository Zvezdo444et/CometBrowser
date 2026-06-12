package zvezdo4et.cometbrowser.model;

import java.util.UUID;

public class BrowserProfile {

    private final String id;
    private String name;
    private String avatarColor;
    private String dataDir;

    public BrowserProfile(String name, String avatarColor, String dataDir) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.avatarColor = avatarColor;
        this.dataDir = dataDir;
    }

    public BrowserProfile(String id, String name, String avatarColor, String dataDir) {
        this.id = id;
        this.name = name;
        this.avatarColor = avatarColor;
        this.dataDir = dataDir;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarColor() {
        return avatarColor;
    }

    public void setAvatarColor(String avatarColor) {
        this.avatarColor = avatarColor;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }
}