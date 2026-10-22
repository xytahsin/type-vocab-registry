# Vocab Registry — native Android build

Production-first IELTS/academic vocabulary trainer for Tahsin's 1,526-word database.
Kotlin · Jetpack Compose · Room · DataStore · WorkManager · Anthropic Messages API.

## Build
1. Open the folder in Android Studio (Koala or newer). Let Gradle sync (AGP 8.4.2 / Kotlin 1.9.24).
2. Run on a device/emulator with minSdk 26+.
3. **No API key needed.** Without one, production and collocation cards use self-grading:
   the app reveals the model example, collocations, and confusable warning, and you rate
   yourself (Missed / Shaky / Good / Precise) — free forever, fully offline. If you ever add
   a key in Settings, LLM grading switches on automatically.

## Architecture
- `data/` — Room (words seeded from `assets/vocab.json` on first run; axis states; review log),
  DataStore prefs (streaks, exam date, proficiency EMAs, API key).
- `domain/Sm2Engine` — modified per-axis SM-2 with growth multipliers (P/C consolidate 0.7×),
  stability decay, harsh resets for neglected productive axes.
- `domain/SessionComposer` — 60% due / 25% new / 15% weak-axis, tier gates (75% → T2, 70% → T3),
  phase-based intake from exam date.
- `grading/Grader` — claude-sonnet-4-6; strict-JSON prompts; heuristic fallback.
- `notify/ReviewWorker` — 12-hourly decay + due-count notification.

## The adaptive engine (`domain/ProficiencyTracker`)
EMAs of review quality (production-weighted) map to a level — Developing / Competent /
Proficient / Advanced — which continuously retunes:

| Lever | Effect |
|---|---|
| **Grader band** | Prompt tells the examiner to grade at band 5.5 → 8.5. The same sentence scores lower as you level up. |
| **Interval modifier** | ×0.85 → ×1.2 on all SM-2 intervals. |
| **Daily intake** | −2 → +4 new words/day on the phase cap. |
| **Distractors** | random-POS → same-theme → near-synonym cloze options. |
| **Collocation check** | above Proficient, even local matches are LLM-verified for precision. |

Asymmetric: promotion needs sustained evidence (EMA α=0.06); demotion is immediate if the
last 20 production reviews average < 2.5. A slump loosens grading and shortens intervals
the same day; a hot streak tightens them only gradually.

## Honest notes
- Generated code, not yet compiled against a real SDK: expect possibly a few minor import or
  API-version fixes on first sync — the logic is the contract.
- `PendingGrade` table is scaffolding for full offline reconciliation (§6.3 of the spec);
  currently provisional heuristic scores are kept rather than re-graded.
- Calibrate constants (growth multipliers, band thresholds) from your artifact usage data.
