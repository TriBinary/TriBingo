# ExamplePlugin - Commit Structure

To maintain a clean and organized codebase, all developers must follow the standardized commit message format. This
ensures that changes to features are easy to track.

## Basic Structure

Every commit message must follow this syntax:
`<tag>: <message>`

## Approved Tags

| Tag             | Usage                                               | Example                                          |
|:----------------|:----------------------------------------------------|:-------------------------------------------------|
| **Feature**     | Adding brand-new functionality.                     | `Feature: Add /help command`                     |
| **Fix**         | Repairing bugs, crashes, or logic errors.           | `Fix: Resolve /help command output error`        |
| **Improvement** | Refining existing code, UX, or performance.         | `Improvement: Add tiered role removal logic`     |
| **Internal**    | Documentation, comments, or repository maintenance. | `Internal: Update COMMIT_STRUCTURE instructions` |
| **Backend**     | Database schema changes or configuration updates.   | `Backend: Add FeatureSwitch model for toggles`   |
| **Update**      | Plugin version changes.                             | `Update: 1.0.0 release`                          |

## Best Practices

* **Use Present Tense:** Write "Add feature" instead of "Added feature."
* **Be Specific:** Instead of `Fix: bug`, use `Fix: Handle undefined member in /role command`.
* **No Period at End:** Do not end the commit message with a period.

---