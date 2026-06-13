package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.model.Bookmark;
import zvezdo4et.cometbrowser.service.BookmarkManager;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
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

        bookmarksGrid = new JPanel(new WrapLayout(FlowLayout.CENTER, 12, 12));
        bookmarksGrid.setOpaque(false);

        JScrollPane scroll = new JScrollPane(bookmarksGrid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setAlignmentX(Component.CENTER_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(660, 260));
        scroll.setPreferredSize(new Dimension(660, 260));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUI(new CometScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.getVerticalScrollBar().setOpaque(false);

        content.add(scroll);

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
            public Dimension getPreferredSize() {
                return new Dimension(14, 14);
            }
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

        JButton editBtn = new JButton(iconChar("edit")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.TAIL : Theme.DIM);
                g2.setFont(Theme.FONT_ICON_SMALL);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        editBtn.setPreferredSize(new Dimension(16, 16));
        editBtn.setBorderPainted(false);
        editBtn.setContentAreaFilled(false);
        editBtn.setFocusPainted(false);
        editBtn.setVisible(false);
        editBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        editBtn.setToolTipText("Изменить");
        editBtn.addActionListener(e -> showEditBookmarkDialog(bookmark));

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
        removeBtn.setToolTipText("Удалить");
        removeBtn.addActionListener(e -> {
            BookmarkManager.getInstance().removeBookmark(bookmark.getId());
            refreshBookmarks();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        actions.add(editBtn);
        actions.add(removeBtn);

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(dotWrap, BorderLayout.WEST);
        topRow.add(actions, BorderLayout.EAST);

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
                onNavigate.accept(bookmark.getUrl());
            }
        });

        java.awt.event.MouseListener hoverListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                editBtn.setVisible(true);
                removeBtn.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point p = e.getPoint();
                SwingUtilities.convertPointToScreen(p, e.getComponent());
                Point loc = tile.getLocationOnScreen();
                Rectangle bounds = new Rectangle(loc.x, loc.y, tile.getWidth(), tile.getHeight());
                if (!bounds.contains(p)) {
                    editBtn.setVisible(false);
                    removeBtn.setVisible(false);
                }
            }
        };

        tile.addMouseListener(hoverListener);
        topRow.addMouseListener(hoverListener);
        actions.addMouseListener(hoverListener);
        editBtn.addMouseListener(hoverListener);
        removeBtn.addMouseListener(hoverListener);
        textCol.addMouseListener(hoverListener);
        dotWrap.addMouseListener(hoverListener);
        dot.addMouseListener(hoverListener);
        nameLbl.addMouseListener(hoverListener);
        urlLbl.addMouseListener(hoverListener);

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
            public void mouseEntered(MouseEvent e) {
                plus.setForeground(Theme.STARDUST);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                plus.setForeground(Theme.DIM);
            }
        });

        return tile;
    }

    private void showAddBookmarkDialog() {
        JTextField nameField = new JTextField(18);
        JTextField urlField = new JTextField(18);
        styleField(nameField);
        styleField(urlField);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.NEBULA);
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel nameLbl = new JLabel("Название");
        nameLbl.setForeground(Theme.DIM);
        nameLbl.setFont(Theme.FONT_SMALL);
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel urlLbl = new JLabel("Адрес");
        urlLbl.setForeground(Theme.DIM);
        urlLbl.setFont(Theme.FONT_SMALL);
        urlLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        urlField.setAlignmentX(Component.LEFT_ALIGNMENT);
        urlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        panel.add(nameLbl);
        panel.add(Box.createVerticalStrut(4));
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(14));
        panel.add(urlLbl);
        panel.add(Box.createVerticalStrut(4));
        panel.add(urlField);

        JPanel wrapper = new JPanel(new BorderLayout()) {
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
        wrapper.setOpaque(false);
        wrapper.add(panel, BorderLayout.CENTER);
        wrapper.setBorder(new EmptyBorder(1, 1, 1, 1));

        UIManager.put("OptionPane.background", Theme.VOID);
        UIManager.put("Panel.background", Theme.VOID);
        UIManager.put("OptionPane.messageForeground", Theme.STARDUST);
        UIManager.put("Button.background", Theme.CRATER);
        UIManager.put("Button.foreground", Theme.STARDUST);
        UIManager.put("Button.select", Theme.BORDER);
        UIManager.put("Button.focus", Theme.TRANSPARENT);

        JOptionPane optionPane = new JOptionPane(
                wrapper,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        optionPane.setOptions(new Object[]{"Добавить", "Отмена"});
        optionPane.setBackground(Theme.VOID);

        JDialog dialog = optionPane.createDialog(this, "Новая закладка");
        dialog.getContentPane().setBackground(Theme.VOID);
        styleDialogButtons(dialog.getContentPane());
        dialog.setVisible(true);

        Object value = optionPane.getValue();

        if ("Добавить".equals(value)) {
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

    private void showEditBookmarkDialog(Bookmark bookmark) {
        JTextField nameField = new JTextField(18);
        JTextField urlField = new JTextField(18);
        styleField(nameField);
        styleField(urlField);
        nameField.setText(bookmark.getName());
        urlField.setText(bookmark.getUrl());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.NEBULA);
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel nameLbl = new JLabel("Название");
        nameLbl.setForeground(Theme.DIM);
        nameLbl.setFont(Theme.FONT_SMALL);
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel urlLbl = new JLabel("Адрес");
        urlLbl.setForeground(Theme.DIM);
        urlLbl.setFont(Theme.FONT_SMALL);
        urlLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        urlField.setAlignmentX(Component.LEFT_ALIGNMENT);
        urlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        panel.add(nameLbl);
        panel.add(Box.createVerticalStrut(4));
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(14));
        panel.add(urlLbl);
        panel.add(Box.createVerticalStrut(4));
        panel.add(urlField);

        JPanel wrapper = new JPanel(new BorderLayout()) {
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
        wrapper.setOpaque(false);
        wrapper.add(panel, BorderLayout.CENTER);
        wrapper.setBorder(new EmptyBorder(1, 1, 1, 1));

        UIManager.put("OptionPane.background", Theme.VOID);
        UIManager.put("Panel.background", Theme.VOID);
        UIManager.put("OptionPane.messageForeground", Theme.STARDUST);
        UIManager.put("Button.background", Theme.CRATER);
        UIManager.put("Button.foreground", Theme.STARDUST);
        UIManager.put("Button.select", Theme.BORDER);
        UIManager.put("Button.focus", Theme.TRANSPARENT);

        JOptionPane optionPane = new JOptionPane(
                wrapper,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        optionPane.setOptions(new Object[]{"Сохранить", "Отмена"});
        optionPane.setBackground(Theme.VOID);

        JDialog dialog = optionPane.createDialog(this, "Изменить закладку");
        dialog.getContentPane().setBackground(Theme.VOID);
        styleDialogButtons(dialog.getContentPane());
        dialog.setVisible(true);

        Object value = optionPane.getValue();

        if ("Сохранить".equals(value)) {
            String name = nameField.getText().trim();
            String url = urlField.getText().trim();
            if (!name.isBlank() && !url.isBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                bookmark.setName(name);
                bookmark.setUrl(url);
                BookmarkManager.getInstance().saveAll();
                refreshBookmarks();
            }
        }
    }

    private void styleDialogButtons(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton btn) {
                btn.setBackground(Theme.CRATER);
                btn.setForeground(Theme.STARDUST);
                btn.setFont(Theme.FONT_REGULAR);
                btn.setFocusPainted(false);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER),
                        BorderFactory.createEmptyBorder(6, 18, 6, 18)));
                if ("Добавить".equals(btn.getText()) || "Сохранить".equals(btn.getText())) {
                    btn.setBackground(Theme.COMET);
                    btn.setForeground(Color.WHITE);
                    btn.setBorder(BorderFactory.createEmptyBorder(7, 19, 7, 19));
                }
            } else if (c instanceof Container nested) {
                nested.setBackground(Theme.VOID);
                styleDialogButtons(nested);
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
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
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
            case "plus":
                return "+";
            case "close":
                return "x";
            case "edit":
                return "✎";
            default:
                return "";
        }
    }

    private static class CometScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = Theme.BORDER;
            this.trackColor = Theme.TRANSPARENT;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !c.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = isThumbRollover() ? Theme.TAIL : Theme.BORDER;
            g2.setColor(color);
            g2.fill(new RoundRectangle2D.Float(
                    thumbBounds.x + 1, thumbBounds.y,
                    thumbBounds.width - 2, thumbBounds.height,
                    6, 6));
            g2.dispose();
        }
    }

    private static class WrapLayout extends FlowLayout {
        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = layoutSize(target, false);
            minimum.width -= (getHgap() + 1);
            return minimum;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                Container container = target;

                while (container.getSize().width == 0 && container.getParent() != null) {
                    container = container.getParent();
                }

                targetWidth = container.getSize().width;

                if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);

                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }

                        if (rowWidth != 0) {
                            rowWidth += hgap;
                        }

                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }

                addRow(dim, rowWidth, rowHeight);

                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;

                Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
                if (scrollPane != null && target.isValid()) {
                    dim.width -= (hgap + 1);
                }

                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);

            if (dim.height > 0) {
                dim.height += getVgap();
            }

            dim.height += rowHeight;
        }
    }
}