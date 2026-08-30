# 苍穹外卖网页用户端

浏览器点餐端，使用 Vue 3、TypeScript、Vite、Pinia 和 Axios。

## 启动

```powershell
# 1. 在仓库根目录启动业务服务
mvn -pl sky-server -am package -DskipTests
java -jar .\sky-server\target\sky-server-1.0-SNAPSHOT.jar

# 2. 新开终端启动 AI 服务
mvn -pl sky-ai-service package -DskipTests
java -jar .\sky-ai-service\target\sky-ai-service-0.0.1-SNAPSHOT.jar

# 3. 新开终端启动网页端
cd .\sky-web-user
npm install
npm run dev
```

访问 http://localhost:5173 。

首次使用前执行 `sql/web-user-migration.sql`。普通业务接口的开发代理默认连接
`http://localhost:8080`，AI 接口默认连接 `http://localhost:8081`；可分别通过
`VITE_API_TARGET` 和 `VITE_AI_API_TARGET` 修改。

登录后访问 `/support` 可使用智能客服。页面支持新建/切换会话、历史消息加载、门店与本人订单查询、POST SSE 增量展示，以及断线续传和失败重试。订单查询时可填写订单 ID。

生产环境需要把 `/api` 路由到业务服务、把 `/ai-api` 路由到 AI 服务，并为 `/ai-api/**/stream` 关闭反向代理缓冲，确保 SSE 事件能及时到达浏览器。
