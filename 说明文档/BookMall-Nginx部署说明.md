# 墨枢 InkNexus · Nginx 部署说明

## 1. 本次实现了什么

这次新增了 BookMall 前端的 Nginx 部署方案，用来模拟更贴近线上环境的访问方式。

现在访问链路变成：

```text
浏览器
  -> Nginx
  -> 前端静态页面
  -> /api 请求转发到 Gateway(8080)
  -> Gateway 转发到各个微服务
```

这样做的价值是：

- 前端不再依赖 Vite 开发服务器才能访问
- 前端和网关入口更接近生产部署结构
- 浏览器只访问一个入口，避免本地调试时路径混乱
- 浏览器只通过 Nginx 访问前端和 Gateway 接口

## 2. 新增文件

### 2.1 Nginx 配置文件

文件：
- [nginx.conf](D:/workspace_idea/BookMall/front/nginx.conf)

作用：
- 托管前端 `dist` 静态文件
- 处理 Vue Router 的 history 路由刷新问题
- 把 `/api/` 请求反向代理到 `http://host.docker.internal:8080`

关键点：
- `try_files $uri $uri/ /index.html;`
  用来支持前端路由直达，例如 `/login`、`/books`
- `location /api/`
  用来把接口请求转发到 Gateway

### 2.2 前端 Dockerfile

文件：
- [Dockerfile](D:/workspace_idea/BookMall/front/Dockerfile)

作用：
- 基于 `nginx:1.27-alpine` 构建前端镜像
- 将打包后的 `dist` 复制到 Nginx 静态目录
- 将 `nginx.conf` 覆盖默认站点配置

### 2.3 Docker Compose 文件

文件：
- [docker-compose.nginx.yml](D:/workspace_idea/BookMall/docker-compose.nginx.yml)

作用：
- 一条命令启动前端 Nginx 容器
- 暴露宿主机 `80` 端口
- 通过 `host.docker.internal` 回源到 Windows 上运行的 Gateway

## 3. 已修改文件

### 3.1 前端 package.json

文件：
- [package.json](D:/workspace_idea/BookMall/front/package.json)

新增脚本：
- `build:docker`

作用：
- 保留原有 `vite build`
- 增加一个面向 Docker/Nginx 部署的打包脚本入口

## 4. 当前访问方式

在 Nginx 容器启动成功后：

前端访问地址：
- `http://localhost`

接口访问流量：
- 浏览器请求 `http://localhost/api/...`
- Nginx 转发到 `http://host.docker.internal:8080/...`
- Gateway 再转发到各个微服务

## 5. 使用步骤

### 5.1 先保证后端可用

至少需要：
- `bookmall-gateway` 已启动，端口 `8080`
- 相关业务微服务已启动
- Nacos、MySQL、Redis 已正常运行

### 5.2 打包前端

在 `D:\workspace_idea\BookMall\front` 目录执行：

```powershell
npm run build
```

生成目录：
- `front/dist`

### 5.3 启动 Nginx 容器

在 `D:\workspace_idea\BookMall` 目录执行：

```powershell
docker compose -f docker-compose.nginx.yml up -d --build
```

### 5.4 浏览器验证

打开：

```text
http://localhost
```

重点验证：
- 登录页是否正常显示
- 登录后是否能进入首页
- 图书、购物车、订单页面是否能正常访问接口
- 浏览器直接刷新 `/home`、`/books` 时是否仍然正常打开

## 6. 验证点

### 6.1 静态页面验证

访问：
- `http://localhost`

预期：
- 能打开 BookMall 登录页

### 6.2 前端路由验证

直接在浏览器访问：
- `http://localhost/login`
- `http://localhost/home`
- `http://localhost/books`

预期：
- 不出现 Nginx 404
- 能正确回到前端页面

### 6.3 反向代理验证

在浏览器开发者工具的 Network 里查看：
- `/api/auth/login`
- `/api/books`
- `/api/orders`

预期：
- 请求地址仍然是当前域名下的 `/api/...`
- 实际由 Nginx 转发到 Gateway

## 7. 当前方案说明

因为你的 Java 微服务现在运行在 Windows IDEA，而不是全部在 Docker 容器中，所以 Nginx 容器不能直接通过容器名访问 Gateway。

因此这里使用：
- `host.docker.internal:8080`

这表示：
- Nginx 容器访问宿主机上的 `8080`
- 宿主机上的 `8080` 就是你 IDEA 启动的 Gateway

这非常适合你当前“Windows + WSL + Docker + IDEA”的开发结构。
