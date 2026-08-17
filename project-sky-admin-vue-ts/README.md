# 苍穹外卖商家管理端

项目已迁移到 Vue 3、Vite、TypeScript、Vue Router 4、Vuex 4 和 Element Plus，使用 Vue 3 兼容模式承载原有类组件，便于后续逐页迁移到 Composition API。

## 环境要求

- Node.js 22.12 或更高版本
- npm 10.9 或更高版本

## 本地运行

```bash
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:8888`，`/api` 请求代理到 `.env.development` 中配置的后端管理接口。

## 构建

```bash
npm run build
```

首次执行 `npm install` 后会由当前 npm 生成新的 `package-lock.json`。旧 Vue CLI/Yarn 锁文件已移除，不要继续使用旧锁文件安装依赖。

## 环境变量

- `VITE_BASE_API`：浏览器请求前缀
- `VITE_APP_URL`：开发代理目标，例如 `http://localhost:8080/admin`
- `VITE_SOCKET_URL`：订单通知 WebSocket 地址
- `VITE_DELETE_PERMISSIONS`：是否显示删除操作
