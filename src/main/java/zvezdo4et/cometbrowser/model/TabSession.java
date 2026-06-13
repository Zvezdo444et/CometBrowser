package zvezdo4et.cometbrowser.model;

import java.util.UUID;

public class TabSession {

    private final String id;
    private String title;
    private String url;
    private String profileId;
    private boolean active;
    private boolean closed;
    private long lastAccessedAt;
    private int tabOrder;

    public TabSession(String profileId, String url, String title) {
        this.id = UUID.randomUUID().toString();
        this.profileId = profileId;
        this.url = url;
        this.title = title;
        this.active = true;
        this.closed = false;
        this.lastAccessedAt = System.currentTimeMillis();
        this.tabOrder = 0;
    }

    public TabSession(String id, String profileId, String url, String title,
                      boolean active, boolean closed, long lastAccessedAt) {
        this.id = id;
        this.profileId = profileId;
        this.url = url;
        this.title = title;
        this.active = active;
        this.closed = closed;
        this.lastAccessedAt = lastAccessedAt;
        this.tabOrder = 0;
    }

    public TabSession(String id, String profileId, String url, String title,
                      boolean active, boolean closed, long lastAccessedAt, int tabOrder) {
        this.id = id;
        this.profileId = profileId;
        this.url = url;
        this.title = title;
        this.active = active;
        this.closed = closed;
        this.lastAccessedAt = lastAccessedAt;
        this.tabOrder = tabOrder;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public String getProfileId() {
        return profileId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public int getTabOrder() {
        return tabOrder;
    }

    public void setTabOrder(int tabOrder) {
        this.tabOrder = tabOrder;
    }
}