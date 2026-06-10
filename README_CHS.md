# DeepSeek Chat for J2ME

适用于 J2ME（MIDP 2.0 / CLDC 1.0）的 DeepSeek AI 客户端，配合 Node.js
代理实现网页搜索、页面抓取和工具调用能力。

已在 [MicroEmulator](https://github.com/barteo/microemu) 和
[KEmulator nnmod](https://github.com/shinovon/KEmulator) 上测试。真机平台
兼容性未知。

---

## 功能特性

- **AI 对话** – 发送消息并接收流式响应（纯文本格式）。
- **联网搜索** – 模型调用 DuckDuckGo 进行网页搜索，限流时则调用 `fetch_page`
  直接访问已知网址。先找 URL，再读页面全文。
- **页面抓取** – 读取任意 URL 的 HTML 源代码。支持分段读取（`offset` 参数）
  以处理长页面。
- **可编辑系统提示词** – 通过「设置 → 系统提示词」直接在设备上修改助手的
  角色和规则。
- **搜索可配置** – 设置中可启用/禁用联网搜索并调整最大搜索轮次（1–99）。
- **输入历史** – 通过「上条/下条」导航已发送消息。
- **中英文界面** – 根据设备语言环境自动切换。
- **Docker 支持** – 支持单条命令启动代理容器。

---

## 架构

```
┌─────────────┐     HTTP POST    ┌──────────────┐     HTTPS     ┌─────────────┐
│  J2ME 手机   │ ───────────────→ │  Node 代理    │ ───────────→ │  DeepSeek   │
│  (MIDlet)   │ ←─────────────── │  server.js    │ ←─────────── │  API        │
└─────────────┘   JSON 响应      │  :8080        │              └─────────────┘
                                 │               │     HTTPS
                                 │  search_page  │ ───────────→ DuckDuckGo
                                 │  fetch_page   │ ←─────────── (lite API)
                                 └──────────────┘
```

代理拦截所有请求后注入系统上下文（服务器时间、工具定义、格式规则），并管理
`search_page`（DDG 搜索）和 `fetch_page`（HTML 阅读器）的工具调用循环。

---

## 前置

| 组件 | 依赖 |
|------|------|
| 代理服务 | Node.js ≥ 18（或 Docker） |
| J2ME 构建 | JDK ≥ 8、Gradle（包含 wrapper） |
| API 密钥 | [DeepSeek API key](https://platform.deepseek.com/api_keys) |

---

## 快速开始 – 代理

### 方案 A：Docker（推荐）

```bash
docker pull ghcr.io/sodalisretro/deepseek-j2me:latest
docker run -d -p 8080:8080 \
  -e DEEPSEEK_API_KEY=sk-your-key-here \
  ghcr.io/sodalisretro/deepseek-j2me:latest
```

### 方案 B：直接运行 Node.js

```bash
cd proxy
set DEEPSEEK_API_KEY=sk-your-key-here   # Windows CMD
# 或: export DEEPSEEK_API_KEY=sk-your-key-here  # Linux/macOS
node server.js
```

代理默认监听 `http://localhost:8080`。可通过 `PROXY_PORT` 环境变量修改端口。

### 验证代理

```bash
curl -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}],\"stream\":false}"
```

---

## 快速开始 – J2ME 应用

### 构建

```bash
./gradlew jar          # Linux / macOS
gradlew.bat jar        # Windows
```

输出：`build/libs/DeepSeekChat.jar` 和 `DeepSeekChat.jad`。

### 在 MicroEmulator 中运行

```bash
./gradlew runEmulator  # Linux / macOS
gradlew.bat runEmulator   # Windows
```

### 部署到手机

将 `DeepSeekChat.jar` 和 `DeepSeekChat.jad` 通过蓝牙、红外或数据线传输到手机，
然后打开 `.jad` 文件。

> **重要：** 在设置中将主机地址设为运行代理的机器 IP。如在本地模拟器上使用，
> 默认的 `localhost` 即可。

---

## 设置项

| 设置 | 默认值 | 说明 |
|------|--------|------|
| **主机** | `localhost` | 运行代理的机器 IP 地址 |
| **端口** | `8080` | 代理端口 |
| **联网搜索** | `否` | 启用 `search_page` + `fetch_page` 工具 |
| **最大搜索轮次** | `15` | 1–99，控制工具调用深度 |
| **系统提示词** | `You are a helpful assistant.` | 可编辑，保存在 RMS 中。可恢复默认。 |

所有设置均持久化在 J2ME RMS 中，重启应用不会丢失。

---

## 联网搜索工作原理

1. 用户在设置中开启**联网搜索**并发送问题。
2. 代理在转发给 DeepSeek 前注入系统指令、`search_page` 和 `fetch_page`
   工具定义。
3. 模型调用 `search_page("关键词")` → DuckDuckGo Lite → 返回最多 5 条结果，
   含标题、摘要和 URL。
4. 模型对最相关的 URL 调用 `fetch_page("https://...")` → 返回 HTML 源码
   （已去除 script/style）。
5. 模型阅读 HTML，提取答案，以纯文本回复。

当 DuckDuckGo 限流（状态码 202）时，模型将被引导跳过后续搜索，直接对已知网站
（如 wikipedia.org、官方项目网站）调用 `fetch_page`。

---

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DEEPSEEK_API_KEY` | *(空)* | 您的 DeepSeek API 密钥 |
| `PROXY_PORT` | `8080` | 代理监听的 HTTP 端口 |

---

## 项目结构

```
deepseek-j2me/
├── proxy/
│   └── server.js          # Node.js 代理（无 npm 依赖）
├── src/main/java/.../
│   ├── DeepSeekMIDlet.java # 主 MIDlet（界面、消息循环）
│   ├── HttpClient.java     # J2ME HTTP POST 客户端
│   ├── I18n.java           # 中英文字符串
│   ├── JsonParser.java     # 轻量流式 JSON 解析器
│   └── Settings.java       # RMS 持久化（主机、端口、提示词等）
├── Dockerfile
├── .dockerignore
├── build.gradle
└── gradle.properties
```

---

## 许可证

基于 [j2me-hello-gradle](https://gitea.bedohswe.eu.org/pixtaded/j2me-hello-gradle)
(MIT)。本项目采用 GNU LGPL-3.0-only 许可 –
Copyright (C) 2026 Sodalitas Retrospicere。
详见 [LICENSE](LICENSE)。
