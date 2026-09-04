package zvezdo4et.cometbrowser.service;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class DownloadManager {

    private static final Logger LOG = Logger.getLogger(DownloadManager.class.getName());
    private static DownloadManager instance;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Download-Worker");
        t.setDaemon(true);
        return t;
    });

    private final List<DownloadEntry> downloads = Collections.synchronizedList(new ArrayList<>());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<DownloadEntry>> startListeners = new CopyOnWriteArrayList<>();

    public static class DownloadEntry {
        public final String url;
        public final String filename;
        public final long startedAt;
        public volatile long bytesReceived;
        public volatile long totalBytes = -1;
        public volatile boolean done;
        public volatile boolean failed;
        public volatile String error;
        public volatile String destPath;

        DownloadEntry(String url, String filename) {
            this.url = url;
            this.filename = filename;
            this.startedAt = System.currentTimeMillis();
        }

        public int getPercent() {
            if (totalBytes <= 0) return -1;
            return (int) Math.min(100, (bytesReceived * 100) / totalBytes);
        }
    }

    private DownloadManager() {
    }

    public static DownloadManager getInstance() {
        if (instance == null) instance = new DownloadManager();
        return instance;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    public void addStartListener(Consumer<DownloadEntry> l) {
        startListeners.add(l);
    }

    public void removeStartListener(Consumer<DownloadEntry> l) {
        startListeners.remove(l);
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyStartListeners(DownloadEntry entry) {
        for (Consumer<DownloadEntry> l : startListeners) {
            try {
                l.accept(entry);
            } catch (Exception ignored) {
            }
        }
    }

    public DownloadEntry startNativeDownload(String url, String filePath, long totalBytes) {
        String filename = new File(filePath).getName();
        if (filename.isBlank()) filename = extractFilename(url);
        DownloadEntry entry = new DownloadEntry(url, filename);
        entry.destPath = filePath;
        entry.totalBytes = totalBytes;
        downloads.add(0, entry);
        LOG.info("[DownloadManager] Native download started: " + url + " -> " + filePath);
        notifyStartListeners(entry);
        notifyListeners();
        return entry;
    }

    public void updateProgress(String filePath, long bytesReceived, long totalBytes) {
        DownloadEntry e = findByPath(filePath);
        if (e == null) return;
        e.bytesReceived = bytesReceived;
        if (totalBytes > 0) e.totalBytes = totalBytes;
        notifyListeners();
    }

    public void updateState(String filePath, int state) {
        DownloadEntry e = findByPath(filePath);
        if (e == null) return;
        if (state == 2) {
            e.done = true;
            if (e.totalBytes > 0) e.bytesReceived = e.totalBytes;
            LOG.info("[DownloadManager] Native download completed: " + filePath);
        } else if (state == 1) {
            e.failed = true;
            e.error = "Прервано";
            LOG.warning("[DownloadManager] Native download interrupted: " + filePath);
        }
        notifyListeners();
    }

    private DownloadEntry findByPath(String filePath) {
        synchronized (downloads) {
            for (DownloadEntry e : downloads) {
                if (filePath.equals(e.destPath)) return e;
            }
        }
        return null;
    }

    public List<DownloadEntry> getDownloads() {
        return Collections.unmodifiableList(new ArrayList<>(downloads));
    }

    public void openDownloadsFolder() {
        try {
            Desktop.getDesktop().open(
                    new File(SettingsManager.getInstance().getDownloadsFolder()));
        } catch (Exception e) {
            LOG.warning("[DownloadManager] Cannot open downloads folder: " + e.getMessage());
        }
    }

    public void openFile(DownloadEntry entry) {
        if (entry.destPath == null) return;
        try {
            Desktop.getDesktop().open(new File(entry.destPath));
            LOG.info("[DownloadManager] Opened file: " + entry.destPath);
        } catch (Exception e) {
            LOG.warning("[DownloadManager] Cannot open file: " + e.getMessage());
        }
    }

    public void openFileWith(DownloadEntry entry) {
        if (entry.destPath == null) return;
        try {
            new ProcessBuilder("rundll32.exe", "shell32.dll,OpenAs_RunDLL", entry.destPath).start();
            LOG.info("[DownloadManager] Opened 'Open With' dialog for: " + entry.destPath);
        } catch (Exception e) {
            LOG.warning("[DownloadManager] Cannot open 'Open With' dialog: " + e.getMessage());
        }
    }

    private String extractFilename(String url) {
        String path = url.split("[?#]")[0];
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isBlank()) name = "download";
        try {
            name = java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return name;
    }
}