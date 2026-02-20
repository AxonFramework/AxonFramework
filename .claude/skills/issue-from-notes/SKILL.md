---
name: issue-from-notes
description: >
  Create well-structured GitHub issues from any source: meeting notes, transcriptions, conversation
  context, informal descriptions, or direct requests. Use when the user says "create a github issue",
  "create issue from notes", "write up an issue", "turn these notes into an issue", "file a bug",
  "file a bug from meeting notes", "create enhancement from discussion", "make an issue for",
  "open an issue about", "create a follow-up issue", or any variation of creating a structured
  GitHub issue — whether the input comes from notes, conversation context, or a direct description.
---

# Issue From Notes

Transform unstructured meeting notes, transcriptions, or descriptions into well-structured GitHub
issue bodies matching the project's issue templates.

## Workflow

### 1. Determine Issue Type

Based on the input, classify as one of:
- **Bug report** - something broken or not working as expected
- **Enhancement request** - improvement to an existing feature
- **Feature request** - entirely new functionality
- **Documentation change** - docs need updating

If ambiguous, ask the user.

### 2. Read the Matching Template

Read the corresponding template from the project's `.github/ISSUE_TEMPLATE/` directory:

| Type | Template file |
|---|---|
| Feature | `.github/ISSUE_TEMPLATE/1_feature_request.md` |
| Enhancement | `.github/ISSUE_TEMPLATE/2_enhancement_request.md` |
| Bug | `.github/ISSUE_TEMPLATE/3_bug_report.md` |
| Documentation | `.github/ISSUE_TEMPLATE/4_documentation_change.md` |

### 3. Fill Out the Template

Parse the user's notes and map information to the template sections. Follow these rules:

- **Keep it short** - focus on the most important parts; remove noise, filler, and tangents
- **Split when needed** - if the notes cover multiple distinct issues, create a separate file for each
- **Extract and restructure** - do not just copy-paste raw notes; reorganize into coherent sections
- **Preserve technical details** - keep code snippets, class names, error messages, version numbers exactly as mentioned
- **Class name references** - always format class names in backticks without spaces: `EventHandlingComponent`, `AccessSerializingRepository`, `CommandBus`
- **Research mentioned classes** - if class names are mentioned in the discussion, read their source code and javadoc to produce an accurate, comprehensive description in the issue
- **Add code blocks** - wrap any code, stack traces, or configuration in proper markdown fenced blocks
- **Fill every section** - if the notes lack info for a section, write a reasonable placeholder or note `N/A` rather than leaving it blank
- **Capture decisions** - if meeting notes contain resolution decisions or action items, include them

### 4. Compose the Title and File Name

Propose a concise, descriptive title (under 80 chars). Patterns:
- Bug: describe the symptom, e.g. "Polymorphic `@EventSourcedEntity` - doesn't evolve immutable entities"
- Enhancement: describe the desired outcome, e.g. "Allow usage of the `AccessSerializingRepository`"
- Feature: describe the capability, e.g. "Support dead letter queue for failed event handlers"

Derive a kebab-case file name from the title, e.g.:
- Title: "Polymorphic `@EventSourcedEntity` - doesn't evolve immutable entities" -> `polymorphic-event-sourced-entity-doesnt-evolve-immutable-entities.md`
- Title: "Allow usage of the `AccessSerializingRepository`" -> `allow-usage-of-access-serializing-repository.md`

Present the proposed title to the user before writing the file.

### 5. Write to File

Structure the output file as:

```markdown
# <Issue Title>

<filled template body>
```

The title goes as an `#` H1 header at the top, followed by the filled-in template sections.

Write the file to: `.ai/temp/skills/issue-from-notes/<file-name>.md`

Create the directory if it doesn't exist. Print the file path after writing.

## Quality Checklist

Before writing the file, verify:
- [ ] All template sections are filled
- [ ] The `#` H1 header contains the issue title
- [ ] Code snippets have proper syntax highlighting (```java, ```kotlin, etc.)
- [ ] Technical terms and class names are accurate to what was mentioned
- [ ] The title is concise and descriptive
- [ ] No raw meeting artifacts remain (filler words, off-topic tangents, speaker labels)

## References

For real-world examples of well-written issues in this project, see [references/examples.md](references/examples.md).
Consult these examples to match the tone, depth, and structure expected in this project's issues.
