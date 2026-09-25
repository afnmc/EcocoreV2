# EcoCoreV2 Audit / Fix Report

Source: `EcoCoreV2-AdminGUI-PrivateSearch-AUDIT-PROGRESS.zip`

## Applied fixes

- Preserved live shop `current_price`, `stock`, and restock tracking during `shop-items.yml` reload. Static catalog fields now update without resetting runtime state.
- Admin Shop Editor now opens with the complete catalog (paginated) instead of an empty/search-only screen.
- Added `/ecoadmin` and `/ecocore:admin` admin entry points, both protected by `ecocore.admin`.
- Added an admin Night Market editor for the active rotation, including price/stock editing and manual rotation.
- Added admin Player Data lookup for online player balances.
- Explorer `DELIVER_ITEM` missions were removed from default mission pools because EcoCore has no real delivery subsystem. This prevents a mission from depending on an action that is not a genuine player activity.
- Explorer item collection now has a real `EntityPickupItemEvent` source (`COLLECT_<MATERIAL>`).
- Added breeding, sheep shearing, and cow milking job action listeners.
- Added crop planting action detection for common vanilla crops.
- Woodcutter mission mapping now recognizes log/wood/stem/hyphae break actions as `CHOP_TREE` missions.
- Removed the artificial `COLLECT_ITEM` reward triggered merely by opening a loot chest.
- Updated README so it describes EcoCore rather than the removed LagLens project.

## Existing verified structure

- LagLens/LagBomb Java sources are absent from the submitted workspace and are not referenced by EcoCore Java/resources.
- `shop-items.yml` contains 1,387 configured shop entries.
- Configured shop categories are populated: blocks, combat, decoration, farming, magic, mob-drops, music, resources, storage, misc.
- Private chat search is implemented through `PrivateChatInputManager` and cancels the public chat event.
- Shop purchase flow validates balance, stock, persists stock/payment/history, then delivers the configured item variant.
- Potion, splash potion, lingering potion, tipped arrow, and enchanted-book variant metadata remains configuration-owned and is reapplied after database loads.

## Build verification limitation

The execution environment used for this package does not have Maven installed and only has JDK 21, while this project targets Java 25. Therefore `mvn -B clean package` could not be executed here. YAML parsing and Java brace-balance checks passed.

Run the final Maven build on the Termux Debian environment with the configured Java 25 toolchain before deploying the jar.
