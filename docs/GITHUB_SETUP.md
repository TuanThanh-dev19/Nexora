# Create the Nexora GitHub repository

## GitHub website

1. Open https://github.com/new.
2. Set the repository name to `nexora`.
3. Choose **Private** for an internal student team, or **Public** if the course requires it.
4. Because this local project already contains a README and `.gitignore`, do not ask GitHub to generate either file.
5. Create the repository, then copy its HTTPS URL.
6. From the root `nexora` folder run:

```bash
git remote add origin https://github.com/YOUR_ACCOUNT/nexora.git
git push -u origin main
```

## GitHub CLI alternative

After installing GitHub CLI and running `gh auth login`:

```bash
gh repo create nexora --private --source=. --remote=origin --push \
  --description "Campus maintenance and asset management platform"
```

Use `--public` instead of `--private` only when your team intends the source to be public.

## Team setup

- Add teammates at **Settings → Collaborators and teams**.
- Add a branch ruleset for `main` at **Settings → Rules → Rulesets**.
- Require a pull request, one approval, resolved conversations, and passing CI.
- Block force pushes and branch deletion for `main`.
- Store deployment credentials at **Settings → Secrets and variables → Actions**.

Suggested branch and commit names:

```text
feat/ticket-workflow
feat/dynamic-category-form
fix/invalid-status-transition

feat: implement ticket assignment
fix: reject invalid ticket transition
test: cover category field validation
docs: update API contract
```
