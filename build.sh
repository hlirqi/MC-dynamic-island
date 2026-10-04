#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "============================================================"
echo "  Dynamic Island · Forge 1.20.1 构建脚本"
echo "============================================================"

JAVA_CMD="java"
[ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] && JAVA_CMD="$JAVA_HOME/bin/java"

if ! "$JAVA_CMD" -version >/dev/null 2>&1; then
  echo "[错误] 没找到 Java，请安装 JDK 17：https://adoptium.net/temurin/releases/?version=17"
  exit 1
fi

RAW=$("$JAVA_CMD" -version 2>&1 | head -1 | sed -E 's/.*version "([^"]+)".*/\1/')
MAJOR=$(echo "$RAW" | cut -d. -f1)
echo "[信息] Java 主版本: $MAJOR  ($JAVA_CMD)"

if [ "$MAJOR" -lt 17 ] 2>/dev/null || [ -z "$MAJOR" ]; then
  echo "[错误] 需要 JDK 17 或更高。"
  echo "       export JAVA_HOME=/path/to/jdk-17"
  exit 1
fi

GRADLE_CMD=""
[ -x "./gradlew" ] && GRADLE_CMD="./gradlew"
[ -z "$GRADLE_CMD" ] && command -v gradle >/dev/null 2>&1 && GRADLE_CMD="gradle"
if [ -z "$GRADLE_CMD" ]; then
  echo "[提示] 未找到 gradlew / gradle。"
  echo "       用 IntelliJ IDEA 打开本项目，Gradle 同步后运行 wrapper 任务即可生成。"
  exit 1
fi

echo "请选择任务:"
echo "  [1] build      编译打包 jar（推荐）"
echo "  [2] runClient  启动游戏客户端"
echo "  [3] clean      清理缓存"
echo "  [4] genSources 反编译 MC 源码"
printf "输入数字后回车 > "
read -r CHOICE
case "$CHOICE" in
  2) TASK=runClient ;;
  3) TASK=clean ;;
  4) TASK=genSources ;;
  *) TASK=build ;;
esac

echo "============================================================"
echo "  执行: $TASK （首次构建约 10-30 分钟，请保持网络）"
echo "============================================================"

"$GRADLE_CMD" "$TASK" --console=plain

echo
echo "构建成功！jar 位于 build/libs/，放入 .minecraft/mods 即可使用。"
