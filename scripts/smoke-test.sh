#!/usr/bin/env bash
# Usage: scripts/smoke-test.sh https://your-app.up.railway.app [api-key]
set -euo pipefail

URL="${1:?Pass the deployed URL}"
KEY="${2:-}"
AUTH=(-H "X-Smoke-Test: 1")
if [ -n "$KEY" ]; then AUTH=(-H "X-API-Key: $KEY"); fi

echo "1. Health"
curl -fsS "$URL/actuator/health"; echo

echo "2. Info"
curl -fsS "$URL/api/info"; echo

echo "2b. Question page"
curl -fsS "$URL/" | grep -q "Ask your documents" && echo "page OK"

echo "3. Search the seeded sample"
curl -fsS "${AUTH[@]}" "$URL/api/search?q=Do%20I%20need%20an%20API%20key%20for%20search&k=2"; echo

echo "4. Add a document"
curl -fsS "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"text": "The smoke test ran successfully. The secret word is pelican.", "source": "smoke-test"}' \
  "$URL/api/documents"; echo

echo "5. Ask about it"
curl -fsS "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"question": "What is the secret word?", "k": 2}' \
  "$URL/api/ask"; echo

echo "All steps returned HTTP 2xx."
