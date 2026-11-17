# Spring AI RAG Starter (Spring Boot 4 + pgvector)

A Java retrieval-augmented generation (RAG) API you can deploy in one click: Spring Boot 4.1, Spring AI 2.0 and Postgres with pgvector.

- **Search works with no API key.** Embeddings run inside the app (all-MiniLM-L6-v2 via ONNX, 384 dimensions).
- **Answers are optional.** Set `AI_CHAT=openai` and `OPENAI_API_KEY` to get written answers that cite the passages they used.
- **Ready on first boot.** A sample document is loaded so `/api/ask` returns results immediately.

## Endpoints

| Method | Path | What it does |
| --- | --- | --- |
| GET | `/` | Mode, stored chunk count, endpoint list |
| POST | `/api/documents` | Add text: `{"text": "...", "source": "handbook"}` |
| POST | `/api/documents/upload` | Upload a `.txt` or `.md` file (multipart field `file`, 5 MB max) |
| GET | `/api/search?q=...&k=4` | Best-matching passages with scores |
| POST | `/api/ask` | `{"question": "...", "k": 4}`: answer plus sources, or passages only in search-only mode |
| GET | `/actuator/health` | Health check |

```bash
curl -s "$URL/api/ask" -H 'Content-Type: application/json' \
  -d '{"question": "Do I need an API key for search?"}'
```

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | localhost defaults | Postgres connection (pgvector extension required) |
| `DATABASE_URL` | none | Used instead when `PGHOST` isn't set (`postgresql://user:pass@host:port/db`) |
| `AI_CHAT` | `none` | Set to `openai` to turn on written answers |
| `OPENAI_API_KEY` | none | Needed when `AI_CHAT=openai` |
| `OPENAI_CHAT_MODEL` | `gpt-4o-mini` | Chat model for answers |
| `APP_API_KEY` | none | When set, all `/api/**` calls must send it as `X-API-Key` |
| `SEED_SAMPLE` | `true` | Load the sample document on an empty database |
| `SEARCH_TOP_K` | `4` | Default passages per search (max 20 per request) |
| `SEARCH_SIMILARITY_THRESHOLD` | `0.0` | Minimum similarity score (0 to 1); 0 always returns the top matches |
| `RESET_VECTOR_STORE` | `false` | Set `true` for one deploy to drop and recreate the table (deletes all documents) |

## Notes

- The embedding model ships inside the app, so nothing is downloaded at start-up. Startup takes about 10 seconds.
- Give the service at least 1 GB of memory; the embedding model runs in-process.
- Changing the embedding model's dimensions means recreating the `vector_store` table.
- Set `APP_API_KEY` before putting real documents in a public deployment.

## Run locally

Requires Docker.

```bash
docker run -d --name pgvector -e POSTGRES_PASSWORD=postgres -p 5432:5432 pgvector/pgvector:pg18
docker build -t rag-starter .
docker run --rm -p 8080:8080 -e PGHOST=host.docker.internal rag-starter
```

## License

MIT. Copyright (c) 2026 DIGS LLC.
