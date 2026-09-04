@echo off
cd /d D:\JavaProject\CometBrowser\webview2-native
rmdir /s /q build
mkdir build
cd build
call "D:\games\BuildTools\Common7\Tools\VsDevCmd.bat"
"D:\games\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe" .. -G "Visual Studio 18 2026" -A x64
"D:\games\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe" --build . --config Release
pause