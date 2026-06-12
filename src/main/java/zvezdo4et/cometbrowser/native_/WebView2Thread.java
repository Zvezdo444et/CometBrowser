package zvezdo4et.cometbrowser.native_;

import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser.MSG;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public final class WebView2Thread {

    private static final Logger LOG = Logger.getLogger(WebView2Thread.class.getName());

    private static final int PM_REMOVE = 0x0001;

    private static volatile WebView2Thread instance;

    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private volatile boolean running = true;

    private WebView2Thread() {
        thread = new Thread(this::run, "WebView2-STA");
        thread.setDaemon(true);
        thread.start();
    }

    public static WebView2Thread getInstance() {
        if (instance == null) {
            synchronized (WebView2Thread.class) {
                if (instance == null) instance = new WebView2Thread();
            }
        }
        return instance;
    }

    private void run() {
        com.sun.jna.platform.win32.WinNT.HRESULT hr =
                Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
        LOG.info("CoInitializeEx on WebView2-STA thread: hr=0x" + Integer.toHexString(hr.intValue()));

        try {
            CometWebView2Library.INSTANCE.getClass();
        } catch (Throwable t) {
            LOG.severe("Failed to load CometWebView2.dll: " + t);
            return;
        }

        MSG msg = new MSG();
        while (running) {
            Runnable task;
            while ((task = taskQueue.poll()) != null) {
                runSafely(task);
            }

            while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, PM_REMOVE)) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }

            try {
                task = taskQueue.poll(10, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (task != null) runSafely(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            LOG.severe("Error on WebView2 STA thread: " + t);
            t.printStackTrace();
        }
    }

    public <T> T submit(java.util.function.Supplier<T> task) {
        if (Thread.currentThread() == thread) {
            try {
                return task.get();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        taskQueue.add(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void submitAsync(Runnable task) {
        taskQueue.add(task);
    }
}