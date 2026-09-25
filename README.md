# EcoCore

EcoCore is the economy core for the Azthera network. It provides a configurable player economy, GUI shop, Night Market, inflation/deflation, AI-driven pricing, stock/restock management, transaction history, sell system, jobs, Discord integration, and admin controls.

## Commands
- `/shop` — player shop
- `/sell` — sell interface
- `/market` — Night Market
- `/jobs` — Jobs interface
- `/ecoadmin` — admin GUI
- `/ecocore:admin` — admin GUI namespace command

## Configuration
All major player-facing and economy settings are YAML-backed. Indonesian (`id_ID`) is the default locale and English (`en_US`) is included.

## Build
Requires Java 25 and Maven:

```bash
mvn -B clean package
```

The shaded plugin jar is produced as `target/EcoCore-1.0.0.jar`.

## Admin safety
Admin commands require the `ecocore.admin` permission. The shop editor exposes the complete configured catalog with pagination, private chat search, and price editing. The Night Market admin screen edits the active rotation rather than opening the player market.
