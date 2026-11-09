# 前端部署 / nginx 反代配置（admin.web + gis.web）

> 目标：两个前端都只暴露 `http://admin.web` 与 `http://gis.web` 两个地址，
> 浏览器**不直连** 9802 / 4548 / 38190 等端口 —— 换台客户端电脑也不用改任何地址。
> 适用环境：Windows + phpstudy（nginx），后端 jar、中台、kkFileView 都在同一台机器。

## 1. 文件清单

| 文件 | 放哪里 |
|---|---|
| `nginx-admin.web.conf` | 覆盖 `D:/phpstudy_pro/Extensions/Nginx-<版本>/conf/vhosts/admin.web.conf` |
| `nginx-gis.web.conf` | 覆盖 `.../conf/vhosts/gis.web.conf` |
| `static-config-admin.web.js` | 覆盖 `D:/phpstudy_pro/WWW/admin.web/static/config.js` |
| `static-config-gis.web.js` | 覆盖 `D:/phpstudy_pro/WWW/gis.web/static/config.js` |

站点目录（`root` 就是这两个），dist 解压后 `index.html` 必须直接落在目录里：

```
D:/phpstudy_pro/WWW/admin.web/        ← admin-client/dist 里的全部内容
D:/phpstudy_pro/WWW/gis.web/          ← dist/stargisWebGL 里的全部内容
```

## 2. 部署顺序

1. 解压两个 dist 到上面两个目录（`admin.web`、`gis.web` 都要能直接访问到 `index.html`）。
2. 用本目录的两份 `static-config-*.js` 覆盖各自 dist 里的 `static/config.js`
   （这一步不能省：dist 里那份写的是 `http://localhost:8080/jeecg-boot` 和
   `http://127.0.0.1:4548`，浏览器换台电脑就全打不通）。
3. 用本目录的两份 `.conf` 覆盖 phpstudy 的两个 vhost 文件。
4. 检查 `nginx -t`，然后重载（phpstudy 面板里"重启 nginx"或 `nginx -s reload`）。
5. 客户端机器 hosts（如果浏览器不在本机）：

   ```
   127.0.0.1   admin.web   gis.web          # nginx 在本机
   <服务器IP>  admin.web   gis.web          # 浏览器在别的电脑时，写服务器 IP
   ```

   ⚠️ **服务器自己也要能解析这两个域名**（hosts 里加 `127.0.0.1 admin.web gis.web`）：
   kkFileView 是**服务端**去下载"文件 URL"的，它解析不了域名时预览必然白屏。

## 3. 后端对齐要求

| 服务 | 端口 / 路径 | 对应 nginx 段 | 前端配置项 |
|---|---|---|---|
| Java 后端（jeecg） | `9802`，context-path `/api` | `location /api/` | `VUE_BUSINESS_JAVA_URL`（大屏）/ `VUE_APP_API_BASE_URL`（管理端） |
| 中台 ZK-SERVER | `4548`，接口形如 `/app/oauth/token` | `location /zk/`（去掉 `/zk` 前缀） | `VUE_APP_API_BASE_URL`（大屏） |
| JNI 分析服务 | `38190`，`/pipeline/*`、`/api/file/*` | `location /analysis/` | `VUE_DATA_JAVA_URL` |
| 地图 / 数据服务 | `38083` / `38084` | `/mapsrv/`、`/datasrv/` | `VUE_MAP_URL`、`VUE_DATA_SERVER_URL` |
| kkFileView | `8012`，`/onlinePreview` | `location /fileview/` | `VUE_APP_ONLINE_BASE_URL` |

后端 jar 用 `-Pprod` 打包时，端口与上下文路径来自 `.env`
（`TJ_SERVER_PORT=9802`、`TJ_CONTEXT_PATH=/api`，见 `server/.env.example`）。
**两边必须一致**：nginx 按 9802+`/api` 写，jar 也必须听 9802+`/api`。

### 3.1 ⚠️ 两个前端 `.env.production` 里的基址很容易写串

构建期的 `VUE_APP_API_BASE_URL` 含义**两边不同**，写错一边就是 404：

| 前端 | `VUE_APP_API_BASE_URL` 应该是 | 说明 |
|---|---|---|
| `admin-client` | Java 后端（`9802/api`） | 管理端只有这一个后端 |
| `stargis-client-web` | **中台（`4548`）** | 大屏的登录 `/app/oauth/token`、图层/场景 `/app/**` 都在中台；业务接口另有 `VUE_BUSINESS_JAVA_URL`（现名，指 Java 后端） |

大屏如果把 `VUE_APP_API_BASE_URL` 写成 `9802/api`，中台接口会打到 jeecg 上直接 404。
不过——**只要按 §2 第 2 步覆盖了 `static/config.js`，构建期这两个值都会被运行时覆盖**，
所以更稳的做法是：`.env.production` 里不要再写死 `127.0.0.1`/`localhost`，
真正地址统一由部署后的 `static/config.js` 决定。


## 4. 验证清单（部署后按顺序点一遍）

1. `http://admin.web/` 能出登录页；F12 Network 里接口是 `http://admin.web/api/sys/...` 且 200
   —— 如果还是 `localhost:8080`，说明第 2 步的 `static/config.js` 没覆盖成功（或被浏览器缓存，<kbd>Ctrl</kbd>+<kbd>F5</kbd>）。
2. 管理端登录 → 刷新 `/dashboard/analysis` 不 404（验证 history 回退是否生效）。
3. 上传一个 >1MB 的档案附件不报 413/504。
4. 附件点"预览"能出 kkFileView 页面（`/fileview/onlinePreview?...`）。
5. `http://gis.web/` 能出大屏；登录接口走 `/zk/app/oauth/token`；
   地图能出图（图层请求走 `/zk/...` 与 `/api/land/map/mvt/...`）。
6. 大屏点一个分析工具（断面/爆管）能出结果 —— 走 `/analysis/pipeline/...`；
   报 502 就是 38190 没起来。

## 5. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 前端 404 / 白屏，控制台 `app.xxx.js` 404 | dist 放错层级（`index.html` 不直接在 root 下） | 把 dist **里面**的内容放进 root，别多套一层目录 |
| 刷新页面 404 | 管理端是 history 路由，缺 `try_files ... /index.html` | 用本目录的 conf（已带） |
| 接口 502 | 后端没起 / 端口不对 | 本机 `netstat -ano \| findstr 9802`；核对 `.env` 的端口与 context-path |
| 接口 404 且路径是 `/api/api/...` | `proxy_pass` 后面多写了 `/api` | 本目录的 conf 是 `proxy_pass http://127.0.0.1:9802;`（不写 URI = 原样转发） |
| 中台接口 404 | `proxy_pass` 末尾少写 `/`，`/zk` 前缀没被去掉 | `proxy_pass http://127.0.0.1:4548/;`（必须是带 `/` 的） |
| 上传大文件 413 | `client_max_body_size` 太小 | 本目录的 conf 已设 300m（>后端 200MB 上限） |
| 右上角消息通知连不上 | WebSocket 没走反代 / 缺 Upgrade 头 | 检查 `/api/websocket` 那段；另外 `config.js` 里的地址必须是**带 origin 的绝对地址**，`new WebSocket('/api/...')` 会直接抛错 |
| 预览页打不开 / 转圈 | kkFileView 解析不了文件 URL 的域名，或 8012 没起 | 服务器 hosts 加 `127.0.0.1 admin.web gis.web`；确认 8012 可访问 |
| 改了 `config.js` 不生效 | 浏览器缓存 | <kbd>Ctrl</kbd>+<kbd>F5</kbd>；`static/config.js` 在小文件列表里，正常不会被长缓存 |

> 备注：两个前端构建时都开了 `CompressionPlugin`（产物里有 `.gz`），本配置**没有**用
> `gzip_static`（phpstudy 的 nginx 不一定编译了该模块，开了会导致 nginx 起不来），
> 而是用 `gzip on` 在线压缩，效果够用。
