# Notes for AI agents

This folder is the launcher, TV Home V2. Before changing anything, read these in order:

1. [`Documents/Requirements.md`](Documents/Requirements.md): what the launcher does and why, and the decisions
   already made.
2. [`Documents/DeveloperNotes.md`](Documents/DeveloperNotes.md): how it is built. It covers the rules of the house,
   the architecture map, Theme 1's measurements, the storage and phone API reference, how to add a theme, and a
   checklist for every change.

House rules:
- Plain Java 17, not Kotlin.
- Design in pixels on a 1920 × 1080 layout.
- Budget memory for a 1 GB TV.
- Put logic in plain-Java classes, each with unit tests.
- Never rename a saved settings key.
- Update both documents in the same change.

To check a change, push to a branch and let **V2 check** pass, then merge to `main`.
