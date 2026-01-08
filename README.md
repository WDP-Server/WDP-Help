# WDP-Help Plugin

AI-Powered Help System for WDP Minecraft Server using OpenRouter/OpenAI compatible APIs.

## Features

### 🤖 AI-Powered Responses
- OpenRouter/OpenAI compatible API integration
- Streaming responses for real-time chat display
- Custom headers for OpenRouter (X-Title: "WDP-Server")
- Configurable model, temperature, and token limits

### 💭 Smart Thinking Display
- **In-chat thinking animation** with animated dots (● ○ ○ → ○ ● ○ → ○ ○ ●)
- 50 rotating waiting messages after 5 seconds
- Tool usage messages when AI fetches extra context

### 📊 Relevance Scoring System
- AI rates each question 0-10 for relevance:
  - **10**: WDP server-specific (commands, mechanics, quests)
  - **7-9**: Server gameplay related
  - **4-6**: General Minecraft questions
  - **1-3**: Loosely related/off-topic
  - **0**: Completely unrelated
- **Configurable threshold** (default: 6) - only saves relevant questions
- Debug logging for low-relevance questions

### 📝 Conversation History
- Tracks last 5 answers per player
- Shows **title + short description** in `/help` menu
- Repeat detection - suggests using `/help` after 3 same questions
- Persistent JSON storage

### 📚 Context System
- YAML header format for context files:
  ```yaml
  ---
  title: "Context Title"
  included-by-default: true
  priority: 1
  description: "Brief description"
  ---
  
  # Markdown content below...
  ```
- **Default context** (always included): Server info, commands, skills, quests
- **Extra context** (fetchable via tools): Rogue Wanderer, base detection
- Smart context length limiting

## Commands

- `/help` - Show recent questions with short descriptions
- `/help [question]` - Ask the AI anything
- `/help reload` - Reload configuration (admin)
- `/help debug` - Show debug info (admin)

## Configuration

### config.yml

```yaml
ai:
  base-url: "https://openrouter.ai/api/v1"
  api-key: "YOUR_API_KEY_HERE"  # Required!
  model: "openai/gpt-4o-mini"
  
  openrouter:
    enabled: true
    site-url: "https://wdpserver.com"
    site-title: "WDP-Server"

context:
  history:
    count: 5
    suggest-help-after: 3
    relevance-threshold: 6  # Only save questions with score ≥ 6
```

## AI Response Format

The AI responds with metadata tags that are automatically parsed:

```
Your answer to the player's question goes here...

[SHORT: 10-word max summary]
[TITLE: 3-5 word title]
[RELEVANCE: 8]
```

**Note:** The `[SHORT:]`, `[TITLE:]`, and `[RELEVANCE:]` tags are NOT shown to players - they're metadata for the system.

## Help Menu Example

```
━━━━ WDP Help System ━━━━
Type /help [question] to ask anything!

Recent Questions:
• Random Teleport
  Use /rtp to teleport randomly
• Discord Linking
  Link account with /discord link
• Skills System
  Level skills by playing to gain abilities

━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Installation

1. Build: `mvn clean package`
2. Deploy: `./deploy.sh`
3. **Configure API key** in `plugins/WDP-Help/config.yml`
4. Restart server

## Context Files

Located in `plugins/WDP-Help/context/`:

**Default (always included):**
- `server-info.yml` - General server information
- `teleport-commands.yml` - /spawn, /rtp, /tpa, /back
- `economy-commands.yml` - SkillCoins, /pay, /balance
- `discord-integration.yml` - Discord linking
- `home-system.yml` - /sethome, /home (requires Discord)
- `communication.yml` - /msg, /reply, /mail
- `skills-system.yml` - AuraSkills overview
- `quests-system.yml` - Starter quests

**Extra (fetchable via AI tools):**
- `mechanics-wanderer.yml` - Rogue Wanderer hunt
- `mechanics-bases.yml` - Base detection system

## API Key

Get your OpenRouter API key from: https://openrouter.ai/keys

Free tier available! Recommended models:
- `openai/gpt-4o-mini` - Fast, cheap, good quality
- `google/gemini-flash-1.5` - Very fast, free tier
- `anthropic/claude-3-haiku` - Good balance

## Permissions

- `wdphelp.use` - Use /help command (default: true)
- `wdphelp.admin.reload` - Reload config (default: op)
- `wdphelp.admin.debug` - View debug info (default: op)

## Developer Notes

### Key Changes from Initial Version

1. **Thinking Display**: Changed from action bar to chat messages
2. **Relevance Scoring**: Added 0-10 scoring system with configurable threshold
3. **Help Menu**: Now shows short descriptions under each title
4. **Metadata Filtering**: [SHORT:], [TITLE:], and [RELEVANCE:] tags hidden from players

### Project Structure

```
src/main/java/com/wdp/help/
├── WDPHelpPlugin.java          # Main plugin class
├── ai/
│   └── AIService.java          # OpenRouter/OpenAI API handler
├── command/
│   └── HelpCommand.java        # /help command
├── config/
│   ├── ConfigManager.java      # Configuration
│   └── MessageManager.java     # Messages
├── context/
│   ├── ContextManager.java     # Context file loader
│   └── ContextFile.java        # Context model
├── data/
│   ├── PlayerDataManager.java  # Player history
│   ├── PlayerHelpData.java     # Player data model
│   └── HelpAnswer.java         # Answer model
└── display/
    └── ChatDisplay.java        # Streaming chat display
```

## Version

**v1.0.0** - Initial release with relevance scoring and improved UX
