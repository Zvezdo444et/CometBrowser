package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.model.BrowserProfile;
import zvezdo4et.cometbrowser.model.TabSession;
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

    private final TabSession session;
    private final BrowserProfile profile;

    private WebView2Panel webView;

    private JTextField addressBar;
    private JButton backBtn;
    private JButton forwardBtn;
    private JButton reloadBtn;
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

        backBtn = makeNavBtn("<", "Назад");
        forwardBtn = makeNavBtn(">", "Вперёд");
        reloadBtn = makeNavBtn("R", "Обновить");
        JButton homeBtn = makeNavBtn("H", "Домой");

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
        addressBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
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

        toolbar.add(navBtns, BorderLayout.WEST);
        toolbar.add(addressBar, BorderLayout.CENTER);

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
                g2.setFont(Theme.FONT_ICON);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(32, 32));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(Theme.FONT_ICON);
        btn.setToolTipText(tooltip);
        return btn;
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
        }
        contentArea.add(homePanel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
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
            });

            webView.setOnLoadingChanged(isLoading -> {
                progressBar.setIndeterminate(isLoading);
                progressBar.setVisible(isLoading);
                reloadBtn.putClientProperty("stopping", isLoading);
                reloadBtn.repaint();
                if (!isLoading) updateNavButtons();
            });
        } else {
            webView.navigate(url);
        }

        showWebView();
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