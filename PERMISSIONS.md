# WDP-Help Permissions Guide

Complete permission documentation for WDP-Help - the AI-powered help system for answering player questions.

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Base Player Permissions](#base-player-permissions)
3. [Topic Access Permissions](#topic-access-permissions)
4. [Advanced Features](#advanced-features)
5. [Admin Permissions](#admin-permissions)
6. [Notification Permissions](#notification-permissions)
7. [Permission Examples](#permission-examples)
8. [Help System Explained](#help-system-explained)
9. [Best Practices](#best-practices)

---

## Overview

WDP-Help provides an AI-powered help system that answers player questions about the server. Permissions control access to help topics, features, and administrative functions.

**Default Behavior:**
- All players can ask basic questions
- Topic access can be restricted by permission
- Admins can manage help content and view analytics
- Cooldowns prevent spam

---

## Base Player Permissions

### Master Permission
```yaml
wdphelp.use
```
- **Description:** Base access to help system
- **Default:** true
- **Includes:** ask, topics, search
- **Usage:** Core permission for all players

### Individual Base Permissions

| Permission | Default | Description | Command |
|------------|---------|-------------|---------|
| `wdphelp.ask` | true | Ask questions to AI | `/help <question>` |
| `wdphelp.topics` | true | View available topics | `/help topics` |
| `wdphelp.search` | true | Search help database | `/help search <term>` |
| `wdphelp.history` | true | View your question history | `/help history` |

**Example - Basic Player Access:**
```yaml
permissions:
  - wdphelp.use
```

---

## Topic Access Permissions

Control which help topics players can access:

| Permission | Default | Description | Example Questions |
|------------|---------|-------------|-------------------|
| `wdphelp.topic.gameplay` | true | General gameplay help | "How do I craft?", "Where do I mine?" |
| `wdphelp.topic.commands` | true | Command usage help | "What commands exist?", "How do I teleport?" |
| `wdphelp.topic.economy` | true | Economy-related help | "How do I earn money?", "What are tokens?" |
| `wdphelp.topic.skills` | true | Skills/leveling help | "How do I level up?", "What skills exist?" |
| `wdphelp.topic.quests` | true | Quest-related help | "How do I start quests?", "Where are quests?" |
| `wdphelp.topic.bases` | true | Base protection help | "How do I protect my base?", "How do I trust players?" |
| `wdphelp.topic.rules` | true | Server rules/policies | "What are the rules?", "Can I use X mod?" |
| `wdphelp.topic.advanced` | true | Advanced gameplay | "What are the best strategies?", "How do I optimize?" |

### Use Cases

**Restrict advanced help to experienced players:**
```yaml
# New players
permissions:
  - wdphelp.use
  - wdphelp.topic.gameplay
  - wdphelp.topic.commands
  - -wdphelp.topic.advanced

# Veteran players
permissions:
  - wdphelp.use
  - wdphelp.topic.*
```

**Economy-only help for trading server:**
```yaml
permissions:
  - wdphelp.use
  - wdphelp.topic.economy
  - -wdphelp.topic.skills
  - -wdphelp.topic.quests
```

---

## Advanced Features

Special permissions for additional help features:

| Permission | Default | Description |
|------------|---------|-------------|
| `wdphelp.suggest` | true | Suggest new help topics |
| `wdphelp.rate` | true | Rate help responses |
| `wdphelp.cooldown.bypass` | op | Bypass help command cooldown |

### Feature Details

**Suggestions:**
```bash
# Players can suggest improvements
/help suggest "Add info about trading"

# Requires: wdphelp.suggest
```

**Rating:**
```bash
# Players can rate answers
/help rate 5  # 1-5 stars

# Requires: wdphelp.rate
```

**Cooldown Bypass:**
```yaml
# VIP players skip cooldown
permissions:
  - wdphelp.cooldown.bypass
```

---

## Admin Permissions

### Master Admin Permission
```yaml
wdphelp.admin
```
- **Description:** Full admin access
- **Default:** op
- **Includes:** All admin.* and cooldown.bypass

### Individual Admin Permissions

| Permission | Default | Description | Usage |
|------------|---------|-------------|-------|
| `wdphelp.admin.reload` | op | Reload configuration | `/help reload` |
| `wdphelp.admin.debug` | op | View debug information | `/help debug` |
| `wdphelp.admin.edit` | op | Edit help topics | `/help edit <topic>` |
| `wdphelp.admin.viewall` | op | View all player history | `/help viewall <player>` |
| `wdphelp.admin.clear` | op | Clear cache/history | `/help clear` |
| `wdphelp.admin.stats` | op | View usage statistics | `/help stats` |

**Admin Commands:**
```bash
# Reload help system
/help reload

# View statistics
/help stats

# Edit a topic
/help edit gameplay "New content..."

# View player history
/help viewall PlayerName

# Clear cache
/help clear cache
```

---

## Notification Permissions

Receive notifications about help system events:

### Master Notification
```yaml
wdphelp.notify
```
- **Description:** All help notifications
- **Default:** op

### Individual Notifications

| Permission | Default | Description |
|------------|---------|-------------|
| `wdphelp.notify.questions` | op | Notified when players ask questions |
| `wdphelp.notify.suggestions` | op | Notified when players suggest improvements |
| `wdphelp.notify.errors` | op | Notified when help system errors occur |

**Monitoring Use Case:**
```yaml
# Help desk staff
permissions:
  - wdphelp.notify.questions
  - wdphelp.notify.suggestions
  - wdphelp.admin.viewall
```

---

## Permission Examples

### Example 1: New Player (Limited Topics)
Restricted access for new players:
```yaml
permissions:
  - wdphelp.use
  - wdphelp.topic.gameplay
  - wdphelp.topic.commands
  - wdphelp.topic.rules
```

### Example 2: VIP Player
Full access with no cooldown:
```yaml
permissions:
  - wdphelp.use
  - wdphelp.topic.*
  - wdphelp.cooldown.bypass
  - wdphelp.suggest
  - wdphelp.rate
```

### Example 3: Help Desk Staff
Can view and assist but not edit:
```yaml
permissions:
  - wdphelp.admin.viewall
  - wdphelp.admin.stats
  - wdphelp.notify.questions
  - wdphelp.notify.suggestions
```

### Example 4: Senior Admin
Full control over help system:
```yaml
permissions:
  - wdphelp.admin
```

### Example 5: Content Manager
Can edit topics but not access admin features:
```yaml
permissions:
  - wdphelp.admin.edit
  - wdphelp.admin.reload
  - wdphelp.admin.stats
```

---

## Help System Explained

### How It Works

1. **Player asks question:** `/help How do I level up mining?`
2. **AI analyzes question:** Identifies topic (skills), extracts intent
3. **Permission check:** Verifies player has `wdphelp.topic.skills`
4. **Generate response:** AI creates answer from help database
5. **Send to player:** Formatted response with relevant links/commands

### Topic Categories

| Topic | What It Covers |
|-------|----------------|
| **Gameplay** | Crafting, building, survival basics |
| **Commands** | All server commands and usage |
| **Economy** | Money, trading, shops, tokens |
| **Skills** | Skill leveling, abilities, progression |
| **Quests** | Quest system, objectives, rewards |
| **Bases** | Protection, trusting, claiming |
| **Rules** | Server policies, allowed mods, behavior |
| **Advanced** | Optimization, strategies, end-game |

### Response Quality

The help system learns from:
- Player ratings (`/help rate`)
- Suggestion frequency
- Question patterns
- Admin edits

---

## Best Practices

### 1. **Grant Topics Progressively**
Give new players limited topics, expand as they progress:
```yaml
# Starter group
permissions:
  - wdphelp.topic.gameplay
  - wdphelp.topic.commands
  - wdphelp.topic.rules

# Intermediate group
permissions:
  - wdphelp.topic.*
  - -wdphelp.topic.advanced

# Advanced group
permissions:
  - wdphelp.topic.*
```

### 2. **Monitor Common Questions**
Use stats to identify gaps in help content:
```bash
/help stats

# See most asked questions
# Create new topics for common queries
```

### 3. **Encourage Player Feedback**
Enable rating for all players:
```yaml
permissions:
  - wdphelp.use
  - wdphelp.rate
  - wdphelp.suggest
```

### 4. **Set Reasonable Cooldowns**
Configure cooldown in config.yml:
```yaml
cooldown:
  enabled: true
  seconds: 30
  bypass-permission: wdphelp.cooldown.bypass
```

### 5. **Regular Content Updates**
Admins should review and update topics:
```bash
# Monthly review
/help stats
/help edit <topic> "<updated-content>"
/help reload
```

### 6. **Help Desk Integration**
Create help desk role:
```yaml
permissions:
  - wdphelp.notify.questions
  - wdphelp.admin.viewall
  - wdphelp.topic.*
```

---

## Configuration Integration

Many features are controlled by config.yml:

```yaml
# Example config
help-system:
  enabled: true
  ai-provider: "openai"  # or custom
  
  cooldown:
    enabled: true
    seconds: 30
    bypass-permission: wdphelp.cooldown.bypass
  
  topics:
    gameplay:
      enabled: true
      permission: wdphelp.topic.gameplay
    economy:
      enabled: true
      permission: wdphelp.topic.economy
  
  suggestions:
    enabled: true
    permission: wdphelp.suggest
    notify-admins: true
  
  rating:
    enabled: true
    permission: wdphelp.rate
```

---

## Troubleshooting

### Problem: "You don't have permission to ask about that topic"
**Solutions:**
1. Check if player has `wdphelp.use`
2. Verify specific topic permission (e.g., `wdphelp.topic.skills`)
3. Use `/help topics` to see available topics
4. Grant `wdphelp.topic.*` for all topics

### Problem: "You must wait before asking another question"
**Solutions:**
1. Wait for cooldown to expire
2. Grant `wdphelp.cooldown.bypass`
3. Adjust cooldown in config.yml
4. Check if cooldown is enabled

### Problem: Help system not responding
**Solutions:**
1. Verify plugin is loaded: `/plugins`
2. Check AI provider configuration
3. Use `/help debug` to see errors
4. Review console for API errors

### Problem: Admin can't edit topics
**Solutions:**
1. Grant `wdphelp.admin.edit`
2. Or grant `wdphelp.admin` for full access
3. Check command syntax: `/help edit <topic> <content>`
4. Verify write permissions on help database

---

## Need Help?

- Check [README.md](README.md) for plugin information
- Review configuration in `config.yml`
- Join our Discord: https://dsc.gg/wdp-server
- Report issues on GitHub

---

**Last Updated:** January 10, 2026
**Plugin Version:** 1.0.0+
**Document Version:** 1.0
