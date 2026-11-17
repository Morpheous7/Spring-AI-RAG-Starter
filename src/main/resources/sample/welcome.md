# Welcome to the Spring AI RAG Starter

This service stores your documents in Postgres with the pgvector extension and finds the passages that best match a question. It is a Spring Boot 4 application built on Spring AI 2.

## How search works

Each document is split into chunks of about 1,000 characters with a small overlap. Every chunk is turned into a 384-number embedding by the all-MiniLM-L6-v2 model, which runs inside the application itself. No outside AI service or API key is needed for search.

## How answers work

Answer generation is optional. Set the variables AI_CHAT=openai and OPENAI_API_KEY to your key, and the /api/ask endpoint writes an answer from the best-matching chunks and cites them by number. Without those variables, /api/ask returns the matching chunks themselves, which is called search-only mode.

## Adding your own documents

Send plain text to POST /api/documents with a JSON body containing text and source, or upload a .txt or .md file to POST /api/documents/upload. To protect these endpoints, set APP_API_KEY and send the same value in the X-API-Key header.

## Example questions

Try asking: "Do I need an API key for search?" or "How big are the chunks?"
