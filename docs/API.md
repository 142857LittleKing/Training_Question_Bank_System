# 培训题库系统 - 后端接口约定 (v1)

- Base URL: `http://localhost:8080/api`
- 统一响应包装: `{"code":0,"message":"ok","data":{...}}`, `code != 0` 为业务失败。
  HTTP 状态码: 400 参数/业务错误, 401 未登录, 403 无权限, 404 不存在, 500 系统错误。
- 除标注【公开】接口外, 均需请求头 `Authorization: Bearer <token>`(登录接口返回)。
- 分页参数: `page` 从 0 开始, `size` 默认 10, 最大 100。分页响应: `{"list":[],"total":0,"page":0,"size":10}`。

## 枚举字典

| 枚举 | 值(存库/传参) | 显示标签 |
| --- | --- | --- |
| 题型 QuestionType | SINGLE / MULTIPLE / JUDGE / SHORT | 单选题 / 多选题 / 判断题 / 简答题 |
| 状态 QuestionStatus | DRAFT / GENERATED / PENDING / PUBLISHED / REJECTED / OFFLINE | 草稿 / AI生成待处理 / 待审核 / 已上架 / 已驳回 / 已下架 |
| 来源 QuestionSource | MANUAL / AI / IMPORT | 手工录入 / 大模型生成 / 批量导入 |
| 角色 UserRole | ADMIN / GENERATOR / REVIEWER | 系统管理员 / 出题录入员 / 审核员 |
| 审核动作 ReviewAction | PASS / REJECT / IMPORT_PASS | 审核通过 / 审核驳回 / 批量导入自动通过 |

流程: 手工录入→DRAFT / AI生成→GENERATED / 导入可选状态 → 提交审核 submit → PENDING → approve → PUBLISHED(上架)
或 reject → REJECTED(可改后重新提交); PUBLISHED 可 offline → OFFLINE。

## 1. 认证 Auth

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| POST | /auth/login | 公开 | 登录, body {username,password} → {token,id,username,displayName,role,roleLabel} |
| GET | /auth/me | 登录 | 当前用户 → {id,username,displayName,role,roleLabel,enabled,createdAt} |
| PUT | /auth/password | 登录 | 修改密码 {oldPassword,newPassword} |
| GET | /auth/users | ADMIN | 用户列表 |

默认账号: admin/admin123(GENERATOR+ADMIN 权限最大), generator/gen123456(出题录入), reviewer/rev123456(审核)。

## 2. 知识点 KnowledgePoint

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | /knowledge-points?keyword=&page=&size= | 登录 | 分页(含 questionCount) |
| GET | /knowledge-points/all | 登录 | 全量下拉(含 questionCount) |
| POST | /knowledge-points {name,category,description} | GENERATOR/ADMIN | 新增 |
| PUT | /knowledge-points/{id} | GENERATOR/ADMIN | 修改 |
| DELETE | /knowledge-points/{id} | GENERATOR/ADMIN | 删除(有题目时拒绝) |

## 3. 题目 Question (题库检索/管理)

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | /questions?keyword=&kpId=&type=&status=&source=&mine=&page=&size= | 登录 | 检索 |
| GET | /questions/{id} | 登录 | 详情(含审核意见等) |
| POST | /questions | GENERATOR/ADMIN | 手工录入→DRAFT, body 见题目载荷 |
| PUT | /questions/{id} | GENERATOR/ADMIN | 编辑(上架/待审核不可编辑) |
| DELETE | /questions/{id} | 见权限 | 管理员任意; 录入员仅本人草稿等 |
| POST | /questions/batch-delete | GENERATOR/ADMIN | 批量删除 body {ids:[1,2]} → {requested,deleted,items:[{id,deleted,reason}]}; 逐条校验可部分成功(管理员任意, 录入员仅本人且非上架/待审核), 单次≤500 |
| POST | /questions/{id}/submit | GENERATOR/ADMIN | 提交审核 DRAFT/GENERATED/REJECTED→PENDING |
| POST | /questions/{id}/offline | ADMIN/REVIEWER | 下架 PUBLISHED→OFFLINE |

题目载荷 QuestionPayload(录入/生成/导入三端统一, 服务端做格式校验):
```
{
  "type": "SINGLE|MULTIPLE|JUDGE|SHORT",
  "stem": "题干",
  "options": ["A内容","B内容","C内容","D内容"],   // 选择/多选必填; 判断/简答为 null
  "answer": "B" | "A,C" | "对"/"错" | "参考答案文本",
  "analysis": "解析(可选)",
  "difficulty": 3,                 // 1-5
  "knowledgePointId": 1,           // 二选一
  "knowledgePointName": "党史党建"  // 二选一(不存在时自动创建)
}
```
返回视图 QuestionView:
```
{id,type,typeLabel,status,statusLabel,source,sourceLabel,stem,options:[],answer,analysis,difficulty,
 knowledgePointId,knowledgePointName,knowledgePointCategory,createdBy,reviewerName,reviewComment,
 submittedAt,publishedAt,createdAt,updatedAt}
```

## 4. 审核 Review

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| POST | /review/{questionId}/approve | REVIEWER/ADMIN | body {comment?} → 上架, 写审核记录 PASS |
| POST | /review/{questionId}/reject | REVIEWER/ADMIN | body {comment:必填} → 驳回, 写审核记录 REJECT |
| GET | /review-records?questionId=&action=&reviewerName=&page=&size= | 登录 | 审核记录分页 |
| GET | /review-records/question/{questionId} | 登录 | 单题审核历史 |

## 5. AI 智能出题 (千帆大模型 / 本地模拟)

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | /ai/provider | 登录 | 当前出题源: {name,label,requiresApiKey,apiKeyConfigured,model,modeTip} |
| POST | /ai/generate | GENERATOR/ADMIN | body 见下; 返回逐题格式校验结果(不落库) |
| POST | /ai/save | GENERATOR/ADMIN | body {questions:[载荷],batchLabel?}; 保存通过校验的题为 GENERATED |

生成请求:
```
{ "knowledgePointId": 1, "type": "SINGLE",   // 缺省=四题型均衡
  "count": 5, "difficulty": 3, "extraInstruction": "围绕建党历史出题" }
```
生成结果:
```
{provider,providerLabel,modeTip,requested,validCount,
 items:[{index,valid:true/false,reasons:[校验失败原因],duplicate:bool,
         question:{type,stem,options,answer,analysis,difficulty,knowledgePointId,knowledgePointName}}]}
```
保存结果: {requested,saved,skippedInvalid,skippedDuplicate,batch}

## 6. 批量导入 Import (支持任意更换题库)

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | /import/template | GENERATOR/ADMIN | 下载 xlsx 模板 |
| POST | /import | GENERATOR/ADMIN | multipart: file(.xlsx/.json), defaultStatus(PUBLISHED/PENDING/DRAFT), kpName(整体覆盖知识点,可选) |
| POST | /import/json | GENERATOR/ADMIN | body {content: JSON文本, defaultStatus?} |

JSON 文件结构(数组):
```
[{"knowledgePoint":"党史党建","type":"SINGLE","stem":"...","options":[...],
  "answer":"D","analysis":"...","difficulty":3}]
```
结果: {fileName,totalRows,success,failed,defaultStatus,rows:[{rowNo,success,message,questionId}]}

## 7. 检索使用 Quiz 【公开, 无需登录】(培训考试/组卷集成)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /quiz/random?type=&kpId=&count=10 | 已上架题目随机抽题(≤100) |
| GET | /quiz/search?keyword=&kpId=&type=&page=&size= | 已上架题目检索 |

## 8. 首页统计

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| GET | /dashboard/summary | 登录 | {total,totalPublished,pendingReview,byStatus,byType,bySource,topKnowledgePoints,recent} |
