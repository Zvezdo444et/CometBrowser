package zvezdo4et.cometbrowser.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import zvezdo4et.cometbrowser.model.Bookmark;
import zvezdo4et.cometbrowser.util.UserDataDirUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class BookmarkManager {

    private static final Logger LOG = Logger.getLogger(BookmarkManager.class.getName());
    private static BookmarkManager instance;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final File file = new File(
            UserDataDirUtil.getAppDataDir() + File.separator + "bookmarks.json");

    private BookmarkManager() {}

    public static BookmarkManager getInstance() {
        if (instance == null) instance = new BookmarkManager();
        return instance;
    }

    public void initialize() {
        loadFromDisk();
        if (bookmarks.isEmpty()) {
            bookmarks.add(new Bookmark("Google", "https://www.google.com"));
            bookmarks.add(new Bookmark("YouTube", "https://www.youtube.com"));
            bookmarks.add(new Bookmark("GitHub", "https://www.github.com"));
            bookmarks.add(new Bookmark("Claude", "https://claude.ai"));
            saveAll();
        }
    }

    private void loadFromDisk() {
        if (!file.exists()) return;
        try {
            List<Map<String, String>> raw = mapper.readValue(file, new TypeReference<>() {});
            for (Map<String, String> entry : raw) {
                String id   = entry.getOrDefault("id", UUID.randomUUID().toString());
                String name = entry.getOrDefault("name", "");
                String url  = entry.getOrDefault("url", "");
                if (!name.isBlank() && !url.isBlank()) {
                    bookmarks.add(new Bookmark(id, name, url));
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to load bookmarks: " + e.getMessage());
        }
    }

    public void saveAll() {
        List<Map<String, String>> raw = new ArrayList<>();
        for (Bookmark b : bookmarks) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("id",   b.getId());
            entry.put("name", b.getName());
            entry.put("url",  b.getUrl());
            raw.add(entry);
        }
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, raw);
        } catch (IOException e) {
            LOG.warning("Failed to save bookmarks: " + e.getMessage());
        }
    }

    public List<Bookmark> getAllBookmarks() {
        return Collections.unmodifiableList(bookmarks);
    }

    public void addBookmark(String name, String url) {
        bookmarks.add(new Bookmark(name, url));
        saveAll();
    }

    public void removeBookmark(String id) {
        bookmarks.removeIf(b -> b.getId().equals(id));
        saveAll();
    }
}