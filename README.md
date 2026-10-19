# GitContext

A RAG developer copilot that answers questions about a GitHub repository using its source code, docs, commit history, PR discussions, and incident reports, with cited answers.

Built with **Java 25** and **Spring Boot**.

> 🚧 Under active development.

## Running Qdrant

GitContext uses Qdrant for vector similarity search. You can start the local Qdrant container with Docker Compose:

```bash
# Start Qdrant in the background
docker compose up -d

# Open the Qdrant web dashboard
# http://localhost:6333/dashboard

# Stop Qdrant when finished
docker compose down
```
