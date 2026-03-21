# EuroCoder — Sovereign AI Developer Agent

[![CI](https://github.com/eurocoder-ai/euro-coder/actions/workflows/ci.yml/badge.svg)](https://github.com/eurocoder-ai/euro-coder/actions/workflows/ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/eurocoder-ai/euro-coder?label=download&color=brightgreen)](https://github.com/eurocoder-ai/euro-coder/releases/latest)
[![License: EUPL-1.2](https://img.shields.io/badge/license-EUPL--1.2-blue)](https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12)
[![Java 21](https://img.shields.io/badge/java-21-orange)](https://adoptium.net/)

A locally-running AI coding agent that gives you **Cursor / Claude Code capabilities** with full control over your AI provider. Supports **7 providers** — Mistral, Ollama, OpenAI, Anthropic, Google Gemini, xAI (Grok), and DeepSeek — with full offline support via Ollama.

**[Download latest release](https://github.com/eurocoder-ai/euro-coder/releases/latest)** — native binaries for Linux, macOS, and Windows (no Java required), or JAR for Java 21+.

## Changelog

| Version | Date | Highlights |
|---|---|---|
| **0.4.1** | 2026-03-21 | Multiline prompt support (paste + interactive), markdown rendering for agent responses |
| **0.4.0** | 2026-03-11 | Multi-provider support (7 providers), RAG with multi-hop retrieval, agent rules, beta flag, streaming, error handling |
| **0.3.0** | 2026-02-19 | Benchmarking framework (12 tasks, raw + agent modes, result persistence) |
| **0.2.0** | 2026-02-18 | Security fixes, SIGINT handling, package rename to `eu.eurocoder` |
| **0.1.0** | 2026-02-17 | Initial release — hybrid planner/coder, Mistral + Ollama, security hardening |

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
- **Security hardened** — Sandboxed execution, permission controls, and full audit logging

## Features

### Hybrid Planner/Coder Architecture

EuroCoder uses a two-model strategy for complex tasks. All agents automatically receive a project structure snapshot and git context, and have tools to explore, read, search, and modify the codebase — similar to how Claude Code or Cursor operate:

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

- **Context-aware**: Project structure and git context are auto-injected; all agents explore before acting
- **Auto mode**: Mistral Large reasons and plans, Codestral writes code
- **Single-model mode**: Use any one model for everything
- **Custom pairing**: Mix and match any planner + coder independently

### Security Hardening

EuroCoder includes three security layers to make AI-assisted coding trustworthy for professional use:

```
Tool Invocation
     │
     ▼
┌──────────────────┐     Path outside project?    ┌─────────┐
│  Sandbox Check   │ ──── yes ──────────────────► │ BLOCKED │
│ (directory scope)│                              └─────────┘
└────────┬─────────┘
         │ path OK
         ▼
┌──────────────────┐     Destructive operation?   ┌─────────────────┐
│ Permission Check │ ──── yes ──────────────────► │ Prompt User     │
│ (trust level)    │                              │ Allow? [y/N]    │
└────────┬─────────┘                              └────────┬────────┘
         │ allowed                                         │
         ▼                                                 ▼
┌──────────────────┐                              ┌─────────────────┐
│   Audit Log      │ ◄────────────────────────────│ Record Decision │
│ (JSON-Lines)     │                              └─────────────────┘
└────────┬─────────┘
         │
         ▼
    Execute Operation
```

**Sandboxed Execution** — File operations and shell commands are restricted to the project directory. The agent cannot access files outside the sandbox unless explicitly allowed.

**Permission System** — Before destructive operations (file deletion, shell commands), the agent requests explicit user approval. Three configurable trust levels:

| Trust Level | Behavior |
|---|---|
| `ask-always` | Prompt before every write and destructive operation |
| `ask-destructive` | Prompt only before destructive operations (default) |
| `trust-all` | Never prompt (for automation/scripting) |

**Audit Logging** — Every tool invocation is logged with timestamps, parameters, and outcomes (ALLOWED/DENIED) to `~/.eurocoder/audit.jsonl`. Review exactly what the agent did for compliance and debugging.

**Ctrl+C Handling** — Pressing Ctrl+C during a permission prompt denies the current operation and auto-denies all remaining tool calls for that agent turn (preventing cascading prompts). The next `ask` or `plan` command starts fresh.

**Memory Reset on Config Changes** — Changing the trust level, sandbox root, or allowed paths automatically reinitializes the agent with fresh chat memory. This prevents the agent from carrying over stale denial decisions from a previous security configuration.

### Git-Aware Context

Agents automatically receive git context including:
- Current branch name
- Working tree status (modified, added, deleted files)
- Recent commit history
- Uncommitted changes summary

This enables the agent to make informed decisions about version control and avoid conflicts.

### Benchmarking Framework

Automated evaluation of AI model performance on coding tasks. Compare models, track quality over time, and publish reproducible results.

```
euro-coder:> benchmark list

  Available Benchmark Tasks
  ============================================================

  CODE-GENERATION
    gen-fibonacci             [raw]    Generate a Fibonacci function in Java
    gen-fizzbuzz              [raw]    Generate a FizzBuzz implementation in Java
    gen-linked-list           [raw]    Generate a linked list reversal in Java
    gen-rest-endpoint         [raw]    Generate a Spring REST controller

  DEBUGGING
    debug-fix-npe             [agent]  Read a file and fix a NullPointerException
    ...

  Total: 12 tasks
```

- **Two benchmark modes**: *Raw* (prompt-to-response, tracks tokens) and *Agent* (full tool-calling pipeline with isolated sandbox)
- **Assertion types**: `response_contains`, `file_exists`, `file_contains`, `file_not_contains`, `compiles`
- **Isolated execution**: Each task runs in a fresh temp directory with a trust-all sandbox
- **Result persistence**: JSON results saved to `~/.eurocoder/benchmark-results/` for comparison and dataset publication
- **12 starter tasks** across 4 categories: code generation, refactoring, debugging, tool calling
- **Extensible**: Add new tasks by dropping JSON files into `src/main/resources/benchmarks/`

### Multi-Provider Support

| Provider | How it works | API Key? | Default Models |
|---|---|---|---|
| **Mistral** (default) | European cloud API | Yes | `mistral-large-latest` + `codestral-latest` |
| **Ollama** | 100% local / offline | No | `llama3.1` + `qwen3:4b` |
| **OpenAI** | OpenAI cloud API | Yes | `gpt-4o` |
| **Anthropic** | Anthropic cloud API | Yes | `claude-sonnet-4-20250514` |
| **Google Gemini** | Google AI cloud API | Yes | `gemini-2.0-flash` |
| **xAI (Grok)** | xAI cloud API | Yes | `grok-3` |
| **DeepSeek** | DeepSeek cloud API | Yes | `deepseek-chat` |

Switch providers anytime:

```
euro-coder:> provider openai
euro-coder:> provider anthropic
euro-coder:> provider google
euro-coder:> provider xai
euro-coder:> provider deepseek
euro-coder:> provider ollama
```

List available models from any provider:

```
euro-coder:> model list
```

### Beta Features

EuroCoder includes a beta flag system for features under active development. Once a feature is stable, it graduates out of beta and becomes always-available.

```
euro-coder:> beta

  Beta Features: DISABLED

  Currently behind beta flag:
    - Streaming Responses  (real-time token streaming, progress indicators)
    - RAG Semantic Search  (auto-indexes project, injects relevant context)

  Usage:
    beta on    — Enable beta features
    beta off   — Disable beta features
```

Enable beta to access experimental features:

```
euro-coder:> beta on
Beta features ENABLED
  Experimental features are now active. Use at your own risk.
```

### RAG — Semantic Code Search

Inspired by [Recursive Language Models](https://arxiv.org/abs/2512.24601) (Zhang et al., 2026), EuroCoder treats your codebase as an **external environment** rather than stuffing it into the prompt. When beta is enabled:

1. **Semantic indexing** — project files are embedded and stored in-memory on first use
2. **Multi-hop context injection** — the most relevant code chunks are found first, then their type/class references are extracted and used for a second retrieval pass, pulling in transitive dependencies automatically (e.g. query about "login" → finds `AuthService` → second hop finds `UserRepository` and `SessionStore`)
3. **`semanticSearch` tool** — the agent can query for additional context during execution
4. **Automatic deduplication** — second-hop results skip files already found in the first hop

This mirrors the RLM principle of "selective context access" with one level of recursive exploration, while keeping token usage optimized within a strict budget (~4000 char cap).

```
euro-coder:> rag

  RAG — Semantic Code Search

  Provider:   OpenAI
  Embeddings: supported
  Status:     INDEXED
  Files:      42
  Chunks:     187

  Commands:
    rag index              — Build/rebuild semantic index
    rag search <query>     — Search codebase by meaning
    rag clear              — Clear the index
```

**Supported embedding providers:** Mistral (`mistral-embed`), Ollama (`nomic-embed-text`), OpenAI (`text-embedding-3-small`), Google Gemini (`text-embedding-004`). Anthropic, xAI, and DeepSeek do not currently offer embedding APIs.

### Agent Rules

Define persistent instructions for the AI agent — like Cursor rules. Rules are `.md` files in `.eurocoder/rules/` within your project directory. They are injected into every agent prompt automatically.

```
euro-coder:> rules add coding-style
Rule 'coding-style' created.
  Edit it at: .eurocoder/rules/coding-style.md
  The rule is active immediately — the agent reads it on every request.
```

Example rule (`.eurocoder/rules/coding-style.md`):

```markdown
# coding-style

- Always use functional programming with streams instead of loops
- Extract constants for magic numbers and strings
- Follow Spring Boot conventions
- Write tests for every new feature
```

Manage rules:

```
euro-coder:> rules              — List all active rules
euro-coder:> rules show <name>  — Show a rule's content
euro-coder:> rules add <name>   — Create a new rule template
euro-coder:> rules remove <name> — Remove a rule
euro-coder:> rules path         — Show the rules directory
```

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
| `writeFile` | Create or overwrite files (security-checked) |
| `appendToFile` | Append to existing files (security-checked) |
| `createDirectory` | Create directories (security-checked) |
| `deleteFile` | Delete files (security-checked, requires approval) |
| `runCommand` | Execute any shell command (security-checked, requires approval) |
| `runCommandInDirectory` | Execute shell command in a specific directory (security-checked) |

## Quick Start

### Download & Run (native — no Java required)

Download the archive for your platform from the [latest release](https://github.com/eurocoder-ai/euro-coder/releases/latest):

| Platform | Archive | Run |
|---|---|---|
| **Linux x64** | `euro-coder-*-linux-x64.tar.gz` | `tar xzf euro-coder-*-linux-x64.tar.gz && ./euro-coder/bin/euro-coder` |
| **macOS arm64** | `euro-coder-*-macos-arm64.tar.gz` | `tar xzf euro-coder-*-macos-arm64.tar.gz && ./euro-coder/bin/euro-coder` |
| **Windows x64** | `euro-coder-*-windows-x64.zip` | Extract, then run `euro-coder\euro-coder.exe` |

Each archive is self-contained — it bundles a stripped JRE so you don't need Java installed.

### Download & Run (JAR — requires Java 21+)

```bash
java -jar euro-coder-*.jar
```

### Build from source

```bash
git clone https://codeberg.org/euro-coder/eurocoder.git
cd eurocoder

# Build
./mvnw clean package -DskipTests

# Run
java -jar target/sovereign-agent-0.4.1-SNAPSHOT.jar
```

> **Note:** Always use `java -jar` to run EuroCoder. Running via `mvn spring-boot:run` causes Ctrl+C to kill the entire process because Maven intercepts the signal before JLine can handle it.

On first launch, an interactive setup will guide you through:

1. **Choose provider** — Mistral, Ollama, OpenAI, Anthropic, Google Gemini, xAI, or DeepSeek
2. **Enter API key** (cloud providers only, input is masked)
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
| `ask` | Enter interactive multiline prompt (submit with empty line, cancel with Ctrl+C) |
| `plan <prompt>` | Force hybrid mode: planner analyzes, then coder executes |
| `plan` | Enter interactive multiline prompt for hybrid planning |
| `code <description>` | Direct code generation (fast, single model) |
| `code` | Enter interactive multiline prompt for code generation |

**Multiline support:** All agent commands accept multiline input. You can paste multiline text (e.g. markdown lists) directly after the command — newlines are preserved and sent as a single prompt. Alternatively, type the command with no arguments to enter an interactive multiline editor.

### Model Management

| Command | Description |
|---|---|
| `model` | Show current model configuration |
| `model list` | List all available models (live from API) |
| `model <name>` | Switch to a single model for all tasks |
| `model auto` | Reset to default auto pairing |
| `model planner <name>` | Set a custom planner model |
| `model coder <name>` | Set a custom coder model |
| `provider` | Show all available providers |
| `provider <name>` | Switch provider (`mistral`, `ollama`, `openai`, `anthropic`, `google`, `xai`, `deepseek`) |

### Security

| Command | Description |
|---|---|
| `security` | Show complete security configuration |
| `trust` | Show current trust level and options |
| `trust <level>` | Set trust level (`ask-always`, `ask-destructive`, `trust-all`) |
| `sandbox` | Show sandbox configuration |
| `sandbox on` / `sandbox off` | Enable/disable sandbox |
| `sandbox root <path>` | Set sandbox root directory |
| `sandbox allow <path>` | Add an additional allowed path |
| `sandbox reset` | Reset sandbox to defaults |
| `audit show` | Show recent audit log entries |
| `audit clear` | Clear audit log |
| `audit count` | Show number of audit entries |
| `audit path` | Show audit log file path |

### Benchmarking

| Command | Description |
|---|---|
| `benchmark list` | Show all available benchmark tasks grouped by category |
| `benchmark run` | Run all benchmark tasks against the current model |
| `benchmark run --category <cat>` | Run tasks from a specific category (e.g. `code-generation`) |
| `benchmark run --task <id>` | Run a single task by ID (e.g. `gen-fibonacci`) |
| `benchmark run --model <name>` | Run against a specific model instead of the default |
| `benchmark report` | Show the latest benchmark run results |
| `benchmark compare` | Compare results across all saved benchmark runs |

### Rules

| Command | Description |
|---|---|
| `rules` | List all active agent rules |
| `rules show <name>` | Show a rule's full content |
| `rules add <name>` | Create a new rule template file |
| `rules remove <name>` | Remove a rule |
| `rules path` | Show the rules directory path |

### Configuration

| Command | Description |
|---|---|
| `config-show` | Show current configuration (API key is masked) |
| `config-key <key>` | Set API key for the current provider |
| `config-clear` | Remove stored API key |
| `beta` | Show beta feature status |
| `beta on` / `beta off` | Enable/disable experimental features |
| `status` | Show full agent status |
| `help` | List all available commands |

## Project Structure

```
sovereign-cli/
├── src/main/java/eu/eurocoder/sovereigncli/
│   ├── SovereignCliApplication.java        # Spring Boot entry point
│   ├── agent/
│   │   ├── HybridAgentRouter.java          # Planner/Coder/Direct routing logic
│   │   ├── ModelManager.java               # Model lifecycle, provider switching
│   │   ├── GitContextProvider.java         # Git-aware context for agent prompts
│   │   ├── Provider.java                   # Provider enum (Mistral/Ollama)
│   │   ├── ModelOption.java                # Model metadata record
│   │   └── HybridResult.java              # Agent result record
│   ├── benchmark/
│   │   ├── BenchmarkTask.java              # Task definition record (loaded from JSON)
│   │   ├── BenchmarkAssertion.java         # Assertion definition record
│   │   ├── BenchmarkResult.java            # Execution result record
│   │   ├── TaskLoader.java                 # Loads tasks from classpath JSON files
│   │   ├── BenchmarkEvaluator.java         # Assertion checking engine
│   │   ├── BenchmarkRunner.java            # Orchestrates raw + agent mode execution
│   │   └── BenchmarkReport.java            # Console tables + JSON persistence
│   ├── config/
│   │   ├── ApiKeyManager.java              # Config persistence (~/.eurocoder/config.json)
│   │   ├── RuleManager.java                # Agent rules (.eurocoder/rules/*.md)
│   │   ├── TerminalConfig.java             # SIGINT, parser, bracketed paste config
│   │   └── MultilineAwareParser.java       # JLine parser preserving newlines in pastes
│   ├── security/
│   │   ├── ToolSecurityManager.java        # Central security orchestrator
│   │   ├── PermissionService.java          # User approval prompts
│   │   ├── AuditLog.java                   # JSON-Lines audit trail
│   │   ├── AuditEntry.java                 # Audit entry record
│   │   └── TrustLevel.java                 # Trust level enum
│   ├── shell/
│   │   ├── AgentCommands.java              # ask, plan, code, ls commands
│   │   ├── MarkdownRenderer.java           # Markdown-to-ANSI terminal rendering
│   │   ├── MultilineInputReader.java       # Multiline prompt input (JLine)
│   │   ├── ConfigCommands.java             # provider, model, config, beta commands
│   │   ├── RagCommands.java                # rag index/search/clear/status
│   │   ├── RulesCommands.java              # rules list/show/add/remove
│   │   ├── SecurityCommands.java           # trust, sandbox, audit, security commands
│   │   ├── BenchmarkCommands.java          # benchmark list/run/report/compare
│   │   ├── FirstRunSetup.java              # Interactive setup wizard
│   │   └── SovereignPromptProvider.java    # Custom shell prompt (euro-coder:>)
│   ├── rag/
│   │   └── RagService.java                 # Semantic indexing + retrieval (RLM-inspired)
│   └── tool/
│       └── FileSystemTools.java            # Agent tools: file I/O, shell, search, semanticSearch
├── src/main/resources/
│   ├── application.properties              # App configuration
│   ├── banner.txt                          # Custom startup banner
│   └── benchmarks/                         # Benchmark task definitions (12 JSON files)
│       ├── gen-fibonacci.json              # Code generation tasks
│       ├── debug-fix-npe.json              # Debugging tasks
│       ├── refactor-extract-method.json    # Refactoring tasks
│       └── tool-read-modify.json           # Tool-calling tasks (+ 8 more)
├── src/test/java/eu/eurocoder/sovereigncli/
│   ├── agent/
│   │   ├── HybridAgentRouterTest.java      # Routing, context, result tests
│   │   ├── ModelManagerTest.java           # Model switching, provider tests
│   │   ├── ProviderTest.java              # Provider enum, ModelOption tests
│   │   └── GitContextProviderTest.java     # Git context detection tests
│   ├── benchmark/
│   │   ├── BenchmarkEvaluatorTest.java     # Assertion checking tests (17 tests)
│   │   ├── TaskLoaderTest.java             # JSON task loading tests (11 tests)
│   │   ├── BenchmarkReportTest.java        # Report formatting + persistence (8 tests)
│   │   └── BenchmarkResultTest.java        # Result record factory tests (3 tests)
│   ├── config/
│   │   ├── ApiKeyManagerTest.java          # Config persistence tests
│   │   ├── RuleManagerTest.java            # Rule loading/saving tests
│   │   └── MultilineAwareParserTest.java  # Multiline parser tests (12 tests)
│   ├── rag/
│   │   └── RagServiceTest.java             # RAG indexing/retrieval tests
│   ├── shell/
│   │   ├── MarkdownRendererTest.java      # Markdown rendering tests (27 tests)
│   │   └── MultilineInputReaderTest.java  # Multiline input tests (7 tests)
│   ├── security/
│   │   ├── TrustLevelTest.java            # Trust level enum tests
│   │   ├── AuditLogTest.java              # Audit read/write/clear tests
│   │   ├── PermissionServiceTest.java     # Permission logic tests
│   │   └── ToolSecurityManagerTest.java   # Sandbox + permission integration tests
│   └── tool/
│       └── FileSystemToolsTest.java        # File I/O, shell, search tests
└── pom.xml                                 # Maven build (Spring Boot + LangChain4j)
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
  "ollama_base_url": "http://localhost:11434",
  "trust_level": "ask-destructive",
  "sandbox_enabled": "true",
  "beta_enabled": "false"
}
```

You can also set API keys via environment variables:

```bash
export MISTRAL_API_KEY=sk-your-key-here
export OPENAI_API_KEY=sk-your-key-here
export ANTHROPIC_API_KEY=sk-your-key-here
export GOOGLE_API_KEY=your-key-here
export XAI_API_KEY=your-key-here
export DEEPSEEK_API_KEY=sk-your-key-here
```

## Technology Stack

| Component | Technology | Why |
|---|---|---|
| Framework | [Spring Boot](https://spring.io/projects/spring-boot) 3.3 | Production-grade Java framework |
| CLI | [Spring Shell](https://spring.io/projects/spring-shell) 3.3 | Interactive terminal with history, autocomplete |
| AI Framework | [LangChain4j](https://docs.langchain4j.dev/) 1.11 | Java-native AI integration with tool calling |
| Cloud AI | [Mistral](https://mistral.ai/), [OpenAI](https://openai.com/), [Anthropic](https://anthropic.com/), [Google Gemini](https://ai.google.dev/), [xAI](https://x.ai/), [DeepSeek](https://deepseek.com/) | 6 cloud providers with live model listing |
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

| Milestone | Feature | Status |
|---|---|---|
| M1 | Hybrid Planner/Coder + Security Hardening | ✓ Done (v0.1.0) |
| M2 | Benchmarking Framework | ✓ Done (v0.3.0) |
| M3 | Multi-Provider Support (7 providers) | ✓ Done (v0.4.0) |
| M4 | Streaming Responses — real-time token streaming, progress indicators, cancel support | Beta flag |
| M5 | MCP (Model Context Protocol) — standard tool integration protocol | Planned |
| M6 | Local RAG — embed and search your codebase locally | Planned |
| M7 | IDE Integration — VS Code extension and IntelliJ plugin | Planned |
| M8 | Conversation Persistence — save and resume coding sessions | Planned |
| M9 | Native Packaging — jpackage binaries for Linux, macOS, Windows | ✓ Done (v0.5.0) |

## Changelog

### 0.4.1-SNAPSHOT (2026-03-21)

**New Features**
- Multiline prompt support for `ask`, `plan`, and `code` commands
  - **Paste support**: paste multiline text (e.g. markdown lists) directly after the command — newlines are preserved via a custom JLine parser with bracketed paste
  - **Interactive mode**: type the command with no arguments to enter a multiline editor (submit with empty line, cancel with Ctrl+C)
- Markdown rendering for agent responses — raw markdown is now converted to styled ANSI terminal output
  - Headers (`#`, `##`, `###`) rendered as bold cyan
  - Bold (`**text**`, `__text__`) rendered with ANSI bold
  - Inline code (`` `code` ``) rendered in yellow
  - Fenced code blocks (` ``` `) rendered in green with language label
  - Bullet and numbered lists with styled markers
  - Blockquotes with `│` bar, horizontal rules with `─` line
  - Streaming responses render line-by-line with full markdown support

### 0.4.0-SNAPSHOT (2026-03-11)

**New Features**
- Multi-provider support: 7 AI providers — Mistral, Ollama, OpenAI, Anthropic, Google Gemini, xAI (Grok), DeepSeek
  - Each provider has curated model suggestions and default planner/coder models
  - Live model listing from provider APIs via `model list`
  - Per-provider API key storage in `~/.eurocoder/config.json` with env var overrides
  - xAI and DeepSeek use OpenAI-compatible protocol (shared LangChain4j integration)
- RAG semantic code search (beta): inspired by [Recursive Language Models](https://arxiv.org/abs/2512.24601)
  - Auto-indexes project files using provider embedding models on first use
  - **Multi-hop retrieval**: first hop finds directly relevant chunks, then extracts type/class references and performs a second retrieval pass for transitive dependencies (e.g. query about "login" → finds `AuthService` → second hop finds `UserRepository`)
  - Automatic deduplication between hops, strict token budget (~4000 chars)
  - `semanticSearch` agent tool for iterative context refinement during execution
  - Supports Mistral, Ollama, OpenAI, Google Gemini embeddings
  - Shell commands: `rag`, `rag index`, `rag search <query>`, `rag clear`
- Agent rules: persistent instructions for the AI agent, stored in `.eurocoder/rules/*.md`
  - Rules are injected into every agent prompt automatically (like Cursor rules)
  - Shell commands: `rules`, `rules add`, `rules show`, `rules remove`
- Beta feature flag system: `beta on` / `beta off` to enable experimental features
  - Streaming responses and RAG semantic search are behind beta flag
- Updated first-run setup wizard with all 7 providers
- `config-key` now sets the API key for the currently active provider
- `provider` command shows all available providers with descriptions
- Improved exception handling for all providers (rate limits, auth, server errors)

### 0.3.0-SNAPSHOT (2026-02-19)

**New Features**
- Benchmarking framework for automated AI model evaluation on coding tasks
  - Two modes: raw (prompt-to-response with token tracking) and agent (full tool-calling pipeline)
  - 5 assertion types: `response_contains`, `file_exists`, `file_contains`, `file_not_contains`, `compiles`
  - 12 starter tasks across 4 categories: code generation, refactoring, debugging, tool calling
  - Shell commands: `benchmark list`, `benchmark run`, `benchmark report`, `benchmark compare`
  - Isolated execution in temp directories with trust-all sandbox
  - JSON result persistence for cross-model comparison

### 0.2.0-SNAPSHOT (2026-02-18)

**Security Fixes**
- Fixed permission prompts not appearing inside Spring Shell (JLine Terminal integration replaces `System.console()`)
- Fixed agent retaining stale denial decisions after security config changes (memory reset on trust/sandbox changes)
- Fixed Ctrl+C during a permission prompt causing cascading prompts and JVM crash (interrupt flag auto-denies remaining ops)
- Added SIGINT handler for graceful Ctrl+C at idle shell prompt

**Breaking Changes**
- Package renamed from `dev.aihelpcenter.sovereigncli` to `eu.eurocoder.sovereigncli`
- Maven groupId changed from `com.sovereign` to `eu.eurocoder`

**Improvements**
- Agent system prompts now instruct models to always retry tool calls after denial (prevents silent refusals from chat memory)
- Security config changes (`trust`, `sandbox`) automatically reinitialize agents with fresh chat memory

### 0.1.0-SNAPSHOT (2026-02-17)

**Initial Release**
- Hybrid Planner/Coder architecture with auto-routing
- Dual provider support: Mistral Cloud API and Ollama (local)
- File system tools: read, write, search, delete, shell commands
- Security hardening: sandbox, permission system, audit logging
- Git-aware context injection (branch, status, commits)
- Interactive first-run setup wizard
- Custom model pairing (planner + coder independently configurable)

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
