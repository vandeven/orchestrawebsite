## What and why
<!-- What does this change, and why? Link the requirement IDs (for example MEM-20). -->

## Checklist (definition of done: docs/roadmap.md)
- [ ] The change is in the right component and follows the architecture rules (`api` / `internal` / `web`)
- [ ] Tests are added or updated: every validation rule (valid, boundary, invalid), access control for every role, and where needed end-to-end and accessibility
- [ ] Admin actions write audit entries, and no secrets or personal data are logged
- [ ] The feature document is up to date (requirements, input validation, automatic tests, status) and a line is added to the status log in `docs/PLAN.md`
- [ ] No secrets, personal data or real content in the change
- [ ] For `infra/` changes: the plan is reviewed and no policy check is bypassed
- [ ] All checks in the pipeline pass (tests, scans, formatting, coverage, budgets)
