package zvezdo4et.cometbrowser;

import zvezdo4et.cometbrowser.controller.MainWindow;
import zvezdo4et.cometbrowser.service.BookmarkManager;
import zvezdo4et.cometbrowser.service.ProfileManager;
import zvezdo4et.cometbrowser.service.SessionPersistenceService;

import javax.swing.*;
import java.util.logging.Logger;

public class CometBrowserApplication {

    private static final Logger LOG = Logger.getLogger(CometBrowserApplication.class.getName());

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");

        try {
            ProfileManager.getInstance().initialize();
            SessionPersistenceService.getInstance().initialize();
            BookmarkManager.getInstance().initialize();
        } catch (Exception e) {
            LOG.severe("Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}