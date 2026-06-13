package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.model.BrowserProfile;
import zvezdo4et.cometbrowser.model.TabSession;
import zvezdo4et.cometbrowser.service.BookmarkManager;
import zvezdo4et.cometbrowser.service.SessionPersistenceService;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class BrowserPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(BrowserPanel.class.getName());

    public static final String HOME_URL = "comet://home";

    private static final String ICON_BACK = "\u2190";  // ←
    private static final String ICON_FORWARD = "\u2192";  // →
    private static final String ICON_RELOAD = "\u21BB";  // ↻
    private static final String ICON_STOP = "\u2715";  // ✕
    private static final String ICON_HOME = "\u2302";  // ⌂

    private final TabSession session;
    private final BrowserProfile profile;

    private WebView2Panel webView;

    private JTextField addressBar;
    private JButton backBtn;
    private JButton forwardBtn;
    private JButton reloadBtn;
    private JButton starBtn;
    private JProgressBar progressBar;

    private JPanel contentArea;
    private HomePanel homePanel;

    private String title = "Новая вкладка";
    private Consumer<String> onTitleChange;
    private boolean disposed = false;

    private boolean webViewInitScheduled = false;

    public BrowserPanel(TabSession session, BrowserProfile profile) {
        this.session = session;
        this.profile = profile;
        setLayout(new BorderLayout());
        setBackground(Theme.VOID);
        buildToolbar();

        contentArea = new JPanel(new BorderLayout());
        contentArea.setBackground(Theme.VOID);
        add(contentArea, BorderLayout.CENTER);

        String startUrl = session.getUrl();
        if (startUrl == null || startUrl.isBlank() || startUrl.equals(HOME_URL)) {
            showHome();
        } else {
            updateStarState();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!webViewInitScheduled && webView == null) {
            String startUrl = session.getUrl();
            if (startUrl != null && !startUrl.isBlank() && !startUrl.equals(HOME_URL)) {
                webViewInitScheduled = true;
                SwingUtilities.invokeLater(() -> initWebView(startUrl));
            }
        }
    }

    private void buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.NEBULA);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        toolbar.setPreferredSize(new Dimension(0, 44));

        backBtn = makeNavBtn(ICON_BACK, "Назад");
        forwardBtn = makeNavBtn(ICON_FORWARD, "Вперёд");
        reloadBtn = makeNavBtn(ICON_RELOAD, "Обновить");
        JButton homeBtn = makeNavBtn(ICON_HOME, "Домой");

        backBtn.setEnabled(false);
        forwardBtn.setEnabled(false);

        backBtn.addActionListener(e -> {
            if (webView != null) webView.goBack();
        });
        forwardBtn.addActionListener(e -> {
            if (webView != null) webView.goForward();
        });
        reloadBtn.addActionListener(e -> {
            if (Boolean.TRUE.equals(reloadBtn.getClientProperty("stopping"))) {
                if (webView != null) webView.stop();
            } else {
                if (webView != null) webView.reload();
            }
        });
        homeBtn.addActionListener(e -> goHome());

        addressBar = new JTextField() {
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
        addressBar.setOpaque(false);
        addressBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 36));
        addressBar.setFont(Theme.FONT_REGULAR);
        addressBar.setForeground(Theme.STARDUST);
        addressBar.setCaretColor(Theme.STARDUST);
        addressBar.setPreferredSize(new Dimension(0, 32));

        String displayUrl = session.getUrl();
        addressBar.setText(HOME_URL.equals(displayUrl) ? "" : (displayUrl != null ? displayUrl : ""));

        addressBar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateTo(addressBar.getText().trim());
                }
            }
        });

        addressBar.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(addressBar::selectAll);
                addressBar.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                addressBar.repaint();
            }
        });

        JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navBtns.setOpaque(false);
        navBtns.add(backBtn);
        navBtns.add(forwardBtn);
        navBtns.add(reloadBtn);
        navBtns.add(homeBtn);

        JLayeredPane addressLayer = new JLayeredPane();
        addressLayer.setOpaque(false);
        addressLayer.setPreferredSize(new Dimension(0, 32));
        addressLayer.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = addressLayer.getWidth();
                int h = addressLayer.getHeight();
                addressBar.setBounds(0, 0, w, h);
                starBtn.setBounds(w - 30, (h - 24) / 2, 24, 24);
            }
        });

        starBtn = new JButton("\u2606") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean filled = Boolean.TRUE.equals(getClientProperty("bookmarked"));
                Color c = filled ? new Color(0xFB, 0xBF, 0x24)
                        : (getModel().isRollover() ? Theme.STARDUST : Theme.DIM);
                g2.setColor(c);
                g2.setFont(Theme.FONT_ICON.deriveFont(15f));
                String txt = filled ? "\u2605" : "\u2606";
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(txt)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(txt, tx, ty);
                g2.dispose();
            }
        };
        starBtn.setBorderPainted(false);
        starBtn.setContentAreaFilled(false);
        starBtn.setFocusPainted(false);
        starBtn.setOpaque(false);
        starBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        starBtn.setToolTipText("Добавить в закладки");
        starBtn.addActionListener(e -> toggleBookmark());

        addressLayer.add(addressBar, JLayeredPane.DEFAULT_LAYER);
        addressLayer.add(starBtn, JLayeredPane.PALETTE_LAYER);

        toolbar.add(navBtns, BorderLayout.WEST);
        toolbar.add(addressLayer, BorderLayout.CENTER);

        progressBar = new JProgressBar() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.VOID);
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (isIndeterminate() || getValue() > 0) {
                    g2.setColor(Theme.COMET);
                    int w = isIndeterminate() ? getWidth() / 3
                            : (int) (getWidth() * getValue() / 100.0);
                    g2.fillRect(0, 0, w, getHeight());
                }
                g2.dispose();
            }
        };
        progressBar.setPreferredSize(new Dimension(0, 2));
        progressBar.setBorderPainted(false);
        progressBar.setVisible(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(toolbar, BorderLayout.CENTER);
        top.add(progressBar, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);

        updateStarState();
    }

    private JButton makeNavBtn(String text, String tooltip) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() && isEnabled()) {
                    g2.setColor(Theme.BORDER);
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                }
                g2.setColor(isEnabled()
                        ? (getModel().isRollover() ? Theme.TAIL : Theme.STARDUST)
                        : Theme.DIM);
                g2.setFont(Theme.FONT_ICON.deriveFont(16f));
                FontMetrics fm = g2.getFontMetrics();
                String t = getText();
                int tx = (getWidth() - fm.stringWidth(t)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(t, tx, ty);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(32, 32));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(Theme.FONT_ICON.deriveFont(16f));
        btn.setToolTipText(tooltip);
        return btn;
    }

    private void setReloadStopping(boolean stopping) {
        reloadBtn.putClientProperty("stopping", stopping);
        reloadBtn.setText(stopping ? ICON_STOP : ICON_RELOAD);
        reloadBtn.setToolTipText(stopping ? "Остановить" : "Обновить");
        reloadBtn.repaint();
    }

    private void updateStarState() {
        if (starBtn == null) return;
        String url = session.getUrl();
        boolean homeOrEmpty = url == null || url.isBlank() || url.equals(HOME_URL);
        starBtn.setVisible(!homeOrEmpty);
        if (!homeOrEmpty) {
            boolean bookmarked = BookmarkManager.getInstance().isBookmarked(url);
            starBtn.putClientProperty("bookmarked", bookmarked);
            starBtn.setToolTipText(bookmarked ? "Удалить из закладок" : "Добавить в закладки");
            starBtn.repaint();
        }
    }

    private void toggleBookmark() {
        String url = session.getUrl();
        if (url == null || url.isBlank() || url.equals(HOME_URL)) return;

        String name = (title == null || title.isBlank() || title.equals("Новая вкладка")) ? null : title;
        BookmarkManager.getInstance().toggleBookmark(name, url);
        updateStarState();

        if (homePanel != null) {
            homePanel.refreshBookmarks();
        }
    }

    private void showHome() {
        session.setUrl(HOME_URL);
        addressBar.setText("");
        title = "Новая вкладка";
        if (onTitleChange != null) onTitleChange.accept(title);
        SessionPersistenceService.getInstance().updateSession(session);

        contentArea.removeAll();

        if (homePanel == null) {
            homePanel = new HomePanel(query -> navigateTo(query));
        } else {
            homePanel.refreshBookmarks();
        }
        contentArea.add(homePanel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();

        updateStarState();
    }

    private void goHome() {
        if (webView != null) {
            webView.stop();
        }
        showHome();
    }

    private void showWebView() {
        contentArea.removeAll();
        if (webView != null) {
            contentArea.add(webView, BorderLayout.CENTER);
        }
        contentArea.revalidate();
        contentArea.repaint();
    }

    private void initWebView(String url) {
        if (disposed) return;

        if (webView == null) {
            String userDataFolder = profile.getDataDir() + java.io.File.separator + "webview2";
            new java.io.File(userDataFolder).mkdirs();

            webView = new WebView2Panel(userDataFolder, url);
            webView.setPreferredSize(new Dimension(800, 600));

            webView.setOnTitleChanged(t -> {
                if (t == null || t.isBlank()) return;
                title = t;
                session.setTitle(t);
                SessionPersistenceService.getInstance().updateSession(session);
                if (onTitleChange != null) onTitleChange.accept(t);
            });

            webView.setOnUrlChanged(u -> {
                addressBar.setText(u);
                session.setUrl(u);
                SessionPersistenceService.getInstance().updateSession(session);
                updateNavButtons();
                updateStarState();
            });

            webView.setOnLoadingChanged(isLoading -> {
                progressBar.setIndeterminate(isLoading);
                progressBar.setVisible(isLoading);
                setReloadStopping(isLoading);
                if (!isLoading) updateNavButtons();
            });
        } else {
            webView.navigate(url);
        }

        showWebView();
        updateStarState();
        LOG.info("[BrowserPanel] WebView2 initialized for session=" + session.getId());
    }

    private void updateNavButtons() {
        if (webView == null) return;
        SwingUtilities.invokeLater(() -> {
            backBtn.setEnabled(webView.canGoBack());
            forwardBtn.setEnabled(webView.canGoForward());
            backBtn.repaint();
            forwardBtn.repaint();
        });
    }

    public void navigateTo(String input) {
        if (input == null || input.isBlank()) return;

        if (input.equals(HOME_URL)) {
            goHome();
            return;
        }

        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + input.replace(" ", "+");
        }

        addressBar.setText(url);
        webViewInitScheduled = true;

        if (webView != null) {
            showWebView();
            webView.navigate(url);
        } else {
            initWebView(url);
        }

        session.setUrl(url);
        SessionPersistenceService.getInstance().updateSession(session);
        updateStarState();
    }

    public void setOnTitleChange(Consumer<String> cb) {
        this.onTitleChange = cb;
    }

    public String getTitle() {
        return title;
    }

    public TabSession getSession() {
        return session;
    }

    public BrowserProfile getProfile() {
        return profile;
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (webView != null) {
            webView.dispose();
            webView = null;
        }
    }
}
