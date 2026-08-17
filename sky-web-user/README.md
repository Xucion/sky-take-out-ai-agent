# 苍穹外卖网页用户端

独立于微信小程序的浏览器点餐端，使用 Vue 3、TypeScript、Vite、Pinia 和 Axios。

## 启动

```powershell
# 1. 在仓库根目录启动后端
mvn -pl sky-server -am package -DskipTests
java -jar .\sky-server\target\sky-server-1.0-SNAPSHOT.jar

# 2. 新开终端启动网页端
cd .\sky-web-user
npm install
npm run dev
```

访问 http://localhost:5173 。

首次使用前执行 `sql/web-user-migration.sql`。开发代理默认连接 `http://localhost:8080`；如需连接其他后端，可在启动前设置 `VITE_API_TARGET`。
