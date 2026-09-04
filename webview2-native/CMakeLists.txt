cmake_minimum_required(VERSION 3.16)
project(CometWebView2 C)

set(CMAKE_C_STANDARD 11)

set(WEBVIEW2_SDK_DIR "${CMAKE_SOURCE_DIR}/webview2-sdk"
    CACHE PATH "Path to WebView2 SDK (from NuGet package)")

add_library(CometWebView2 SHARED CometWebView2.c)

target_include_directories(CometWebView2 PRIVATE
    "${WEBVIEW2_SDK_DIR}/include"
)

target_link_libraries(CometWebView2
    "${WEBVIEW2_SDK_DIR}/lib/x64/WebView2LoaderStatic.lib"
    ole32
    oleaut32
    uuid
    user32
)

set_target_properties(CometWebView2 PROPERTIES
    OUTPUT_NAME "CometWebView2"
    PREFIX ""
)
