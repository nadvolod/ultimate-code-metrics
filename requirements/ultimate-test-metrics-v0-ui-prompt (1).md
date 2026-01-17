# v0 UI Prompt — Ultimate Test Metrics (MVP)

You are building the **UI-first MVP** for a product called **Ultimate Test Metrics**.

This UI is for:
- demos
- a technical talk
- an MVP that looks real and credible

It does **not** need full backend integration yet.
Use mock data and placeholders where needed.

The UI must feel:
- professional
- enterprise-ready
- fast
- opinionated
- trustworthy

Avoid gimmicks. Avoid overdesign. Clarity wins.

---

## Tech constraints
- **Next.js App Router**
- **TypeScript**
- **Tailwind CSS**
- No authentication flows yet
- No database assumptions
- Assume data will come from REST APIs later

---

## Global layout
- Clean top-level layout
- Strong typography
- Subtle motion only
- Neutral, professional color palette
- Use emojis very sparingly and only for emphasis

---

## Pages to build

### 1. Marketing Landing Page (`/`)
Purpose: explain value in under 10 seconds.

Sections (top to bottom):

#### Hero
- Headline:  
  **“Trustworthy test and quality signals from CI — not vibes.”**
- Subheadline:  
  “AI can propose changes. Only evidence should merge them.”
- Primary CTA: **View Dashboard**
- Secondary CTA: **Request Early Access** (non-functional)
- Include a hero visual or illustration placeholder (no stock photos)

#### Value Props (3 cards)
1. **Playwright Test Signals**  
   “High-signal API and E2E tests that actually matter.”

2. **AI Review, Governed**  
   “Code, test, and security reviews — orchestrated, not guessed.”

3. **Single PR Decision**  
   “One clear recommendation per pull request.”

#### How It Works
A simple 4-step flow:
1. PR opened
2. Tests run
3. AI agents analyze
4. Recommendation posted

#### Footer
- Links: GitHub, Docs (placeholder), Contact
- Small and understated

---

### 2. Dashboard Shell (`/dashboard`)
Purpose: make the product feel real.

#### Layout
- Left sidebar navigation
- Main content area
- Top header with product name

Sidebar items:
- Overview
- Data Sources
- Reports
- Settings

---

### 3. Dashboard Overview (`/dashboard`)
Show **mock metrics**.

Cards:
- Total Tests
- Pass Rate
- Failed Tests
- Avg Duration

Below cards:
- Line chart placeholder: “Test results over time”
- Card: **Latest PR Governance**
  - PR title (mock)
  - Status badge: APPROVE / REQUEST_CHANGES / BLOCK
  - Short explanation paragraph

---

### 4. Data Sources (`/dashboard/sources`)
Purpose: future extensibility, not functionality.

Show two source cards:
- **JSON**
- **Azure DevOps**

Each card:
- Status badge (Connected / Not Connected)
- Last sync time (mock)
- “Sync” button (disabled or no-op)

Below:
- Table: “Recent Sync Runs” (mock rows)

---

### 5. Reports (`/dashboard/reports`)
Purpose: show the output of the AI review.

Layout:
- Left: Recommendation summary
- Right: Detailed Report Panel

Elements:
- Overall Recommendation badge
- Risk summary per agent:
  - Code Quality
  - Test Quality
  - Security
- Each section:
  - Risk badge
  - Bullet findings (mock but realistic)
- Button: “Download JSON” (no-op)

---

### 6. Settings (`/dashboard/settings`)
Purpose: convey required configuration.

Show read-only fields:
- Required Header:
  ```
  Authorization: Bearer <token>
  ```
- OpenAI Key status:
  - “Configured in CI” (placeholder)

No forms need to work.

---

## Components to create
- SidebarNav
- MetricCard
- StatusBadge
- SourceCard
- ReportPanel
- RecommendationBadge

---

## Data handling
- Use static mock data
- Structure data as if from an API
- No business logic in UI components

---

## UX rules
- No modals
- No toasts
- No login flows
- No fake loading spinners

---

## Deliverable
Return:
- Full Next.js project structure
- All pages and components
- Tailwind styling
- Clean mock data

The UI must look credible to senior engineers and engineering leaders.

Simplicity, clarity, and trust are the goals.
