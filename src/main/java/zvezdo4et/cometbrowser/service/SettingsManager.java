package zvezdo4et.cometbrowser.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import zvezdo4et.cometbrowser.util.UserDataDirUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SettingsManager {

    private static final Logger LOG = Logger.getLogger(SettingsManager.class.getName());
    private static SettingsManager instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private final File settingsFile = new File(
            UserDataDirUtil.getAppDataDir() + File.separator + "settings.json");

    private Map<String, Object> data = new LinkedHashMap<>();
    private boolean initialized = false;

    private SettingsManager() {
    }

    public static SettingsManager getInstance() {
        if (instance == null) instance = new SettingsManager();
        return instance;
    }

    public synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        load();
    }

    private void load() {
        if (!settingsFile.exists()) {
            LOG.info("[SettingsManager] No settings.json found, using defaults");
            return;
        }
        try {
            data = mapper.readValue(settingsFile, new TypeReference<>() {
            });
            LOG.info("[SettingsManager] Loaded settings from " + settingsFile);
        } catch (IOException e) {
            LOG.warning("[SettingsManager] Failed to load settings: " + e.getMessage());
        }
    }

    public synchronized void save() {
        if (!initialized) initialize();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile, data);
            LOG.fine("[SettingsManager] Settings saved");
        } catch (IOException e) {
            LOG.warning("[SettingsManager] Failed to save settings: " + e.getMessage());
        }
    }

    public void saveWindowBounds(Rectangle bounds, boolean maximized) {
        if (!maximized && bounds != null) {
            data.put("windowX", bounds.x);
            data.put("windowY", bounds.y);
            data.put("windowW", bounds.width);
            data.put("windowH", bounds.height);
        }
        data.put("windowMaximized", maximized);
        save();
    }

    public Rectangle getWindowBounds() {
        if (!data.containsKey("windowX")) return null;
        int x = ((Number) data.get("windowX")).intValue();
        int y = ((Number) data.get("windowY")).intValue();
        int w = ((Number) data.getOrDefault("windowW", 1280)).intValue();
        int h = ((Number) data.getOrDefault("windowH", 800)).intValue();
        return new Rectangle(x, y, w, h);
    }

    public boolean isWindowMaximized() {
        return Boolean.TRUE.equals(data.get("windowMaximized"));
    }

    public void saveLastActive(String profileId, String sessionId) {
        if (profileId != null) data.put("lastProfileId", profileId);
        if (sessionId != null) data.put("lastSessionId", sessionId);
        save();
    }

    public String getLastProfileId() {
        return (String) data.get("lastProfileId");
    }

    public String getLastSessionId() {
        return (String) data.get("lastSessionId");
    }

    public String getDownloadsFolder() {
        String val = (String) data.get("downloadsFolder");
        if (val == null) {
            val = System.getProperty("user.home") + File.separator + "Downloads";
        }
        return val;
    }

    public void setDownloadsFolder(String path) {
        data.put("downloadsFolder", path);
        save();
    }

    public String getSearchEngine() {
        return (String) data.getOrDefault("searchEngine", "google");
    }

    public void setSearchEngine(String engine) {
        data.put("searchEngine", engine);
        save();
    }

    public void set(String key, Object value) {
        data.put(key, value);
        save();
    }
}