# Required GitHub Secrets

This document describes the secrets required for the GitHub Actions workflow to function properly.

## OPENAI_API_KEY
**Required:** Yes
**Description:** OpenAI API key for running AI-powered PR reviews
**Get from:** https://platform.openai.com/api-keys
**Permissions:** Access to OpenAI API

## VERCEL_TOKEN
**Required:** Optional (recommended)
**Description:** Vercel API token for fetching preview deployment URLs
**Get from:** https://vercel.com/account/tokens
**Permissions:** Read deployments
**Note:** If not provided, workflow will use production dashboard URL as fallback

## VERCEL_PROJECT_ID
**Required:** Optional (recommended)
**Description:** Vercel project ID for identifying the correct project
**Get from:** Vercel Project Settings → General → Project ID
**Example:** `prj_abc123xyz`
**Note:** If not provided, workflow will use production dashboard URL as fallback

## How to Add Secrets

1. Navigate to your GitHub repository
2. Go to Settings → Secrets and variables → Actions
3. Click "New repository secret"
4. Add the secret name and value
5. Click "Add secret"

## Testing Vercel Integration

To test if Vercel integration is working:
1. Ensure both `VERCEL_TOKEN` and `VERCEL_PROJECT_ID` are set
2. Create a test PR
3. Wait for the workflow to complete
4. Check the PR comment - the dashboard link should point to your preview URL (e.g., `https://ultimate-test-metrics-abc123.vercel.app/dashboard`)
5. If secrets are not configured, the link will fallback to production URL
