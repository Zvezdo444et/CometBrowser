package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.model.BrowserProfile;
import zvezdo4et.cometbrowser.model.TabSession;
import zvezdo4et.cometbrowser.service.DownloadManager;
import zvezdo4et.cometbrowser.service.ProfileManager;
import zvezdo4et.cometbrowser.service.SessionPersistenceService;
import zvezdo4et.cometbrowser.service.SettingsManager;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;

public class MainWindow extends JFrame {

    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());

    private static final String ICON_CLOSE_TAB = "\u2715";
    private static final Color DRAG_HIGHLIGHT = new Color(0x7C, 0x1F, 0x4A);
    private static final int MAX_TAB_TEXT_WIDTH = 200;
    private static final int TAB_GAP = 4;

    private JPanel titleBar;
    private JPanel profileBar;
    private JPanel profileCards;
    private CardLayout cardLayout;
    private JLabel statusLabel;
    private Timer statusClearTimer;

    private String selectedProfileId;

    private Point dragOffset;
    private JButton maximizeBtn;
    private JButton downloadsBtn;

    private Rectangle normalBounds;
    private boolean isMaximized = false;

    private final Map<String, BrowserPanel> tabPanels = new HashMap<>();
    private final Map<String, TabStrip> profileTabs = new HashMap<>();

    private SparkleGlassPane sparkleGlassPane;
    private DownloadsPanel downloadsPanel;

    public MainWindow() {
        super("CometBrowser");
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        Rectangle savedBounds = SettingsManager.getInstance().getWindowBounds();
        if (savedBounds != null) {
            setBounds(savedBounds);
        } else {
            setSize(1280, 800);
            setLocationRelativeTo(null);
        }
        setMinimumSize(new Dimension(600, 450));

        getContentPane().setBackground(Theme.VOID);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });

        buildUI();

        sparkleGlassPane = new SparkleGlassPane();
        sparkleGlassPane.setOpaque(false);
        setGlassPane(sparkleGlassPane);
        sparkleGlassPane.setVisible(true);

        buildProfileBar();

        DownloadManager.getInstance().addStartListener(entry ->
                SwingUtilities.invokeLater(() -> showDownloadToast(entry)));

        String lastProfile = SettingsManager.getInstance().getLastProfileId();
        List<BrowserProfile> profiles = ProfileManager.getInstance().getAllProfiles();
        boolean found = false;
        if (lastProfile != null) {
            for (BrowserProfile p : profiles) {
                if (p.getId().equals(lastProfile)) {
                    selectedProfileId = p.getId();
                    found = true;
                    break;
                }
            }
        }
        if (!found && !profiles.isEmpty()) selectedProfileId = profiles.get(0).getId();

        ensureProfileTabs(selectedProfileId);
        cardLayout.show(profileCards, selectedProfileId);

        if (SettingsManager.getInstance().isWindowMaximized()) {
            SwingUtilities.invokeLater(this::maximizeToScreenBounds);
        }
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        titleBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.VOID);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        titleBar.setOpaque(false);
        titleBar.setPreferredSize(new Dimension(0, 44));

        profileBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        profileBar.setOpaque(false);

        JPanel windowControls = buildWindowControls();

        titleBar.add(profileBar, BorderLayout.CENTER);
        titleBar.add(windowControls, BorderLayout.EAST);

        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) toggleMaximize();
            }
        });
        titleBar.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) return;
                if (isMaximized) restoreFromMaximize();
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - dragOffset.x, loc.y + e.getY() - dragOffset.y);
            }
        });

        cardLayout = new CardLayout();
        profileCards = new JPanel(cardLayout) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.VOID);
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        profileCards.setOpaque(true);
        profileCards.setBackground(Theme.VOID);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(Theme.FONT_SMALL);
        statusLabel.setForeground(Theme.DIM);
        statusLabel.setBorder(new EmptyBorder(2, 12, 2, 12));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Theme.VOID);

        statusClearTimer = new Timer(3000, e -> statusLabel.setText(" "));
        statusClearTimer.setRepeats(false);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(statusLabel, BorderLayout.WEST);

        add(titleBar, BorderLayout.NORTH);
        add(profileCards, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (event.getID() == KeyEvent.KEY_PRESSED
                    && event.getKeyCode() == KeyEvent.VK_SPACE) {
                Component owner = KeyboardFocusManager
                        .getCurrentKeyboardFocusManager().getFocusOwner();
                if (!(owner instanceof javax.swing.text.JTextComponent)
                        && !(owner instanceof Canvas)) {
                    event.consume();
                    return true;
                }
            }
            return false;
        });
    }

    private JPanel buildWindowControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);

        JButton settingsBtn = makeIconButton("\u2699", "Настройки");
        settingsBtn.addActionListener(e -> {
            SettingsDialog dlg = new SettingsDialog(this);
            dlg.setVisible(true);
        });

        downloadsBtn = makeIconButton("\u2193", "Загрузки");
        downloadsBtn.addActionListener(e -> openDownloadsPanel());

        JButton minBtn = makeWindowButton("\u2212", false);
        minBtn.addActionListener(e -> setExtendedState(JFrame.ICONIFIED));

        maximizeBtn = makeWindowButton("\u25A1", false);
        maximizeBtn.addActionListener(e -> toggleMaximize());

        JButton closeBtn = makeWindowButton("\u2715", true);
        closeBtn.addActionListener(e -> onExit());

        controls.add(settingsBtn);
        controls.add(downloadsBtn);
        controls.add(minBtn);
        controls.add(maximizeBtn);
        controls.add(closeBtn);
        return controls;
    }

    private void openDownloadsPanel() {
        if (downloadsPanel != null && downloadsPanel.isDisplayable()) {
            downloadsPanel.toFront();
            downloadsPanel.requestFocus();
            return;
        }
        downloadsPanel = new DownloadsPanel(this, downloadsBtn);
        downloadsPanel.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                downloadsPanel = null;
            }
        });
        downloadsPanel.setVisible(true);
    }

    private void showDownloadToast(DownloadManager.DownloadEntry entry) {
        JWindow toast = new JWindow(this);
        toast.setBackground(new Color(0, 0, 0, 0));
        try {
            toast.setOpacity(1f);
        } catch (Exception ignored) {
        }

        JPanel panel = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.NEBULA);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 14, 12, 16));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel("\u2193");
        icon.setFont(Theme.FONT_ICON.deriveFont(18f));
        icon.setForeground(Theme.COMET);
        icon.setBorder(new EmptyBorder(0, 0, 0, 4));

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);

        JLabel headline = new JLabel("Загрузка началась");
        headline.setFont(Theme.FONT_SMALL);
        headline.setForeground(Theme.DIM);
        headline.setAlignmentX(LEFT_ALIGNMENT);

        String name = (entry.filename == null || entry.filename.isBlank()) ? "Файл" : entry.filename;
        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(Theme.FONT_REGULAR);
        nameLbl.setForeground(Theme.STARDUST);
        nameLbl.setAlignmentX(LEFT_ALIGNMENT);

        textCol.add(headline);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(nameLbl);

        panel.add(icon, BorderLayout.WEST);
        panel.add(textCol, BorderLayout.CENTER);

        toast.setContentPane(panel);
        toast.pack();
        int width = Math.max(260, toast.getWidth());
        toast.setSize(width, toast.getHeight());

        if (downloadsBtn != null && downloadsBtn.isShowing()) {
            try {
                Point loc = downloadsBtn.getLocationOnScreen();
                int x = loc.x + downloadsBtn.getWidth() - toast.getWidth();
                int y = loc.y + downloadsBtn.getHeight() + 6;
                toast.setLocation(x, y);
            } catch (IllegalComponentStateException ex) {
                toast.setLocationRelativeTo(this);
            }
        } else {
            toast.setLocationRelativeTo(this);
        }

        toast.setVisible(true);

        Timer autoClose = new Timer(3400, null);
        autoClose.setRepeats(false);
        autoClose.addActionListener(e -> fadeOutAndDispose(toast));

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                autoClose.stop();
                fadeOutAndDispose(toast);
                openDownloadsPanel();
            }
        });

        autoClose.start();
    }

    private void fadeOutAndDispose(JWindow toast) {
        if (!toast.isDisplayable()) return;
        Timer fade = new Timer(30, null);
        float[] opacity = {1f};
        fade.addActionListener(e -> {
            opacity[0] -= 0.09f;
            if (opacity[0] <= 0f) {
                fade.stop();
                toast.dispose();
            } else {
                try {
                    toast.setOpacity(Math.max(0f, opacity[0]));
                } catch (Exception ex) {
                    fade.stop();
                    toast.dispose();
                }
            }
        });
        fade.start();
    }

    private JButton makeIconButton(String icon, String tooltip) {
        JButton btn = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? Theme.BORDER : Theme.CRATER;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(4, 7, getWidth() - 8, getHeight() - 14, 8, 8));
                g2.setColor(Theme.STARDUST);
                g2.setFont(Theme.FONT_ICON.deriveFont(16f));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(38, 44));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusable(false);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton makeWindowButton(String text, boolean isClose) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg;
                if (getModel().isRollover()) {
                    bg = isClose ? Theme.ALERT : Theme.BORDER;
                } else {
                    bg = Theme.CRATER;
                }
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor((isClose && getModel().isRollover()) ? Color.WHITE : Theme.STARDUST);
                g2.setFont(Theme.FONT_ICON.deriveFont(14f));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(46, 44));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void toggleMaximize() {
        if (isMaximized) restoreFromMaximize();
        else maximizeToScreenBounds();
    }

    private void maximizeToScreenBounds() {
        normalBounds = getBounds();
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null)
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();

        Rectangle screenBounds = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

        int x = screenBounds.x + screenInsets.left;
        int y = screenBounds.y + screenInsets.top;
        int w = screenBounds.width - screenInsets.left - screenInsets.right;
        int h = screenBounds.height - screenInsets.top - screenInsets.bottom;

        setBounds(x, y, w, h);
        isMaximized = true;
        maximizeBtn.setText("\u2750");
        LOG.info("[MainWindow] Maximized to " + x + "," + y + " " + w + "x" + h);
    }

    private void restoreFromMaximize() {
        if (normalBounds != null) setBounds(normalBounds);
        isMaximized = false;
        maximizeBtn.setText("\u25A1");
    }

    private static class TabHeaderPanel extends JPanel {
        JLabel titleLabel;
        boolean selected;
        boolean hovered;

        TabHeaderPanel() {
            super(new BorderLayout(4, 0));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = selected ? Theme.NEBULA : hovered ? Theme.CRATER : Theme.VOID;
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(1, 2, getWidth() - 2, getHeight() - 4, 6, 6));
            if (selected) {
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(1, 2, getWidth() - 3, getHeight() - 5, 6, 6));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class TabStrip {
        final JPanel root = new JPanel(new BorderLayout());
        final JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, TAB_GAP, 4));
        final JScrollPane headerScroll;
        final JPanel contentHost = new JPanel(new CardLayout());
        final Map<String, JPanel> headers = new LinkedHashMap<>();
        String selectedSessionId;

        TabStrip(Runnable onPlusClicked) {
            headerRow.setOpaque(false);

            headerScroll = new JScrollPane(headerRow,
                    JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            headerScroll.setOpaque(false);
            headerScroll.getViewport().setOpaque(false);
            headerScroll.setBorder(null);
            headerScroll.setPreferredSize(new Dimension(0, 42));

            contentHost.setOpaque(true);
            contentHost.setBackground(Theme.VOID);

            JLabel plusLbl = new JLabel("+") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getForeground());
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                    int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 - 2;
                    g2.drawString(getText(), tx, ty);
                    g2.dispose();
                }
            };
            plusLbl.setFont(Theme.FONT_ICON.deriveFont(Font.BOLD, 14f));
            plusLbl.setForeground(Theme.DIM);
            plusLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            plusLbl.setPreferredSize(new Dimension(33, 34));
            plusLbl.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onPlusClicked.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    plusLbl.setForeground(Theme.STARDUST);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    plusLbl.setForeground(Theme.DIM);
                }
            });
            headerRow.add(plusLbl);

            root.setOpaque(false);
            root.add(headerScroll, BorderLayout.NORTH);
            root.add(contentHost, BorderLayout.CENTER);
        }

        void select(String sessionId) {
            if (sessionId == null || !headers.containsKey(sessionId)) return;
            selectedSessionId = sessionId;
            for (Map.Entry<String, JPanel> entry : headers.entrySet()) {
                if (entry.getValue() instanceof TabHeaderPanel thp) {
                    thp.selected = entry.getKey().equals(sessionId);
                    thp.repaint();
                }
            }
            ((CardLayout) contentHost.getLayout()).show(contentHost, sessionId);
        }
    }

    private void buildProfileBar() {
        profileBar.removeAll();

        List<BrowserProfile> profiles = ProfileManager.getInstance().getAllProfiles();
        for (BrowserProfile profile : profiles) {
            profileBar.add(buildProfileButton(profile));
        }

        JButton addBtn = makeTextButton("+ Планета");
        addBtn.addActionListener(e -> showAddProfileDialog());
        profileBar.add(addBtn);

        if (!profiles.isEmpty()) {
            if (selectedProfileId == null) selectedProfileId = profiles.get(0).getId();
            markProfileSelected(selectedProfileId);
        }

        profileBar.revalidate();
        profileBar.repaint();

        installProfileDragSupport();
    }

    private void installProfileDragSupport() {
        for (Component c : profileBar.getComponents()) {
            if (!(c instanceof JPanel profileBtn)) continue;
            Object pid = profileBtn.getClientProperty("profileId");
            if (pid == null) continue;

            MouseAdapter dragAdapter = new MouseAdapter() {
                private Point pressPoint;
                private boolean dragging = false;

                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.getDeepestComponentAt(
                            profileBtn,
                            SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), profileBtn).x,
                            SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), profileBtn).y
                    ) instanceof JButton) return;
                    pressPoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), profileBar);
                    dragging = false;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (pressPoint == null) return;
                    Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), profileBar);
                    if (!dragging && Math.abs(p.x - pressPoint.x) > 6) dragging = true;
                    if (dragging) {
                        profileBtn.putClientProperty("dragging", true);
                        int insertIdx = getProfileInsertIndex(p.x);
                        int currentIdx = getProfileComponentIndex(profileBtn);
                        Component[] comps = profileBar.getComponents();
                        int addBtnIdx = comps.length - 1;
                        if (insertIdx > addBtnIdx) insertIdx = addBtnIdx;
                        if (insertIdx >= 0 && insertIdx != currentIdx && insertIdx != currentIdx + 1) {
                            profileBar.remove(profileBtn);
                            int target = insertIdx > currentIdx ? insertIdx - 1 : insertIdx;
                            if (target >= addBtnIdx) target = addBtnIdx - 1;
                            profileBar.add(profileBtn, target);
                            profileBar.revalidate();
                            profileBar.repaint();
                        } else {
                            profileBar.repaint();
                        }
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragging) {
                        profileBtn.putClientProperty("dragging", false);
                        profileBar.repaint();
                        saveProfileOrder();
                    } else {
                        Point pInBtn = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), profileBtn);
                        Component deepest = SwingUtilities.getDeepestComponentAt(profileBtn, pInBtn.x, pInBtn.y);
                        if (deepest instanceof JButton) {
                            dragging = false;
                            pressPoint = null;
                            return;
                        }
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            String profileId = (String) profileBtn.getClientProperty("profileId");
                            if (profileId != null && !profileId.equals(selectedProfileId)) {
                                selectedProfileId = profileId;
                                markProfileSelected(selectedProfileId);
                                switchToProfile(selectedProfileId);
                                saveLastActive();
                            }
                        }
                    }
                    dragging = false;
                    pressPoint = null;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (dragging) return;
                    Point pInBtn = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), profileBtn);
                    Component deepest = SwingUtilities.getDeepestComponentAt(profileBtn, pInBtn.x, pInBtn.y);
                    if (deepest instanceof JButton) return;
                    if (e.getClickCount() >= 2) {
                        String profileId = (String) profileBtn.getClientProperty("profileId");
                        if (profileId != null) {
                            BrowserProfile profile = ProfileManager.getInstance().getProfile(profileId);
                            if (profile != null) showRenameProfileDialog(profile);
                        }
                    }
                }

                private int getProfileComponentIndex(Component comp) {
                    Component[] comps = profileBar.getComponents();
                    for (int i = 0; i < comps.length; i++) if (comps[i] == comp) return i;
                    return -1;
                }

                private int getProfileInsertIndex(int x) {
                    Component[] comps = profileBar.getComponents();
                    for (int i = 0; i < comps.length; i++) {
                        Rectangle b = comps[i].getBounds();
                        if (x < b.x + b.width / 2) return i;
                    }
                    return comps.length;
                }
            };

            attachDragListenerRecursively(profileBtn, dragAdapter, profileBtn);
        }
    }

    private void attachDragListenerRecursively(Component c, MouseAdapter adapter, JPanel profileBtn) {
        if (c instanceof JButton) return;
        c.addMouseListener(adapter);
        c.addMouseMotionListener(adapter);
        if (c instanceof Container container)
            for (Component child : container.getComponents())
                attachDragListenerRecursively(child, adapter, profileBtn);
    }

    private void saveProfileOrder() {
        List<String> orderedIds = new ArrayList<>();
        for (Component c : profileBar.getComponents()) {
            if (c instanceof JPanel panel) {
                Object pid = panel.getClientProperty("profileId");
                if (pid instanceof String id) orderedIds.add(id);
            }
        }
        if (!orderedIds.isEmpty()) ProfileManager.getInstance().reorderProfiles(orderedIds);
    }

    private JPanel buildProfileButton(BrowserProfile profile) {
        JPanel btn = new JPanel(new BorderLayout(4, 0)) {
            boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        Point p = e.getPoint();
                        SwingUtilities.convertPointToScreen(p, (Component) e.getSource());
                        try {
                            Point loc = getLocationOnScreen();
                            if (!new Rectangle(loc.x, loc.y, getWidth(), getHeight()).contains(p)) {
                                hovered = false;
                                repaint();
                            }
                        } catch (IllegalComponentStateException ex) {
                            hovered = false;
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isDragging = Boolean.TRUE.equals(getClientProperty("dragging"));
                Color bg = isDragging ? DRAG_HIGHLIGHT
                        : profile.getId().equals(selectedProfileId) ? Theme.ACTIVE_BG
                        : hovered ? Theme.CRATER : Theme.NEBULA;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                if (isDragging) {
                    g2.setColor(new Color(0xC0, 0x40, 0x80));
                    g2.setStroke(new java.awt.BasicStroke(1.5f));
                    g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                } else if (profile.getId().equals(selectedProfileId)) {
                    g2.setColor(Theme.COMET);
                    g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                }
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setBorder(new EmptyBorder(4, 10, 4, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color dotColor;
        try {
            dotColor = Color.decode(profile.getAvatarColor());
        } catch (Exception ex) {
            dotColor = Theme.COMET;
        }

        JPanel dot = makeDot(dotColor);
        JLabel nameLbl = new JLabel(profile.getName());
        nameLbl.setFont(Theme.FONT_REGULAR);
        nameLbl.setForeground(Theme.STARDUST);
        nameLbl.setToolTipText("Двойной клик — переименовать");
        nameLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton deleteBtn = new JButton(ICON_CLOSE_TAB) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.DELETE_HOVER : Theme.DIM);
                g2.setFont(Theme.FONT_ICON_SMALL);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        deleteBtn.setPreferredSize(new Dimension(18, 18));
        deleteBtn.setBorderPainted(false);
        deleteBtn.setContentAreaFilled(false);
        deleteBtn.setFocusPainted(false);
        deleteBtn.setFocusable(false);
        deleteBtn.setVisible(false);
        deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        deleteBtn.addActionListener(e -> deleteProfile(profile));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(dot);
        left.add(nameLbl);

        btn.add(left, BorderLayout.CENTER);
        btn.add(deleteBtn, BorderLayout.EAST);

        MouseAdapter hoverListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                deleteBtn.setVisible(true);
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point p = e.getPoint();
                SwingUtilities.convertPointToScreen(p, (Component) e.getSource());
                try {
                    Point loc = btn.getLocationOnScreen();
                    if (!new Rectangle(loc.x, loc.y, btn.getWidth(), btn.getHeight()).contains(p)) {
                        deleteBtn.setVisible(false);
                        btn.repaint();
                    }
                } catch (IllegalComponentStateException ex) {
                    deleteBtn.setVisible(false);
                }
            }
        };
        btn.addMouseListener(hoverListener);
        left.addMouseListener(hoverListener);
        dot.addMouseListener(hoverListener);
        nameLbl.addMouseListener(hoverListener);
        deleteBtn.addMouseListener(hoverListener);

        btn.putClientProperty("profileId", profile.getId());
        return btn;
    }

    private JPanel makeDot(Color color) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fill(new Ellipse2D.Float(0, 2, 10, 10));
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(10, 14);
            }
        };
    }

    private JButton makeTextButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.CRATER : Theme.NEBULA);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(Theme.DIM);
                g2.setFont(Theme.FONT_REGULAR);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setFocusable(false);
        btn.setFont(Theme.FONT_REGULAR);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void markProfileSelected(String profileId) {
        for (Component c : profileBar.getComponents()) if (c instanceof JPanel p) p.repaint();
    }

    private void ensureProfileTabs(String profileId) {
        if (profileTabs.containsKey(profileId)) return;
        BrowserProfile profile = ProfileManager.getInstance().getProfile(profileId);
        if (profile == null) return;

        TabStrip ts = new TabStrip(() -> openNewTabForProfile(profileId));
        profileTabs.put(profileId, ts);
        profileCards.add(ts.root, profileId);

        String lastSession = SettingsManager.getInstance().getLastSessionId();

        List<TabSession> sessions = SessionPersistenceService.getInstance().getActiveSessions();
        boolean any = false;
        for (TabSession session : sessions) {
            if (!profileId.equals(session.getProfileId())) continue;
            openTabForSession(ts, session, profile);
            any = true;
        }

        if (!any) {
            openNewTab(profileId, ts);
        } else if (lastSession != null && ts.headers.containsKey(lastSession)) {
            selectTab(ts, lastSession);
        }
    }

    private void openNewTabForProfile(String profileId) {
        TabStrip ts = profileTabs.get(profileId);
        if (ts != null) openNewTab(profileId, ts);
    }

    private void saveLastActive() {
        if (selectedProfileId == null) return;
        TabStrip ts = profileTabs.get(selectedProfileId);
        String sessionId = ts != null ? ts.selectedSessionId : null;
        SettingsManager.getInstance().saveLastActive(selectedProfileId, sessionId);
    }

    private String profileIdOf(TabStrip ts) {
        for (Map.Entry<String, TabStrip> e : profileTabs.entrySet())
            if (e.getValue() == ts) return e.getKey();
        return selectedProfileId;
    }

    private void selectTab(TabStrip ts, String sessionId) {
        ts.select(sessionId);
        BrowserPanel bp = tabPanels.get(sessionId);
        if (bp != null) {
            bp.forceRefreshWebView();
            Timer kick = new Timer(120, ev -> bp.forceRefreshWebView());
            kick.setRepeats(false);
            kick.start();
        }
    }

    private void switchToProfile(String profileId) {
        ensureProfileTabs(profileId);
        cardLayout.show(profileCards, profileId);
        profileCards.revalidate();
        profileCards.repaint();

        TabStrip ts = profileTabs.get(profileId);
        if (ts != null && ts.selectedSessionId != null) {
            BrowserPanel bp = tabPanels.get(ts.selectedSessionId);
            if (bp != null) {
                Timer kick = new Timer(120, ev -> bp.forceRefreshWebView());
                kick.setRepeats(false);
                kick.start();
            }
        }
        LOG.info("[MainWindow] Switched to profile=" + profileId);
    }

    private void deleteProfile(BrowserProfile profile) {
        List<BrowserProfile> all = ProfileManager.getInstance().getAllProfiles();
        if (all.size() <= 1) {
            setStatus("Нельзя удалить последнюю планету.");
            return;
        }

        for (java.util.Iterator<Map.Entry<String, BrowserPanel>> it = tabPanels.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, BrowserPanel> entry = it.next();
            BrowserPanel bp = entry.getValue();
            if (profile.getId().equals(bp.getSession().getProfileId())) {
                bp.dispose();
                SessionPersistenceService.getInstance().closeSession(bp.getSession().getId());
                it.remove();
            }
        }

        TabStrip ts = profileTabs.remove(profile.getId());
        if (ts != null) profileCards.remove(ts.root);

        ProfileManager.getInstance().deleteProfile(profile.getId());
        List<BrowserProfile> remaining = ProfileManager.getInstance().getAllProfiles();
        selectedProfileId = remaining.get(0).getId();
        buildProfileBar();
        switchToProfile(selectedProfileId);
        setStatus("Планета «" + profile.getName() + "» удалена.");
        LOG.info("[MainWindow] Deleted profile=" + profile.getId());
    }

    private void openNewTab(String profileId, TabStrip ts) {
        BrowserProfile profile = ProfileManager.getInstance().getProfile(profileId);
        if (profile == null) return;
        TabSession session = SessionPersistenceService.getInstance()
                .createSession(profile.getId(), BrowserPanel.HOME_URL, "Новая вкладка");
        openTabForSession(ts, session, profile);
    }

    private void openTabForSession(TabStrip ts, TabSession session, BrowserProfile profile) {
        BrowserPanel panel = new BrowserPanel(session, profile);

        panel.setOnOpenNewTab(url -> {
            if (url == null || url.isBlank()) return;
            BrowserProfile p = ProfileManager.getInstance().getProfile(profileIdOf(ts));
            if (p == null) return;
            TabSession newSession = SessionPersistenceService.getInstance()
                    .createSession(p.getId(), url, "Новая вкладка");
            openTabForSession(ts, newSession, p);
            LOG.info("[MainWindow] Popup redirected to new tab: " + url);
        });

        JPanel header = createHeader(ts, session, profile);
        panel.setOnTitleChange(t -> SwingUtilities.invokeLater(() -> updateHeaderTitle(header, t)));

        ts.headerRow.add(header, ts.headerRow.getComponentCount() - 1);
        ts.contentHost.add(panel, session.getId());
        ts.headers.put(session.getId(), header);
        ts.headerRow.revalidate();
        ts.headerRow.repaint();

        tabPanels.put(session.getId(), panel);
        selectTab(ts, session.getId());
        LOG.info("[MainWindow] Opened tab session=" + session.getId() + " profile=" + profile.getId());
        saveLastActive();
    }

    private JPanel createHeader(TabStrip ts, TabSession session, BrowserProfile profile) {
        Color dotColor;
        try {
            dotColor = Color.decode(profile.getAvatarColor());
        } catch (Exception ex) {
            dotColor = Theme.COMET;
        }

        TabHeaderPanel header = new TabHeaderPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, MAX_TAB_TEXT_WIDTH), 34);
            }
        };
        header.putClientProperty("sessionId", session.getId());
        header.setBorder(new EmptyBorder(2, 8, 2, 4));

        JPanel dot = makeDot(dotColor);

        JLabel titleLbl = new JLabel();
        titleLbl.setFont(Theme.FONT_SMALL);
        titleLbl.setForeground(Theme.STARDUST);
        header.titleLabel = titleLbl;

        int reservedWidth = dot.getPreferredSize().width + 16 + 8;
        int availableWidth = MAX_TAB_TEXT_WIDTH - reservedWidth;
        titleLbl.setText(elideText(titleLbl, session.getTitle(), availableWidth));
        titleLbl.setToolTipText(session.getTitle());

        JButton closeBtn = new JButton(ICON_CLOSE_TAB) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.DELETE_HOVER : Theme.DIM);
                g2.setFont(Theme.FONT_ICON_SMALL);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        closeBtn.setPreferredSize(new Dimension(16, 16));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setFocusable(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> closeTabAt(ts, session.getId()));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(dot);
        left.add(titleLbl);

        header.add(left, BorderLayout.CENTER);
        header.add(closeBtn, BorderLayout.EAST);

        MouseAdapter dragSelect = new MouseAdapter() {
            private Point pressPoint;
            private boolean dragging;
            private int dragOffsetX;
            private List<Component> order;
            private int trackMinX;

            @Override
            public void mousePressed(MouseEvent e) {
                pressPoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), ts.headerRow);
                dragOffsetX = pressPoint.x - header.getX();
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressPoint == null) return;
                Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), ts.headerRow);

                if (!dragging && Math.abs(p.x - pressPoint.x) > 6) {
                    dragging = true;
                    order = new ArrayList<>();
                    int count = ts.headerRow.getComponentCount() - 1;
                    for (int i = 0; i < count; i++) {
                        order.add(ts.headerRow.getComponent(i));
                    }
                    trackMinX = order.isEmpty() ? header.getX() : order.get(0).getX();
                }

                if (dragging && order != null && !order.isEmpty()) {
                    int totalWidth = 0;
                    for (Component c : order) totalWidth += c.getWidth() + TAB_GAP;
                    totalWidth -= TAB_GAP;

                    int maxX = trackMinX + Math.max(0, totalWidth - header.getWidth());
                    int newX = Math.max(trackMinX, Math.min(maxX, p.x - dragOffsetX));
                    header.setBounds(newX, header.getY(), header.getWidth(), header.getHeight());

                    List<Component> others = new ArrayList<>(order);
                    others.remove(header);

                    int[] starts = new int[others.size()];
                    int x = trackMinX;
                    for (int i = 0; i < others.size(); i++) {
                        starts[i] = x;
                        x += others.get(i).getWidth() + TAB_GAP;
                    }

                    double pointerX = p.x;
                    int targetIndexInOthers = others.size();
                    for (int i = 0; i < others.size(); i++) {
                        int mid = starts[i] + others.get(i).getWidth() / 2;
                        if (pointerX < mid) {
                            targetIndexInOthers = i;
                            break;
                        }
                    }

                    List<Component> newOrder = new ArrayList<>(others);
                    newOrder.add(targetIndexInOthers, header);

                    if (!newOrder.equals(order)) {
                        order = newOrder;

                        int xx = trackMinX;
                        for (Component c : order) {
                            if (c != header) {
                                Rectangle to = new Rectangle(xx, header.getY(), c.getWidth(), c.getHeight());
                                if (!c.getBounds().equals(to)) {
                                    animateSlide(c, c.getBounds(), to);
                                }
                            }
                            xx += c.getWidth() + TAB_GAP;
                        }
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging) {
                    if (order != null && !order.isEmpty()) {
                        int xx = trackMinX;
                        Rectangle target = header.getBounds();
                        for (Component c : order) {
                            if (c == header) {
                                target = new Rectangle(xx, header.getY(), header.getWidth(), header.getHeight());
                            }
                            xx += c.getWidth() + TAB_GAP;
                        }
                        Rectangle dragPos = header.getBounds();
                        animateSlide(header, dragPos, target);

                        Component plus = ts.headerRow.getComponent(ts.headerRow.getComponentCount() - 1);
                        ts.headerRow.removeAll();
                        for (Component c : order) ts.headerRow.add(c);
                        ts.headerRow.add(plus);
                    }
                    saveTabOrder(ts);
                    ts.headerRow.revalidate();
                    ts.headerRow.repaint();
                } else {
                    Point pInHeader = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), header);
                    Component deepest = SwingUtilities.getDeepestComponentAt(header, pInHeader.x, pInHeader.y);
                    if (deepest != closeBtn && SwingUtilities.isLeftMouseButton(e)) {
                        selectTab(ts, session.getId());
                        saveLastActive();
                    }
                }
                pressPoint = null;
                dragging = false;
                order = null;
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                header.hovered = true;
                header.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point p = e.getPoint();
                SwingUtilities.convertPointToScreen(p, (Component) e.getSource());
                try {
                    Point loc = header.getLocationOnScreen();
                    if (!new Rectangle(loc.x, loc.y, header.getWidth(), header.getHeight()).contains(p)) {
                        header.hovered = false;
                        header.repaint();
                    }
                } catch (IllegalComponentStateException ex) {
                    header.hovered = false;
                    header.repaint();
                }
            }
        };

        attachHeaderListenerRecursively(header, dragSelect, closeBtn);
        attachSparkle(header);
        return header;
    }

    private void attachSparkle(TabHeaderPanel header) {
        Timer[] timer = {null};
        int[] frame = {0};

        MouseAdapter sparkleAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (timer[0] != null && timer[0].isRunning()) return;
                timer[0] = new Timer(40, ev -> {
                    frame[0]++;
                    Point o = SwingUtilities.convertPoint(header, 0, 0, sparkleGlassPane);
                    sparkleGlassPane.setSparkleData(new Rectangle(o.x, o.y, header.getWidth(), header.getHeight()), frame[0]);
                });
                timer[0].start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point p = e.getPoint();
                Component src = (Component) e.getSource();
                Point onHeader = SwingUtilities.convertPoint(src, p, header);
                if (onHeader.x < 0 || onHeader.y < 0
                        || onHeader.x >= header.getWidth() || onHeader.y >= header.getHeight()) {
                    if (timer[0] != null) timer[0].stop();
                    sparkleGlassPane.setSparkleData(null, 0);
                }
            }
        };

        attachSparkleRecursively(header, sparkleAdapter);
    }

    private void attachSparkleRecursively(Component c, MouseAdapter adapter) {
        c.addMouseListener(adapter);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachSparkleRecursively(child, adapter);
            }
        }
    }

    private void attachHeaderListenerRecursively(Component c, MouseAdapter listener, Component excluded) {
        if (c == excluded) return;
        c.addMouseListener(listener);
        c.addMouseMotionListener(listener);
        if (c instanceof Container container)
            for (Component child : container.getComponents())
                attachHeaderListenerRecursively(child, listener, excluded);
    }

    private void animateSlide(Component c, Rectangle from, Rectangle to) {
        if (c instanceof JComponent jc) {
            Object existing = jc.getClientProperty("cometSlideTimer");
            if (existing instanceof Timer existingTimer) {
                existingTimer.stop();
            }
        }

        long start = System.currentTimeMillis();
        Timer t = new Timer(15, null);
        t.addActionListener(e -> {
            float p = Math.min(1f, (System.currentTimeMillis() - start) / 140f);
            int x = (int) (from.x + (to.x - from.x) * p);
            c.setBounds(x, to.y, to.width, to.height);
            if (p >= 1f) {
                t.stop();
                if (c instanceof JComponent jc2) {
                    jc2.putClientProperty("cometSlideTimer", null);
                }
            }
        });
        if (c instanceof JComponent jc) {
            jc.putClientProperty("cometSlideTimer", t);
        }
        t.start();
    }

    private void updateHeaderTitle(Component headerComp, String title) {
        if (!(headerComp instanceof TabHeaderPanel header)) return;
        int reservedWidth = 10 + 16 + 8;
        int availableWidth = MAX_TAB_TEXT_WIDTH - reservedWidth;
        header.titleLabel.setText(elideText(header.titleLabel, title, availableWidth));
        header.titleLabel.setToolTipText(title);
    }

    private String elideText(JLabel label, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = label.getFontMetrics(label.getFont());
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        int width = ellipsisWidth;
        int i = 0;
        while (i < text.length()) {
            int cw = fm.charWidth(text.charAt(i));
            if (width + cw > maxWidth) break;
            width += cw;
            i++;
        }
        return text.substring(0, Math.max(0, i)) + ellipsis;
    }

    private void saveTabOrder(TabStrip ts) {
        int count = ts.headerRow.getComponentCount() - 1;
        for (int i = 0; i < count; i++) {
            Component c = ts.headerRow.getComponent(i);
            Object sid = ((JComponent) c).getClientProperty("sessionId");
            if (sid instanceof String sessionId) {
                BrowserPanel bp = tabPanels.get(sessionId);
                if (bp != null) {
                    bp.getSession().setTabOrder(i);
                    SessionPersistenceService.getInstance().updateSession(bp.getSession());
                }
            }
        }
    }

    private void closeTabAt(TabStrip ts, String sessionId) {
        JPanel header = ts.headers.remove(sessionId);
        if (header != null) ts.headerRow.remove(header);

        BrowserPanel panel = tabPanels.remove(sessionId);
        if (panel != null) {
            ts.contentHost.remove(panel);
            panel.dispose();
        }
        SessionPersistenceService.getInstance().closeSession(sessionId);

        if (ts.headers.isEmpty()) {
            openNewTab(profileIdOf(ts), ts);
        } else if (sessionId.equals(ts.selectedSessionId)) {
            String next = ts.headers.keySet().iterator().next();
            selectTab(ts, next);
            saveLastActive();
        }

        saveTabOrder(ts);
        ts.headerRow.revalidate();
        ts.headerRow.repaint();
        setStatus("Вкладка закрыта.");
        LOG.info("[MainWindow] Closed tab session=" + sessionId);
    }

    private void showAddProfileDialog() {
        JDialog dialog = createStyledDialog("Новая планета");
        JTextField field = buildStyledField("");

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 24, 8, 24));

        JLabel lbl = new JLabel("Название планеты");
        lbl.setForeground(Theme.DIM);
        lbl.setFont(Theme.FONT_SMALL);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(lbl);
        content.add(Box.createVerticalStrut(6));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        content.add(field);

        JButton addBtn = buildDialogButton("Создать", true);
        JButton cancelBtn = buildDialogButton("Отмена", false);

        addBtn.addActionListener(e -> {
            String name = field.getText().trim();
            if (!name.isBlank()) {
                BrowserProfile profile = ProfileManager.getInstance().createProfile(name);
                buildProfileBar();
                selectedProfileId = profile.getId();
                markProfileSelected(selectedProfileId);
                switchToProfile(selectedProfileId);
                LOG.info("[MainWindow] Created profile=" + profile.getId() + " name=" + name);
            }
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        field.addActionListener(e -> addBtn.doClick());

        showStyledDialog(dialog, content, new JButton[]{addBtn, cancelBtn});
    }

    private void showRenameProfileDialog(BrowserProfile profile) {
        JDialog dialog = createStyledDialog("Переименовать планету");
        JTextField field = buildStyledField(profile.getName());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 24, 8, 24));

        JLabel lbl = new JLabel("Новое название");
        lbl.setForeground(Theme.DIM);
        lbl.setFont(Theme.FONT_SMALL);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(lbl);
        content.add(Box.createVerticalStrut(6));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        content.add(field);

        JButton saveBtn = buildDialogButton("Сохранить", true);
        JButton cancelBtn = buildDialogButton("Отмена", false);

        saveBtn.addActionListener(e -> {
            String name = field.getText().trim();
            if (!name.isBlank() && !name.equals(profile.getName())) {
                ProfileManager.getInstance().renameProfile(profile.getId(), name);
                buildProfileBar();
                setStatus("Планета переименована в «" + name + "».");
            }
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        field.addActionListener(e -> saveBtn.doClick());

        showStyledDialog(dialog, content, new JButton[]{saveBtn, cancelBtn});
    }

    private JDialog createStyledDialog(String title) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        return dialog;
    }

    private JTextField buildStyledField(String initialText) {
        JTextField field = new JTextField(initialText) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hasFocus() ? Theme.NEBULA : Theme.CRATER);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(hasFocus() ? Theme.COMET : Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setFont(Theme.FONT_REGULAR);
        field.setForeground(Theme.STARDUST);
        field.setCaretColor(Theme.COMET);
        field.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        field.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setSelectionStart(0);
                field.setSelectionEnd(0);
                field.repaint();
            }
        });
        return field;
    }

    private JButton buildDialogButton(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = primary
                        ? (getModel().isRollover() ? Theme.TAIL : Theme.COMET)
                        : (getModel().isRollover() ? Theme.CRATER : Theme.NEBULA);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                if (!primary) {
                    g2.setColor(Theme.BORDER);
                    g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                }
                g2.setColor(primary ? Color.WHITE : Theme.STARDUST);
                g2.setFont(Theme.FONT_REGULAR);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(primary ? 110 : 90, 36));
        btn.setFont(Theme.FONT_REGULAR);
        return btn;
    }

    private void showStyledDialog(JDialog dialog, JPanel content, JButton[] buttons) {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.NEBULA);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));
                g2.dispose();
            }
        };
        root.setOpaque(false);

        JLabel titleLbl = new JLabel(dialog.getTitle());
        titleLbl.setFont(Theme.FONT_BOLD.deriveFont(14f));
        titleLbl.setForeground(Theme.STARDUST);
        titleLbl.setBorder(new EmptyBorder(16, 24, 0, 24));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnRow.setOpaque(false);
        btnRow.setBorder(new EmptyBorder(0, 24, 8, 24));
        for (JButton b : buttons) btnRow.add(b);

        root.add(titleLbl, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(btnRow, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(320, dialog.getHeight()));

        Point ownerLoc = getLocation();
        Dimension ownerSize = getSize();
        Dimension dlgSize = dialog.getSize();
        dialog.setLocation(
                ownerLoc.x + (ownerSize.width - dlgSize.width) / 2,
                ownerLoc.y + (ownerSize.height - dlgSize.height) / 2);

        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        SwingUtilities.invokeLater(() -> {
            Component first = findFirstTextField(root);
            if (first != null) {
                first.requestFocusInWindow();
                if (first instanceof JTextField f) f.selectAll();
            }
        });

        dialog.setVisible(true);
    }

    private Component findFirstTextField(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTextField) return c;
            if (c instanceof Container sub) {
                Component f = findFirstTextField(sub);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
        statusClearTimer.restart();
    }

    private void onExit() {
        LOG.info("[MainWindow] Exiting...");

        SettingsManager.getInstance().saveWindowBounds(
                isMaximized ? normalBounds : getBounds(), isMaximized);
        saveLastActive();

        for (Map.Entry<String, TabStrip> entry : profileTabs.entrySet())
            saveTabOrder(entry.getValue());
        for (BrowserPanel p : tabPanels.values()) p.dispose();

        try {
            zvezdo4et.cometbrowser.native_.WebView2Thread.getInstance().submit(() -> null);
        } catch (Exception ignored) {
        }

        SessionPersistenceService.getInstance().saveAll();
        ProfileManager.getInstance().saveAll();
        SettingsManager.getInstance().save();

        zvezdo4et.cometbrowser.util.AppLogger.shutdown();
        dispose();
        System.exit(0);
    }

    private static class SparkleGlassPane extends JPanel {
        private Rectangle targetBounds;
        private int frame = 0;

        SparkleGlassPane() {
            setLayout(null);
        }

        void setSparkleData(Rectangle bounds, int frame) {
            this.targetBounds = bounds;
            this.frame = frame;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (targetBounds == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = targetBounds.x, y = targetBounds.y,
                    w = targetBounds.width, h = targetBounds.height;

            for (int i = 0; i < 6; i++) {
                java.util.Random r = new java.util.Random(i * 7919L);
                double baseAngle = r.nextDouble() * Math.PI * 2;
                double speed = 0.05 + r.nextDouble() * 0.05;
                double angle = baseAngle + frame * speed;
                double cx = x + w / 2.0 + Math.cos(angle) * (w / 2.0 + 10);
                double cy = y + h / 2.0 + Math.sin(angle) * (h / 2.0 + 10);

                double pulse = (Math.sin(frame * 0.18 + i) + 1) / 2.0;
                double outerRadius = 3 + pulse * 4;
                int alpha = 90 + (int) (pulse * 165);

                g2.setColor(new Color(
                        Theme.TAIL.getRed(), Theme.TAIL.getGreen(), Theme.TAIL.getBlue(),
                        Math.min(255, Math.max(0, alpha))));
                drawStar(g2, cx, cy, outerRadius, angle);
            }
            g2.dispose();
        }

        private void drawStar(Graphics2D g2, double cx, double cy, double outerRadius, double angle) {
            double innerRadius = outerRadius * 0.45;
            java.awt.geom.Path2D.Double star = new java.awt.geom.Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double radius = (i % 2 == 0) ? outerRadius : innerRadius;
                double a = angle + (Math.PI * i) / 5 - Math.PI / 2;
                double px = cx + Math.cos(a) * radius;
                double py = cy + Math.sin(a) * radius;
                if (i == 0) star.moveTo(px, py);
                else star.lineTo(px, py);
            }
            star.closePath();
            g2.fill(star);
        }
    }
}