#!/usr/bin/env python3
"""Forward MinIO webhook notifications to ElasticMQ (SQS-compatible).

MinIO community builds do not include a native notify_sqs target, so events
are published as webhooks and this service SendMessage's them onto the queue.
"""

from http.server import BaseHTTPRequestHandler, HTTPServer
import os
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

SQS_ENDPOINT = os.environ["SQS_ENDPOINT"]
QUEUE_URL = os.environ["QUEUE_URL"]
LISTEN_PORT = int(os.environ.get("LISTEN_PORT", "8080"))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        print(f"sqs-bridge: {format % args}")

    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        if not body:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"empty body")
            return

        message = body.decode("utf-8")
        payload = urlencode(
            {
                "Action": "SendMessage",
                "Version": "2012-11-05",
                "QueueUrl": QUEUE_URL,
                "MessageBody": message,
            }
        ).encode("utf-8")

        request = Request(
            SQS_ENDPOINT,
            data=payload,
            method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        try:
            with urlopen(request, timeout=10) as response:
                response.read()
        except (URLError, HTTPError, TimeoutError) as error:
            print(f"sqs-bridge: failed to send to SQS: {error}", flush=True)
            self.send_response(502)
            self.end_headers()
            return

        print(f"sqs-bridge: forwarded message: {message}", flush=True)
        self.send_response(200)
        self.end_headers()


if __name__ == "__main__":
    print(f"sqs-bridge: posting to {QUEUE_URL} via {SQS_ENDPOINT}")
    HTTPServer(("0.0.0.0", LISTEN_PORT), Handler).serve_forever()
