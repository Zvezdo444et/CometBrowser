package zvezdo4et.cometbrowser.util;

import java.io.File;

public final class UserDataDirUtil {

    private UserDataDirUtil() {
    }

    public static String getAppDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        String dir = appData + File.separator + "CometBrowser";
        new File(dir).mkdirs();
        return dir;
    }

    public static void ensureProfileDirs(String profileDataDir) {
        new File(profileDataDir + File.separator + "cache").mkdirs();
        new File(profileDataDir + File.separator + "storage").mkdirs();
        new File(profileDataDir + File.separator + "local-storage").mkdirs();
    }
}