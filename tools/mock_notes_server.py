#!/usr/bin/env python3
"""Minimal in-memory mock of the Nextcloud Notes API v1, for local testing.

Accepts any Basic-auth credentials. Seeds a few markdown notes so the app has
something to render. Not for production — no persistence, no real auth.

Run:  python3 tools/mock_notes_server.py [port]   (default 8088)
From the Android emulator, the host is reachable at http://10.0.2.2:<port>.
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API = "/index.php/apps/notes/api/v1/notes"

NOTES = {}
_next_id = 1


def seed():
    global _next_id
    samples = [
        (
            "Shopping list\n- [ ] Milk\n- [x] Eggs\n- [ ] **Coffee** beans\n- [ ] Bread",
            "Groceries",
            True,
        ),
        (
            "# Project ideas\n1. Markdown notes client\n2. Sync engine\n3. QR login\n\n"
            "> Keep it clean and offline-first.",
            "Work",
            False,
        ),
        (
            "Recipe: pancakes\n\n## Ingredients\n- Flour\n- Milk\n- Eggs\n\n"
            "## Steps\n1. Mix\n2. Fry\n3. Serve with *syrup*",
            "",
            False,
        ),
        ("Remember to call the dentist `0123-456`", "", False),
    ]
    for content, category, favorite in samples:
        add_note(content, category, favorite)


def add_note(content, category, favorite, title=None):
    global _next_id
    # The Notes API v1 title is read/write and used as the filename; honor an
    # explicit one if the client sends it, else derive it from the first line.
    note = {
        "id": _next_id,
        "etag": f"etag{_next_id}-{int(time.time())}",
        "readonly": False,
        "modified": int(time.time()),
        "title": title or content.split("\n", 1)[0],
        "category": category,
        "content": content,
        "favorite": favorite,
    }
    NOTES[_next_id] = note
    _next_id += 1
    return note


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        sys.stderr.write("[mock] " + (fmt % args) + "\n")

    def _send(self, code, payload=None):
        body = b"" if payload is None else json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", 0))
        if not length:
            return {}
        return json.loads(self.rfile.read(length).decode() or "{}")

    def _id_from_path(self):
        return int(self.path.rsplit("/", 1)[-1])

    def do_GET(self):
        if self.path.startswith(API):
            self._send(200, list(NOTES.values()))
        else:
            self._send(404)

    def do_POST(self):
        if self.path.startswith(API):
            data = self._read_json()
            note = add_note(
                data.get("content", ""),
                data.get("category", ""),
                data.get("favorite", False),
                data.get("title") or None,
            )
            self._send(200, note)
        else:
            self._send(404)

    def do_PUT(self):
        try:
            note_id = self._id_from_path()
        except ValueError:
            return self._send(404)
        note = NOTES.get(note_id)
        if not note:
            return self._send(404)
        data = self._read_json()
        content = data.get("content", note["content"])
        note.update(
            content=content,
            title=data.get("title") or content.split("\n", 1)[0],
            category=data.get("category", note["category"]),
            favorite=data.get("favorite", note["favorite"]),
            modified=int(time.time()),
            etag=f"etag{note_id}-{int(time.time())}",
        )
        self._send(200, note)

    def do_DELETE(self):
        try:
            note_id = self._id_from_path()
        except ValueError:
            return self._send(404)
        NOTES.pop(note_id, None)
        self._send(200, {})


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8088
    seed()
    print(f"[mock] Nextcloud Notes mock on http://0.0.0.0:{port}{API}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
