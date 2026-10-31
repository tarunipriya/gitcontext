# GitContext Development & Deployment Guide

This guide covers setting up the local development environment, running services via Docker, and working with the web interface.

---

## Running Qdrant

GitContext uses Qdrant for vector similarity search. You can run a standalone local Qdrant container with Docker Compose:

```bash
# Start Qdrant in the background
docker compose up -d

# Open the Qdrant web dashboard
# http://localhost:6333/dashboard

# Stop Qdrant when finished
docker compose down
```

---

## Run Everything with Docker

To run the complete GitContext stack (Qdrant vector store, Java 25 / Spring Boot backend, and React web frontend) with Docker Compose in Windows PowerShell:

### 1. Configure Environment
Copy `.env.example` to `.env` and configure your API keys:
```powershell
Copy-Item .env.example .env
```
Set `LLM_API_KEY` (for grounded answer generation) and optionally `GITHUB_TOKEN` (for pull request and incident issue ingestion).

### 2. Build and Run
```powershell
docker compose --profile app up --build
```

### 3. Access Services
- **Web UI:** [http://localhost:5173](http://localhost:5173)
- **Swagger UI (Interactive API Docs):** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html) (OpenAPI JSON spec at `http://localhost:8081/v3/api-docs`)
- **Qdrant Dashboard:** [http://localhost:6333/dashboard](http://localhost:6333/dashboard)

### 4. Stop Services
```powershell
docker compose --profile app down
```
Ingested repositories, Lucene keyword indices, and cached reranker model weights persist inside the `gitcontext_data` Docker volume. To stop and wipe all persisted data, use:
```powershell
docker compose --profile app down -v
```

---

## Web UI & Local Development

GitContext features a Claude-style conversational interface built with React 18, TypeScript, Tailwind CSS v4, and shadcn/ui primitives.

### UI Features
- **App Shell & Sidebar**:
  - Left sidebar (`w-64`, collapsible to `w-16` icon view; automatically switches to an accessible `Sheet` drawer on mobile screens `<768px`).
  - Brand header with GitContext's commit-trunk logo and a "New chat" shortcut button.
  - Chronological chat history automatically grouped into **Today**, **Yesterday**, **Previous 7 days**, and **Older** buckets, with context menus to rename or delete conversations.
  - Bottom toolbar with **Repositories** navigation, dev-only **Style guide** link (`#/styleguide`), `ThemeToggle` (light/dark mode), and a collapse toggle.
- **Start Screen (`#/`)**:
  - Clean, vertically centered layout with the GitContext brand mark and query prompt (*"What do you want to know about this repo?"*).
  - Four curated question chips to quickly query project structure, architecture, chunking, or dependencies with a single click.
  - Contextual empty state linking to `#/repos` if no repositories are indexed yet.
- **Interactive Composer**:
  - Rounded card with auto-growing textarea (up to 8 lines) and keyboard shortcuts (`Enter` to submit, `Shift+Enter` for newline).
  - Repository switcher select picker embedded directly inside the composer's bottom-left corner.
  - Real-time **Stop** button powered by `AbortController` to cancel in-flight queries.
- **Conversation & Citations (`#/c/<chatId>`)**:
  - User messages displayed in compact muted bubbles; assistant messages rendered as clean prose with GitContext avatar.
  - Safe markdown rendering with no raw HTML (XSS-safe) and code blocks highlighted with `CodeBlock` (line numbers, language badge, clipboard copy).
  - Verified citation markers rendered as clickable accent chips (`[1]`, `[2]`).
  - Source cards row underneath each answer linking directly to cited code files, commit diffs, PR discussions, and issues.
  - Slide-over **Source Panel (Sheet)** on the right displaying highlighted code snippets, line numbers, chunk symbols, and safe external GitHub links.
  - Fast typewriter reveal effect (~2–3 ms/char) respecting `prefers-reduced-motion`, plus inline ProblemDetail error handling with a one-click Retry button.
  - Latency telemetry metadata (`model · retrieval ms · generation ms`).
- **Repositories Page (`#/repos`)**:
  - Settings-style dashboard inside the app shell.
  - Ingestion form with client-side owner/name format validation.
  - Active ingestion job tracker with single-interval polling (2 seconds) and live duration timers.
  - Indexed repository cards showing total chunks, files, commit SHAs, and a **New chat** button to immediately launch conversations for that repository.
- **Local Persistence**:
  - Conversations, messages, sources, and selected repositories automatically persist in `localStorage` (`gitcontext-chats`), safely capped at 50 conversations.

### Running Backend and Frontend Locally (Windows)

1. **Start Qdrant**:
   ```powershell
   docker compose up -d
   ```
2. **Start Backend** (runs on port 8081):
   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```
3. **Start Frontend** (Vite dev server with `/api` proxy to port 8081):
   ```powershell
   cd frontend
   npm install
   npm run dev
   ```
4. **Open in Browser**:
   Navigate to [http://localhost:5173](http://localhost:5173).
