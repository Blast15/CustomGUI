# CustomGUI

CustomGUI is a YAML-driven Paper menu and transaction-safe recipe/exchange engine. It does not define a custom-item system: vanilla Minecraft or the configured provider remains the source of truth for every item.

## Requirements and installation

- Paper/Leaf compatible with Paper 1.21.11 through 26.2
- Java 21 for Paper 1.21.11; Java 25 for Paper 26.1+
- One Java 21-bytecode artifact for both runtimes

Copy `CustomGUI-0.4.0.jar` to `plugins/`, restart, then use `/customgui editor` or edit `plugins/CustomGUI/` and run `/customgui reload`. Invalid reloads keep the previous snapshot active.

Existing 0.1 menus remain valid. A legacy menu automatically receives close/previous/next controls and a command matching its ID. To use the fully customizable example, back up and replace the old menu file with the new `menus/upgrades.yml` resource.

## Commands and permissions

| Command | Permission | Purpose |
|---|---|---|
| `/customgui open <menu> [player] [page]` | `customgui.open`, `customgui.open.others` | Open for yourself or another online player |
| `/customgui editor` | `customgui.editor` | Open the in-game visual editor |
| `/customgui capture <id> [replace]` | `customgui.admin` | Capture the exact main-hand item as a safe `template` provider item |
| `/customgui list` | `customgui.open` | List loaded menus |
| `/customgui info` | none | Show loaded/invalid counts |
| `/customgui providers` | `customgui.admin` | Show item-provider health |
| `/customgui reload` | `customgui.admin` | Validate and atomically publish config |
| `/gui`, `/menus` | `customgui.menu.showcase` | Open the polished example dashboard |
| `/upgrades`, `/forge`, `/nangcap` | `customgui.menu.upgrades` | Open the example forge |
| `/exchange`, `/trade`, `/doido` | `customgui.menu.exchange` | Open the example exchange |

Each menu can register any unique one-word commands with `open-commands`. Use `open-commands: []` to disable them. A conflict rejects startup/reload so the active command set is never partially replaced.

The bundled example is a three-menu flow: `showcase.yml` is the dashboard, `upgrades.yml` demonstrates filtered recipes and pagination, and `exchange.yml` demonstrates a compact transaction menu. The YAML files are intentionally annotated as copyable documentation, including slot ranges, priorities, click variants, permissions, MiniMessage, custom-item providers, recipe requirements, and safe action usage.

## In-game editor

`/customgui editor` opens an administrator-only dashboard:

- **GUI menus:** create menus, edit title/rows/permissions/commands/recipe filters, place items by clicking real inventory slots, and edit every item visual/action/permission field.
- **Recipes:** create recipes, toggle/group/categorize them, and add/edit/remove individual requirement and result entries.
- **Global config:** edit batch limits, inventory-interaction policy, messages, and arbitrary configuration paths.
- **Advanced YAML path:** edit any current or future schema field with a one-line YAML value, so the visual editor does not become a compatibility ceiling.

Text and structured values are entered through a private, cancelled chat prompt. Type `cancel` or wait 120 seconds to return without applying that input. All changes remain in a per-player draft until **Lưu, validate & áp dụng** is clicked.

Saving performs these steps as one guarded workflow:

1. Reject if another admin or external process changed the source file after the draft opened.
2. Store a timestamped copy under `plugins/CustomGUI/backups/editor/`.
3. Write UTF-8 through a same-directory temporary file and atomic move when the filesystem supports it.
4. Validate every config/menu/recipe and prepare the complete dynamic command replacement.
5. Publish the new snapshot only on success; otherwise restore the previous file and reload it.

The editor intentionally rewrites the selected YAML file through Bukkit's serializer, so comments and hand formatting may be normalized. The untouched backup preserves the exact previous file.

Menu access supports:

- global `customgui.open`;
- menu `permission:` such as `customgui.menu.upgrades`;
- `customgui.bypass.<menu-id>` or `customgui.bypass.*`;
- item `view-permission` and per-click `click-permissions`.

## Custom menu schema

```yaml
id: upgrades
title: "<gradient:#A855F7:#EC4899>Nâng cấp</gradient>"
rows: 6
permission: customgui.menu.upgrades
open-commands: [upgrades, forge]

recipes:
  slots: [10-16, 19-25, 28-34]
  groups: [exchange, forge]
  categories: [vanilla]
  name: "<gold>%recipe_id%</gold>"
  lore:
    - "<gray>Nhóm: %recipe_group%</gray>"
    - "<green>Trái: 1 | Phải: tất cả</green>"
  click-actions:
    left: 1
    right: all
    shift-left: 16
    shift-right: 64

items:
  close:
    material: BARRIER
    name: "<red>Đóng</red>"
    slot: 49
    actions:
      click: ["[close]"]

  next:
    provider: itemsadder
    id: ui_next
    name: "<yellow>Trang sau</yellow>"
    lore: ["<gray>Nhấn để chuyển trang.</gray>"]
    slot: 51
    glow: true
    custom-model-data: 10
    item-flags: [HIDE_ATTRIBUTES]
    view-permission: customgui.navigation
    click-permissions:
      click: customgui.navigation
    actions:
      click: ["[next-page]"]
```

`slot` accepts one integer. `slots` accepts integers, lists, and ranges such as `0-8`. When multiple visible items use one slot, the lowest `priority` wins, matching the familiar conditional-item pattern.

Static icons and recipes support vanilla, ItemEdit server items, ItemsAdder, Oraxen, Nexo, MMOItems, EcoItems, ExecutableItems, Slimefun and its addons, MythicMobs/MythicCrucible, Nova, captured templates, or a provider registered through `CustomGuiApi`. Display options include MiniMessage/PAPI name and lore, amount, glow, custom model data, hidden tooltip, and Bukkit item flags.

Recipe placeholders: `%recipe_id%`, `%recipe_group%`, `%recipe_category%`. Static-item placeholder: `%item_id%`. PlaceholderAPI placeholders can also be used when the integration is present.

## Click actions

Actions can be assigned to `click`, `left`, `right`, `shift-left`, `shift-right`, or `middle`:

| Action | Effect |
|---|---|
| `[close]` | Close the current GUI |
| `[refresh]` | Re-render from the current snapshot |
| `[next-page]`, `[previous-page]` | Navigate the current recipe component |
| `[open-menu] <id> [page]` | Open a linked GUI with its permission check |
| `[recipe] <id> <1..max/all>` | Execute a configured recipe atomically |
| `[message] <MiniMessage>` | Send a message |
| `[player] <command>` | Run a command as the viewer |
| `[console] <command>` | Run a trusted configured command as console |

`%player%` is available in configured commands. Commands reject line breaks and menu/recipe links are resolved during reload; broken links reject the new snapshot.

## Atomic exchange modes

- `1`, `16`, `64`, or any positive value up to `security.max-batch-size` executes exactly that many copies.
- `all` finds the largest affordable batch up to the configured cap.
- All consumed requirements share one virtual inventory plan; one stack cannot pay twice.
- An item requirement with `include-shulkers: true` can count and atomically consume matching items inside Shulker Boxes carried in the player's storage inventory. Loose inventory stacks are used first; outputs are still placed in the main inventory.
- Non-consumed tool/key requirements remain base-sized rather than multiplying with the batch.
- Inputs, money, output construction, partial-stack merging, and capacity are validated before one inventory commit.
- The complete storage inventory is revalidated after external economy callbacks; conflicts never overwrite newer state.
- If inventory commit fails after withdrawal, inventory restoration and Vault refund are attempted independently. A failed or ambiguous compensation is logged with a transaction ID for manual reconciliation; Vault cannot provide distributed ACID guarantees.

The default `max-batch-size` is 256 and may be configured from 1 to 4096. Keep it near realistic inventory limits when using costly third-party item identity APIs.

## Security model

- The GUI contains previews only; ingredients always come from the player's real inventory at click time.
- GUI top slots are immutable. Shift transfer, number keys, double-click collection, drag into GUI, drop, and off-hand swaps are cancelled.
- Unsupported click types cannot trigger actions after cancellation.
- Sessions are holder-backed, UUID-bound, revisioned, and invalidated on close, quit, death, reload, and disable.
- External items are never inferred from display name, lore, model data, or base material.
- Unknown requirements/results/providers reject the transaction instead of silently falling back.

## Item integrations

| Integration | Create | Identity | Status |
|---|---:|---:|---|
| Vanilla | yes | provider-aware material | supported |
| ItemEdit | yes | official ServerStorage ID | API/source-verified; use `provider: itemedit` |
| ItemsAdder | yes | official namespaced ID | API-verified; runtime version must be staged |
| Oraxen | yes | official item ID | API-verified; runtime version must be staged |
| Nexo | yes | official item ID | API-verified; runtime version must be staged |
| MMOItems | yes | official type + ID | API-verified; runtime version must be staged |
| EcoItems | yes | official EcoItem ID | source/API-verified; runtime version must be staged |
| ExecutableItems | yes | official SCore manager identity | documentation-verified; requires SCore |
| Slimefun + addons | yes | official Slimefun item ID | source/API-verified |
| MythicMobs / MythicCrucible | yes | official Mythic item type | API-verified; Crucible items are Mythic items |
| Nova | yes | official namespaced NovaItem ID | source/API-verified; use a Nova build matching Paper |
| Template capture | yes | exact Bukkit ItemMeta similarity | built in; fallback for plugins without an API |
| Vault | n/a | balance/withdraw/refund | API-verified |
| PlaceholderAPI | n/a | placeholder parsing/comparison | API-verified |
| CrazyEnchantments | item modifier | official enchantment name + level | API/source-verified |
| Other plugins | provider-defined | provider-defined | register through Bukkit `CustomGuiApi` service |

Third-party adapters fail closed. Test the exact proprietary plugin versions used by your server before deploying paid-item recipes.

Provider IDs in YAML are `itemedit`, `itemsadder`, `oraxen`, `nexo`, `mmoitems`, `ecoitems`, `executableitems`, `slimefun`, `mythicmobs`, `mythiccrucible`, `nova`, and `template`. ItemEdit uses the `/serveritem` ID; Nova and ItemsAdder commonly require namespaced IDs; MMOItems additionally requires `item-type`.

CrazyEnchantments constraints attach to the same item requirement, so another enchanted item cannot satisfy it accidentally. The same map can decorate a generated result:

```yaml
requirements:
  - type: item
    provider: vanilla
    material: DIAMOND_SWORD
    amount: 1
    crazy-enchantments: {rage: 3}
results:
  - type: give-item
    provider: itemedit
    id: upgraded_sword
    amount: 1
    crazy-enchantments: {lifesteal: 2}
```

Configured enchantments and levels are checked during startup/reload. If CrazyEnchantments is absent, a configuration that uses `crazy-enchantments` is rejected while the previous runtime remains active. ItemEdit identity is exact by design; if you want an ItemEdit item with pre-existing custom enchants to remain identifiable, save that final enchanted variant in ItemEdit.

### Plugins that enchant or modify items

CrazyEnchantments has direct requirement/result support through `crazy-enchantments`. Other enchant plugins such as AdvancedEnchantments, ExcellentEnchants and EcoEnchants remain item modifiers rather than identity providers. For a supported custom-item provider with stable identity APIs, CustomGUI asks that provider for the underlying item ID; generated results are fresh provider items and do not copy modifiers from an input unless explicitly configured.

For an item system without a public identity API, hold the finished item and run:

```text
/customgui capture ruby_sword
```

Then use it in menus or recipes:

```yaml
provider: template
id: ruby_sword
```

Templates are stored in `plugins/CustomGUI/item-templates.yml` through an atomic write with a backup. Matching uses the complete Bukkit item metadata while ignoring stack amount, so renamed, re-enchanted, damaged, re-socketed, or otherwise changed copies will not match unless that exact variant is captured. Use `replace` explicitly to update an existing ID. This strict fallback prevents lore/name spoofing; prefer a native provider whenever one is available.

## Building

Run `gradlew.bat clean build` on Windows. Output is written to `build/libs/`.
