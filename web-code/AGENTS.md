# Agent Instructions

## Post-Edit Validation

After **every** code change (creating, modifying, or deleting source files), run the build to verify TypeScript compilation succeeds:

```bash
npm run build
```

This runs `vue-tsc --noEmit` (type-check) followed by `vite build` (bundle). If either step fails, fix the errors before proceeding.

### Rules

- Do not batch multiple edits into a single validation — verify after each logical change.
- If the build fails, read the error output, fix the issue, and re-run until it passes.
- Do not skip validation even for "trivial" changes (renames, comment edits, import reordering).
