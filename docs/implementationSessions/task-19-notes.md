# Task 19 — notes

## Not in rules.md

- Rest days: at most 7 a week (app limit, CreateArcViewModel.setRestTokensPerWeek and remix pre-fill)
- Commitments: at most 10 per arc (MAX_COMMITMENTS)

## Known issues, not fixed

- B5: `isSubmitting` stays true if `createArc` throws, so Begin stays on "Starting…" (pre-existing).
- B6: the remix pre-fill overwrites anything typed before it loads (pre-existing).
- B13: the draft is lost on process death; keeping it needs a `SavedStateHandle`.
