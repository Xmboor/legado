#!/bin/bash
# 纯静态文件服务（飞牛原生应用，无容器、无后台进程）。
# 由 fnOS 内置 Web 服务以 CGI 方式执行，从 target/www 读取静态文件。
BASE_PATH="${TRIM_APPDEST:-/var/apps/LegadoDownload/target}/www"

URI_NO_QUERY="${REQUEST_URI%%\?*}"
REL_PATH="/"
case "$URI_NO_QUERY" in
  *index.cgi*) REL_PATH="${URI_NO_QUERY#*index.cgi}" ;;
esac
if [ -z "$REL_PATH" ] || [ "$REL_PATH" = "/" ]; then
  REL_PATH="/index.html"
fi

TARGET_FILE="${BASE_PATH}${REL_PATH}"

# 防路径穿越
if echo "$TARGET_FILE" | grep -q '\.\.'; then
  echo "Status: 400 Bad Request"
  echo "Content-Type: text/plain; charset=utf-8"
  echo ""
  echo "Bad Request"
  exit 0
fi

if [ ! -f "$TARGET_FILE" ]; then
  echo "Status: 404 Not Found"
  echo "Content-Type: text/plain; charset=utf-8"
  echo ""
  echo "404 Not Found"
  exit 0
fi

EXT="${TARGET_FILE##*.}"
case "$EXT" in
  html|htm) mime="text/html; charset=utf-8" ;;
  css)      mime="text/css; charset=utf-8" ;;
  js)       mime="application/javascript; charset=utf-8" ;;
  json)     mime="application/json; charset=utf-8" ;;
  png)      mime="image/png" ;;
  jpg|jpeg) mime="image/jpeg" ;;
  svg)      mime="image/svg+xml" ;;
  ico)      mime="image/x-icon" ;;
  apk)      mime="application/vnd.android.package-archive" ;;
  *)        mime="application/octet-stream" ;;
esac

echo "Content-Type: $mime"
if [ "$EXT" = "apk" ]; then
  echo "Content-Disposition: attachment; filename=\"legado_app_3.26.111315_10077.apk\""
fi
echo ""
cat "$TARGET_FILE"