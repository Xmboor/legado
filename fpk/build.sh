#!/usr/bin/env bash
# 构建 LegadoDownload 飞牛原生静态应用 (.fpk)。
# 用法: FNPACK_BIN=/path/to/fnpack ./build.sh [APK路径]
#   不传 APK 路径时，优先用仓库本地构建产物，否则从 GitHub Release 下载。
# 依赖: fnpack (https://developer.fnnas.com)，置于 PATH 或用 FNPACK_BIN 指定。
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PROJ="$HERE/LegadoDownload"
WWW="$PROJ/app/www"
VERSION="3.26.111315"
APK_NAME="legado_app_${VERSION}_10077.apk"
RELEASE_URL="https://github.com/Xmboor/legado/releases/download/v${VERSION}/${APK_NAME}"
FNPACK="${FNPACK_BIN:-fnpack}"

mkdir -p "$WWW"

APK_SRC="${1:-}"
if [ -z "$APK_SRC" ]; then
  LOCAL="$HERE/../app/build/outputs/apk/app/debug/$APK_NAME"
  if [ -f "$LOCAL" ]; then APK_SRC="$LOCAL"; fi
fi

if [ -n "$APK_SRC" ] && [ -f "$APK_SRC" ]; then
  echo "==> 使用本地 APK: $APK_SRC"
  cp -f "$APK_SRC" "$WWW/legado.apk"
else
  echo "==> 从 GitHub Release 下载 APK: $RELEASE_URL"
  curl -fL --retry 3 --connect-timeout 30 -o "$WWW/legado.apk" "$RELEASE_URL"
fi

SZ=$(stat -c %s "$WWW/legado.apk" 2>/dev/null || stat -f %z "$WWW/legado.apk")
echo "==> APK 大小: $SZ 字节"

echo "==> fnpack build"
( cd "$PROJ" && "$FNPACK" build )

FPK="$PROJ/LegadoDownload.fpk"
# Windows 下 fnpack 会丢失可执行位（cmd/* 与 ui/index.cgi 需 +x 才能被 fnOS 执行）。
# 解包修正权限并清理 .DS_Store 后重打包（GNU tar 保留 +x）。
echo "==> 修正可执行位并重打包"
TMP="$(mktemp -d)"
tar xzf "$FPK" -C "$TMP"
mkdir -p "$TMP/inner"
tar xzf "$TMP/app.tgz" -C "$TMP/inner"
find "$TMP/inner" -name '.DS_Store' -delete
[ -f "$TMP/inner/ui/index.cgi" ] && chmod +x "$TMP/inner/ui/index.cgi"
( cd "$TMP/inner" && tar czf "$TMP/app.tgz" -- * )
chmod +x "$TMP"/cmd/*
tar czf "$FPK" -C "$TMP" app.tgz cmd config ICON.PNG ICON_256.PNG manifest wizard
rm -rf "$TMP"

echo "==> 完成：$FPK"
ls -la "$FPK"