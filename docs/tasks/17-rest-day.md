Do Part 1 of task 17 only, on a new branch: task-17-rest-day.

1. Amend contract/rules.md exactly as Part 1 says:
    - R5: new ladder order (rest check before pending) + the "why" sentence.
    - R6: a pending flagged day consumes the token; flags on complete or
      future days are ignored and not consumed; add the "takes the token
      immediately, finishing returns it" sentence.
    - Also add to R6: "Tokens are never applied automatically. An unflagged
      missed or partial day stays missed or partial even if the week has
      tokens left."
    - R11: add the note that a flagged, unfinished today is rest and counts
      toward the current streak. No rule change.

2. Read every existing fixture in contract/fixtures/ against the amended
   R5 and R6. Do not run or change any Kotlin yet. If any existing
   fixture's expected value is now wrong, STOP and list each one with the
   reason. Do not edit existing fixtures.

3. If nothing conflicts, add these four fixtures, deriving every expected
   value by hand from the amended rules:
    - rest_today_finished_releases_token
    - rest_future_flag_ignored
    - rest_today_counts_toward_streak
    - rest_today_ignored_in_strict
      Copy the shape of an existing rest-token fixture.

4. Create two placeholder files I will write myself, containing only
   {"TODO": "Priyanshu writes this"} :
    - rest_today_reserves_token.json
    - rest_today_second_flag_in_week_invalid.json
      Then make the test harness skip any fixture containing a top-level
      "TODO" key, and print which files were skipped, so the suite still runs.

5. Run ./gradlew :domain:test. The new fixtures are EXPECTED to fail,
   because the engine hasn't changed yet. Report which fail and why, in
   one line each. Existing fixtures must still pass.

6. Commit. Do not start Part 2. Do not open a PR yet.

Report back:
- the exact R5/R6/R11 diff
- any existing fixture that conflicts (or "none")
- the red/green result of step 5, with the reason for each failure