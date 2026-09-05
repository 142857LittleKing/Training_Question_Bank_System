# 培训题库管理系统 (Training Question Bank System)

面向**培训题库统一管理与快速扩充**需求的一体化系统:

- **题目录入管理**: 单选/多选/判断/简答四类题目的手工录入与统一检索;
- **按知识点自动出题**: 对接**百度智能云千帆(文心)大模型**, 按知识点批量生成题目(含答案与解析), 未配置 API Key 时自动降级为本地模拟生成, 保证流程可演示;
- **生成—审核—上架 流程管控**: 题目经历 `草稿/AI生成 → 待审核 → 已上架/已驳回 → (可下架/重新提交)` 全生命周期, 审核过程留痕(审核记录表);
- **格式校验**: AI 出题结果、手工录入、批量导入统一经过格式校验(题干/选项/答案范围/题型规则), 不合法题目逐条给出原因;
- **批量导入**: 支持任意更换题库数据 —— 上传 `.xlsx` / `.json`(或粘贴 JSON), 可先解析 PDF 题库再导入;
- **检索使用**: 已上架题目可对外提供随机抽题/检索接口, 供培训考试、组卷等下游场景调用。

技术栈: **Spring Boot 3.5 (Java 17) + Spring Data JPA + MySQL 8 + JWT**, **Vue 3 + Vite + Element Plus + Pinia**, 中间件统一 **Docker Compose** 管理。

---

## 1. 快速开始(开发模式, 推荐先跑通)

### 1.1 前置环境

| 组件 | 版本建议 | 备注 |
| --- | --- | --- |
| JDK | 17+ | 已配置 JDK25 亦可, 本项目按 17 编译 |
| Maven | 3.8+ | |
| Node.js | 18+ | |
| MySQL | 8.x | 本地 3306 或 Docker 3307(见下) |

### 1.2 启动 MySQL(任选其一)

- 方式 A: 使用已有本地 MySQL, 执行建库(默认后端连接 `localhost:3306`):

```sql
CREATE DATABASE IF NOT EXISTS question_bank CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- 方式 B: Docker 启动(推荐, 中间件统一 docker 管理; 本机 3306 被占用, 默认映射宿主 **3307**):

```bash
# 若无法访问 Docker Hub, 可先执行: set MYSQL_IMAGE=docker.m.daocloud.io/library/mysql:8.0
docker compose up -d mysql
```

### 1.3 启动后端

```bash
cd backend
mvn -DskipTests package
java -jar target/training-question-bank-1.0.0.jar
```

连接参数可用环境变量覆盖(默认值见 `backend/src/main/resources/application.yml`):

| 环境变量 | 默认 | 说明 |
| --- | --- | --- |
| DB_HOST / DB_PORT / DB_NAME | localhost / 3307 / question_bank | MySQL 连接 |
| DB_USER / DB_PASSWORD | tqb / tqb123456 | 账号 |
| QIANFAN_API_KEY | (空) | 千帆 API Key, 配置后自动使用大模型出题 |
| QIANFAN_MODEL | ernie-4.0-turbo-8k | 模型名 |
| AI_PROVIDER | auto | auto / qianfan / mock |
| JWT_SECRET | 开发默认值 | 生产请替换(>=32字符) |

> 首次启动(空库)会自动: ①创建默认账号; ②导入内置题库 **《党建知识竞赛题库》150 道单选题**(数据来自仓库内 PDF 的解析结果 `data/seed/dangshi_150.json`), 导入即为已上架。

### 1.4 启动前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173  (已代理 /api -> localhost:8080)
```

### 1.5 默认账号

| 账号 | 密码 | 角色 | 能力 |
| --- | --- | --- | --- |
| admin | admin123 | 系统管理员 | 全部功能 |
| generator | gen123456 | 出题/录入员 | 录入、智能出题、提交审核、批量导入 |
| reviewer | rev123456 | 审核员 | 待审核队列通过/驳回、下架 |

### 1.6 一键体验“智能出题”

登录 `generator` → 菜单「智能出题」→ 选择知识点(如 党史党建)→ 选题型与数量 → 「开始生成」。
未配置千帆 Key 时为**本地模拟生成**并明确提示; 生成结果**逐题展示格式校验结论**, 一键保存 → 到「题库管理」勾选提交审核 → 换 `reviewer` 账号在「审核中心」通过/驳回 → 「已上架」题目即可在「抽题·检索使用」中被随机抽到。

---

## 2. 使用百度千帆大模型出题(国产大模型)

1. 登录 [千帆 ModelBuilder 控制台](https://console.bce.baidu.com/qianfan/overview)(需百度智能云账号并实名认证);
2. 菜单 **应用接入 / API Key管理** 创建应用并获取 **API Key**(形如 `bce-v3/ALTAK-…`);
3. 配置环境变量后重启后端:

```bash
set QIANFAN_API_KEY=你的APIKey
set QIANFAN_MODEL=ernie-4.0-turbo-8k     # 也可用 ernie-4.5-turbo-128k / ernie-speed-128k / deepseek-v3 等
```

4. 「智能出题」页顶部会显示当前出题源; 系统调用千帆 **v2 OpenAI 兼容接口**(`https://qianfan.baidubce.com/v2/chat/completions`, Bearer 鉴权), 参考文档:
   - [千帆 ModelBuilder 快速开始](https://cloud.baidu.com/doc/WENXINWORKSHOP/s/wm9cvs292)
   - [支持的模型列表](https://cloud.baidu.com/doc/WENXINWORKSHOP/s/wm7ltcvgc)
   - [API Key 管理说明](https://cloud.baidu.com/doc/qianfan/s/wmh8l6tnf)

> 说明: 模型返回内容由系统做**宽容解析 + 严格格式校验**, 单题不合法会给出具体原因且不会入库; 未解析出任何 JSON 时会返回模型原文片段便于排查。

---

## 3. 数据导入(任意更换题库)

入口: 登录后「批量导入」(角色: 录入员/管理员)。

- **Excel 模板**: 页面「下载导入模板」, 字段 = `知识点 | 题型 | 题干 | 选项A~F | 答案 | 解析 | 难度`; 题型支持 单选/多选/判断/简答;
- **JSON 文件或粘贴**: 数组结构见 `data/templates/样例导入.json`、`data/seed/dangshi_150.json`:

```json
[{"knowledgePoint":"综合示例","type":"SINGLE","stem":"题干","options":["A","B","C","D"],
  "answer":"C","analysis":"解析","difficulty":2}]
```

- 导入目标状态可选: **直接上架 / 待审核 / 草稿**(走审核流程则选“待审核”);
- 每行结果(成功/失败原因)即时反馈, 自动查重;
- **已有 PDF 题库想入库?** 若 PDF 结构与《党建知识竞赛题库》一致(题号+题干+[单选题]+`A、选项(正确答案)` 格式), 可用解析脚本一键转 JSON 后再导入:

```bash
pip install pypdf
python tools/parse_pdf.py "你的题库.pdf" data/seed/你的题库.json --kp "知识点名称"
```

---

## 4. Docker Compose 一键部署(中间件 + 前后端)

```bash
cp .env.example .env     # 按需修改
docker compose up -d --build
# 访问 http://localhost:8088 (前端, nginx 代理 /api -> backend:8080)
# MySQL 映射宿主 3307; 后端直连 8080
```

组件: `mysql:8.0`(数据卷 `tqb_mysql_data`) + `backend`(Spring Boot) + `web`(Nginx 托管前端)。
后台/私有环境拉不到镜像时: 用 `MYSQL_IMAGE=docker.m.daocloud.io/library/mysql:8.0` 覆盖镜像源, 或参考 `backend/Dockerfile`、`frontend/Dockerfile` 自行替换基础镜像。

---

## 5. 系统设计速览

### 数据库表(核心三张业务表 + 用户表)

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| knowledge_point | 知识点 | name(唯一)、category、description |
| question | 题目 | type、stem、options_json、answer、analysis、difficulty、source、status、knowledge_point_id、reviewer/comment、generate_batch |
| review_record | 审核记录 | question_id、stem_snapshot、reviewer、action(PASS/REJECT/IMPORT_PASS)、comment |
| sys_user | 用户 | username、password(BCrypt)、role |

### 状态机

```
录入 DRAFT ─┐
AI生成 GENERATED ─┤ submit ─> PENDING ─approve─> PUBLISHED(上架/可检索/可抽题)
导入 IMPORT   ─┘                  └─reject─> REJECTED(可编辑后重新 submit)
PUBLISHED ─offline─> OFFLINE(可编辑后重新 submit)
```

### 模块/目录

```
├── backend/                 # Spring Boot 后端
│   └── src/main/java/com/questionbank/
│       ├── ai/              # 出题源: 千帆 OpenAI兼容客户端 / 本地模拟器 / 宽容JSON解析
│       ├── auth/            # JWT 认证 + 角色拦截
│       ├── common/          # 统一响应/异常/枚举
│       ├── config/          # Web/CORS/初始化(默认账号+内置题库)
│       ├── controller/ service/ repository/ entity/ dto/
├── frontend/                # Vue3 + Element Plus
│   └── src/views/           # 登录/看板/题库管理/题目录入/智能出题/审核中心/审核记录/知识点/批量导入/抽题
├── docker-compose.yml       # MySQL + backend + web 统一编排
├── data/
│   ├── seed/dangshi_150.json          # 党建150题(内置种子, 可整体替换)
│   └── templates/           # 导入模板.xlsx / 样例导入.json
├── tools/                   # PDF题库解析脚本、E2E 测试脚本
├── docs/API.md              # 接口契约(前后端/下游集成)
└── .env.example
```

---

## 6. 接口测试情况

`tools/e2e_test.ps1` 提供 30 项端到端断言, 覆盖: 三角色登录、数据看板、知识点、公开抽题/检索、AI 按知识点出题(混合题型/格式校验/查重)、AI题保存、提交审核→通过→上架、驳回留痕、下架→重新上架、手工录入非法拦截、批量导入与重复拦截、详情检索, 当前 **全部通过**。

接口细节见 [docs/API.md](docs/API.md)。「抽题·检索使用」对外接口(`/api/quiz/**`)无需登录, 便于考试/组卷系统直接集成(默认仅返回已上架题目)。

---

## 7. 远程仓库

本仓库: <https://github.com/142857LittleKing/Training_Question_Bank_System.git>
