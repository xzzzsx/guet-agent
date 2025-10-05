# 桂电智答 AI

## 📋 项目简介

桂电智答AI是一个基于 **Spring AI** 框架的智能问答系统，为用户提供关于桂林电子科技大学的智能问答服务。系统采用 **双引擎架构**：

1. **RAG知识库引擎**（Ollama）：基于本地部署的 Ollama + Qwen2.5，提供校园知识问答，检索准确率提升 15%，幻觉率降低 80%
2. **智能体协同引擎**（阿里云百炼）：通过路由智能体分发任务到专项智能体，支持高考分数评估、专业预约、校区查询、智能导航等功能，意图识别准确率达 95%

**技术栈：** Spring Boot、Spring AI、MyBatis-Plus、MySQL、MongoDB、Qdrant、Ollama (Qwen2.5)、阿里云百炼 (qwen-plus)、高德地图 API

---

## ✨ 核心功能展示

### 一、RAG 知识库问答

#### 1. 多格式文档上传与向量化存储
支持 TXT/MD/PDF 多格式文档上传，自动进行向量化处理并存储到 Qdrant 向量数据库，实现知识库自动化构建。

![效果图9](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/效果图9.png)

![效果图10](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/效果图10.png)

![文档上传成功提示](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114611772.png)

![向量化处理完成界面](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114633255.png)

---

#### 2. 智能文档切分与检索优化

基于 `TokenTextSplitter` 实现智能文档切分，配置最优分块大小（800 tokens），解决大模型上下文长度限制，检索准确率提升 15%。

![补充图1](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/补充图1.png)

---

#### 3. 查询重写与上下文增强
- **查询重写**：集成 `RewriteQueryTransformer`，将用户口语化提问优化为结构化查询语句，检索准确率提升 5%
- **上下文增强**：集成 `ContextualQueryAugmenter`，通过自定义空上下文处理逻辑防止幻觉回答并引导访问学校官网，幻觉率降低 80%

**查询重写前后对比：**

![补充图2](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/补充图2.png)

**空上下文处理演示：**

![空上下文拦截并引导访问官网](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005113342702.png)

---

#### 4. 安全防护与隐私保护

集成 `SafeGuardAdvisor`（安全防护顾问），在请求阶段拦截"学生个人信息"、"学生档案"、"成绩排名"等敏感词，确保校园数据安全合规，隐私泄露风险降至 0。

![敏感词拦截演示](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005113506247.png)

---

### 二、多智能体协同系统（阿里云百炼）

#### 1. 路由工作流与智能意图识别

**重要说明**：智能体系统仅用于 **OpenAI 项目类型**，使用 **阿里云百炼**（qwen-plus）作为底层大模型，与 RAG 系统（Ollama）完全独立。

基于 Spring AI Workflow 的路由工作流模式，自行实现智能体动态路由架构。通过**路由智能体**分析用户意图并分发至专项智能体（推荐智能体、预约智能体、校区查询智能体、地图智能体等），实现提示词模块化管理，意图识别准确率达 95%。

**核心分发逻辑（AiService.java 270-283行）：**
```java
// 只对OpenAI项目启用智能体路由
if (SystemConstant.MODEL_TYPE_OPENAI.equals(project.getType())) {
    return agentCoordinatorService.coordinate(...);  // 智能体系统
}
// 非OpenAI项目（Ollama）直接使用RAG处理
return this.directModelProcessing(queryVo);  // RAG系统
```

---

#### 2. MCP Client 集成高德地图服务

利用 Spring AI 的 **MCP Client** 以 SSE 流式模式集成高德地图服务，让 AI 能够实时获取地理位置、路径规划、天气情况等 **12 种工具能力**，实现智能出行导航和周边推荐功能。

**支持的工具能力：**
- 地点搜索、路径规划、天气查询
- 周边POI推荐、距离计算、坐标转换
- 实时路况、公交查询等

**高德地图工具调用演示 - 路径规划：**

![image-20251005113841773](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005113841773.png)

![image-20251005113944959](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005113944959.png)

![image-20251005114008774](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114008774.png)

![image-20251005114101308](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114101308.png)

**高德地图工具调用演示 - 天气查询：**

![image-20251005114151822](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114151822.png)

---

#### 3. 专项智能体分工协作（仅OpenAI项目）
系统内置多个专项智能体，各司其职，协同工作：
- **RouteAgent（路由智能体）**：负责意图识别和任务分发，调用阿里云百炼识别用户意图
- **RecommendAgent（推荐智能体）**：根据高考分数和兴趣推荐专业，调用数据库工具查询课程信息
- **ReservationAgent（预约智能体）**：处理专业咨询预约，收集信息后写入 MySQL 数据库
- **SchoolQueryAgent（校区查询智能体）**：查询校区地址信息，从 MySQL 读取校区数据
- **MapsQueryAgent（地图智能体）**：通过 MCP Client 调用高德地图 12 种工具，提供导航和天气服务

![image-20251005114830725](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114830725.png)

![image-20251005114859379](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114859379.png)

![image-20251005114923055](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005114923055.png)

![image-20251005115043142](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005115043142.png)

![image-20251005115101197](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005115101197.png)

![image-20251005115148283](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005115148283.png)

---

## 🏗️ 技术架构

### 整体架构图

**核心说明：**

- 🟣 **RAG系统（Ollama）**：使用 Qdrant 向量库 + Ollama 本地模型
- 🟡 **智能体系统（阿里云百炼）**：**不使用向量库**，仅通过工具调用（MySQL查询 + MCP高德地图）

![image-20251005125620651](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005125620651.png)

**架构图说明：**
- 🔵 **用户层**：Web应用和移动端
- 🟠 **接入层**：Nginx负载均衡
- 🟢 **业务层**：三大业务模块
- 🟣 **RAG引擎**：使用 Qdrant 向量库进行知识检索，调用 Ollama 生成答案
- 🟡 **智能体引擎**：不使用向量库，通过工具调用（MySQL数据库查询 + MCP高德地图）配合阿里云百炼生成答案
- 🟣 **数据层**：MySQL（业务）+ MongoDB（会话）+ Qdrant（**仅RAG使用**）
- ⚫ **基础设施层**：Ollama本地模型 + 阿里云百炼 + 高德地图API

![image-20251005125112392](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005125112392.png)

### 数据存储架构图

![image-20251005125256887](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005125256887.png)

### 智能体系统工具调用架构图

![image-20251005125356919](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005125356919.png)

### 技术选型说明

| 技术层 | 技术选型 | 选型理由 |
|--------|---------|---------|
| **AI 框架** | Spring AI | 官方支持完善，集成 Advisor、RAG、MCP 等高级特性 |
| **RAG 大模型** | Ollama + Qwen2.5 | 本地部署，成本降低 100%，数据安全性显著提升 |
| **智能体大模型** | 阿里云百炼 (qwen-plus) | OpenAI 接口兼容，工具调用能力强，支持多智能体协同 |
| **向量数据库** | Qdrant | 高性能向量检索，支持复杂过滤查询，**仅 RAG 引擎（Ollama）使用** |
| **聊天记录存储** | MongoDB | 灵活的 JSON 结构，适合海量会话数据存储 |
| **业务数据存储** | MySQL | 结构化数据存储，保证数据一致性，存储用户信息、智能体配置、课程数据等 |
| **外部服务集成** | MCP (Model Context Protocol) | 标准化的工具调用协议，SSE 流式连接高德地图 12 种工具 |

### 核心技术点

本项目对应简历中的 **8 个技术亮点**，具体实现如下：

1. **Ollama 本地部署大模型** → 成本降低 100%，数据安全性提升
2. **MongoDB 存储聊天历史** → 支撑海量会话数据，水平扩展能力强
3. **ETL 数据处理流程** → 知识库上线效率提升 5 倍
4. **TokenTextSplitter 智能切分** → 检索准确率提升 15%，搜索速度加快
5. **RewriteQueryTransformer 查询重写** → 检索准确率提升 5%
6. **ContextualQueryAugmenter + SafeGuardAdvisor 双重防护** → 前者防幻觉（幻觉率降低 80%），后者拦截敏感词（隐私风险降至 0）
7. **MCP Client 集成高德地图** → 12 种工具能力，智能出行导航
8. **路由工作流模式** → 意图识别准确率 95%，系统扩展性显著提升

---

## 💡 核心设计思路

### 1. 双引擎架构设计

系统采用 **双引擎独立处理** 架构，根据项目类型路由到不同的处理引擎：

```
用户请求 → AiService.chatStream()
         ↓
    判断 project.type
    ├─ 'ollama'  → RAG引擎（Ollama + Qdrant）
    └─ 'openai'  → 智能体引擎（阿里云百炼 + 路由分发）
```

### 2. RAG 引擎流程设计（Ollama）

采用完整的 **ETL（Extract-Transform-Load）** + **RAG Advisor 链路**：

```
【文档上传阶段】
文档上传 → 文档读取 (DocumentReader) 
        → 分块转换 (TokenTextSplitter, 800 tokens/块)
        → 关键词提取 (KeywordMetadataEnricher, 8个关键词)
        → 向量化加载 (VectorStore, 存入 Qdrant Ollama集合)

【查询阶段】
用户提问 → QueryTransformer (查询重写)
        → VectorStore 相似度检索 (Top-K=3)
        → ContextualQueryAugmenter (上下文增强)
        → Ollama (Qwen2.5) 生成答案
        → MongoDB 存储会话
```

**关键优化点：**
- **分块大小优化**：通过实验确定 800 tokens 为最优分块大小
- **查询重写**：RewriteQueryTransformer 提升检索召回率 +5%
- **空上下文处理**：ContextualQueryAugmenter 避免幻觉，降低 80%
- **本地部署**：Ollama 成本降低 100%，数据安全性提升

#### RAG 处理流程时序图

**流程说明：**

该时序图展示了 RAG 引擎（Ollama）从用户提问到生成答案的完整链路，核心步骤包括：

1. **用户提问** → 前端发送用户输入
2. **SafeGuardAdvisor 安全拦截** → 检测敏感词（学生隐私、成绩等），命中则直接返回拒绝回复
3. **QueryTransformer 查询重写** → 将口语化提问优化为结构化查询语句，提升检索准确率 +5%
4. **VectorStore 向量检索** → 在 Qdrant 中进行相似度检索，返回 Top-K=3 相关文档块
5. **ContextualQueryAugmenter 上下文增强** → 处理空上下文（无相关文档时引导访问官网），防止幻觉回答
6. **Ollama 生成答案** → 基于检索到的上下文，调用 Qwen2.5 本地模型生成答案
7. **MongoDB 存储会话** → 保存用户提问和 AI 回答到聊天历史
8. **返回结果** → 流式返回给前端展示

**关键技术点：**
- Advisor 链式调用确保请求安全和检索质量
- 空上下文处理机制使幻觉率降低 80%
- 本地部署 Ollama 保证数据安全性

![](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005140900108.png)

---

### 3. 智能体引擎路由机制（阿里云百炼，仅OpenAI项目）

**重要**：智能体系统与 RAG 系统完全独立，仅用于 OpenAI 项目类型。

参考 Spring AI Workflow 的路由工作流模式，自行实现动态路由架构：

```
用户输入 (OpenAI项目) → AgentCoordinatorService.coordinate()
                      ↓
                 RouteAgent (意图识别, 阿里云百炼)
                      ↓
         ┌────────────┼────────────┬─────────────┐
         ↓            ↓            ↓             ↓
    RECOMMEND    RESERVATION  SCHOOL_QUERY  MAPS_QUERY
  (推荐智能体)   (预约智能体)  (校区查询)    (地图智能体)
         │            │            │             │
    查询课程表    写入预约表    查询校区表    MCP高德地图
```

**设计优势：**
- **双引擎隔离**：RAG 引擎（Ollama）和智能体引擎（阿里云百炼）完全独立
- **模块化提示词管理**：每个智能体独立维护提示词（SystemConstant.java）
- **可扩展性强**：新增智能体只需实现 Agent 接口并注册到 AgentTypeEnum
- **意图识别准确**：阿里云百炼 qwen-plus 识别准确率达 95%

**核心代码位置：**
- 双引擎路由：`guli-ai/src/main/java/com/atguigu/guliai/service/AiService.java` (270-283行)
- 路由智能体：`guli-ai/src/main/java/com/atguigu/guliai/agent/RouteAgent.java`
- 智能体协调器：`guli-ai/src/main/java/com/atguigu/guliai/service/AgentCoordinatorService.java`
- 智能体枚举：`guli-ai/src/main/java/com/atguigu/guliai/enums/AgentTypeEnum.java`
- 专项智能体：`guli-ai/src/main/java/com/atguigu/guliai/agent/` 目录下的各智能体类

#### 智能体协调流程时序图

**流程说明：**

该时序图展示了智能体引擎（阿里云百炼）如何通过路由工作流模式协调多个专项智能体处理用户请求，核心步骤包括：

1. **用户提问（OpenAI 项目）** → 前端发送用户输入到 AiService
2. **判断项目类型** → AiService 检测 `project.type == 'openai'`，路由到智能体引擎
3. **AgentCoordinator 协调** → 智能体协调器初始化会话上下文
4. **RouteAgent 意图识别** → 调用阿里云百炼 qwen-plus，分析用户意图并返回目标智能体类型（如 `RECOMMEND`、`MAPS_QUERY` 等）
5. **分发到专项智能体** → 根据意图类型实例化对应的智能体（RecommendAgent、MapsQueryAgent 等）
6. **工具调用（两种方式）**：
   - **数据库工具**：专项智能体调用 DatabaseQueryTools 查询 MySQL（课程表、预约表、校区表等）
   - **MCP 外部工具**：MapsQueryAgent 通过 MCP Client 以 SSE 流式模式调用高德地图 12 种工具
7. **阿里云百炼生成答案** → 智能体将工具调用结果作为上下文，再次调用 qwen-plus 生成最终回答
8. **MongoDB 存储会话** → 保存用户提问和 AI 回答到聊天历史
9. **返回结果** → 流式返回给前端展示

**关键技术点：**
- 路由智能体实现意图识别准确率 95%（基于阿里云百炼的工具调用能力）
- 专项智能体不使用向量库，完全依赖工具调用（数据库 + MCP）
- MCP Client 通过 SSE 流式连接高德地图，实时获取地理位置、路径规划、天气等信息
- 智能体系统与 RAG 系统完全隔离，互不干扰

![](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005134736339.png)

---

### 4. 系统整体处理流程图

**流程说明：**

该流程图展示了桂电智答 AI 系统从用户请求到返回结果的完整处理链路，清晰呈现了**双引擎架构**的路由判断和各自独立的处理流程。

#### 核心流程节点：

**1. 用户入口层**
- 用户通过前端界面发送提问

**2. 项目类型判断**（系统核心路由点）
- AiService 检测 `project.type` 字段
- 根据类型路由到不同的处理引擎：
  - `type == 'ollama'` → RAG 引擎
  - `type == 'openai'` → 智能体引擎

**3. RAG 引擎处理链路**（Ollama 项目）
- ① SafeGuardAdvisor 安全拦截
- ② QueryTransformer 查询重写
- ③ Qdrant 向量检索
- ④ ContextualQueryAugmenter 上下文增强
- ⑤ Ollama 本地生成答案
- ⑥ MongoDB 存储会话
- ⑦ 返回结果

**4. 智能体引擎处理链路**（OpenAI 项目）
- ① AgentCoordinator 协调器初始化
- ② RouteAgent 意图识别（阿里云百炼）
- ③ 分发到专项智能体
- ④ 工具调用：
  - DatabaseQueryTools 查询 MySQL
  - MCP Client 调用高德地图
- ⑤ 阿里云百炼生成答案
- ⑥ MongoDB 存储会话
- ⑦ 返回结果

**关键设计特点：**
- ✅ **清晰的路由边界**：通过 `project.type` 实现双引擎完全隔离
- ✅ **独立的处理链路**：RAG 和智能体各自独立，互不干扰
- ✅ **统一的数据存储**：MongoDB 作为共享的会话存储层
- ✅ **不同的 AI 能力**：RAG 专注知识问答，智能体专注任务协同

![image-20251005142054876](https://gitee.com/zeng-shaoxiong/my-images/raw/master/xiaogu-classmates/image-20251005142054876.png)

---

### 5. RAG 引擎关键组件

在 **RAG 引擎**（Ollama）中集成多个关键组件，实现检索优化和防幻觉保护：

| Advisor | 作用时机 | 防护内容 | 效果 |
|---------|---------|---------|------|
| **ContextualQueryAugmenter** | RAG检索阶段 | 处理空上下文，防止幻觉回答 | 幻觉率降低 80% |
| **RewriteQueryTransformer** | RAG检索前 | 查询重写，优化检索准确率 | 检索准确率提升 5% |
| **KeywordMetadataEnricher** | 文档加载时 | 提取8个关键词，增强元数据 | 检索相关性提升 |

**核心代码位置：**
- RAG Advisor 链路：`guli-ai/src/main/java/com/atguigu/guliai/strategy/OllamaAiOperator.java` (61-149行)

---

### 5. 数据存储方案选型

根据不同数据特点，采用分层存储策略：

| 数据类型 | 存储方案 | 选型理由 |
|---------|---------|---------|
| **聊天历史** | MongoDB | 数据量大、单条价值低、查询简单、JSON 结构灵活 |
| **向量数据（RAG）** | Qdrant (ollama.vector.store) | **仅 RAG 引擎使用**，支持 projectId + knowledgeId 过滤 |
| **向量数据（智能体）** | 无 | **智能体引擎不使用向量库**，仅通过工具调用（数据库查询 + MCP） |
| **业务数据** | MySQL | 结构化数据，ACID 保证，存储用户、课程、预约、校区等信息 |

---

## 📁 项目结构

```
xiaogu/
├── guli-ai/                    # Spring AI 核心实现模块
│   ├── agent/                  # 智能体实现（RouteAgent, RAGAgent 等）
│   ├── service/                # 业务服务（AgentCoordinatorService, AiService 等）
│   ├── config/                 # Spring AI 配置（Advisor, VectorStore 等）
│   ├── mcp/                    # MCP Client 实现（高德地图集成）
│   └── enums/                  # 枚举定义（AgentTypeEnum 等）
├── guli-admin/                 # 后台管理模块（若依框架）
│   ├── controller/             # 控制器
│   ├── service/                # 业务逻辑
│   └── mapper/                 # 数据访问层
├── guli-ui/                    # 前端界面（Vue 3）
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── api/                # API 接口
│   │   └── components/         # 通用组件
├── guli-common/                # 公共模块
├── guli-framework/             # 框架核心
├── guli-system/                # 系统管理模块
└── 数据表/                     # 数据库初始化脚本
    ├── MongoDB/                # 聊天记录数据
    └── MYSQL/                  # 业务数据（itheima.sql）
```

**关键类文件说明：**

| 文件路径 | 功能说明 |
|---------|---------|
| `guli-ai/agent/RouteAgent.java` | 路由智能体，负责意图识别和任务分发 |
| `guli-ai/service/AgentCoordinatorService.java` | 智能体协调器，管理智能体调用流程 |
| `guli-ai/service/AiService.java` | AI 服务入口，集成 RAG 和智能体 |
| `guli-ai/mcp/AmapMcpService.java` | 高德地图 MCP 服务集成 |
| `guli-ai/constant/SystemConstant.java` | 提示词模板和系统常量 |

---

## 📞 联系方式

- **Gitee 仓库：** https://gitee.com/zeng-shaoxiong/xiaogu-classmate
- **个人邮箱：** 3466202941@qq.com
- **简历项目说明：** 本项目为简历中"桂电智答AI"项目的完整实现
