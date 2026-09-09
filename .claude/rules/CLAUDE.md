# Project Instructions

## General Principles
Follow these core software engineering principles in all code changes and architectural decisions:

- **YAGNI (You Ain't Gonna Need It)**: Do not implement functionality until it is actually needed. Avoid over-engineering for hypothetical future requirements.
- **DRY (Don't Repeat Yourself)**: Abstract repeated logic into reusable components, functions, or utilities.
- **KISS (Keep It Simple, Stupid)**: Favor simple, readable, and maintainable solutions over complex ones. If a solution is unnecessarily complicated, simplify it.
- **SOLID**: Adhere to the SOLID principles of object-oriented design:
    - **S**ingle Responsibility Principle: A class or module should have one, and only one, reason to change.
    - **O**pen/Closed Principle: Software entities should be open for extension but closed for modification.
    - **L**iskov Substitution Principle: Objects of a superclass should be replaceable with objects of its subclasses without breaking the application.
    - **I**nterface Segregation Principle: Clients should not be forced to depend on methods they do not use.
    - **D**ependency Inversion Principle: Depend on abstractions, not on concretions.

## Coding Standards
- **Comments**: Avoid comments that merely restate the code. Only comment when explaining **why**, non-obvious logic, important context, or public APIs. Keep comments short and focused; never write mini-essays.
- **Readability**: Write clean, expressive code. Use meaningful variable and function names to make the code self-documenting.
- **Consistency**: Maintain a consistent style throughout the project, following established patterns and naming conventions.

## Workflow
- **Important**: At the beginning of a new chat, read the project's root `README.md` once before making suggestions or code changes.
- **Git Branching**: main should only be merged into from dev. Always create a new branch from dev when creating new files or modifying existing files.