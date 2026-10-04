@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title Dynamic Island - 构建脚本

echo ============================================================
echo   Dynamic Island  Forge 1.20.1  构建脚本
echo ============================================================
echo.

REM ---------- 1. 定位 Java ----------
set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 没有找到 Java。请先安装 JDK 17。
    echo        下载地址: https://adoptium.net/temurin/releases/?version=17
    pause & exit /b 1
)

REM ---------- 2. 检查版本必须 ^>= 17 ----------
for /f "tokens=3" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do (
    set "RAW=%%v"
)
set "RAW=%RAW:"=%"
for /f "tokens=1 delims=." %%a in ("%RAW%") do set /a MAJOR=%%a
if not defined MAJOR set MAJOR=0

echo [信息] 检测到 Java 主版本: %MAJOR%
echo [信息] Java 路径: %JAVA_CMD%
echo.

if %MAJOR% LSS 17 (
    echo [错误] Forge 1.20.1 必须使用 JDK 17 或更高，当前是 %MAJOR%。
    echo.
    echo   解决办法（任选其一）:
    echo     A. 安装 JDK 17: https://adoptium.net/temurin/releases/?version=17
    echo     B. 临时指定: set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.x
    echo        然后重新运行本脚本
    pause & exit /b 1
)
if %MAJOR% GTR 21 (
    echo [警告] Java %MAJOR% 高于 21，ForgeGradle 可能不兼容，建议用 JDK 17。
    echo.
)

REM ---------- 3. 选择构建方式 ----------
set "GRADLE_CMD="
if exist "gradlew.bat" (
    set "GRADLE_CMD=gradlew.bat"
) else (
    where gradle >nul 2>&1
    if not errorlevel 1 set "GRADLE_CMD=gradle"
)

if not defined GRADLE_CMD (
    echo [提示] 没有找到 gradlew.bat，也没有系统级 gradle。
    echo.
    echo   推荐做法（最简单）:
    echo     1. 用 IntelliJ IDEA 打开本文件夹
    echo     2. 等待 Gradle 同步完成
    echo     3. 在右侧 Gradle 面板里展开 dynamicisland ^> Tasks ^> build
    echo        双击 wrapper，即可生成 gradlew.bat
    echo     4. 之后就能用本脚本了
    echo.
    echo   或者手动安装 Gradle 8.1.1: https://services.gradle.org/distributions/
    pause & exit /b 1
)

echo [信息] 使用构建器: %GRADLE_CMD%
echo.

REM ---------- 4. 选择任务 ----------
echo 请选择要执行的任务:
echo   [1] build      编译并打包 jar（推荐）
echo   [2] runClient  启动带模组的游戏客户端
echo   [3] clean      清理构建缓存
echo   [4] genSources 反编译 MC 源码（便于查看底层）
echo.
set /p CHOICE=输入数字后回车 ^> 

if "%CHOICE%"=="1" set "TASK=build"
if "%CHOICE%"=="2" set "TASK=runClient"
if "%CHOICE%"=="3" set "TASK=clean"
if "%CHOICE%"=="4" set "TASK=genSources"
if not defined TASK set "TASK=build"

echo.
echo ============================================================
echo   开始执行: %TASK%
echo   首次构建需要下载 Forge 与反编译 Minecraft，约 10-30 分钟
echo   期间请保持网络畅通，不要关闭窗口
echo ============================================================
echo.

call %GRADLE_CMD% %TASK% --console=plain
set "EXITCODE=%ERRORLEVEL%"

echo.
if "%EXITCODE%"=="0" (
    echo ============================================================
    echo   构建成功！
    if "%TASK%"=="build" echo   jar 位置: build\libs\dynamicisland-1.0.0.jar
    if "%TASK%"=="build" echo   把它丢进 .minecraft\mods 文件夹即可使用
    echo ============================================================
) else (
    echo ============================================================
    echo   构建失败，退出码 %EXITCODE%
    echo   请把上方红色错误信息复制给我，我帮你定位
    echo ============================================================
)
echo.
pause
