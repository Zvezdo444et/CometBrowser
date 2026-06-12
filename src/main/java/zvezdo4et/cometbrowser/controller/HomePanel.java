package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.model.Bookmark;
import zvezdo4et.cometbrowser.service.BookmarkManager;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

public class HomePanel extends JPanel {

    private final Consumer<String> onNavigate;
    private JPanel bookmarksGrid;

    public HomePanel(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
        setLayout(new GridBagLayout());
        setBackground(Theme.VOID);
        setOpaque(true);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel logo = new JLabel("Comet");
        logo.setFont(Theme.FONT_TITLE.deriveFont(36f));
        logo.setForeground(Theme.STARDUST);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(logo);
        content.add(Box.createVerticalStrut(24));

        JTextField searchField = buildSearchField();
        searchField.setMaximumSize(new Dimension(560, 44));
        searchField.setPreferredSize(new Dimension(560, 44));
        searchField.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(searchField);
        content.add(Box.createVerticalStrut(36));

        JLabel bmLabel = new JLabel("Закладки");
        bmLabel.setFont(Theme.FONT_BOLD);
        bmLabel.setForeground(Theme.DIM);
        bmLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(bmLabel);
        content.add(Box.createVerticalStrut(12));

        bookmarksGrid = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        bookmarksGrid.setOpaque(false);
        bookmarksGrid.setAlignmentX(Component.CENTER_ALIGNMENT);
        bookmarksGrid.setMaximumSize(new Dimension(640, 400));
        content.add(bookmarksGrid);

        refreshBookmarks();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(content, gbc);
    }

    private JTextField buildSearchField() {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hasFocus() ? Theme.NEBULA : Theme.CRATER);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(hasFocus() ? Theme.COMET : Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        field.setFont(Theme.FONT_REGULAR.deriveFont(15f));
        field.setForeground(Theme.STARDUST);
        field.setCaretColor(Theme.STARDUST);
        field.setHorizontalAlignment(JTextField.CENTER);

        field.addActionListener(e -> {
            String text = field.getText().trim();
            if (!text.isBlank()) {
                onNavigate.accept(text);
            }
        });

        return field;
    }

    public void refreshBookmarks() {
        bookmarksGrid.removeAll();

        for (Bookmark bookmark : BookmarkManager.getInstance().getAllBookmarks()) {
            bookmarksGrid.add(buildBookmarkTile(bookmark));
        }
        bookmarksGrid.add(buildAddTile());

        bookmarksGrid.revalidate();
        bookmarksGrid.repaint();
    }

    private JPanel buildBookmarkTile(Bookmark bookmark) {
        JPanel tile = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.NEBULA);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
                g2.dispose();
            }
        };
        tile.setOpaque(false);
        tile.setPreferredSize(new Dimension(140, 80));
        tile.setBorder(new EmptyBorder(10, 12, 10, 12));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color dotColor = colorForName(bookmark.getName());
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(dotColor);
                g2.fill(new Ellipse2D.Float(0, 0, 14, 14));
                g2.dispose();
            }
            @Override
            public Dimension getPreferredSize() { return new Dimension(14, 14); }
        };
        dot.setOpaque(false);

        JPanel dotWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dotWrap.setOpaque(false);
        dotWrap.add(dot);

        JLabel nameLbl = new JLabel(bookmark.getName());
        nameLbl.setFont(Theme.FONT_REGULAR);
        nameLbl.setForeground(Theme.STARDUST);

        JLabel urlLbl = new JLabel(shortUrl(bookmark.getUrl()));
        urlLbl.setFont(Theme.FONT_SMALL);
        urlLbl.setForeground(Theme.DIM);

        JButton removeBtn = new JButton(iconChar("close")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.ALERT : Theme.DIM);
                g2.setFont(Theme.FONT_ICON_SMALL);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        removeBtn.setPreferredSize(new Dimension(16, 16));
        removeBtn.setBorderPainted(false);
        removeBtn.setContentAreaFilled(false);
        removeBtn.setFocusPainted(false);
        removeBtn.setVisible(false);
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.addActionListener(e -> {
            BookmarkManager.getInstance().removeBookmark(bookmark.getId());
            refreshBookmarks();
        });

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(dotWrap, BorderLayout.WEST);
        topRow.add(removeBtn, BorderLayout.EAST);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.add(nameLbl);
        textCol.add(urlLbl);

        tile.add(topRow, BorderLayout.NORTH);
        tile.add(textCol, BorderLayout.SOUTH);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getSource() == tile) {
                    onNavigate.accept(bookmark.getUrl());
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) { removeBtn.setVisible(true); }
            @Override
            public void mouseExited(MouseEvent e)  { removeBtn.setVisible(false); }
        });

        return tile;
    }

    private JPanel buildAddTile() {
        JPanel tile = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CRATER);
                float[] dash = {4f, 4f};
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
                g2.dispose();
            }
        };
        tile.setOpaque(false);
        tile.setPreferredSize(new Dimension(140, 80));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel plus = new JLabel(iconChar("plus"), SwingConstants.CENTER);
        plus.setFont(Theme.FONT_ICON.deriveFont(20f));
        plus.setForeground(Theme.DIM);
        tile.add(plus, BorderLayout.CENTER);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAddBookmarkDialog();
            }
            @Override
            public void mouseEntered(MouseEvent e) { plus.setForeground(Theme.STARDUST); }
            @Override
            public void mouseExited(MouseEvent e)  { plus.setForeground(Theme.DIM); }
        });

        return tile;
    }

    private void showAddBookmarkDialog() {
        JTextField nameField = new JTextField(18);
        JTextField urlField = new JTextField(18);
        styleField(nameField);
        styleField(urlField);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.setBackground(Theme.NEBULA);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel nameLbl = new JLabel("Название:");
        nameLbl.setForeground(Theme.STARDUST);
        nameLbl.setFont(Theme.FONT_REGULAR);

        JLabel urlLbl = new JLabel("Адрес:");
        urlLbl.setForeground(Theme.STARDUST);
        urlLbl.setFont(Theme.FONT_REGULAR);

        panel.add(nameLbl);
        panel.add(nameField);
        panel.add(urlLbl);
        panel.add(urlField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Добавить закладку",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String url = urlField.getText().trim();
            if (!name.isBlank() && !url.isBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                BookmarkManager.getInstance().addBookmark(name, url);
                refreshBookmarks();
            }
        }
    }

    private void styleField(JTextField field) {
        field.setFont(Theme.FONT_REGULAR);
        field.setBackground(Theme.CRATER);
        field.setForeground(Theme.STARDUST);
        field.setCaretColor(Theme.STARDUST);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    private String shortUrl(String url) {
        String s = url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        if (s.length() > 22) s = s.substring(0, 22) + "…";
        return s;
    }

    private Color colorForName(String name) {
        String[] palette = {
                "#A78BFA", "#60A5FA", "#34D399", "#F472B6",
                "#FBBF24", "#F87171", "#38BDF8", "#818CF8"
        };
        int idx = Math.abs(name.hashCode()) % palette.length;
        return Color.decode(palette[idx]);
    }

    private String iconChar(String key) {
        switch (key) {
            case "plus": return "+";
            case "close": return "x";
            default: return "";
        }
    }
}