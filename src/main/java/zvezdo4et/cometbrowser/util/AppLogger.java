package zvezdo4et.cometbrowser.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

public final class AppLogger {

    private static final Logger ROOT = Logger.getLogger("");
    private static FileHandler fileHandler;
    private static Path currentLogFile;

    private AppLogger() {
    }

    public static void initialize() {
        String appData = UserDataDirUtil.getAppDataDir();
        Path logsDir = Path.of(appData, "logs");
        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        rotateOldLogs(logsDir, null);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        currentLogFile = logsDir.resolve("session_" + timestamp + ".log");

        try {
            fileHandler = new FileHandler(currentLogFile.toString(), false);
            fileHandler.setFormatter(new SimpleFormatter() {
                private final DateTimeFormatter dtf =
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

                @Override
                public String format(LogRecord r) {
                    String ts = LocalDateTime.now().format(dtf);
                    String level = r.getLevel().getName();
                    String logger = r.getLoggerName();
                    if (logger != null && logger.contains(".")) {
                        logger = logger.substring(logger.lastIndexOf('.') + 1);
                    }
                    String msg = formatMessage(r);
                    StringBuilder sb = new StringBuilder();
                    sb.append(ts).append(" [").append(level).append("] ")
                            .append(logger).append(": ").append(msg).append("\n");
                    if (r.getThrown() != null) {
                        StringWriter sw = new StringWriter();
                        r.getThrown().printStackTrace(new PrintWriter(sw));
                        sb.append(sw);
                    }
                    return sb.toString();
                }
            });
            fileHandler.setLevel(Level.ALL);

            ROOT.addHandler(fileHandler);
            ROOT.setLevel(Level.ALL);

            for (Handler h : ROOT.getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    ROOT.removeHandler(h);
                }
            }
            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(Level.ALL);
            ROOT.addHandler(ch);

            ROOT.info("[AppLogger] Log file: " + currentLogFile);
            ROOT.info("[AppLogger] App data dir: " + appData);
            ROOT.info("[AppLogger] Java version: " + System.getProperty("java.version"));
            ROOT.info("[AppLogger] OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void shutdown() {
        ROOT.info("[AppLogger] Shutting down, rotating logs");
        if (fileHandler != null) {
            fileHandler.flush();
            fileHandler.close();
            ROOT.removeHandler(fileHandler);
        }
        String appData = UserDataDirUtil.getAppDataDir();
        Path logsDir = Path.of(appData, "logs");
        rotateOldLogs(logsDir, null);
    }

    private static void rotateOldLogs(Path logsDir, Path skip) {
        if (logsDir == null || !Files.isDirectory(logsDir)) {
            return;
        }
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(logsDir, "session_*.log")) {
            for (Path old : stream) {
                if (skip != null && old.equals(skip)) continue;
                boolean hasCritical = fileContainsSevere(old);
                if (hasCritical) {
                    String name = old.getFileName().toString()
                            .replace("session_", "critical_");
                    Path dest = logsDir.resolve(name);
                    try {
                        Files.move(old, dest, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        Files.deleteIfExists(old);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean fileContainsSevere(Path file) {
        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("[SEVERE]")) return true;
            }
        } catch (IOException e) {
        }
        return false;
    }
}