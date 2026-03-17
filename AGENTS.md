# AGENTS.md

This file provides guidance to AI coding agents (Codex, Gemini, Cursor, Windsurf, Copilot, etc.) working in this repository.

**All project instructions are maintained in the Claude Code configuration structure.** Read the files listed below to understand the project, its conventions, and task-specific rules before making any changes.

## Instructions

### Primary: Project guidance

Read [`CLAUDE.md`](CLAUDE.md) for complete project instructions, including:

- Project overview and architectural principles
- Build commands (Maven wrapper)
- Test guidelines and conventions
- Module dependency hierarchy
- Architecture and design patterns
- Code style and conventions
- Javadoc guidelines

### Contextual: Task-specific rules

Check `.claude/rules/` for task-specific rule files. Each `.md` file in that directory (and subdirectories) contains rules scoped to particular tasks or file patterns. Read any rules relevant to your current task.

### Directory-specific guidance

When working in specific directories, read the local `CLAUDE.md` for context-specific instructions:

| Directory | File | Scope |
|---|---|---|
| `docs/` | [`docs/CLAUDE.md`](docs/CLAUDE.md) | Reference guide migration (Axon 4 to 5) — terminology, style rules, verification workflow |
| `docs/af5-getting-started/` | [`docs/af5-getting-started/CLAUDE.md`](docs/af5-getting-started/CLAUDE.md) | Getting Started tutorial — content structure, writing guidelines, AsciiDoc conventions |
