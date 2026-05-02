# DAVMux

> **Branch `davmux-remix`** — Termux terminal engine × DAVMux UI × AI session

DAVMux is an Android terminal app with a built-in AI coding assistant.

## Architecture

```
DAVMux
├── app/                                    ← DAVMux Android app
│   └── src/main/java/com/davmux/app/
│       ├── MainActivity.kt                 ← Upgraded: Termux PTY + AI panel
│       ├── terminal/DAVMuxTerminalClient.kt ← Termux session ↔ TerminalView bridge
│       └── ai/DAVMuxAISession.kt           ← Claude Sonnet streaming + slash commands
├── terminal-emulator/                      ← Termux VT100/xterm core (Java + C JNI)
├── terminal-view/                          ← Termux Android TerminalView widget
└── termux-shared/                          ← Shell, file, net utilities from Termux
```

## What changed from v1

| v1 (main) | v2 remix |
|---|---|
| `Runtime.getRuntime().exec()` | Full Termux PTY via `TerminalSession` |
| `TextView` output | `TerminalView` (VT100, colors, cursor, scrollback) |
| No AI | Claude Sonnet streaming panel (toggle `[AI]`) |
| Basic shell | Slash commands: `/model` `/autopilot` `/clear` `/help` |

## Branches

| Branch | Contents |
|---|---|
| `main` | DAVMux v1 — original simple terminal |
| `termux-features` | Termux source modules (reference) |
| `davmux-remix` | **This branch** — v2 with Termux engine + AI |

## Build

```bash
git clone https://github.com/cptleftnut/DAVMux -b davmux-remix
cd DAVMux
./gradlew :app:assembleDebug
```

Set `DAVMUX_API_KEY` env var or in app SharedPreferences to enable AI.

## AI Slash Commands

| Command | Action |
|---|---|
| `/model claude-opus-4` | Switch model |
| `/autopilot` | Toggle autopilot mode |
| `/clear` | Clear conversation history |
| `/help` | Show all commands |
