# Contributing to ConLuz

Thank you for your interest in contributing to **ConLuz**! This document provides guidelines and best practices for collaborating on the project.

---

## Table of Contents

- [Getting Started](#getting-started)
- [Workflow](#workflow)
- [Branch Naming Convention](#branch-naming-convention)
- [Commit Message Format](#commit-message-format)
- [Pull Requests](#pull-requests)
- [Code of Conduct](#code-of-conduct)
- [Contact](#contact)

---

## Getting Started

To contribute, you will need:

- A GitHub account
- Git installed on your local machine
- A fork of this repository
- A properly configured local development environment (see project README for setup instructions)

---

## Workflow

We follow the **GitFlow Workflow** for managing development.

1. Fork this repository.
2. Create a new branch from `main` for your changes.
3. Make your changes in that branch.
4. Push the branch to your fork.
5. Open a **Pull Request** (PR) against the `main` branch in this repository.

All contributions must be submitted via pull request — **no direct pushes to `main` are allowed**.

---

## Branch Naming Convention

Branches for new features must follow this pattern:

```
feature/conluz-XXX
```


- `XXX` refers to the issue or ticket number (e.g., `feature/conluz-123`).

---

## Commit Message Format

All commits must follow this format:

```
[conluz-XXX] Your commit message
```

**Examples:**

- `[conluz-101] Add login page`
- `[conluz-205] Fix energy consumption bug`

> This format helps maintain traceability between commits and issues.

---

## Pull Requests

- Pull requests must target the `main` branch.
- Use **Squash and Merge** as the merge strategy.
- Ensure your PR references the related issue (e.g., "Closes #123").
- Provide a clear description of the changes made.
- Ensure all CI checks pass and the code is properly tested.

---

## Coding

- The code must follow Clean Code standards.
- The code must be self explanatory adding comments when further explanation is required.
- The code must be in English.
- The code must be tested through automated tests to validate it works as expected.

---

## Code of Conduct

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md) to help create a welcoming and respectful community.

---

## Contact

If you have any questions, feel free to open an issue or contact the maintainers.

---

Happy coding! 🚀
