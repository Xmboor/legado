# LegadoDownload · 飞牛 fnOS 原生静态应用

阅读 legado 自定义增强版 APK 下载站。**纯静态飞牛应用（.fpk），无 Docker、无容器、无后台进程**：由 fnOS 内置 Web 服务以 CGI（`app/ui/index.cgi`）托管 `app/www/` 静态文件，对 NAS 体验零负担。

## 结构

- `LegadoDownload/manifest` — 应用元数据（appname / version / arch …，`ctl_stop=false` 隐藏启停控件）
- `LegadoDownload/app/ui/config` — 桌面入口（`type:iframe` → `/cgi/ThirdParty/LegadoDownload/index.cgi/`）
- `LegadoDownload/app/ui/index.cgi` — 静态文件 CGI（按扩展名设 MIME，`.apk` 触发下载，防路径穿越）
- `LegadoDownload/app/www/index.html` — 下载页（版本 / 更新说明 / 大小 / SHA-256 / GitHub 链接）
- `LegadoDownload/app/www/legado.apk` — 安装包（构建时放入，**不提交 git**）
- `LegadoDownload/cmd/main` — 生命周期 no-op（静态应用无进程，start/stop/status 均 exit 0）

## 构建

依赖 [fnpack](https://developer.fnnas.com/)（置于 PATH 或用 `FNPACK_BIN` 指定）：

```bash
FNPACK_BIN=/path/to/fnpack ./build.sh [APK路径]
```

不传 APK 路径时优先用仓库本地构建产物（`app/build/outputs/apk/app/debug/`），否则从 GitHub Release 下载。产物 `LegadoDownload.fpk` **不提交 git**。

## 安装

飞牛 **应用中心 → 手动安装** → 上传 `LegadoDownload.fpk`。安装后从桌面「阅读 legado 下载站」入口打开下载页。

> 架构：manifest `arch=x86_64`。若 NAS 为 ARM，请改为对应架构后重新构建。