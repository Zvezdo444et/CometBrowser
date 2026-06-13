package zvezdo4et.cometbrowser.util;

import java.awt.*;
import java.io.InputStream;
import java.util.logging.Logger;

public final class Theme {

    private static final Logger LOG = Logger.getLogger(Theme.class.getName());

    public static final Color VOID = new Color(0x08, 0x0B, 0x14);
    public static final Color NEBULA = new Color(0x0E, 0x14, 0x22);
    public static final Color CRATER = new Color(0x14, 0x1B, 0x2D);
    public static final Color BORDER = new Color(0x1C, 0x24, 0x40);
    public static final Color COMET = new Color(0x6C, 0x63, 0xFF);
    public static final Color TAIL = new Color(0xA7, 0x8B, 0xFA);
    public static final Color STARDUST = new Color(0xC4, 0xC9, 0xDC);
    public static final Color DIM = new Color(0x5A, 0x60, 0x78);
    public static final Color ALERT = new Color(0xE5, 0x25, 0x25);
    public static final Color ALERT_BG = new Color(0x3D, 0x15, 0x22);
    public static final Color ACTIVE_BG = new Color(0x1C, 0x16, 0x50);
    public static final Color PROGRESS = new Color(0x6C, 0x63, 0xFF);
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    public static final Color DELETE_HOVER = new Color(0xF4, 0x72, 0xB6);

    public static Font FONT_REGULAR;
    public static Font FONT_BOLD;
    public static Font FONT_SMALL;
    public static Font FONT_TITLE;
    public static Font FONT_ICON;
    public static Font FONT_ICON_SMALL;

    static {
        Font base = loadTekturFont();
        FONT_REGULAR = base.deriveFont(Font.PLAIN, 13f);
        FONT_BOLD = base.deriveFont(Font.BOLD, 13f);
        FONT_SMALL = base.deriveFont(Font.PLAIN, 11f);
        FONT_TITLE = base.deriveFont(Font.BOLD, 15f);

        Font icon = resolveIconFont();
        FONT_ICON = icon.deriveFont(Font.PLAIN, 14f);
        FONT_ICON_SMALL = icon.deriveFont(Font.PLAIN, 11f);
    }

    private static Font loadTekturFont() {
        Font variable = loadFontResource("/fonts/Tektur-VariableFont_wght.ttf");
        if (variable != null) {
            LOG.info("Loaded Tektur variable font");
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(variable);
            return variable;
        }

        Font regular = loadFontResource("/fonts/Tektur-Regular.ttf");
        if (regular != null) {
            LOG.info("Loaded Tektur Regular font");
            Font bold = loadFontResource("/fonts/Tektur-Bold.ttf");
            if (bold != null) GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(bold);
            Font medium = loadFontResource("/fonts/Tektur-Medium.ttf");
            if (medium != null) GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(medium);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(regular);
            return regular;
        }

        LOG.warning("Tektur font not found in resources, falling back to system font");
        return resolveCyrillicFont();
    }

    private static Font loadFontResource(String resourcePath) {
        try (InputStream in = Theme.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            LOG.warning("Failed to load font from " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    private static Font resolveCyrillicFont() {
        java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

        String[] candidates = {
                "Segoe UI", "Inter", "Roboto", "Noto Sans", "Arial", "Tahoma", "Dialog"
        };

        for (String name : candidates) {
            if (available.contains(name)) {
                Font f = new Font(name, Font.PLAIN, 13);
                if (f.canDisplay('А') && f.canDisplay('я')) {
                    LOG.info("UI font fallback: " + name);
                    return f;
                }
            }
        }
        return new Font("Dialog", Font.PLAIN, 13);
    }

    private static Font resolveIconFont() {
        java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

        String[] candidates = {
                "Segoe UI Symbol", "Segoe UI Emoji", "Apple Symbols", "Symbola", "Dialog"
        };

        for (String name : candidates) {
            if (available.contains(name)) {
                LOG.info("Icon font: " + name);
                return new Font(name, Font.PLAIN, 13);
            }
        }
        return new Font("Dialog", Font.PLAIN, 13);
    }

    private Theme() {
    }
}
