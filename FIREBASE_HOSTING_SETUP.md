# Firebase Hosting Setup

This repo now includes Firebase Hosting config (`firebase.json`) and a deploy workflow (`.github/workflows/deploy-hosting.yml`).

## Required GitHub Secrets

Add these in `Settings -> Secrets and variables -> Actions`:

1. `FIREBASE_PROJECT_ID`
2. `FIREBASE_TOKEN`

## Get `FIREBASE_TOKEN`

```bash
npm install -g firebase-tools
firebase login:ci
```

Copy the returned token and save it as `FIREBASE_TOKEN`.

## Deploy Flow

1. Push to `main`, or run `Deploy Firebase Hosting` manually from GitHub Actions.
2. Workflow deploys `hosting/` to Firebase Hosting.
3. Demo URL will be:

```text
https://<FIREBASE_PROJECT_ID>.web.app
```

## Local Deploy (Optional)

```bash
npm install -g firebase-tools
firebase deploy --only hosting --project <FIREBASE_PROJECT_ID>
```
