package zvezdo4et.cometbrowser.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import zvezdo4et.cometbrowser.model.TabSession;
import zvezdo4et.cometbrowser.util.UserDataDirUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SessionPersistenceService {

    private static SessionPersistenceService instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, TabSession> sessions = new LinkedHashMap<>();
    private final File sessionFile = new File(
            UserDataDirUtil.getAppDataDir() + File.separator + "sessions.json");

    private SessionPersistenceService() {
    }

    public static SessionPersistenceService getInstance() {
        if (instance == null) instance = new SessionPersistenceService();
        return instance;
    }

    public void initialize() {
        loadFromDisk();
    }

    private void loadFromDisk() {
        if (!sessionFile.exists()) return;
        try {
            List<Map<String, Object>> raw = mapper.readValue(sessionFile, new TypeReference<>() {
            });
            for (Map<String, Object> entry : raw) {
                TabSession session = new TabSession(
                        (String) entry.get("id"),
                        (String) entry.get("profileId"),
                        (String) entry.get("url"),
                        (String) entry.get("title"),
                        (Boolean) entry.getOrDefault("active", true),
                        (Boolean) entry.getOrDefault("closed", false),
                        ((Number) entry.getOrDefault("lastAccessedAt", 0L)).longValue()
                );
                sessions.put(session.getId(), session);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAll() {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (TabSession s : sessions.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", s.getId());
            entry.put("profileId", s.getProfileId());
            entry.put("url", s.getUrl());
            entry.put("title", s.getTitle());
            entry.put("active", s.isActive());
            entry.put("closed", s.isClosed());
            entry.put("lastAccessedAt", s.getLastAccessedAt());
            raw.add(entry);
        }
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(sessionFile, raw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public TabSession createSession(String profileId, String url, String title) {
        TabSession session = new TabSession(profileId, url, title);
        sessions.put(session.getId(), session);
        saveAll();
        return session;
    }

    public void updateSession(TabSession session) {
        sessions.put(session.getId(), session);
        saveAll();
    }

    public void closeSession(String sessionId) {
        TabSession s = sessions.get(sessionId);
        if (s != null) {
            s.setClosed(true);
            s.setActive(false);
            saveAll();
        }
    }

    public void reopenSession(String sessionId) {
        TabSession s = sessions.get(sessionId);
        if (s != null) {
            s.setClosed(false);
            s.setActive(true);
            s.touch();
            saveAll();
        }
    }

    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        saveAll();
    }

    public List<TabSession> getActiveSessions() {
        return sessions.values().stream().filter(s -> !s.isClosed()).collect(Collectors.toList());
    }

    public List<TabSession> getClosedSessions() {
        return sessions.values().stream().filter(TabSession::isClosed).collect(Collectors.toList());
    }

    public TabSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
}