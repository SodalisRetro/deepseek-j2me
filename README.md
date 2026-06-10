# DeepSeek Chat for J2ME

A J2ME (MIDP 2.0 / CLDC 1.0) client for DeepSeek AI, with an intelligent
Node.js proxy providing web search, page fetching, and tool‑calling
capabilities.

Tested on [MicroEmulator](https://github.com/barteo/microemu) and
[KEmulator nnmod](https://github.com/shinovon/KEmulator). Real device
compatibility is unknown.

---

## Features

- **Chat with DeepSeek** – send messages and receive streaming-style
  responses (plain text, J2ME‑safe formatting).
- **Web Search** – model‑driven DuckDuckGo search with automatic
  `fetch_page` fallback when rate‑limited. Finds URLs, then reads
  full page content.
- **Page Fetching** – reads raw HTML from any URL. Pagination support
  (`offset` parameter) for long pages.
- **Editable System Prompt** – change the assistant's personality and
  rules directly on the device via Settings → System Prompt. Reset to
  default anytime.
- **Configurable Search** – enable/disable web search and set max search
  rounds (1–99) from settings.
- **Input History** – navigate previously sent messages with Prev/Next.
- **English & Chinese UI** – auto‑detected via device locale.
- **Docker Support** – run the proxy in a single container
  (`node:22-alpine`, ~55 MB).

---

## Architecture

```
┌─────────────┐     HTTP POST    ┌──────────────┐     HTTPS     ┌─────────────┐
│  J2ME phone │ ───────────────→ │  Node proxy   │ ───────────→ │  DeepSeek   │
│  (MIDlet)   │ ←─────────────── │  server.js    │ ←─────────── │  API        │
└─────────────┘   JSON response  │  :8080        │              └─────────────┘
                                 │               │     HTTPS
                                 │  search_page  │ ───────────→ DuckDuckGo
                                 │  fetch_page   │ ←─────────── (lite API)
                                 └──────────────┘
```

The proxy intercepts all requests before they reach DeepSeek. When
`web_search` is enabled, it injects system context (server time, tool
definitions, formatting rules) and manages a tool‑calling loop with
`search_page` (DDG) and `fetch_page` (HTML reader).

---

## Prerequisites

| Component | Requires |
|-----------|----------|
| Proxy     | Node.js ≥ 18 (or Docker) |
| J2ME build | JDK ≥ 8, Gradle (wrapper included) |
| API key   | [DeepSeek API key](https://platform.deepseek.com/api_keys) |

---

## Quick Start – Proxy

### Option A: Docker (recommended)

```bash
docker pull ghcr.io/sodalisretro/deepseek-j2me:latest
docker run -d -p 8080:8080 \
  -e DEEPSEEK_API_KEY=sk-your-key-here \
  ghcr.io/sodalisretro/deepseek-j2me:latest
```

### Option B: Node.js directly

```bash
cd proxy
set DEEPSEEK_API_KEY=sk-your-key-here   # Windows CMD
# or: export DEEPSEEK_API_KEY=sk-your-key-here  # Linux/macOS
node server.js
```

The proxy starts on `http://localhost:8080`. Use `PROXY_PORT` env var
to change the port.

### Verify the proxy

```bash
curl -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}],\"stream\":false}"
```

---

## Quick Start – J2ME App

### Build

```bash
./gradlew jar          # Linux / macOS
gradlew.bat jar        # Windows
```

Output: `build/libs/DeepSeekChat.jar` and `DeepSeekChat.jad`.

### Run in MicroEmulator

```bash
./gradlew runEmulator  # Linux / macOS
gradlew.bat runEmulator   # Windows
```

### Deploy to a phone

Transfer `DeepSeekChat.jar` and `DeepSeekChat.jad` to the phone via
Bluetooth, infrared, or data cable, then open the `.jad` file.

> **Important:** Set the proxy host in Settings to the IP of the
> machine running the proxy. If using an emulator on the same machine,
> default `localhost` works.

---

## Settings

| Setting | Default | Notes |
|---------|---------|-------|
| **Host** | `localhost` | IP of the machine running the proxy |
| **Port** | `8080` | Proxy port |
| **Web Search** | `No` | Enable `search_page` + `fetch_page` tools |
| **Max Search Rounds** | `15` | 1–99, controls tool‑calling depth |
| **System Prompt** | `You are a helpful assistant.` | Editable, stored in RMS. Reset to default available. |

All settings are persisted in J2ME RMS and survive app restarts.

---

## How Web Search Works

1. User enables **Web Search** in Settings and sends a question.
2. Proxy injects system instructions + `search_page` and `fetch_page`
   tool definitions before forwarding to DeepSeek.
3. Model calls `search_page("keyword query")` → DuckDuckGo Lite →
   returns up to 5 results with titles, snippets, and URLs.
4. Model calls `fetch_page("https://...")` on the most relevant URL →
   returns raw HTML (script/style stripped).
5. Model reads the HTML, extracts the answer, responds in plain text.

If DuckDuckGo rate‑limits (status 202), the model is instructed to skip
further searches and directly `fetch_page` well‑known URLs (e.g.
wikipedia.org, official project sites).

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DEEPSEEK_API_KEY` | *(empty)* | Your DeepSeek API key |
| `PROXY_PORT` | `8080` | HTTP port the proxy listens on |

---

## Project Structure

```
deepseek-j2me/
├── proxy/
│   └── server.js          # Node.js proxy (no npm dependencies)
├── src/main/java/.../
│   ├── DeepSeekMIDlet.java # Main MIDlet (UI, message loop)
│   ├── HttpClient.java     # HTTP POST client for J2ME
│   ├── I18n.java           # English / Chinese strings
│   ├── JsonParser.java     # Minimal streaming JSON parser
│   └── Settings.java       # RMS persistence (host, port, prompt, etc.)
├── Dockerfile
├── .dockerignore
├── build.gradle
└── gradle.properties
```

---

## License

Derived from [j2me-hello-gradle](https://gitea.bedohswe.eu.org/pixtaded/j2me-hello-gradle)
(MIT). This project is licensed under GNU LGPL-3.0-only –
Copyright (C) 2026 Sodalitas Retrospicere.
See [LICENSE](LICENSE) for full terms.
