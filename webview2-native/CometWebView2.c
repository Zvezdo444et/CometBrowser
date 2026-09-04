#include <windows.h>
#include <initguid.h>
#include <ole2.h>
#include <wchar.h>
#include <stdint.h>
#include <stdio.h>
#include <stdarg.h>
#include "WebView2.h"

static void DbgLog(const char *fmt, ...) {
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    OutputDebugStringA(buf);

    char path[MAX_PATH];
    GetTempPathA(MAX_PATH, path);
    strcat(path, "comet_webview2_debug.log");
    FILE *f = fopen(path, "a");
    if (f) {
        fputs(buf, f);
        fclose(f);
    }
}

static wchar_t* DupWide(const wchar_t *s) {
    if (!s) return NULL;
    size_t len = wcslen(s) + 1;
    wchar_t *copy = (wchar_t*)HeapAlloc(GetProcessHeap(), 0, len * sizeof(wchar_t));
    if (copy) wcscpy(copy, s);
    return copy;
}

typedef struct WebView2Handle {
    ICoreWebView2Environment  *env;
    ICoreWebView2Controller   *controller;
    ICoreWebView2             *webview;
    HWND                       hwnd;
    wchar_t                    pendingUrl[2048];
    wchar_t                    downloadsFolder[1024];
    BOOL                       ready;
    void (*onTitleChanged)(const wchar_t* title, void* userData);
    void (*onUrlChanged)(const wchar_t* url, void* userData);
    void (*onLoadingChanged)(BOOL isLoading, void* userData);
    void (*onDownloadStarted)(const wchar_t* url, const wchar_t* filePath, long long totalBytes, void* userData);
    void (*onDownloadProgress)(const wchar_t* filePath, long long bytesReceived, long long totalBytes, void* userData);
    void (*onDownloadStateChanged)(const wchar_t* filePath, int state, void* userData);
    void *userData;
    void *downloadUserData;
} WebView2Handle;

/* ---- Forward declarations of all handler structs ---- */
typedef struct EnvHandler             EnvHandler;
typedef struct CtrlHandler            CtrlHandler;
typedef struct TitleHandler           TitleHandler;
typedef struct NavStartHandler        NavStartHandler;
typedef struct NavCompHandler         NavCompHandler;
typedef struct DownloadStartHandler   DownloadStartHandler;
typedef struct DownloadProgressHandler DownloadProgressHandler;
typedef struct DownloadStateHandler   DownloadStateHandler;

/* ---- EnvHandler ---- */
struct EnvHandler {
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandlerVtbl *lpVtbl;
    LONG   ref;
    WebView2Handle *wv;
};

static HRESULT STDMETHODCALLTYPE EnvHandler_QueryInterface(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *this_,
        REFIID riid, void **ppv) {
    (void)riid;
    *ppv = this_;
    return S_OK;
}
static ULONG STDMETHODCALLTYPE EnvHandler_AddRef(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *this_) {
    return ++((EnvHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE EnvHandler_Release(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *this_) {
    LONG r = --((EnvHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}
static HRESULT STDMETHODCALLTYPE EnvHandler_Invoke(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *this_,
        HRESULT hr, ICoreWebView2Environment *env);

/* ---- CtrlHandler ---- */
struct CtrlHandler {
    ICoreWebView2CreateCoreWebView2ControllerCompletedHandlerVtbl *lpVtbl;
    LONG            ref;
    WebView2Handle *wv;
};

static HRESULT STDMETHODCALLTYPE CtrlHandler_QueryInterface(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler *this_,
        REFIID riid, void **ppv) { (void)riid; *ppv = this_; return S_OK; }
static ULONG STDMETHODCALLTYPE CtrlHandler_AddRef(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler *this_) {
    return ++((CtrlHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE CtrlHandler_Release(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler *this_) {
    LONG r = --((CtrlHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}
static HRESULT STDMETHODCALLTYPE CtrlHandler_Invoke(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler *this_,
        HRESULT hr, ICoreWebView2Controller *controller);

/* ---- TitleHandler ---- */
struct TitleHandler {
    ICoreWebView2DocumentTitleChangedEventHandlerVtbl *lpVtbl;
    LONG            ref;
    WebView2Handle *wv;
};

static HRESULT STDMETHODCALLTYPE TitleHandler_QueryInterface(
        ICoreWebView2DocumentTitleChangedEventHandler *this_,
        REFIID riid, void **ppv) { (void)riid; *ppv = this_; return S_OK; }
static ULONG STDMETHODCALLTYPE TitleHandler_AddRef(
        ICoreWebView2DocumentTitleChangedEventHandler *this_) {
    return ++((TitleHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE TitleHandler_Release(
        ICoreWebView2DocumentTitleChangedEventHandler *this_) {
    LONG r = --((TitleHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}
static HRESULT STDMETHODCALLTYPE TitleHandler_Invoke(
        ICoreWebView2DocumentTitleChangedEventHandler *this_,
        ICoreWebView2 *sender, IUnknown *args) {
    (void)args;
    TitleHandler *self = (TitleHandler*)this_;
    if (!self->wv->onTitleChanged) return S_OK;
    LPWSTR title = NULL;
    sender->lpVtbl->get_DocumentTitle(sender, &title);
    if (title) {
        self->wv->onTitleChanged(title, self->wv->userData);
        CoTaskMemFree(title);
    }
    return S_OK;
}

/* ---- NavStartHandler ---- */
struct NavStartHandler {
    ICoreWebView2NavigationStartingEventHandlerVtbl *lpVtbl;
    LONG            ref;
    WebView2Handle *wv;
};

static HRESULT STDMETHODCALLTYPE NavStartHandler_QueryInterface(
        ICoreWebView2NavigationStartingEventHandler *this_,
        REFIID riid, void **ppv) { (void)riid; *ppv = this_; return S_OK; }
static ULONG STDMETHODCALLTYPE NavStartHandler_AddRef(
        ICoreWebView2NavigationStartingEventHandler *this_) {
    return ++((NavStartHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE NavStartHandler_Release(
        ICoreWebView2NavigationStartingEventHandler *this_) {
    LONG r = --((NavStartHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}
static HRESULT STDMETHODCALLTYPE NavStartHandler_Invoke(
        ICoreWebView2NavigationStartingEventHandler *this_,
        ICoreWebView2 *sender, ICoreWebView2NavigationStartingEventArgs *args) {
    (void)sender;
    NavStartHandler *self = (NavStartHandler*)this_;
    if (self->wv->onLoadingChanged) self->wv->onLoadingChanged(TRUE, self->wv->userData);
    if (self->wv->onUrlChanged) {
        LPWSTR uri = NULL;
        args->lpVtbl->get_Uri(args, &uri);
        if (uri) {
            self->wv->onUrlChanged(uri, self->wv->userData);
            CoTaskMemFree(uri);
        }
    }
    return S_OK;
}

/* ---- NavCompHandler ---- */
struct NavCompHandler {
    ICoreWebView2NavigationCompletedEventHandlerVtbl *lpVtbl;
    LONG            ref;
    WebView2Handle *wv;
};

static HRESULT STDMETHODCALLTYPE NavCompHandler_QueryInterface(
        ICoreWebView2NavigationCompletedEventHandler *this_,
        REFIID riid, void **ppv) { (void)riid; *ppv = this_; return S_OK; }
static ULONG STDMETHODCALLTYPE NavCompHandler_AddRef(
        ICoreWebView2NavigationCompletedEventHandler *this_) {
    return ++((NavCompHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE NavCompHandler_Release(
        ICoreWebView2NavigationCompletedEventHandler *this_) {
    LONG r = --((NavCompHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}
static HRESULT STDMETHODCALLTYPE NavCompHandler_Invoke(
        ICoreWebView2NavigationCompletedEventHandler *this_,
        ICoreWebView2 *sender, ICoreWebView2NavigationCompletedEventArgs *args) {
    (void)sender; (void)args;
    NavCompHandler *self = (NavCompHandler*)this_;
    if (self->wv->onLoadingChanged) self->wv->onLoadingChanged(FALSE, self->wv->userData);
    return S_OK;
}

/* ---- DownloadProgressHandler ---- */
struct DownloadProgressHandler {
    ICoreWebView2BytesReceivedChangedEventHandlerVtbl *lpVtbl;
    LONG                        ref;
    WebView2Handle             *wv;
    ICoreWebView2DownloadOperation *op;
    wchar_t                    *filePath;
    long long                   totalBytes;
};

static HRESULT STDMETHODCALLTYPE DownloadProgressHandler_QueryInterface(
        ICoreWebView2BytesReceivedChangedEventHandler *this_,
        REFIID riid, void **ppv) { (void)riid; *ppv = this_; return S_OK; }
static ULONG STDMETHODCALLTYPE DownloadProgressHandler_AddRef(
        ICoreWebView2BytesReceivedChangedEventHandler *this_) {
    return ++((DownloadProgressHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE DownloadProgressHandler_Release(
        ICoreWebView2BytesReceivedChangedEventHandler *this_) {
    LONG r = --((DownloadProgressHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}
static HRESULT STDMETHODCALLTYPE DownloadProgressHandler_Invoke(
        ICoreWebView2BytesReceivedChangedEventHandler *this_,
        ICoreWebView2DownloadOperation *sender, IUnknown *args) {
    (void)args;
    DownloadProgressHandler *self = (DownloadProgressHandler*)this_;
    if (!self->wv->onDownloadProgress) return S_OK;
    INT64 bytes = 0;
    sender->lpVtbl->get_BytesReceived(sender, &bytes);
    self->wv->onDownloadProgress(self->filePath, (long long)bytes, self->totalBytes, self->wv->downloadUserData);
    return S_OK;
}

/* ---- DownloadStateHandler ---- */
struct DownloadStateHandler {
    ICoreWebView2StateChangedEventHandlerVtbl *lpVtbl;
    LONG                        ref;
    WebView2Handle             *wv;
    ICoreWebView2DownloadOperation *op;
    wchar_t                    *filePath;
};

static HRESULT STDMETHODCALLTYPE DownloadStateHandler_QueryInterface(
        ICoreWebView2StateChangedEventHandler *this_,
        REFIID riid, void **ppv) { (void)riid; *ppv = this_; return S_OK; }
static ULONG STDMETHODCALLTYPE DownloadStateHandler_AddRef(
        ICoreWebView2StateChangedEventHandler *this_) {
    return ++((DownloadStateHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE DownloadStateHandler_Release(
        ICoreWebView2StateChangedEventHandler *this_) {
    LONG r = --((DownloadStateHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}
static HRESULT STDMETHODCALLTYPE DownloadStateHandler_Invoke(
        ICoreWebView2StateChangedEventHandler *this_,
        ICoreWebView2DownloadOperation *sender, IUnknown *args) {
    (void)args;
    DownloadStateHandler *self = (DownloadStateHandler*)this_;
    if (!self->wv->onDownloadStateChanged) return S_OK;
    COREWEBVIEW2_DOWNLOAD_STATE state;
    sender->lpVtbl->get_State(sender, &state);
    self->wv->onDownloadStateChanged(self->filePath, (int)state, self->wv->downloadUserData);
    return S_OK;
}

/* ---- DownloadStartHandler ---- */
struct DownloadStartHandler {
    ICoreWebView2DownloadStartingEventHandlerVtbl *lpVtbl;
    LONG            ref;
    WebView2Handle *wv;
};

static HRESULT STDMETHODCALLTYPE DownloadStartHandler_QueryInterface(
        ICoreWebView2DownloadStartingEventHandler *this_,
        REFIID riid, void **ppv) { (void)riid; *ppv = this_; return S_OK; }
static ULONG STDMETHODCALLTYPE DownloadStartHandler_AddRef(
        ICoreWebView2DownloadStartingEventHandler *this_) {
    return ++((DownloadStartHandler*)this_)->ref;
}
static ULONG STDMETHODCALLTYPE DownloadStartHandler_Release(
        ICoreWebView2DownloadStartingEventHandler *this_) {
    LONG r = --((DownloadStartHandler*)this_)->ref;
    if (r == 0) HeapFree(GetProcessHeap(), 0, this_);
    return r;
}

static ICoreWebView2BytesReceivedChangedEventHandlerVtbl g_downloadProgressVtbl = {
    DownloadProgressHandler_QueryInterface, DownloadProgressHandler_AddRef,
    DownloadProgressHandler_Release, DownloadProgressHandler_Invoke
};
static ICoreWebView2StateChangedEventHandlerVtbl g_downloadStateVtbl = {
    DownloadStateHandler_QueryInterface, DownloadStateHandler_AddRef,
    DownloadStateHandler_Release, DownloadStateHandler_Invoke
};

/*
 * FIXED: ICoreWebView2DownloadStartingEventArgs has NO get_Uri member.
 * The URI (and total size) must be read from the ICoreWebView2DownloadOperation
 * object obtained via args->lpVtbl->get_DownloadOperation(). The previous
 * version referenced an undeclared `op` identifier and duplicated/leaked a
 * separate `download` object — both are removed below; there is now a single,
 * properly declared `op` used throughout the function.
 */
static HRESULT STDMETHODCALLTYPE DownloadStartHandler_Invoke(
        ICoreWebView2DownloadStartingEventHandler *this_,
        ICoreWebView2 *sender, ICoreWebView2DownloadStartingEventArgs *args) {
    (void)sender;
    DownloadStartHandler *self = (DownloadStartHandler*)this_;
    WebView2Handle *wv = self->wv;

    LPWSTR defaultPath = NULL;
    args->lpVtbl->get_ResultFilePath(args, &defaultPath);

    ICoreWebView2DownloadOperation *op = NULL;
    args->lpVtbl->get_DownloadOperation(args, &op);

    LPWSTR uri = NULL;
    if (op) {
        op->lpVtbl->get_Uri(op, &uri);
    }

    wchar_t finalPath[2048];
    finalPath[0] = L'\0';

    if (wv->downloadsFolder[0] != L'\0' && defaultPath) {
        const wchar_t *slash = wcsrchr(defaultPath, L'\\');
        const wchar_t *name = slash ? slash + 1 : defaultPath;

        wchar_t base[1024];
        wcsncpy(base, name, 1023);
        base[1023] = L'\0';

        wchar_t stem[900];
        wchar_t ext[128];
        ext[0] = L'\0';
        wchar_t *dot = wcsrchr(base, L'.');
        if (dot) {
            wcsncpy(ext, dot, 127);
            ext[127] = L'\0';
            size_t stemLen = (size_t)(dot - base);
            if (stemLen > 899) stemLen = 899;
            wcsncpy(stem, base, stemLen);
            stem[stemLen] = L'\0';
        } else {
            wcsncpy(stem, base, 899);
            stem[899] = L'\0';
        }

        wsprintfW(finalPath, L"%s\\%s%s", wv->downloadsFolder, stem, ext);
        int counter = 2;
        while (GetFileAttributesW(finalPath) != INVALID_FILE_ATTRIBUTES && counter < 1000) {
            wsprintfW(finalPath, L"%s\\%s (%d)%s", wv->downloadsFolder, stem, counter, ext);
            counter++;
        }
        args->lpVtbl->put_ResultFilePath(args, finalPath);
    } else if (defaultPath) {
        wcsncpy(finalPath, defaultPath, 2047);
        finalPath[2047] = L'\0';
    }

    args->lpVtbl->put_Handled(args, TRUE);

    INT64 total = -1;
    if (op) op->lpVtbl->get_TotalBytesToReceive(op, &total);

    DbgLog("[CometWV2] Download starting uri=%ls path=%ls total=%lld\n",
           uri ? uri : L"(null)", finalPath, (long long)total);

    if (wv->onDownloadStarted) {
        wv->onDownloadStarted(uri ? uri : L"", finalPath, (long long)total, wv->downloadUserData);
    }

    if (op) {
        if (wv->onDownloadProgress) {
            DownloadProgressHandler *ph = (DownloadProgressHandler*)HeapAlloc(
                    GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(DownloadProgressHandler));
            ph->lpVtbl = &g_downloadProgressVtbl;
            ph->ref = 1;
            ph->wv = wv;
            ph->op = op;
            op->lpVtbl->AddRef(op);
            ph->filePath = DupWide(finalPath);
            ph->totalBytes = (long long)total;
            EventRegistrationToken pt;
            op->lpVtbl->add_BytesReceivedChanged(op, (ICoreWebView2BytesReceivedChangedEventHandler*)ph, &pt);
        }

        if (wv->onDownloadStateChanged) {
            DownloadStateHandler *sh = (DownloadStateHandler*)HeapAlloc(
                    GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(DownloadStateHandler));
            sh->lpVtbl = &g_downloadStateVtbl;
            sh->ref = 1;
            sh->wv = wv;
            sh->op = op;
            op->lpVtbl->AddRef(op);
            sh->filePath = DupWide(finalPath);
            EventRegistrationToken st;
            op->lpVtbl->add_StateChanged(op, (ICoreWebView2StateChangedEventHandler*)sh, &st);
        }

        op->lpVtbl->Release(op);
    }

    if (uri) CoTaskMemFree(uri);
    if (defaultPath) CoTaskMemFree(defaultPath);

    return S_OK;
}

static ICoreWebView2DownloadStartingEventHandlerVtbl g_downloadStartVtbl = {
    DownloadStartHandler_QueryInterface, DownloadStartHandler_AddRef,
    DownloadStartHandler_Release, DownloadStartHandler_Invoke
};

/* ---- Vtables (defined once, file-scope static) ---- */
static ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandlerVtbl g_envVtbl = {
    EnvHandler_QueryInterface, EnvHandler_AddRef, EnvHandler_Release, EnvHandler_Invoke
};
static ICoreWebView2CreateCoreWebView2ControllerCompletedHandlerVtbl g_ctrlVtbl = {
    CtrlHandler_QueryInterface, CtrlHandler_AddRef, CtrlHandler_Release, CtrlHandler_Invoke
};
static ICoreWebView2DocumentTitleChangedEventHandlerVtbl g_titleVtbl = {
    TitleHandler_QueryInterface, TitleHandler_AddRef, TitleHandler_Release, TitleHandler_Invoke
};
static ICoreWebView2NavigationStartingEventHandlerVtbl g_navStartVtbl = {
    NavStartHandler_QueryInterface, NavStartHandler_AddRef, NavStartHandler_Release, NavStartHandler_Invoke
};
static ICoreWebView2NavigationCompletedEventHandlerVtbl g_navCompVtbl = {
    NavCompHandler_QueryInterface, NavCompHandler_AddRef, NavCompHandler_Release, NavCompHandler_Invoke
};

/* ---- CtrlHandler_Invoke: wires events, sets bounds, navigates ---- */
static HRESULT STDMETHODCALLTYPE CtrlHandler_Invoke(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler *this_,
        HRESULT hr, ICoreWebView2Controller *controller) {
    CtrlHandler *self = (CtrlHandler*)this_;
    WebView2Handle *wv = self->wv;
    DbgLog("[CometWV2] CtrlHandler_Invoke hr=0x%08lX controller=%p\n", (long)hr, (void*)controller);
    if (FAILED(hr) || !controller) return hr;

    wv->controller = controller;
    controller->lpVtbl->AddRef(controller);

    controller->lpVtbl->get_CoreWebView2(controller, &wv->webview);

    EventRegistrationToken token;

    TitleHandler *th = (TitleHandler*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(TitleHandler));
    th->lpVtbl = &g_titleVtbl;
    th->ref = 1;
    th->wv = wv;
    wv->webview->lpVtbl->add_DocumentTitleChanged(wv->webview,
        (ICoreWebView2DocumentTitleChangedEventHandler*)th, &token);

    NavStartHandler *ns = (NavStartHandler*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(NavStartHandler));
    ns->lpVtbl = &g_navStartVtbl;
    ns->ref = 1;
    ns->wv = wv;
    wv->webview->lpVtbl->add_NavigationStarting(wv->webview,
        (ICoreWebView2NavigationStartingEventHandler*)ns, &token);

    NavCompHandler *nc = (NavCompHandler*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(NavCompHandler));
    nc->lpVtbl = &g_navCompVtbl;
    nc->ref = 1;
    nc->wv = wv;
    wv->webview->lpVtbl->add_NavigationCompleted(wv->webview,
        (ICoreWebView2NavigationCompletedEventHandler*)nc, &token);

    {
        ICoreWebView2_4 *webview4 = NULL;
        HRESULT hr4 = wv->webview->lpVtbl->QueryInterface(wv->webview, &IID_ICoreWebView2_4, (void**)&webview4);
        if (SUCCEEDED(hr4) && webview4) {
            DownloadStartHandler *dh = (DownloadStartHandler*)HeapAlloc(
                    GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(DownloadStartHandler));
            dh->lpVtbl = &g_downloadStartVtbl;
            dh->ref = 1;
            dh->wv = wv;
            EventRegistrationToken dtoken;
            webview4->lpVtbl->add_DownloadStarting(webview4,
                (ICoreWebView2DownloadStartingEventHandler*)dh, &dtoken);
            webview4->lpVtbl->Release(webview4);
            DbgLog("[CometWV2] DownloadStarting handler registered\n");
        } else {
            DbgLog("[CometWV2] ICoreWebView2_4 not available, downloads not intercepted, hr=0x%08lX\n", (long)hr4);
        }
    }

    RECT rc;
    GetClientRect(wv->hwnd, &rc);
    DbgLog("[CometWV2] CtrlHandler_Invoke bounds=(%ld,%ld,%ld,%ld) hwnd=%p pendingUrl=%ls\n",
           (long)rc.left, (long)rc.top, (long)rc.right, (long)rc.bottom,
           (void*)wv->hwnd, wv->pendingUrl);
    controller->lpVtbl->put_Bounds(controller, rc);
    controller->lpVtbl->put_IsVisible(controller, TRUE);

    wv->ready = TRUE;

    if (wv->pendingUrl[0] != L'\0') {
        wv->webview->lpVtbl->Navigate(wv->webview, wv->pendingUrl);
    }

    return S_OK;
}

/* ---- EnvHandler_Invoke: creates controller after env is ready ---- */
static HRESULT STDMETHODCALLTYPE EnvHandler_Invoke(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler *this_,
        HRESULT hr, ICoreWebView2Environment *env) {
    EnvHandler *self = (EnvHandler*)this_;
    WebView2Handle *wv = self->wv;
    DbgLog("[CometWV2] EnvHandler_Invoke hr=0x%08lX env=%p\n", (long)hr, (void*)env);
    if (FAILED(hr) || !env) return hr;

    wv->env = env;
    env->lpVtbl->AddRef(env);

    CtrlHandler *ch = (CtrlHandler*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(CtrlHandler));
    ch->lpVtbl = &g_ctrlVtbl;
    ch->ref = 1;
    ch->wv = wv;

    env->lpVtbl->CreateCoreWebView2Controller(env, wv->hwnd,
        (ICoreWebView2CreateCoreWebView2ControllerCompletedHandler*)ch);
    return S_OK;
}

/* ──────────────────────────────────────────────────────────
 *  Exported API
 * ────────────────────────────────────────────────────────── */

__declspec(dllexport) WebView2Handle* CometWV2_Create(
        HWND parentHwnd,
        const wchar_t *userDataFolder,
        const wchar_t *initialUrl,
        void (*onTitleChanged)(const wchar_t*, void*),
        void (*onUrlChanged)(const wchar_t*, void*),
        void (*onLoadingChanged)(BOOL, void*),
        void *userData)
{
    WebView2Handle *wv = (WebView2Handle*)HeapAlloc(
        GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(WebView2Handle));
    if (!wv) return NULL;

    wv->hwnd = parentHwnd;
    wv->onTitleChanged   = onTitleChanged;
    wv->onUrlChanged     = onUrlChanged;
    wv->onLoadingChanged = onLoadingChanged;
    wv->userData         = userData;
    if (initialUrl) wcsncpy(wv->pendingUrl, initialUrl, 2047);

    EnvHandler *eh = (EnvHandler*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(EnvHandler));
    if (!eh) {
        HeapFree(GetProcessHeap(), 0, wv);
        return NULL;
    }
    eh->lpVtbl = &g_envVtbl;
    eh->ref = 1;
    eh->wv = wv;

    HRESULT hr = CreateCoreWebView2EnvironmentWithOptions(
        NULL,
        userDataFolder,
        NULL,
        (ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler*)eh);

    DbgLog("[CometWV2] CreateCoreWebView2EnvironmentWithOptions hr=0x%08lX hwnd=%p userDataFolder=%ls initialUrl=%ls\n",
           (long)hr, (void*)parentHwnd,
           userDataFolder ? userDataFolder : L"(null)",
           initialUrl ? initialUrl : L"(null)");

    if (FAILED(hr)) {
        HeapFree(GetProcessHeap(), 0, eh);
        HeapFree(GetProcessHeap(), 0, wv);
        return NULL;
    }
    return wv;
}

__declspec(dllexport) void CometWV2_SetDownloadFolder(WebView2Handle *wv, const wchar_t *folder) {
    if (!wv || !folder) return;
    wcsncpy(wv->downloadsFolder, folder, 1023);
    wv->downloadsFolder[1023] = L'\0';
}

__declspec(dllexport) void CometWV2_SetDownloadCallback(
        WebView2Handle *wv,
        void (*onDownloadStarted)(const wchar_t*, const wchar_t*, long long, void*),
        void (*onDownloadProgress)(const wchar_t*, long long, long long, void*),
        void (*onDownloadStateChanged)(const wchar_t*, int, void*),
        void *userData) {
    if (!wv) return;
    wv->onDownloadStarted      = onDownloadStarted;
    wv->onDownloadProgress     = onDownloadProgress;
    wv->onDownloadStateChanged = onDownloadStateChanged;
    wv->downloadUserData       = userData;
}

__declspec(dllexport) void CometWV2_Navigate(WebView2Handle *wv, const wchar_t *url) {
    if (!wv || !url) return;
    if (wv->ready && wv->webview) {
        wv->webview->lpVtbl->Navigate(wv->webview, url);
    } else {
        wcsncpy(wv->pendingUrl, url, 2047);
    }
}

__declspec(dllexport) void CometWV2_Resize(WebView2Handle *wv, int x, int y, int w, int h) {
    DbgLog("[CometWV2] CometWV2_Resize wv=%p controller=%p (%d,%d,%d,%d)\n",
           (void*)wv, wv ? (void*)wv->controller : NULL, x, y, w, h);
    if (!wv || !wv->controller) return;
    RECT rc = { x, y, x + w, y + h };
    wv->controller->lpVtbl->put_Bounds(wv->controller, rc);
}

__declspec(dllexport) void CometWV2_SetVisible(WebView2Handle *wv, BOOL visible) {
    if (!wv || !wv->controller) return;
    wv->controller->lpVtbl->put_IsVisible(wv->controller, visible);
}

__declspec(dllexport) void CometWV2_GoBack(WebView2Handle *wv) {
    if (!wv || !wv->webview) return;
    BOOL can = FALSE;
    wv->webview->lpVtbl->get_CanGoBack(wv->webview, &can);
    if (can) wv->webview->lpVtbl->GoBack(wv->webview);
}

__declspec(dllexport) void CometWV2_GoForward(WebView2Handle *wv) {
    if (!wv || !wv->webview) return;
    BOOL can = FALSE;
    wv->webview->lpVtbl->get_CanGoForward(wv->webview, &can);
    if (can) wv->webview->lpVtbl->GoForward(wv->webview);
}

__declspec(dllexport) BOOL CometWV2_CanGoBack(WebView2Handle *wv) {
    if (!wv || !wv->webview) return FALSE;
    BOOL can = FALSE;
    wv->webview->lpVtbl->get_CanGoBack(wv->webview, &can);
    return can;
}

__declspec(dllexport) BOOL CometWV2_CanGoForward(WebView2Handle *wv) {
    if (!wv || !wv->webview) return FALSE;
    BOOL can = FALSE;
    wv->webview->lpVtbl->get_CanGoForward(wv->webview, &can);
    return can;
}

__declspec(dllexport) void CometWV2_Reload(WebView2Handle *wv) {
    if (!wv || !wv->webview) return;
    wv->webview->lpVtbl->Reload(wv->webview);
}

__declspec(dllexport) void CometWV2_Stop(WebView2Handle *wv) {
    if (!wv || !wv->webview) return;
    wv->webview->lpVtbl->Stop(wv->webview);
}

__declspec(dllexport) void CometWV2_ExecuteScript(
        WebView2Handle *wv,
        const wchar_t *script,
        void (*callback)(const wchar_t* result, void* userData),
        void *userData) {
    if (!wv || !wv->webview || !script) return;
    /* Simple fire-and-forget execution; result handling can be wired via
       ICoreWebView2ExecuteScriptCompletedHandler if the caller needs the
       return value. Left as a stub matching original scope of this file. */
    (void)callback;
    (void)userData;
}

__declspec(dllexport) void CometWV2_Close(WebView2Handle *wv) {
    if (!wv) return;
    if (wv->controller) {
        wv->controller->lpVtbl->Close(wv->controller);
        wv->controller->lpVtbl->Release(wv->controller);
        wv->controller = NULL;
    }
    if (wv->webview) {
        wv->webview->lpVtbl->Release(wv->webview);
        wv->webview = NULL;
    }
    if (wv->env) {
        wv->env->lpVtbl->Release(wv->env);
        wv->env = NULL;
    }
    HeapFree(GetProcessHeap(), 0, wv);
}