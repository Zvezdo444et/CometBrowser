package zvezdo4et.cometbrowser.native_;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class NativeLibraryLoader {

    private static final Logger LOG = Logger.getLogger(NativeLibraryLoader.class.getName());
    private static final String RESOURCE_PATH = "/native/CometWebView2.dll";
    private static final String DLL_NAME = "CometWebView2.dll";

    private static volatile String extractedPath;

    private NativeLibraryLoader() {
    }

    public static synchronized String getLibraryPath() {
        if (extractedPath != null) return extractedPath;

        try {
            Path tempDir = Files.createTempDirectory("comet-webview2-");
            tempDir.toFile().deleteOnExit();

            Path target = tempDir.resolve(DLL_NAME);

            try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(RESOURCE_PATH)) {
                if (in == null) {
                    throw new IOException("Resource not found: " + RESOURCE_PATH
                            + " (expected at src/main/resources" + RESOURCE_PATH + ")");
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            target.toFile().deleteOnExit();
            extractedPath = target.toAbsolutePath().toString();
            LOG.info("Extracted CometWebView2.dll to " + extractedPath);
            return extractedPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract CometWebView2.dll", e);
        }
    }
}