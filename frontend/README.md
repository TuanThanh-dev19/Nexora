# Nexora frontend

ReactJS + JavaScript/JSX single-page application for Nexora.

```bash
npm install
npm run dev
npm run lint
npm run test
npm run build
```

The codebase is organized by business feature. Keep HTTP calls in a feature API module, server state in TanStack Query hooks, and only small client state such as the current user in Context.

```text
src/
├── app/                  Router, providers, route guards
├── features/
│   ├── auth/             components, context, pages, schemas
│   ├── tickets/          api, components, constants, hooks, pages
│   ├── dashboard/        components, pages
│   ├── assets/           components, constants
│   └── categories/       components, constants
├── shared/               Cross-feature components, layouts, hooks, utilities
├── test/
├── App.jsx
└── main.jsx
```

Use `.jsx` for React components/pages that render JSX and `.js` for plain modules such as API clients, hooks, schemas, configuration, and utilities. `jsconfig.json` provides editor support; there is no TypeScript compiler step.

`features` is not another name for `components`. A feature is a business module; it may contain components, pages, API calls, hooks, validation schemas, and runtime models/constants. Read `src/features/README.md` before adding a new feature.

The current sign-in screen deliberately contains a UI-only demo session so route and role guards can be reviewed before the backend authentication endpoints exist. It stores no token. Replace `loginDemo` with the real authentication flow before implementing real users.
