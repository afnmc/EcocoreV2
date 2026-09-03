# EcoCore

AI-driven economy engine for the Azthera Minecraft network, by Afn.

## Requirements

- Paper/Purpur, Minecraft 26.1.2+
- Java 25 (JDK 25)
- Maven 3.9+

## Building

```bash
mvn clean package
```

The shaded jar is produced at `target/EcoCore-1.0.0.jar`. Drop it into your
server's `plugins/` folder.

## Soft Dependencies

All optional - EcoCore runs standalone without any of them:

- **PlaceholderAPI** - enables `%ecocore_*%` placeholders
- **Vault** - registers EcoCore as the server's Vault economy provider
- **ItemsAdder / Oraxen / MMOItems / Slimefun** - custom items from these
  plugins can be blacklisted from trading via `blacklist.yml`

## First Run

On first startup EcoCore will:

1. Create its SQLite database (`plugins/EcoCore/ecocore.db`) and run all
   schema migrations automatically.
2. Generate every config file (`config.yml`, `shop.yml`, `prices.yml`,
   `inflation.yml`, `jobs.yml`, `minions.yml`, `discord.yml`,
   `blacklist.yml`, `database.yml`, `messages.yml`, `gui.yml`, `ai.yml`)
   with sensible defaults.
3. Seed a small default shop catalog per category (see
   `ShopCatalogLoader`) so the server isn't an empty shop on day one.
   Curate this further via direct database edits or future admin tooling.

To enable the Discord bot, set `bot.token` and `bot.guild-id` in
`discord.yml`, then run `/ecocore reload` or restart the server.

## How the AI Economy Works

`AiEconomyEngine` runs entirely locally - no external API, no internet
access required. Every `ai.yml engine.calculation-interval-seconds`
(default 300s), for every tradeable item it:

1. Gathers this cycle's feature signals (supply, demand, transaction
   volume, player count, stock level, market saturation, velocity of
   money, current inflation/deflation) via `SupplyDemandAnalyzer`,
   `MarketSaturationAnalyzer`, and `VelocityOfMoneyTracker`.
2. Loads the item's learned per-feature weight profile from
   `AiLearningModel` (adjusted over time based on how volatile the
   item's price has been - see `AiLearningModel.retrain`).
3. Computes a new price via `PriceCalculator`, bounded by
   `prices.yml`'s min/max multipliers and `ai.yml`'s max-change-per-cycle
   limits, then smooths toward that target rather than jumping straight
   to it.
4. Persists the new price, a market snapshot for trend graphs, and a
   training sample for the next retrain cycle.

`InflationEngine` runs independently (default every 900s), computing a
weighted macro-economic score from total money supply, wealth
concentration (Gini-style, via `WealthDistributionTracker`), average
balance, trading volume, and money flow, then resolves the server into
one of five states (`BOOM`, `ECONOMIC_GROWTH`, `STABLE`, `RECESSION`,
`ECONOMIC_CRISIS`) via `EconomicCycleManager`. Each state applies a
price multiplier and job-bonus multiplier, configurable per-state in
`inflation.yml`.

## Known Extension Points

- **Per-item price-change broadcasts**: `notifications.broadcast-price-changes`
  in `config.yml` is read by `NotificationManager.announcePriceChange`,
  but `AiEconomyEngine` currently writes prices straight to the database
  every cycle without calling back per item (broadcasting every price
  tick for hundreds of items would spam chat/Discord). Wire a listener
  from `AiEconomyEngine.processItem` into `NotificationManager` if you
  want live per-item broadcasts instead of on-demand (`/prices`, `/item`,
  placeholders).
- **Vanilla replanting for `FarmerMinion`/`HarvesterMinion`**: crop
  blocks are treated as immediately re-harvestable rather than tracking
  individual growth stages, keeping the minion tick loop cheap. Extend
  `MinionAiController.handleBlockBreak` if per-stage growth simulation
  is wanted.
- **Skill tree point allocation**: `JobSkillTreeManager` currently
  auto-unlocks every node once the player's level meets its requirement
  (no manual point spending). Extend `JobData` with a spent-points
  field if manual allocation is wanted.

## Commands

Admin: `/ecocore <reload|debug|restock|inflation|ai|save|backup|market|graph>`

Player: `/shop` `/sell` `/jobs` `/job <name>` `/minions` `/minion <id>`
`/market` `/prices <item>` `/inflation` `/history`

Discord slash commands (if bot enabled): `/market` `/inflation`
`/topmarket` `/history` `/restock` `/item` `/player` `/jobs` `/minions`

## Permissions

- `ecocore.admin` (default: op)
- `ecocore.shop`, `ecocore.sell`, `ecocore.jobs`, `ecocore.minions`,
  `ecocore.market` (default: true)
- `ecocore.*` (default: op) - grants everything above
