# LimboNPC

> NOTICE: VIBE CODED
> This project has been vibe coded as a small project meant for me and a few friends to use. This isn't meant to be used in big deployment.
> USE AT YOUR OWN DESCRETION
> At a glance I don't se any vulnerabilities.

Lightweight clickable server-selector NPCs for [LOOHP Limbo](https://github.com/LOOHP/Limbo) and Velocity.

A click sends a small, versioned plugin message to Velocity. The bridge validates the source, player, and destination, then runs the normal `server <name>` command as that player. It does not bypass Velocity permissions, command events, ServerPermissions, or pre-connect plugins.

## Compatibility

| Artifact suffix | Minecraft range | Limbo API baseline | Java |
|---|---|---|---|
| `26` | 26.1–26.2 | `2026.0.1-ALPHA` | 21 |
| `1.21` | 1.21–1.21.11 | `0.7.10-ALPHA` | 17+ |
| `1.20` | 1.20–1.20.6 | `0.7.5-ALPHA` | 17+ |

Velocity 3.4.x is supported. Each range is compiled against its oldest Limbo API baseline; packet IDs and spawn-packet differences are adapted at runtime.

## Build

Build every compatibility range:

```bash
./gradlew buildAllVersions
# or: ./scripts/build-all.sh
```

The six distributable jars are collected in `build/artifacts/`:

```text
LimboNPC-Velocity-26.jar
LimboNPC-Velocity-1.21.jar
LimboNPC-Velocity-1.20.jar
LimboNPC-Limbo-26.jar
LimboNPC-Limbo-1.21.jar
LimboNPC-Limbo-1.20.jar
```

To build one range only:

```bash
./gradlew clean build -PminecraftVersion=1.21
```

Install the matching Limbo and Velocity jars in the corresponding servers' `plugins` directories. On first start, Limbo creates `plugins/LimboNPC/`; Velocity creates `plugins/limbo-npc/`.

### Docker Compose

A reproducible Java 21 build environment is included:

```bash
docker compose run --rm --build build
```

Artifacts are written to the host's `build/artifacts/` directory. The named `gradle-cache` volume preserves downloaded dependencies.

### GitHub Actions

- `.github/workflows/build.yml` tests all three ranges on pushes and pull requests and uploads each artifact pair.
- `.github/workflows/release.yml` builds all six jars for `v*` tags and attaches them to the GitHub release.

## Velocity configuration

```yaml
channel: "limbo-npc:main"
trusted-limbo-servers:
  - "limbo"
debug: false
```

The backend name must exactly identify the Limbo server in Velocity's server registry. The channel must match the Limbo configuration.

## Limbo commands

```text
/limbonpc create <id> [server]
/limbonpc remove <id>
/limbonpc enable <id>
/limbonpc disable <id>
/limbonpc move <id>
/limbonpc server <id> <server>
/limbonpc name <id> <MiniMessage...>
/limbonpc skin <id> username <Minecraft name>
/limbonpc skin <id> texture <value> <signature>
/limbonpc skin <id> clear
/limbonpc hologram <id> add <MiniMessage...>
/limbonpc hologram <id> set <1-based line> <MiniMessage...>
/limbonpc hologram <id> remove <1-based line>
/limbonpc hologram <id> clear
/limbonpc info <id>
/limbonpc list
/limbonpc status
/limbonpc reload
```

Alias: `/lnpc`. IDs are lowercase and must match `[a-z0-9_-]{1,32}`. Mutations are persisted atomically to `plugins/LimboNPC/npcs.yml`.

## Permissions

Grant all administration with:

```text
limbo-npc.npc.*
```

Or grant individual actions under `limbo-npc.npc.<action>` (`create`, `remove`, `enable`, `disable`, `move`, `server`, `name`, `skin`, `hologram`, `info`, `list`, `status`, `reload`). LimboNPC explicitly checks both the wildcard and action node because Limbo permissions use exact matching.

Players need no LimboNPC permission to click NPCs. They still need the normal Velocity `/server` permission and any destination permission required by your proxy plugins.

## Security

The Velocity bridge marks matching messages handled and rejects client-originated messages, untrusted backends, malformed protocol data, mismatched player UUIDs, stale backend connections, rate-limited requests, and unknown destinations. Only a canonical registered server name can reach `CommandManager.executeAsync(player, "server " + name)`.

## Operations

The versioned protocol includes transfer acknowledgements and health probes. Limbo displays configurable timeout, unavailable-server, rate-limit, and transfer-failure messages instead of failing silently.

Velocity administration:

```text
/limbonpcvelocity status
/limbonpcvelocity reload
/limbonpcreload
```

Permission: `limbo-npc.velocity.admin`.

Both plugins expose counters through their status commands. Set `debug: true` in either plugin's `config.yml` for request IDs, routing decisions, acknowledgements, and rejection logging. Velocity also supports `rate-limit.cooldown-ms`; Limbo supports `bridge.acknowledgement-timeout-ms`. User-facing messages are configurable under `messages`.

Runtime integration smoke test:

```bash
./scripts/integration-smoke.sh
```

It boots real Limbo 26.2 and Velocity 3.5.1 instances, loads a persisted NPC, verifies protocol compatibility, and fails on plugin initialization errors.
