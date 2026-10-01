# HRGenius UI — olive and white

Implemented 1 October 2026. The running frontend is at http://localhost:4200.

## Visual design

The user's reference informed the warm ivory workspace, white cards, deep olive navigation, natural interior photograph, and people-focused dashboard. Primary actions have contrasting olive fills and white labels. Dark mode uses charcoal olive surfaces and pale olive actions with dark labels. A first visit now opens in light mode; an explicitly saved light/dark preference is preserved. The top-bar theme control has a visible action label on desktop.

| Role | Light | Dark |
|---|---|---|
| Workspace | `#F7F7F2` | `#191D14` |
| Cards | `#FFFFFF` | `#252B1E` |
| Primary action | `#5C6936` | `#CCD79E` |
| Primary action text | `#FFFFFF` | `#293316` |
| Navigation | `#293121` | `#14190F` |
| Selected navigation | `#EFF1DC` | `#EFF1DC` |
| Main text | `#2D3424` | `#F0F2E8` |

Material components and custom widgets share semantic tokens, including menus, tooltips, dialogs, form fields, disabled states, and status chips. Motion respects the operating system's reduced-motion preference.

## Functional design details

- Dashboard: time-aware greeting, artwork, existing role-aware statistics, real headcount and department charts, 6/12-month switch, data table, and a policy acknowledgement ring.
- People: public appreciation carousel and direct access to the existing feedback dialog. No messages were submitted during verification. Existing leave balances, holidays, tasks, and expiring documents remain available.
- Analytics: period selection, keyboard/touch/hover chart values, accessible data tables, and a retry state.
- Attendance: selectable calendar days expose check-in, check-out, and worked time without changing attendance records.
- Leave: proportional used/pending/available bars with text labels and existing request flows.
- Payroll: expandable gross/net comparisons derived from existing runs. Existing approval and calculation behaviour is untouched.
- Navigation: permission-filtered page search, Ctrl/Cmd+K, arrow-key selection, responsive mobile drawer, and skip-to-content link.
- Organisation chart: separate profile links and expansion buttons allow keyboard expansion without opening a profile.
- Shared modules: consistent cards, filters, forms, tables, action states, progress labels, focus outlines, and empty states.

No engagement percentages, birthdays, milestones, people photographs, or AI capabilities were fabricated. All figures and appreciation records come from existing API responses. No backend code, database schema, API contract, or route permission guard was changed.

## Verification

- Production Angular build passed within the existing bundle/style budgets.
- 18 main routes scanned in light and dark mode (36 combinations): zero axe WCAG A/AA rule violations reported and zero browser runtime errors. See [raw audit](ui-audit.json).
- 19 browser interaction checks passed, including role visibility, chart keyboard tooltips, dialog open/cancel, navigation, theme persistence, error states, and mobile drawer behaviour. See [interaction results](ui-interactions.json).
- All 18 main routes fit a 390px-wide viewport without horizontal page overflow. Tables and the organisation canvas retain their own scrolling. See [mobile measurements](ui-mobile.json).
- Login and appreciation/attendance dialogs also passed the automated accessibility checks listed in the interaction results.
- Final checks confirmed the first-visit light theme even with a dark OS preference, both dashboard themes without reported accessibility violations, mobile layout, and graceful failed-request states. See [final results](ui-final.json).
- A fresh payroll-admin login loaded the real dashboard statistics and chart without failed API requests. The request error in the user's earlier screenshot could not be reproduced in that fresh session.

Automated accessibility checks do not constitute a complete accessibility certification. Existing business operations were preserved and checked through non-destructive flows; payroll execution, employee mutations, approval decisions, and message submission were not exercised against the live database.

## Screenshots

- [Light dashboard](ui-dashboard-light.png)
- [Dark dashboard](ui-dashboard-dark.png)
- [Mobile dashboard](ui-dashboard-mobile.png)
- [People appreciation](ui-people-moments.png)
- [Appreciation dialog](ui-appreciation-dialog.png)
- [Employee directory](ui-employees-dark.png)
- [Analytics](ui-analytics-dark.png)
- [Leave](ui-leave-dark.png)
- [Sign-in](ui-login-light.png)

## Artwork provenance

Both images were generated with the built-in imagegen tool, not the fallback CLI. The image-generation skill was used. The original generated files remain in the Codex generated-images directory.

Final application asset: [`frontend/src/assets/olive-workspace.png`](../frontend/src/assets/olive-workspace.png).

Final prompt:

> Use the attached HR dashboard image only as mood reference for its warm olive and ivory interior photography. Generate a NEW standalone wide 3:2 photographic asset, not a dashboard, no interface, no writing, no watermark, no people. This is a welcoming greeting banner background for HRGenius. Premium natural editorial interior photography of a peaceful contemporary workplace lounge, cream linen armchair and sofa, sculptural olive trees and broad-leaf plants in matte warm ivory ceramic pots, a walnut coffee table with a coffee cup and a single cream notebook. Beautiful morning sunlight streaming from a tall window on the right, soft shadows, textured ivory plaster walls. Warm olive, cream, natural wood, muted champagne tones. Composition: furniture and plants in right two thirds, uncluttered soft cream wall on left for blending into UI, camera slightly wide, eye level. Calm refined natural photography, sophisticated and inviting, realistic plants. No text anywhere, especially no quote or slogan on wall. Full bleed landscape image.

Earlier illustration study, retained outside the application's assets: [`reports/workspace-greeting-study.png`](workspace-greeting-study.png).

Study prompt:

> Use case: stylized-concept. Asset type: decorative hero illustration for a calm premium HR management dashboard named HRGenius. Generate ONE landscape illustration, no lettering, no logo, no watermark. A refined editorial gouache and paper-cut inspired still life: a beautiful leafy sage plant in a small ivory ceramic pot on a quiet desk, a coffee cup and a closed notebook, a soft pale golden sun disk behind, with delicate deep forest-green leaves arching upwards, gentle muted lavender accent. Compact composition centered toward the right, generous simple pale sage background #e3ebe1. Sophisticated soft textured illustration, beautiful silhouettes, restrained, soothing to the eyes. Palette charcoal green #202d29, sage #8aa38d, ivory #f5f5f0, pale sage #e3ebe1, small dusty lavender touches. Landscape 3:2 aspect ratio. This will be shown small alongside greeting text, so use bold clean forms without tiny details or any writing. No UI, no charts, no screenshot, no people.
