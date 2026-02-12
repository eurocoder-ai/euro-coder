# EuroCoder — Sovereign AI Developer Agent

A locally-running AI coding agent that gives you **Cursor / Claude Code capabilities** without sending your code to US cloud providers. Built on European AI (Mistral) with full offline support via Ollama.

```
euro-coder:> ask check my running docker containers

  -> Direct mode (codestral-latest)

Agent: Here are your currently running containers:

CONTAINER ID   IMAGE          STATUS         PORTS                    NAMES
a1b2c3d4e5f6   postgres:16    Up 3 hours     0.0.0.0:5432->5432/tcp   local-db
f6e5d4c3b2a1   redis:7        Up 3 hours     0.0.0.0:6379->6379/tcp   cache
```

## Why This Exists

AI coding assistants like GitHub Copilot, Cursor, and Claude Code are powerful — but they route all your code through US cloud infrastructure. For developers working on sensitive projects, in regulated industries, or within the EU's evolving digital sovereignty framework, that's a problem.

**EuroCoder runs on your terms:**

- **European AI** — Powered by [Mistral](https://mistral.ai/), a French AI company
- **100% offline mode** — Run models locally via [Ollama](https://ollama.com/) with zero network calls
- **Your machine, your data** — API keys stored locally with restricted file permissions, no telemetry, no tracking
- **Full agent capabilities** — File I/O, shell command execution, project scaffolding, git operations, Docker management

## Features

### Hybrid Planner/Coder Architecture

EuroCoder uses a two-model strategy for complex tasks. All agents automatically receive a project structure snapshot and have tools to explore, read, search, and modify the codebase — similar to how Claude Code or Cursor operate:

```
User Request
     │
     ▼
┌──────────┐     Complex task?    ┌───────────┐
│  Router  │ ──── yes ──────────► │ Planner   │ ── explores project, reads files,
│  + auto  │                      │ (Large)   │    creates detailed plan
│  context │                      └────┬──────┘
│  inject  │                           │ plan (with specific file paths)
│          │                      ┌────▼──────┐
│          │                      │  Coder    │ ── reads files, executes plan,
│          │                      │(Codestral)│    writes code, runs builds
│          │                      └───────────┘
│          │
│          │     Simple task?     ┌───────────┐
│          │ ──── yes ──────────► │  Direct   │ ── explores, reads, writes,
│          │                      │  Agent    │    executes — all in one
└──────────┘                      └───────────┘
```

- **Context-aware**: Project structure is auto-injected; all agents explore before acting
- **Auto mode**: Mistral Large reasons and plans, Codestral writes code
- **Single-model mode**: Use any one model for everything
- **Custom pairing**: Mix and match any planner + coder independently

### Dual Provider Support

| Provider | How it works | API Key? | Best for |
|---|---|---|---|
| **Mistral** (default) | Cloud API to Mistral's European servers | Yes | Best quality, low latency |
| **Ollama** | Runs models 100% on your machine | No | Total sovereignty, offline |

### Agent Tools

The AI agent can interact with your system through:

| Tool | Description |
|---|---|
| `getProjectTree` | Recursive tree view of the project (used automatically for context) |
| `findFiles` | Find files by glob pattern recursively (`*.java`, `pom.xml`, etc.) |
| `searchContent` | Grep-like content search across all files with line numbers |
| `listFiles` | List directory contents |
| `readFile` | Read full file contents |
| `readFileRange` | Read specific line range from large files (with line numbers) |
| `writeFile` | Create or overwrite files |
| `appendToFile` | Append to existing files |
| `createDirectory` | Create directories |
| `deleteFile` | Delete files |
| `runCommand` | Execute any shell command (git, docker, npm, curl, etc.) |
| `runCommandInDirectory` | Execute shell command in a specific directory |

## Quick Start

### Prerequisites

- **Java 21+** ([Eclipse Temurin](https://adoptium.net/) or any OpenJDK distribution)
- **Mistral API key** ([get one here](https://console.mistral.ai/api-keys)) — or Ollama for offline mode

### Build & Run

```bash
git clone https://codeberg.org/euro-coder/eurocoder.git
cd eurocoder

# Build
./mvnw clean package -DskipTests

# Run
java -jar target/sovereign-agent-0.1.0-SNAPSHOT.jar
```

On first launch, an interactive setup will guide you through:

1. **Choose provider** — Mistral Cloud or Ollama Local
2. **Enter API key** (Mistral only, input is masked)
3. **Select model** — from curated recommendations or type any model name

### Using Ollama (Offline Mode)

```bash
# Install Ollama (macOS)
brew install ollama

# Start the server
ollama serve

# Pull recommended models
ollama pull llama3.1      # Planner (4.7GB)
ollama pull qwen3:4b      # Coder (2.5GB)
```

Then choose option `[2] Ollama` during first-run setup, or switch anytime:

```
euro-coder:> provider ollama
```

## Commands

### Core

| Command | Description |
|---|---|
| `ask <prompt>` | Ask the agent anything — auto-routes between planner and coder |
| `plan <prompt>` | Force hybrid mode: planner analyzes, then coder executes |
| `code <description>` | Direct code generation (fast, single model) |

### Model Management

| Command | Description |
|---|---|
| `model` | Show current model configuration |
| `model list` | List all available models (live from API) |
| `model <name>` | Switch to a single model for all tasks |
| `model auto` | Reset to default auto pairing |
| `model planner <name>` | Set a custom planner model |
| `model coder <name>` | Set a custom coder model |
| `provider` | Show current provider |
| `provider mistral` | Switch to Mistral Cloud API |
| `provider ollama` | Switch to Ollama (local) |

### Configuration

| Command | Description |
|---|---|
| `config-show` | Show current configuration (API key is masked) |
| `config-key <key>` | Update your Mistral API key |
| `config-clear` | Remove stored API key |
| `status` | Show full agent status |
| `help` | List all available commands |

## Project Structure

```
sovereign-cli/
├── src/main/java/dev/aihelpcenter/sovereigncli/
│   ├── SovereignCliApplication.java   # Spring Boot entry point
│   ├── SovereignPromptProvider.java   # Custom shell prompt (euro-coder:>)
│   ├── FirstRunSetup.java            # Interactive setup wizard
│   ├── ApiKeyManager.java            # Config persistence (~/.eurocoder/config.json)
│   ├── ModelManager.java             # Model lifecycle, provider switching, dynamic listing
│   ├── HybridAgentRouter.java        # Planner/Coder/Direct routing logic
│   ├── FileSystemTools.java          # Agent tools: file I/O, shell, project exploration, search
│   └── AgentCommands.java            # Spring Shell command definitions
├── src/main/resources/
│   ├── application.properties         # App configuration
│   └── banner.txt                     # Custom startup banner
└── pom.xml                            # Maven build (Spring Boot + LangChain4j)
```

## Configuration

All configuration is stored in `~/.eurocoder/config.json` with owner-only file permissions:

```json
{
  "provider": "mistral",
  "mistral_api_key": "sk-...",
  "model_mode": "auto",
  "custom_planner_model": "",
  "custom_coder_model": "",
  "ollama_base_url": "http://localhost:11434"
}
```

You can also set the API key via environment variable:

```bash
export MISTRAL_API_KEY=sk-your-key-here
```

## Technology Stack

| Component | Technology | Why |
|---|---|---|
| Framework | [Spring Boot](https://spring.io/projects/spring-boot) 3.3 | Production-grade Java framework |
| CLI | [Spring Shell](https://spring.io/projects/spring-shell) 3.3 | Interactive terminal with history, autocomplete |
| AI Framework | [LangChain4j](https://docs.langchain4j.dev/) 1.11 | Java-native AI integration with tool calling |
| Cloud AI | [Mistral AI](https://mistral.ai/) | European AI provider (Paris, France) |
| Local AI | [Ollama](https://ollama.com/) | Local model runtime, no cloud dependency |
| Build | Maven + GraalVM (optional) | Standard Java build with native image option |

## Hardware Requirements

### Mistral Cloud (default)

Any machine with Java 21 and internet access. The AI runs on Mistral's servers.

### Ollama (local)

| RAM | Recommended models |
|---|---|
| 8 GB | `llama3.1` (8B) or `qwen3:4b` (4B) — one at a time |
| 16 GB | Two 8B models, or `codestral` (22B), `devstral-small` (24B) |
| 32 GB | `qwen3-coder` (30B), `command-r` (35B) |
| 64 GB+ | `llama3.1:70b` — best local quality |

Apple Silicon Macs (M1/M2/M3/M4) are particularly well-suited due to unified memory architecture.

## Roadmap

EuroCoder is a working prototype. Planned future development includes:

- **MCP (Model Context Protocol)** — Standard tool integration protocol for interoperability
- **Local RAG** — Embed and search your entire codebase locally for context-aware assistance
- **IDE Integration** — VS Code extension and IntelliJ plugin
- **Multi-provider expansion** — Hugging Face Inference API, local GGUF models
- **Native packaging** — Homebrew formula, GraalVM native binary, Docker image
- **Streaming responses** — Real-time token streaming for better UX
- **Conversation persistence** — Save and resume coding sessions

## Contributing

Contributions are welcome! Please [open an issue](https://codeberg.org/euro-coder/eurocoder/issues) to discuss proposed changes before submitting a pull request.

## Contact

- **Repository**: [codeberg.org/euro-coder/eurocoder](https://codeberg.org/euro-coder/eurocoder)
- **Email**: [contact@eurocoder.eu](mailto:contact@eurocoder.eu)

## License

This project is licensed under the **European Union Public License v1.2 (EUPL-1.2)** — see the [LICENSE](LICENSE) file for details.

The EUPL is the EU's own open source license, legally valid in all 23 EU languages and compatible with GPL, AGPL, and other major open source licenses.

## Acknowledgements

- [Mistral AI](https://mistral.ai/) — European AI models
- [LangChain4j](https://docs.langchain4j.dev/) — Java AI framework
- [Spring Shell](https://spring.io/projects/spring-shell) — Interactive CLI framework
- [Ollama](https://ollama.com/) — Local model runtime
