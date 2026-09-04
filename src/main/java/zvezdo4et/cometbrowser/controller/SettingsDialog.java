package zvezdo4et.cometbrowser.controller;

import zvezdo4et.cometbrowser.service.SettingsManager;
import zvezdo4et.cometbrowser.util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.logging.Logger;

public class SettingsDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(SettingsDialog.class.getName());

    public SettingsDialog(Frame owner) {
        super(owner, "Настройки", true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        build();
        pack();
        setMinimumSize(new Dimension(460, getHeight()));
        setLocationRelativeTo(owner);
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

        JLabel title = new JLabel("Настройки");
        title.setFont(Theme.FONT_BOLD.deriveFont(14f));
        title.setForeground(Theme.STARDUST);
        title.setBorder(new EmptyBorder(16, 24, 0, 24));
        root.add(title, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 24, 8, 24));

        JLabel dlLabel = makeLabel("Папка загрузок");
        content.add(dlLabel);
        content.add(Box.createVerticalStrut(4));

        JPanel dlRow = new JPanel(new BorderLayout(8, 0));
        dlRow.setOpaque(false);
        dlRow.setAlignmentX(LEFT_ALIGNMENT);
        dlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JTextField dlField = makeField(SettingsManager.getInstance().getDownloadsFolder());
        JButton dlBrowse = makeButton("Обзор", false);
        dlBrowse.setPreferredSize(new Dimension(80, 36));
        dlBrowse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(dlField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                dlField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        dlRow.add(dlField, BorderLayout.CENTER);
        dlRow.add(dlBrowse, BorderLayout.EAST);
        content.add(dlRow);
        content.add(Box.createVerticalStrut(16));

        JLabel searchLabel = makeLabel("Поисковая система");
        content.add(searchLabel);
        content.add(Box.createVerticalStrut(4));

        String[] engines = {"google", "yandex", "duckduckgo", "bing"};
        JComboBox<String> engineBox = new JComboBox<>(engines);
        engineBox.setSelectedItem(SettingsManager.getInstance().getSearchEngine());
        engineBox.setFont(Theme.FONT_REGULAR);
        engineBox.setForeground(Theme.STARDUST);
        engineBox.setBackground(Theme.CRATER);
        engineBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        engineBox.setAlignmentX(LEFT_ALIGNMENT);
        content.add(engineBox);
        content.add(Box.createVerticalStrut(16));

        JLabel logsLabel = makeLabel("Логи сохраняются в:");
        content.add(logsLabel);
        content.add(Box.createVerticalStrut(4));

        String logsPath = zvezdo4et.cometbrowser.util.UserDataDirUtil.getAppDataDir()
                + File.separator + "logs";
        JLabel logsPath2 = new JLabel(logsPath);
        logsPath2.setFont(Theme.FONT_SMALL);
        logsPath2.setForeground(Theme.DIM);
        logsPath2.setAlignmentX(LEFT_ALIGNMENT);
        content.add(logsPath2);

        JButton openLogs = makeButton("Открыть папку логов", false);
        openLogs.setAlignmentX(LEFT_ALIGNMENT);
        openLogs.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(logsPath));
            } catch (Exception ex) {
                LOG.warning("Cannot open logs folder: " + ex.getMessage());
            }
        });
        content.add(Box.createVerticalStrut(6));
        content.add(openLogs);

        root.add(content, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnRow.setOpaque(false);
        btnRow.setBorder(new EmptyBorder(0, 24, 8, 24));

        JButton save = makeButton("Сохранить", true);
        JButton cancel = makeButton("Отмена", false);

        save.addActionListener(e -> {
            SettingsManager.getInstance().setDownloadsFolder(dlField.getText().trim());
            SettingsManager.getInstance().setSearchEngine((String) engineBox.getSelectedItem());
            LOG.info("[SettingsDialog] Settings saved by user");
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        btnRow.add(cancel);
        btnRow.add(save);
        root.add(btnRow, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(
                e2 -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setContentPane(root);
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.DIM);
        l.setFont(Theme.FONT_SMALL);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JTextField makeField(String text) {
        JTextField f = new JTextField(text) {
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
        f.setOpaque(false);
        f.setFont(Theme.FONT_REGULAR);
        f.setForeground(Theme.STARDUST);
        f.setCaretColor(Theme.COMET);
        f.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        f.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        f.setAlignmentX(LEFT_ALIGNMENT);
        return f;
    }

    private JButton makeButton(String text, boolean primary) {
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
}