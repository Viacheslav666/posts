# Self-hosted fonts (FE-3)

IBM Plex Sans and Source Serif 4 (latin + cyrillic) are vendored as `.woff2`
so `next build` does not call Google Fonts at compile time (CI flake /
egress). Sourced from `@fontsource/*` packages (SIL OFL).
