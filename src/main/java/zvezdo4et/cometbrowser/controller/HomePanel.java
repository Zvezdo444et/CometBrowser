package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.model.Bookmark;
import zvezdo4et.cometbrowser.service.BookmarkManager;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;

public class HomePanel extends JPanel {

    private final Consumer<String> onNavigate;
    private JPanel bookmarksGrid;
    private JTextField activeSearchField;

    public HomePanel(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
        setLayout(new BorderLayout());
        setBackground(Theme.VOID);
        setOpaque(true);

        JPanel outerCenter = new JPanel(new GridBagLayout());
        outerCenter.setOpaque(false);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel logo = new JLabel("COMET");
        logo.setFont(Theme.FONT_TITLE.deriveFont(36f));
        logo.setForeground(Theme.STARDUST);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(logo);
        content.add(Box.createVerticalStrut(24));

        activeSearchField = buildSearchField();
        activeSearchField.setMaximumSize(new Dimension(560, 44));
        activeSearchField.setPreferredSize(new Dimension(560, 44));
        activeSearchField.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(activeSearchField);
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
        scroll.setMaximumSize(new Dimension(760, 300));
        scroll.setPreferredSize(new Dimension(760, 300));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUI(new CometScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.getVerticalScrollBar().setOpaque(false);

        scroll.setCursor(Cursor.getDefaultCursor());
        scroll.getViewport().setCursor(Cursor.getDefaultCursor());

        MouseMotionAdapter viewportCursorAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursorForPoint(scroll, bookmarksGrid, e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                updateCursorForPoint(scroll, bookmarksGrid, e.getPoint());
            }
        };
        scroll.getViewport().addMouseMotionListener(viewportCursorAdapter);

        scroll.getViewport().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                scroll.getViewport().setCursor(Cursor.getDefaultCursor());
            }
        });

        content.add(scroll);

        refreshBookmarks();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        outerCenter.add(content, gbc);

        add(outerCenter, BorderLayout.CENTER);
    }

    private void updateCursorForPoint(JScrollPane scroll, JPanel grid, Point viewportPoint) {
        Point gridPoint = SwingUtilities.convertPoint(scroll.getViewport(), viewportPoint, grid);
        Component target = SwingUtilities.getDeepestComponentAt(grid, gridPoint.x, gridPoint.y);
        if (target != null) {
            scroll.getViewport().setCursor(target.getCursor());
        } else {
            scroll.getViewport().setCursor(Cursor.getDefaultCursor());
        }
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

            @Override
            public Cursor getCursor() {
                return Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
            }
        };
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        field.setFont(Theme.FONT_REGULAR.deriveFont(15f));
        field.setForeground(Theme.STARDUST);
        field.setCaretColor(Theme.COMET);
        field.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        field.setHorizontalAlignment(JTextField.CENTER);

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

        List<Bookmark> bookmarks = BookmarkManager.getInstance().getAllBookmarks();
        for (Bookmark bookmark : bookmarks) {
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
        tile.setPreferredSize(new Dimension(172, 84));
        tile.setMinimumSize(new Dimension(172, 84));
        tile.setMaximumSize(new Dimension(172, 84));
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
        dot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel dotWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dotWrap.setOpaque(false);
        dotWrap.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dotWrap.add(dot);

        JLabel nameLbl = new JLabel(bookmark.getName());
        nameLbl.setFont(Theme.FONT_REGULAR);
        nameLbl.setForeground(Theme.STARDUST);
        nameLbl.setMaximumSize(new Dimension(148, 20));
        nameLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel urlLbl = new JLabel(shortUrl(bookmark.getUrl()));
        urlLbl.setFont(Theme.FONT_SMALL);
        urlLbl.setForeground(Theme.DIM);
        urlLbl.setMaximumSize(new Dimension(148, 16));
        urlLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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
        actions.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        actions.add(editBtn);
        actions.add(removeBtn);

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        topRow.add(dotWrap, BorderLayout.WEST);
        topRow.add(actions, BorderLayout.EAST);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        textCol.add(Box.createVerticalStrut(4));
        textCol.add(nameLbl);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(urlLbl);

        tile.add(topRow, BorderLayout.NORTH);
        tile.add(textCol, BorderLayout.CENTER);

        MouseAdapter clickListener = new MouseAdapter() {
            private boolean dragging = false;
            private Point pressPoint;

            @Override
            public void mousePressed(MouseEvent e) {
                dragging = false;
                pressPoint = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressPoint != null) {
                    int dx = Math.abs(e.getPoint().x - pressPoint.x);
                    int dy = Math.abs(e.getPoint().y - pressPoint.y);
                    if (dx > 5 || dy > 5) dragging = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!dragging && SwingUtilities.isLeftMouseButton(e)) {
                    Component src = e.getComponent();
                    if (src == editBtn || src == removeBtn) return;
                    onNavigate.accept(bookmark.getUrl());
                }
                dragging = false;
            }
        };

        tile.addMouseListener(clickListener);
        tile.addMouseMotionListener(clickListener);

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
                try {
                    Point loc = tile.getLocationOnScreen();
                    Rectangle bounds = new Rectangle(loc.x, loc.y, tile.getWidth(), tile.getHeight());
                    if (!bounds.contains(p)) {
                        editBtn.setVisible(false);
                        removeBtn.setVisible(false);
                    }
                } catch (IllegalComponentStateException ex) {
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

        MouseAdapter textColClick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                clickListener.mousePressed(SwingUtilities.convertMouseEvent(e.getComponent(), e, tile));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Component src = e.getComponent();
                if (src == editBtn || src == removeBtn) return;
                clickListener.mouseReleased(SwingUtilities.convertMouseEvent(e.getComponent(), e, tile));
            }
        };
        textCol.addMouseListener(textColClick);
        nameLbl.addMouseListener(textColClick);
        urlLbl.addMouseListener(textColClick);
        dot.addMouseListener(textColClick);
        dotWrap.addMouseListener(textColClick);

        return tile;
    }

    private JPanel buildAddTile() {
        JPanel tile = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float[] dash = {4f, 4f};
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
                g2.dispose();
            }
        };
        tile.setOpaque(false);
        tile.setPreferredSize(new Dimension(172, 84));
        tile.setMinimumSize(new Dimension(172, 84));
        tile.setMaximumSize(new Dimension(172, 84));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel plus = new JLabel(iconChar("plus"), SwingConstants.CENTER);
        plus.setFont(Theme.FONT_ICON.deriveFont(20f));
        plus.setForeground(Theme.DIM);
        plus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.add(plus, BorderLayout.CENTER);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAddBookmarkDialog();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                plus.setForeground(Theme.STARDUST);
                tile.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                plus.setForeground(Theme.DIM);
                tile.repaint();
            }
        });

        plus.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAddBookmarkDialog();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                plus.setForeground(Theme.STARDUST);
                tile.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                plus.setForeground(Theme.DIM);
                tile.repaint();
            }
        });

        return tile;
    }

    private void showAddBookmarkDialog() {
        JDialog dialog = createStyledDialog("Новая закладка");

        JTextField nameField = buildStyledField("Название");
        JTextField urlField = buildStyledField("https://");

        JPanel content = buildDialogContent(
                new String[]{"Название", "Адрес"},
                new JTextField[]{nameField, urlField}
        );

        JButton addBtn = buildDialogButton("Добавить", true);
        JButton cancelBtn = buildDialogButton("Отмена", false);

        addBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String url = urlField.getText().trim();
            if (!name.isBlank() && !url.isBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                BookmarkManager.getInstance().addBookmark(name, url);
                refreshBookmarks();
            }
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        nameField.addActionListener(e -> urlField.requestFocusInWindow());
        urlField.addActionListener(e -> addBtn.doClick());

        showStyledDialog(dialog, content, new JButton[]{addBtn, cancelBtn});
    }

    private void showEditBookmarkDialog(Bookmark bookmark) {
        JDialog dialog = createStyledDialog("Изменить закладку");

        JTextField nameField = buildStyledField(bookmark.getName());
        JTextField urlField = buildStyledField(bookmark.getUrl());

        JPanel content = buildDialogContent(
                new String[]{"Название", "Адрес"},
                new JTextField[]{nameField, urlField}
        );

        JButton saveBtn = buildDialogButton("Сохранить", true);
        JButton cancelBtn = buildDialogButton("Отмена", false);

        saveBtn.addActionListener(e -> {
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
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        nameField.addActionListener(e -> urlField.requestFocusInWindow());
        urlField.addActionListener(e -> saveBtn.doClick());

        showStyledDialog(dialog, content, new JButton[]{saveBtn, cancelBtn});
    }

    private JDialog createStyledDialog(String title) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog;
        if (owner instanceof Frame) {
            dialog = new JDialog((Frame) owner, title, true);
        } else if (owner instanceof Dialog) {
            dialog = new JDialog((Dialog) owner, title, true);
        } else {
            dialog = new JDialog((Frame) null, title, true);
        }
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

            @Override
            public Cursor getCursor() {
                return Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
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

    private JPanel buildDialogContent(String[] labels, JTextField[] fields) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(20, 24, 8, 24));

        for (int i = 0; i < labels.length; i++) {
            if (i > 0) panel.add(Box.createVerticalStrut(14));
            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(Theme.DIM);
            lbl.setFont(Theme.FONT_SMALL);
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(lbl);
            panel.add(Box.createVerticalStrut(4));
            fields[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            fields[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            panel.add(fields[i]);
        }
        return panel;
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
        dialog.setMinimumSize(new Dimension(340, dialog.getHeight()));

        Window owner = dialog.getOwner();
        if (owner != null && owner.isVisible()) {
            Point ownerLoc = owner.getLocation();
            Dimension ownerSize = owner.getSize();
            Dimension dlgSize = dialog.getSize();
            dialog.setLocation(
                    ownerLoc.x + (ownerSize.width - dlgSize.width) / 2,
                    ownerLoc.y + (ownerSize.height - dlgSize.height) / 2
            );
        } else {
            dialog.setLocationRelativeTo(null);
        }

        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        SwingUtilities.invokeLater(() -> {
            if (dialog.getContentPane().getComponentCount() > 0) {
                findFirstField(dialog.getContentPane()).ifPresent(Component::requestFocusInWindow);
            }
        });

        dialog.setVisible(true);
    }

    private java.util.Optional<Component> findFirstField(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTextField f) return java.util.Optional.of(f);
            if (c instanceof Container sub) {
                java.util.Optional<Component> found = findFirstField(sub);
                if (found.isPresent()) return found;
            }
        }
        return java.util.Optional.empty();
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
            g2.setColor(isThumbRollover() ? Theme.TAIL : Theme.BORDER);
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
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

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
                        if (rowWidth != 0) rowWidth += hgap;
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
            if (dim.height > 0) dim.height += getVgap();
            dim.height += rowHeight;
        }
    }
}