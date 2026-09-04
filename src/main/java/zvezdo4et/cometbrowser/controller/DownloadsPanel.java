package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.service.DownloadManager;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class DownloadsPanel extends JDialog {

    private JPanel listContainer;
    private final Runnable refreshListener = this::refreshList;
    private final Component anchor;

    public DownloadsPanel(Frame owner, Component anchor) {
        super(owner, "Загрузки", false);
        this.anchor = anchor;
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        build();
        pack();

        Dimension minSize = new Dimension(520, 320);
        Dimension prefSize = getSize();
        setSize(Math.max(prefSize.width, minSize.width), Math.max(prefSize.height, minSize.height));
        setMinimumSize(minSize);

        positionNearAnchor();

        DownloadManager.getInstance().addListener(() -> SwingUtilities.invokeLater(refreshListener));
    }

    private void positionNearAnchor() {
        if (anchor != null && anchor.isShowing()) {
            try {
                Point loc = anchor.getLocationOnScreen();
                int x = loc.x + anchor.getWidth() - getWidth();
                int y = loc.y + anchor.getHeight() + 6;

                GraphicsConfiguration gc = anchor.getGraphicsConfiguration();
                Rectangle screen = gc != null
                        ? gc.getBounds()
                        : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                        .getDefaultConfiguration().getBounds();

                if (x + getWidth() > screen.x + screen.width) x = screen.x + screen.width - getWidth() - 8;
                if (x < screen.x) x = screen.x + 8;
                if (y + getHeight() > screen.y + screen.height) y = loc.y - getHeight() - 6;
                if (y < screen.y) y = screen.y + 8;

                setLocation(x, y);
                return;
            } catch (IllegalComponentStateException ignored) {
            }
        }
        setLocationRelativeTo(getOwner());
    }

    @Override
    public void dispose() {
        DownloadManager.getInstance().removeListener(refreshListener);
        super.dispose();
    }

    private void build() {
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

        JLabel title = new JLabel("Загрузки");
        title.setFont(Theme.FONT_BOLD.deriveFont(14f));
        title.setForeground(Theme.STARDUST);
        title.setBorder(new EmptyBorder(16, 24, 8, 24));
        root.add(title, BorderLayout.NORTH);

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);
        listContainer.setBorder(new EmptyBorder(8, 16, 8, 16));
        refreshList();

        JScrollPane scroll = new JScrollPane(listContainer,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        root.add(scroll, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnRow.setOpaque(false);
        JButton openFolder = makeBtn("Открыть папку");
        openFolder.addActionListener(e -> DownloadManager.getInstance().openDownloadsFolder());
        JButton close = makeBtn("Закрыть");
        close.addActionListener(e -> dispose());
        btnRow.add(openFolder);
        btnRow.add(close);
        root.add(btnRow, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setContentPane(root);
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.removeAll();

        var downloads = DownloadManager.getInstance().getDownloads();
        if (downloads.isEmpty()) {
            JLabel empty = new JLabel("Нет загрузок");
            empty.setForeground(Theme.DIM);
            empty.setFont(Theme.FONT_REGULAR);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            listContainer.add(empty);
        } else {
            for (var entry : downloads) {
                listContainer.add(buildRow(entry));
                listContainer.add(Box.createVerticalStrut(6));
            }
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel buildRow(DownloadManager.DownloadEntry entry) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 8, 6, 8));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.setAlignmentX(LEFT_ALIGNMENT);

        JLabel name = new JLabel(entry.filename);
        name.setFont(Theme.FONT_REGULAR);
        name.setForeground(Theme.STARDUST);

        int percent = entry.getPercent();
        String status;
        if (entry.failed) {
            status = "Ошибка: " + entry.error;
        } else if (entry.done) {
            status = "Готово";
        } else if (percent >= 0) {
            status = percent + "% \u2014 " + formatSize(entry.bytesReceived) + " из " + formatSize(entry.totalBytes);
        } else {
            status = "Загрузка… " + formatSize(entry.bytesReceived);
        }

        JLabel statusLbl = new JLabel(status);
        statusLbl.setFont(Theme.FONT_SMALL);
        statusLbl.setForeground(entry.failed ? Theme.ALERT : Theme.DIM);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.add(name);
        info.add(statusLbl);

        top.add(info, BorderLayout.CENTER);

        if (entry.done && !entry.failed) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actions.setOpaque(false);

            JButton open = makeBtn("Открыть");
            open.addActionListener(e -> DownloadManager.getInstance().openFile(entry));

            JButton openWith = makeBtn("Открыть с помощью");
            openWith.addActionListener(e -> DownloadManager.getInstance().openFileWith(entry));

            actions.add(open);
            actions.add(openWith);
            top.add(actions, BorderLayout.EAST);
        }

        row.add(top);

        if (!entry.done && !entry.failed) {
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setAlignmentX(LEFT_ALIGNMENT);
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
            bar.setPreferredSize(new Dimension(0, 4));
            bar.setBorderPainted(false);
            bar.setForeground(Theme.COMET);
            bar.setBackground(Theme.CRATER);
            if (percent >= 0) {
                bar.setIndeterminate(false);
                bar.setValue(percent);
            } else {
                bar.setIndeterminate(true);
            }
            row.add(Box.createVerticalStrut(4));
            row.add(bar);
        }

        return row;
    }

    private String formatSize(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format("%.1f КБ", bytes / 1024.0);
        return String.format("%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    private JButton makeBtn(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.CRATER : Theme.NEBULA);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(Theme.BORDER);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.setColor(Theme.STARDUST);
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
        btn.setFont(Theme.FONT_REGULAR);
        btn.setPreferredSize(new Dimension(140, 30));
        return btn;
    }
}