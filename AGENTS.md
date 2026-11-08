# AGENTS.md

MinterMC is a public repository. Every committed file is visible to users and contributors, so treat commit content as public.

## Do not commit personal or environment-specific information

Never add the following to tracked files, commit messages, screenshots, fixtures, or test resources:

- Absolute local paths, home directories, and temporary-directory paths
- Usernames, computer names, hostnames, LAN IPs, public IPs, Discord IDs, XUIDs
- Server names, ports, world names, player names, or configuration from a private environment
- Tokens, API keys, passwords, cookies, session IDs, private keys, captured login packets
- `.env` files, `server.properties`, `permissions.json`, `allowlist.json`, logs, crash dumps
- `node_modules/`, build outputs, caches, generated artifacts

Use placeholders in documentation and examples:

- `<path-to-MinterMC>`
- `127.0.0.1`
- `example.com`
- `TestBot`

## Public-facing writing

- Code comments, documentation, commit messages, and pull request descriptions are written in English.
- `README.md` is the primary English README. `README.ja.md` is the Japanese translation.
- Public documentation should contain facts, compatibility notes, reproducible steps, and legal/license facts. Do not include subjective recommendations, internal project history, or opinions about the project's quality.
- Replace environment-specific test results with generic compatibility statements.

## Repository boundaries

Tracked (public):

- Source modules, build files, and the Gradle wrapper
- `README.md`, `README.ja.md`, `CONTRIBUTING.md`, `AGENTS.md`, `docs/testing.md`
- `examples/`, `sidecar/`, and `.github/` templates

Local only (ignored by git):

- `.agents/` for audits, operational notes, and internal checklists
- Build outputs, Gradle caches, IDE settings, virtual environments, and Python bytecode
- `node_modules/`, `plugins/`, logs, `.env`, keys, and captured artifacts
- Local server configuration such as `server.properties`, `permissions.json`, and `allowlist.json`

## Keep operational documents local

Operational audits, distribution checklists, legal review notes, and environment-specific runbooks belong in the local `.agents/` directory, which is ignored by git. They are not part of the public repository.

## Before committing

1. Review the working tree:
   ```bash
   git status
   git diff --cached
   ```
2. Check for personal paths and secrets:
   ```bash
   git grep -n -I -E 'token|secret|password|privateKey|XUID|192\.168\.'
   ```
3. Confirm `.gitignore` covers build outputs, caches, `node_modules/`, `.env`, logs, and local server configuration.
4. Do not commit generated files or captured network traffic.

## If personal data was committed

Do not push it. Rewrite the history before publishing, or ask the maintainer. If the data already reached the remote, treat it as exposed and rotate any credentials.
