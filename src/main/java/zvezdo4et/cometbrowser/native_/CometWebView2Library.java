package zvezdo4et.cometbrowser.native_;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;

public interface CometWebView2Library extends StdCallLibrary {

    CometWebView2Library INSTANCE = Native.load(
            NativeLibraryLoader.getLibraryPath(),
            CometWebView2Library.class);

    interface TitleChangedCallback extends Callback {
        void invoke(WString title, Pointer userData);
    }

    interface UrlChangedCallback extends Callback {
        void invoke(WString url, Pointer userData);
    }

    interface LoadingChangedCallback extends Callback {
        void invoke(boolean isLoading, Pointer userData);
    }

    Pointer CometWV2_Create(
            HWND parentHwnd,
            WString userDataFolder,
            WString initialUrl,
            TitleChangedCallback onTitleChanged,
            UrlChangedCallback onUrlChanged,
            LoadingChangedCallback onLoadingChanged,
            Pointer userData);

    void CometWV2_Navigate(Pointer wv, WString url);

    void CometWV2_Resize(Pointer wv, int x, int y, int w, int h);

    void CometWV2_SetVisible(Pointer wv, boolean visible);

    void CometWV2_GoBack(Pointer wv);

    void CometWV2_GoForward(Pointer wv);

    boolean CometWV2_CanGoBack(Pointer wv);

    boolean CometWV2_CanGoForward(Pointer wv);

    void CometWV2_Reload(Pointer wv);

    void CometWV2_Stop(Pointer wv);

    void CometWV2_Destroy(Pointer wv);

    boolean CometWV2_IsReady(Pointer wv);
}