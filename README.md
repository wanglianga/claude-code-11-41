# 软件依赖清单数据质量与供应链元数据服务

面向软件供应链的**元数据事实层**：回答“某次构建实际包含哪些组件、这些组件在构建发生时可由哪些 SBOM / 包仓元数据 / 锁文件 / 源码扫描 / 签名证据支持、哪些依赖只能作为候选而不能当作已证实事实”。

系统**不裁决许可证，不构建、签名、发布或分发制品**，只交付可追溯的软件供应链元数据事实。

## 架构

```
┌─────────────┐   ┌──────────────────────────────────────────────────┐
│  Vue 3 + TS │   │              Spring Boot (Java 25)                │
│  Element +  │──▶│  来源保全册 → 身份工作区 → 依赖编译器 → 事实签发器 │
│  Vite       │   │       ↓ 消费目录 ← 变动监测器 ← 复审事件          │
└─────────────┘   └──────────────────────┬───────────────────────────┘
                                          ▼
                                    PostgreSQL 18
```

- **前端**：`frontend/` — Vue 3 + TypeScript + Vite + Element Plus + Pinia + ECharts
- **后端**：`backend/` — Java 25 + Spring Boot 4 + Spring Data JPA
- **数据库**：PostgreSQL（容器内已预装 `ccdev/ccdev@127.0.0.1:5432`）

## 核心子系统

| 子系统 | 职责 |
| --- | --- |
| 来源保全册 | 保存 SBOM、锁文件、构建日志、包仓响应、源码扫描、签名索引、组件目录、漏洞通告的原始载荷、采集/事件时间、来源地址、SHA-256、解析器版本、接收状态；追加式不可改写 |
| 组件身份工作区 | 按生态、命名空间、发布者、坐标、版本、哈希与构建上下文归并组件声明，维护候选 / 已确认 / 来源冲突 / 证据不足身份 |
| 依赖事实编译器 | 区分被实际解析证据支持的已证实边（PROVEN）与仅由包仓/扫描推断的候选边（CANDIDATE）；冲突或证据不足的边不可证实（REJECTED） |
| 构建事实签发器 | 在截止点冻结关系、规则版本与来源对象哈希，签发不可变事实；截止点后到达的来源登记为后续差异来源 |
| 消费目录 | 记录下游风险视图、资产清单、审计视图对事实版本的引用与当时身份版本快照 |
| 变动监测器 | 目录别名/发布者修订或签名撤销时定位当前引用、暂停下游视图；历史分析保留当时身份版本 |

### 身份确认规则（rules-1.0，签发时随事实冻结）

1. 声明按 `生态|命名空间|名称|版本` 分组，再按 `发布者|哈希` 归并为身份；生态缺失按 `unknown` 处理，不跨生态合并。
2. 实际解析证据（SBOM/锁文件/构建日志）哈希一致 → `CONFIRMED`。
3. 解析证据间哈希冲突且构建日志不能证明任一哈希 → 全部 `CONFLICT`（来源冲突），编译器不得签发已证实依赖。
4. 构建日志确认某一哈希 → 该哈希身份 `CONFIRMED`。
5. 仅包仓元数据/源码扫描推断 → `CANDIDATE`；同名异发布者且缺生态上下文 → 多个候选并存，不误合并。
6. 签名作为唯一依据可确认；签名撤销后降为 `INSUFFICIENT_EVIDENCE`，新事实不能继续显示已证实。

## 本地启动

### 前置条件

- Java 25（`/opt/jdk-25`）、Maven 3.9（`/opt/maven`）、Node 22（`/opt/node-22`）
- PostgreSQL 运行于 `127.0.0.1:5432`，库/用户/密码均为 `ccdev`

### 后端

```bash
cd backend
/opt/maven/bin/mvn package -DskipTests
/opt/jdk-25/bin/java -jar target/scf-metadata-service-1.0.0.jar
# 监听 http://127.0.0.1:8080
```

数据库表结构由 JPA 自动创建（`ddl-auto=update`），无需手工建表。

### 前端

```bash
cd frontend
/opt/node-22/bin/npm install --registry=https://registry.npmmirror.com
/opt/node-22/bin/npm run dev        # 开发模式 http://127.0.0.1:5173（/api 代理到 8080）
# 或生产构建 + 预览
/opt/node-22/bin/npm run build
/opt/node-22/bin/npm run preview    # http://127.0.0.1:4173
```

### 测试账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | ADMIN（可写：摄入、编译、签发、修订、撤销） |
| `viewer` | `viewer123` | VIEWER（只读） |

> 演示账号仅用于本隔离开发环境，非生产配置。

## 一键启动（Docker Compose）

```bash
docker compose up --build
```

- 前端：http://127.0.0.1:8088
- 后端 API：http://127.0.0.1:8080
- PostgreSQL：127.0.0.1:5432（`ccdev/ccdev`）

Compose 编排 `db`（postgres:18，含健康检查）→ `backend`（多阶段构建）→ `frontend`（nginx，代理 `/api` 到后端）。

## 验证方式

### 1. 后端规则测试（JUnit + H2）

```bash
cd backend
/opt/maven/bin/mvn test
```

`RuleEngineTests` 覆盖六大验收规则：同名异发布者保留多候选、哈希冲突阻断已证实依赖、私有镜像留待核项、构建后补传登记为后续差异、目录别名修订暂停下游视图、签名撤销降级身份。

### 2. 六大验收场景（API 一键运行）

服务启动后，前端「验收场景」页可逐个运行；或用命令行：

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
for s in same-name-diff-publisher hash-conflict private-mirror late-lockfile catalog-alias-revision signature-revocation; do
  echo "== $s =="; curl -s -X POST http://127.0.0.1:8080/api/scenarios/$s/run -H "Authorization: Bearer $TOKEN"
done
```

每个场景返回 `checks` 列表，`passed=true` 表示该验收点通过：

| 场景 | 验收点 |
| --- | --- |
| `same-name-diff-publisher` | 同名包在 npm 与内部包仓发布者/哈希不同且缺生态上下文 → 保留多个候选，不合并为已确认 |
| `hash-conflict` | SBOM 与锁文件坐标相同哈希不同、构建日志不能证明 → 标为来源冲突，编译器与签发事实均无已证实边 |
| `private-mirror` | 私有镜像字段变体单独映射并留待核项，声明保持候选 |
| `late-lockfile` | 锁文件在截止点后补传且版本不同 → 原事实按截止点证据签发且不被改写，补传件登记为后续差异来源 |
| `catalog-alias-revision` | 目录修订别名/发布者 → 监测器暂停下游视图，历史视图保留当时身份版本快照 |
| `signature-revocation` | 唯一依据的签名撤销 → 组件降为证据不足，新事实不能继续显示已证实 |

### 3. 全链路追溯

任一构建事实可回查：来源对象（含后续差异来源）、身份关系（冻结快照 vs 当前状态 + 支撑声明）、依赖边、冻结版本、复审事件、下游引用。

```bash
curl -s http://127.0.0.1:8080/api/build-facts -H "Authorization: Bearer $TOKEN"   # 取 fact id
curl -s http://127.0.0.1:8080/api/build-facts/<factId>/trace -H "Authorization: Bearer $TOKEN"
```

前端「构建事实签发」页点击「追溯」可视化查看。

## 已执行的验证结论

- `mvn test`：6/6 规则测试通过（H2 内存库）。
- 后端连接本地 PostgreSQL 18 启动成功，JPA 自动建表。
- 通过 API 运行六大验收场景，全部检查通过，且重复运行结果一致。
- 前端 `npm run build`（含 vue-tsc 类型检查）通过；`vite preview` 下联调登录、仪表盘、场景运行、来源列表均正常。
- 权限验证：未认证 401；viewer 只读（GET 200 / POST 403）。
- Docker Compose 配置已静态核对（服务编排、健康检查、多阶段构建），本容器内未实际执行 `docker compose up`。

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录获取令牌 |
| POST | `/api/sources` | 摄入来源（保全原始载荷） |
| GET | `/api/sources` `/api/sources/{id}` | 来源列表/详情（含提取的声明） |
| GET | `/api/identities` `/api/identities/{id}` | 身份列表/详情（含支撑声明） |
| POST | `/api/compile/{buildId}` | 运行依赖事实编译器 |
| GET | `/api/edges?buildId=` | 依赖边列表 |
| POST | `/api/build-facts/issue` | 签发构建事实（可指定截止点） |
| GET | `/api/build-facts/{id}/trace` | 全链路追溯 |
| POST | `/api/catalog/entries` `/api/catalog/entries/{id}/revise` | 目录登记/修订 |
| POST | `/api/catalog/views` | 创建下游视图 |
| POST | `/api/signatures/{id}/revoke` | 撤销签名 |
| GET | `/api/monitor/events` | 变动监测事件 |
| GET | `/api/review-events` | 复审事件 |
| POST | `/api/scenarios/{name}/run` | 运行验收场景 |

## 边界

- 不裁决许可证；漏洞通告仅保全原始载荷，不做处置。
- 不执行构建、签名、发布、分发。
- 已签发事实不可改写；修正通过新版本事实表达。
