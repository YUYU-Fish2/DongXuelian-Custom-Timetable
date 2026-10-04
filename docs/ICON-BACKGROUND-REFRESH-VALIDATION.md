# Icon / background refresh execution

User plan: ICON-BACKGROUND-REFRESH-PLAN.md. Continue ui/v2-refresh from 448337c. Prior user authorization delegates screenshot acceptance to the agent; each RB stage is inspected against the supplied reference before the next stage. No additional user visual approval required.

- RB1 accepted: new window/lake/white flowers/books Hero, original character retained. Fixed scene screenshot inspected: both eyes visible, date heading/range retained. Isolated build and device layout/touch audit passed; host 21 cases and Android Java compile passed.
- RB2 accepted: transparent mint/peach/lavender flowers and books selected by existing card color. Fixed fixture screenshot inspected; isolated build/device layout audit and host 21 cases passed. Final alpha tuning remains RB7.
- RB3 accepted: new rounded clock, soft pin, simplified person and rounded calendar vectors; uniform 24 viewport/1.8 stroke. Build, fixed device screenshot/layout audit and host 21 cases passed.
- RB4 accepted: smaller three dots, standard chevron, outlined slider knobs, centered 2.1 plus and refined sparkle. Build, fixed device screenshot/layout audit and host 21 cases passed.
- RB5 accepted: file with import arrow, notification bell/check badge, plain reminder bell, outline palette/trash and shared chevron. Settings/edit screenshots inspected; isolated build and host 21 cases passed.
- RB6 accepted: new feathered white-flower/mist-blue leaf corner art, mirrored at bottom edges with 0.28/0.22 alpha. Timeline gutter is clear. Build, fixed device screenshot/layout audit and host 21 cases passed.
- RB7 accepted: 14dp meta/20dp header glyphs; FIT_CENTER bounds; 44dp click containers retained. User requested stronger flower decorations and a shorter/paler time pill: normal/completed/next alpha 0.58/0.40/0.32, bottom corners 0.36/0.30; time pill wraps content with 5% accent tint. Temporary darker icon experiment was reverted. Unused replaced artwork removed.

Business/date/storage/reminder/import rules and 44dp click containers remain unchanged. Screenshots use isolated .validation package, fixed Oct 1 2026 13:59 fixture/preset0. Original app data is retained.

Assets generated using built-in image_gen. Hero prompt: preserve the existing right-side character identity/pose, refresh pale watercolor window-sill flowers, books, lake architecture; clean top-left typography space, feathered edges. Source exported to outputs/icon-background-refresh/RB1/hero-source.png.

## Final verification (2026-10-04)

- RMX3700, Android 16/API 36, 1240x2772; native density 560, font 1.0.
- Host: 21 activity cases plus TimetableRules/PDF fixtures and real Android Java compile passed.
- Full device: 32 passed /0 failed /0 skipped; course persistence/edit, PDF confirmation/conflicts, Keystore, notification and AlarmManager delivery, civil dates/DST and status behaviors.
- Timeline: 10 passed; before/during/between/after courses, day/preview hiding and refresh. Status matrix: 8 passed (A-H).
- Density 560/551/505/483 (~354/360/393/411dp) x font 1.0/1.15/1.3: 12 groups, each fixed-scene and measured layout/touch audit passed. Representative narrow/wide screenshots inspected; full time ranges, clear eyes and actions. One physical phone with density overrides, not four hardware devices.
- Isolated Debug/AndroidTest and standard Debug built. Lint: 0 errors /9 existing warnings. Security audit passed. Read-only review found no substantive regression.
- Font/density restored to 1.0/native; USB stay-awake restored to 0. Original app not overwritten.
- Final screenshots: screenshots/icon-background-refresh/home.png, settings.png, course-edit.png, narrow-130.png, wide-130.png. Full raw logs/matrix remain in task outputs/icon-background-refresh/final.
- Final results exclude the first matrix attempt whose PowerShell argument binding ran the full suite instead of visual scenarios. Script corrected; all 12 scenarios rerun and explicit fixture labels verified.

## Asset provenance

Built-in image_gen produced the new Hero, mint/peach/lavender flowers and books, and transparent corner flowers. Card prompts used one airy watercolor style, pale books/white flowers with mint, peach or lavender accents, genuine alpha, detail concentrated lower-right. Corner prompt used white snow-lotus, mist-blue leaves/petals and feathered wash at lower-left, transparent center; mirrored by layout at right. Functional icons are hand-authored VectorDrawable geometry.

Release signing/migration, long idle/reboot behavior and other phone hardware remain outside this visual refresh verification. Date/storage/reminder/PDF business logic is unchanged.
