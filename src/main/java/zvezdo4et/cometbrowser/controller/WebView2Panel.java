package zvezdo4et.cometbrowser.controller;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import zvezdo4et.cometbrowser.native_.CometWebView2Library;
import zvezdo4et.cometbrowser.native_.WebView2Thread;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class WebView2Panel extends Canvas {

    private static final Logger LOG = Logger.getLogger(WebView2Panel.class.getName());

    private final CometWebView2Library lib = CometWebView2Library.INSTANCE;

    private volatile Pointer handle;

    private boolean createDispatched = false;

    private int lastResizeW = -1;
    private int lastResizeH = -1;

    private boolean resizePending = false;

    private final String userDataFolder;
    private String initialUrl;

    private Consumer<String> onTitleChanged;
    private Consumer<String> onUrlChanged;
    private Consumer<Boolean> onLoadingChanged;

    private CometWebView2Library.TitleChangedCallback titleCb;
    private CometWebView2Library.UrlChangedCallback urlCb;
    private CometWebView2Library.LoadingChangedCallback loadingCb;

    private final Timer resizeDebounce;

    public WebView2Panel(String userDataFolder, String initialUrl) {
        this.userDataFolder = userDataFolder;
        this.initialUrl = initialUrl;
        setBackground(java.awt.Color.BLACK);
        setMinimumSize(new Dimension(1, 1));

        resizeDebounce = new Timer(80, e -> doResizeFromEdt());
        resizeDebounce.setRepeats(false);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeDebounce.restart();
            }
        });

        addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
            @Override
            public void ancestorResized(HierarchyEvent e) {
                resizeDebounce.restart();
            }
        });
    }

    public void setOnTitleChanged(Consumer<String> cb) {
        this.onTitleChanged = cb;
    }

    public void setOnUrlChanged(Consumer<String> cb) {
        this.onUrlChanged = cb;
    }

    public void setOnLoadingChanged(Consumer<Boolean> cb) {
        this.onLoadingChanged = cb;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::onAttached);
    }

    @Override
    public void removeNotify() {
        resizeDebounce.stop();
        resizePending = false;

        Pointer h = handle;
        if (h != null) {
            WebView2Thread.getInstance().submitAsync(() -> lib.CometWV2_SetVisible(h, false));
        }
        super.removeNotify();
    }

    private void onAttached() {
        if (createDispatched) {
            Pointer h = handle;
            if (h != null) {
                lastResizeW = -1;
                lastResizeH = -1;
                doResizeFromEdt();
            } else {
                Timer t = new Timer(100, ev -> onAttached());
                t.setRepeats(false);
                t.start();
            }
            return;
        }

        long hwndLong = Native.getComponentID(this);
        if (hwndLong == 0) {
            Timer t = new Timer(50, ev -> onAttached());
            t.setRepeats(false);
            t.start();
            return;
        }

        createDispatched = true;

        HWND hwnd = new HWND(Pointer.createConstant(hwndLong));

        titleCb = (title, userData) -> {
            String s = title == null ? "" : title.toString();
            if (onTitleChanged != null) SwingUtilities.invokeLater(() -> onTitleChanged.accept(s));
        };
        urlCb = (url, userData) -> {
            String s = url == null ? "" : url.toString();
            if (onUrlChanged != null) SwingUtilities.invokeLater(() -> onUrlChanged.accept(s));
        };
        loadingCb = (isLoading, userData) -> {
            if (onLoadingChanged != null) SwingUtilities.invokeLater(() -> onLoadingChanged.accept(isLoading));
        };

        WString udf = new WString(userDataFolder);
        WString url = initialUrl != null ? new WString(initialUrl) : null;

        final long capturedHwnd = hwndLong;

        WebView2Thread.getInstance().submitAsync(() -> {
            Pointer h = lib.CometWV2_Create(hwnd, udf, url, titleCb, urlCb, loadingCb, Pointer.NULL);
            handle = h;
            if (h == null) {
                LOG.severe("CometWV2_Create returned NULL for " + userDataFolder);
                return;
            }
            LOG.info("CometWV2_Create OK handle=" + h + " hwnd=0x" + Long.toHexString(capturedHwnd));
            RECT rc = new RECT();
            if (User32.INSTANCE.GetClientRect(new HWND(Pointer.createConstant(capturedHwnd)), rc)) {
                int w = rc.right - rc.left;
                int ht = rc.bottom - rc.top;
                if (w > 0 && ht > 0) {
                    lib.CometWV2_Resize(h, 0, 0, w, ht);
                    lib.CometWV2_SetVisible(h, true);
                    return;
                }
            }
            SwingUtilities.invokeLater(() -> {
                Timer t = new Timer(150, ev -> doResizeFromEdt());
                t.setRepeats(false);
                t.start();
            });
        });
    }

    private void doResizeFromEdt() {
        if (!isDisplayable()) return;

        Pointer h = handle;
        if (h == null) return;

        long hwndLong = Native.getComponentID(this);
        if (hwndLong == 0) return;

        RECT rc = new RECT();
        if (!User32.INSTANCE.GetClientRect(new HWND(Pointer.createConstant(hwndLong)), rc)) return;

        int w = rc.right - rc.left;
        int ht = rc.bottom - rc.top;
        if (w <= 0 || ht <= 0) return;

        if (w == lastResizeW && ht == lastResizeH) return;

        if (resizePending) return;

        lastResizeW = w;
        lastResizeH = ht;
        resizePending = true;

        final int fw = w, fht = ht;
        WebView2Thread.getInstance().submitAsync(() -> {
            lib.CometWV2_Resize(h, 0, 0, fw, fht);
            lib.CometWV2_SetVisible(h, true);
            SwingUtilities.invokeLater(() -> {
                resizePending = false;
                if (!isDisplayable()) return;
                long hwnd2 = Native.getComponentID(this);
                if (hwnd2 != 0) {
                    RECT rc2 = new RECT();
                    if (User32.INSTANCE.GetClientRect(new HWND(Pointer.createConstant(hwnd2)), rc2)) {
                        int w2 = rc2.right - rc2.left;
                        int h2 = rc2.bottom - rc2.top;
                        if (w2 > 0 && h2 > 0 && (w2 != fw || h2 != fht)) {
                            doResizeFromEdt();
                        }
                    }
                }
            });
        });
    }

    public void navigate(String url) {
        if (url == null) return;
        Pointer h = handle;
        if (h == null) {
            this.initialUrl = url;
            return;
        }
        WString wurl = new WString(url);
        WebView2Thread.getInstance().submitAsync(() -> lib.CometWV2_Navigate(h, wurl));
    }

    public void goBack() {
        Pointer h = handle;
        if (h == null) return;
        WebView2Thread.getInstance().submitAsync(() -> lib.CometWV2_GoBack(h));
    }

    public void goForward() {
        Pointer h = handle;
        if (h == null) return;
        WebView2Thread.getInstance().submitAsync(() -> lib.CometWV2_GoForward(h));
    }

    public boolean canGoBack() {
        Pointer h = handle;
        if (h == null) return false;
        return WebView2Thread.getInstance().submit(() -> lib.CometWV2_CanGoBack(h));
    }

    public boolean canGoForward() {
        Pointer h = handle;
        if (h == null) return false;
        return WebView2Thread.getInstance().submit(() -> lib.CometWV2_CanGoForward(h));
    }

    public void reload() {
        Pointer h = handle;
        if (h == null) return;
        WebView2Thread.getInstance().submitAsync(() -> lib.CometWV2_Reload(h));
    }

    public void stop() {
        Pointer h = handle;
        if (h == null) return;
        WebView2Thread.getInstance().submitAsync(() -> lib.CometWV2_Stop(h));
    }

    public boolean isReady() {
        Pointer h = handle;
        if (h == null) return false;
        return WebView2Thread.getInstance().submit(() -> lib.CometWV2_IsReady(h));
    }

    public void dispose() {
        resizeDebounce.stop();
        Pointer h = handle;
        if (h == null) return;
        handle = null;
        WebView2Thread.getInstance().submitAsync(() -> lib.CometWV2_Destroy(h));
    }
}