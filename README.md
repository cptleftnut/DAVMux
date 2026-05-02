# DAVMux v2

> Android terminal + AI  ·  Branch: `davmux-remix`

## Features

- **Full Termux package ecosystem** — bash, apt, python, git, curl, vim, ssh, and 1000+ packages via `apt install`
- **Termux terminal engine** — real PTY via TerminalSession (VT100/xterm, scrollback, colors)
- **Claude Sonnet AI panel** — streaming AI assistant, slash commands, auto-executes shell suggestions
- **DAVMux aesthetic** — #000000 / #00FF00 / monospace

## Why `applicationId = "com.termux"`?

Termux's bootstrap binaries are compiled with `/data/data/com.termux/files/usr` hardcoded.  
DAVMux uses this applicationId so the paths match and all packages work out of the box.

## First Launch

On first launch DAVMux automatically downloads and installs the Termux bootstrap (~70MB).  
After that, use `apt` or `pkg` to add any package:

```bash
apt update && apt upgrade
apt install python git nodejs vim curl ssh
```

## Build

```bash
git clone https://github.com/cptleftnut/DAVMux -b davmux-remix
cd DAVMux
# downloadBootstraps runs automatically — downloads ~4 bootstrap ZIPs
./gradlew :app:assembleDebug
```

## AI Slash Commands

| Command | Effect |
|---|---|
| `/model claude-opus-4` | Switch model |
| `/autopilot` | Toggle autopilot |
| `/clear` | Clear history |
| `/help` | Show commands |

## Branch Map

| Branch | Contents |
|---|---|
| `main` | DAVMux v1 (simple sh terminal) |
| `termux-features` | Termux source reference |
| `davmux-remix` | **DAVMux v2** (this branch) |
