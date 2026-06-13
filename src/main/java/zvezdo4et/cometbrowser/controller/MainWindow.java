package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.model.BrowserProfile;
import zvezdo4et.cometbrowser.model.TabSession;
import zvezdo4et.cometbrowser.service.ProfileManager;
import zvezdo4et.cometbrowser.service.SessionPersistenceService;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MainWindow extends JFrame {

    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());

    private static final String ICON_CLOSE_TAB = "\u2715"; // ✕

    private JPanel titleBar;
    private JPanel profileBar;
    private JPanel profileCards;
    private CardLayout cardLayout;
    private JLabel statusLabel;
    private Timer statusClearTimer;

    private String selectedProfileId;

    private Point dragOffset;
    private JButton maximizeBtn;

    private Rectangle normalBounds;
    private boolean isMaximized = false;

    private SparkleGlassPane sparkleGlassPane;

    private final Map<String, BrowserPanel> tabPanels = new HashMap<>();
    private final Map<String, ProfileTabs> profileTabs = new HashMap<>();

    public MainWindow() {
        super("CometBrowser");
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
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
        ensureProfileTabs(selectedProfileId);
        cardLayout.show(profileCards, selectedProfileId);
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
        titleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
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
    }

    private JPanel buildWindowControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);

        JButton minBtn = makeWindowButton("\u2212", false);   // −
        minBtn.addActionListener(e -> setExtendedState(JFrame.ICONIFIED));

        maximizeBtn = makeWindowButton("\u25A1", false);       // □
        maximizeBtn.addActionListener(e -> toggleMaximize());

        JButton closeBtn = makeWindowButton("\u2715", true);   // ✕
        closeBtn.addActionListener(e -> onExit());

        controls.add(minBtn);
        controls.add(maximizeBtn);
        controls.add(closeBtn);
        return controls;
    }

    private JButton makeWindowButton(String text, boolean isClose) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(isClose ? Theme.ALERT : Theme.CRATER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.setColor((isClose && getModel().isRollover()) ? Color.WHITE : Theme.STARDUST);
                g2.setFont(Theme.FONT_ICON.deriveFont(13f));
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
        btn.setFocusPainted(false);
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
        maximizeBtn.setText("\u2750");  // ❐
    }

    private void restoreFromMaximize() {
        if (normalBounds != null) setBounds(normalBounds);
        isMaximized = false;
        maximizeBtn.setText("\u25A1");  // □
    }

    private static class ProfileTabs {
        final JTabbedPane pane;
        final JPanel plusPanel;
        boolean ignoreChangeEvent = false;
        boolean openingTab = false;

        ProfileTabs(JTabbedPane pane, JPanel plusPanel) {
            this.pane = pane;
            this.plusPanel = plusPanel;
        }
    }

    private void addNewTabButton(ProfileTabs pt) {
        pt.pane.addTab("", pt.plusPanel);
        int plusIdx = pt.pane.indexOfComponent(pt.plusPanel);
        pt.pane.setTabComponentAt(plusIdx, makePlusTabHeader(pt));
    }

    private JLabel makePlusTabHeader(ProfileTabs pt) {
        JLabel lbl = new JLabel("+");
        lbl.setFont(Theme.FONT_ICON.deriveFont(Font.BOLD, 14f));
        lbl.setForeground(Theme.DIM);
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openNewTab(pt);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                lbl.setForeground(Theme.STARDUST);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                lbl.setForeground(Theme.DIM);
            }
        });
        return lbl;
    }

    private void buildProfileBar() {
        profileBar.removeAll();

        List<BrowserProfile> profiles = ProfileManager.getInstance().getAllProfiles();
        for (BrowserProfile profile : profiles) {
            profileBar.add(buildProfileButton(profile));
        }

        JButton addBtn = makeTextButton("+ Орбита");
        addBtn.addActionListener(e -> showAddProfileDialog());
        profileBar.add(addBtn);

        if (!profiles.isEmpty()) {
            if (selectedProfileId == null) selectedProfileId = profiles.get(0).getId();
            markProfileSelected(selectedProfileId);
        }

        profileBar.revalidate();
        profileBar.repaint();
    }

    private JPanel buildProfileButton(BrowserProfile profile) {
        JPanel btn = new JPanel(new BorderLayout(4, 0)) {
            boolean hovered = false;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = profile.getId().equals(selectedProfileId) ? Theme.ACTIVE_BG
                        : hovered ? Theme.CRATER : Theme.NEBULA;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                if (profile.getId().equals(selectedProfileId)) {
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
        deleteBtn.setVisible(false);
        deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        deleteBtn.addActionListener(e -> deleteProfile(profile));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(dot);
        left.add(nameLbl);

        btn.add(left, BorderLayout.CENTER);
        btn.add(deleteBtn, BorderLayout.EAST);

        nameLbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    showRenameProfileDialog(profile);
                    return;
                }
                if (!profile.getId().equals(selectedProfileId)) {
                    selectedProfileId = profile.getId();
                    markProfileSelected(selectedProfileId);
                    switchToProfile(selectedProfileId);
                }
            }
        });

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getSource() == deleteBtn) return;
                if (e.getClickCount() >= 2) {
                    showRenameProfileDialog(profile);
                    return;
                }
                if (!profile.getId().equals(selectedProfileId)) {
                    selectedProfileId = profile.getId();
                    markProfileSelected(selectedProfileId);
                    switchToProfile(selectedProfileId);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                deleteBtn.setVisible(true);
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point p = e.getPoint();
                SwingUtilities.convertPointToScreen(p, (Component) e.getSource());
                Point loc = btn.getLocationOnScreen();
                Rectangle bounds = new Rectangle(loc.x, loc.y, btn.getWidth(), btn.getHeight());
                if (!bounds.contains(p)) {
                    deleteBtn.setVisible(false);
                    btn.repaint();
                }
            }
        });

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
        btn.setFont(Theme.FONT_REGULAR);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void markProfileSelected(String profileId) {
        for (Component c : profileBar.getComponents()) {
            if (c instanceof JPanel p) p.repaint();
        }
    }

    private void ensureProfileTabs(String profileId) {
        if (profileTabs.containsKey(profileId)) return;

        BrowserProfile profile = ProfileManager.getInstance().getProfile(profileId);
        if (profile == null) return;

        JTabbedPane pane = new JTabbedPane() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.VOID);
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        pane.setBackground(Theme.VOID);
        pane.setForeground(Theme.STARDUST);
        pane.setFont(Theme.FONT_REGULAR);
        pane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        pane.setUI(new CometTabbedPaneUI());

        JPanel plusPanel = new JPanel();
        plusPanel.setBackground(Theme.VOID);

        ProfileTabs pt = new ProfileTabs(pane, plusPanel);

        pane.addChangeListener(e -> {
            if (pt.ignoreChangeEvent || pt.openingTab) return;
            int idx = pane.getSelectedIndex();
            if (idx < 0) return;
            if (pane.getComponentAt(idx) == pt.plusPanel) {
                pt.ignoreChangeEvent = true;
                pane.setSelectedIndex(Math.max(0, idx - 1));
                pt.ignoreChangeEvent = false;
            }
        });

        addNewTabButton(pt);
        profileTabs.put(profileId, pt);
        profileCards.add(pane, profileId);

        boolean any = false;
        for (TabSession session : SessionPersistenceService.getInstance().getActiveSessions()) {
            if (!profileId.equals(session.getProfileId())) continue;
            openTabForSession(pt, session, profile);
            any = true;
        }

        if (!any) openNewTab(pt);
    }

    private void switchToProfile(String profileId) {
        ensureProfileTabs(profileId);
        cardLayout.show(profileCards, profileId);
        profileCards.revalidate();
        profileCards.repaint();
    }

    private void deleteProfile(BrowserProfile profile) {
        List<BrowserProfile> all = ProfileManager.getInstance().getAllProfiles();
        if (all.size() <= 1) {
            setStatus("Нельзя удалить последнюю орбиту.");
            return;
        }

        for (java.util.Iterator<Map.Entry<String, BrowserPanel>> it = tabPanels.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, BrowserPanel> entry = it.next();
            BrowserPanel bp = entry.getValue();
            TabSession s = bp.getSession();
            if (profile.getId().equals(s.getProfileId())) {
                bp.dispose();
                SessionPersistenceService.getInstance().closeSession(s.getId());
                it.remove();
            }
        }

        ProfileTabs pt = profileTabs.remove(profile.getId());
        if (pt != null) profileCards.remove(pt.pane);

        ProfileManager.getInstance().deleteProfile(profile.getId());
        List<BrowserProfile> remaining = ProfileManager.getInstance().getAllProfiles();
        selectedProfileId = remaining.get(0).getId();
        buildProfileBar();
        switchToProfile(selectedProfileId);
        setStatus("Орбита «" + profile.getName() + "» удалена.");
    }

    private void openNewTab(ProfileTabs pt) {
        if (pt.openingTab) return;
        pt.openingTab = true;
        try {
            BrowserProfile profile = ProfileManager.getInstance().getProfile(selectedProfileIdFor(pt));
            if (profile == null) return;
            TabSession session = SessionPersistenceService.getInstance()
                    .createSession(profile.getId(), BrowserPanel.HOME_URL, "Новая вкладка");
            openTabForSession(pt, session, profile);
        } finally {
            pt.openingTab = false;
        }
    }

    private String selectedProfileIdFor(ProfileTabs pt) {
        for (Map.Entry<String, ProfileTabs> e : profileTabs.entrySet()) {
            if (e.getValue() == pt) return e.getKey();
        }
        return selectedProfileId;
    }

    private void openTabForSession(ProfileTabs pt, TabSession session, BrowserProfile profile) {
        BrowserPanel panel = new BrowserPanel(session, profile);
        panel.setOnTitleChange(t -> SwingUtilities.invokeLater(() -> {
            int idx = pt.pane.indexOfComponent(panel);
            if (idx >= 0) {
                pt.pane.setTabComponentAt(idx, buildTabHeader(pt, session, profile));
                pt.pane.revalidate();
            }
        }));

        int plusIdx = pt.pane.indexOfComponent(pt.plusPanel);

        pt.ignoreChangeEvent = true;
        pt.pane.insertTab("", null, panel, null, plusIdx);
        pt.pane.setTabComponentAt(plusIdx, buildTabHeader(pt, session, profile));
        pt.pane.setSelectedIndex(plusIdx);
        pt.ignoreChangeEvent = false;

        tabPanels.put(session.getId(), panel);
        LOG.info("[openTabForSession] opened session=" + session.getId() + " profile=" + session.getProfileId());
    }

    private JPanel buildTabHeader(ProfileTabs pt, TabSession session, BrowserProfile profile) {
        Color dotColor;
        try {
            dotColor = Color.decode(profile.getAvatarColor());
        } catch (Exception ex) {
            dotColor = Theme.COMET;
        }

        JPanel dot = makeDot(dotColor);

        JLabel titleLbl = new JLabel(session.getTitle());
        titleLbl.setFont(Theme.FONT_SMALL);
        titleLbl.setForeground(Theme.STARDUST);

        int reservedWidth = dot.getPreferredSize().width + 16 + 4;
        int availableTextWidth = MAX_TAB_TEXT_WIDTH - reservedWidth;
        titleLbl.setText(elideText(titleLbl, session.getTitle(), availableTextWidth));
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
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            BrowserPanel panel = tabPanels.get(session.getId());
            if (panel == null) return;
            int idx = pt.pane.indexOfComponent(panel);
            if (idx >= 0) closeTabAt(pt, idx, session.getId());
        });

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
        header.add(dot);
        header.add(titleLbl);
        header.add(closeBtn);

        MouseAdapter selectTabListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = pt.pane.indexOfTabComponent(header);
                if (idx >= 0) pt.pane.setSelectedIndex(idx);
            }
        };
        header.addMouseListener(selectTabListener);
        titleLbl.addMouseListener(selectTabListener);
        dot.addMouseListener(selectTabListener);

        if (pt.pane.getUI() instanceof CometTabbedPaneUI ui) {
            ui.attachHoverListener(header, () -> pt.pane.indexOfTabComponent(header));
        }

        return header;
    }

    private static final int MAX_TAB_TEXT_WIDTH = 200;

    private String elideText(JLabel label, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = label.getFontMetrics(label.getFont());
        if (fm.stringWidth(text) <= maxWidth) return text;

        String ellipsis = "…";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        int width = ellipsisWidth;
        int i = 0;
        while (i < text.length()) {
            int charWidth = fm.charWidth(text.charAt(i));
            if (width + charWidth > maxWidth) break;
            width += charWidth;
            i++;
        }
        return text.substring(0, Math.max(0, i)) + ellipsis;
    }

    private void closeTabAt(ProfileTabs pt, int idx, String sessionId) {
        BrowserPanel panel = tabPanels.remove(sessionId);
        if (panel != null) panel.dispose();

        SessionPersistenceService.getInstance().closeSession(sessionId);

        pt.ignoreChangeEvent = true;
        pt.pane.removeTabAt(idx);

        long remaining = pt.pane.getTabCount() - 1;
        if (remaining <= 0) {
            pt.ignoreChangeEvent = false;
            openNewTab(pt);
        } else {
            int selectIdx = Math.min(idx, pt.pane.getTabCount() - 2);
            if (selectIdx < 0) selectIdx = 0;
            pt.pane.setSelectedIndex(selectIdx);
            pt.ignoreChangeEvent = false;
        }

        setStatus("Вкладка закрыта.");
    }

    private void showAddProfileDialog() {
        JTextField field = new JTextField(20);
        field.setFont(Theme.FONT_REGULAR);
        field.setBackground(Theme.CRATER);
        field.setForeground(Theme.STARDUST);
        field.setCaretColor(Theme.STARDUST);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Theme.NEBULA);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel lbl = new JLabel("Название орбиты:");
        lbl.setFont(Theme.FONT_REGULAR);
        lbl.setForeground(Theme.STARDUST);
        panel.add(lbl, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel, "Новая орбита",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = field.getText().trim();
            if (!name.isBlank()) {
                BrowserProfile profile = ProfileManager.getInstance().createProfile(name);
                buildProfileBar();
                selectedProfileId = profile.getId();
                markProfileSelected(selectedProfileId);
                switchToProfile(selectedProfileId);
            }
        }
    }

    private void showRenameProfileDialog(BrowserProfile profile) {
        JTextField field = new JTextField(20);
        field.setFont(Theme.FONT_REGULAR);
        field.setBackground(Theme.CRATER);
        field.setForeground(Theme.STARDUST);
        field.setCaretColor(Theme.STARDUST);
        field.setText(profile.getName());
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Theme.NEBULA);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel lbl = new JLabel("Новое название орбиты:");
        lbl.setFont(Theme.FONT_REGULAR);
        lbl.setForeground(Theme.STARDUST);
        panel.add(lbl, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel, "Переименовать орбиту",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = field.getText().trim();
            if (!name.isBlank() && !name.equals(profile.getName())) {
                ProfileManager.getInstance().renameProfile(profile.getId(), name);
                buildProfileBar();
                setStatus("Орбита переименована в «" + name + "».");
            }
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
        statusClearTimer.restart();
    }

    private void onExit() {
        for (BrowserPanel p : tabPanels.values()) p.dispose();
        SessionPersistenceService.getInstance().saveAll();
        ProfileManager.getInstance().saveAll();
        dispose();
        System.exit(0);
    }


    private static class CometTabbedPaneUI extends javax.swing.plaf.basic.BasicTabbedPaneUI {

        private static final int MAX_TAB_WIDTH = 200;

        private int hoveredTabIndex = -1;
        private Timer sparkleTimer;
        private int sparkleFrame = 0;

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabPane.setBackground(Theme.VOID);
            tabPane.setForeground(Theme.STARDUST);
            highlight = Theme.VOID;
            lightHighlight = Theme.VOID;
            shadow = Theme.VOID;
            darkShadow = Theme.VOID;
            focus = Theme.VOID;
        }

        @Override
        protected void installListeners() {
            super.installListeners();

            tabPane.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(tabForCoordinate(tabPane, e.getX(), e.getY()));
                }
            });

            tabPane.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    updateHover(-1);
                }
            });
        }

        private long hoverVersion = 0;

        private void updateHover(int idx) {
            if (idx == -1) {
                if (hoveredTabIndex == -1) return;
                final long ver = hoverVersion;
                SwingUtilities.invokeLater(() -> {
                    if (hoverVersion != ver) return;
                    applyHover(-1);
                });
            } else {
                hoverVersion++;
                applyHover(idx);
            }
        }

        private void applyHover(int idx) {
            if (idx == hoveredTabIndex) return;
            hoveredTabIndex = idx;
            if (hoveredTabIndex >= 0) {
                startSparkleAnimation();
            } else {
                stopSparkleAnimation();
                if (tabPane != null) {
                    Window w = SwingUtilities.getWindowAncestor(tabPane);
                    if (w instanceof MainWindow mw && mw.sparkleGlassPane != null)
                        mw.sparkleGlassPane.setSparkleData(null, 0);
                }
            }
            tabPane.repaint();
        }

        void attachHoverListener(Component header, java.util.function.IntSupplier indexSupplier) {
            java.awt.event.MouseAdapter listener = new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    updateHover(indexSupplier.getAsInt());
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(indexSupplier.getAsInt());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    Point p = e.getPoint();
                    SwingUtilities.convertPointToScreen(p, e.getComponent());
                    Rectangle bounds = header.getBounds();
                    Point headerLoc = header.getLocationOnScreen();
                    Rectangle screen = new Rectangle(headerLoc.x, headerLoc.y, bounds.width, bounds.height);
                    if (!screen.contains(p)) updateHover(-1);
                }
            };
            attachRecursively(header, listener);
        }

        private void attachRecursively(Component c, java.awt.event.MouseAdapter listener) {
            c.addMouseListener(listener);
            c.addMouseMotionListener(listener);
            if (c instanceof Container container)
                for (Component child : container.getComponents())
                    attachRecursively(child, listener);
        }

        @Override
        protected void uninstallListeners() {
            stopSparkleAnimation();
            super.uninstallListeners();
        }

        private void startSparkleAnimation() {
            if (sparkleTimer != null && sparkleTimer.isRunning()) return;
            sparkleFrame = 0;
            sparkleTimer = new Timer(60, e -> {
                sparkleFrame++;
                updateGlassPaneSparkles();
                if (tabPane != null) tabPane.repaint();
            });
            sparkleTimer.start();
        }

        private void stopSparkleAnimation() {
            if (sparkleTimer != null) {
                sparkleTimer.stop();
                sparkleTimer = null;
            }
        }

        private void updateGlassPaneSparkles() {
            if (tabPane == null || hoveredTabIndex < 0) return;
            Window w = SwingUtilities.getWindowAncestor(tabPane);
            if (!(w instanceof MainWindow mw) || mw.sparkleGlassPane == null) return;

            Rectangle tabBounds = getTabBounds(tabPane, hoveredTabIndex);
            if (tabBounds == null) return;

            Point origin = SwingUtilities.convertPoint(tabPane, tabBounds.x, tabBounds.y, mw.sparkleGlassPane);
            mw.sparkleGlassPane.setSparkleData(
                    new Rectangle(origin.x, origin.y, tabBounds.width, tabBounds.height),
                    sparkleFrame);
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            return Math.min(super.calculateTabWidth(tabPlacement, tabIndex, metrics), MAX_TAB_WIDTH);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = isSelected ? Theme.NEBULA
                    : tabIndex == hoveredTabIndex ? Theme.CRATER
                    : Theme.VOID;
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(x + 1, y + 2, w - 2, h - 2, 6, 6));
            if (isSelected) {
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(x + 1, y + 2, w - 2, h - 2, 6, 6));
            }
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            g.setColor(Theme.BORDER);
            g.fillRect(tabPane.getX(),
                    tabPane.getY() + calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight),
                    tabPane.getWidth(), 1);
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                           int tabIndex, Rectangle iconRect, Rectangle textRect,
                                           boolean isSelected) {
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return 34;
        }
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