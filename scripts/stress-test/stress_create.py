#!/usr/bin/env python3
"""Stress test for POST /createNewReport.

Sends the SAME create request many times at once and checks that the service ends up with exactly one
report for that (firm, tradeRefId).

 It uses only the Python standard library and talks to the service over
HTTP, so it needs no packages, no database access, and none of the project's code. Run it any time the
backend is up. (Independent from server)

    python3 "/Users/elliskelly/coding/DTCC prep/TradeReport/scripts/stress-test/stress_create.py"
    python3 stress_create.py
    python3 stress_create.py --requests 5000 --workers 100
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

CREATE_PATH = "/createNewReport"
LOCAL_HOSTS = ("localhost", "127.0.0.1", "::1", "host.docker.internal")

# What each status code means for this test.
MEANING = {
    200: "created",
    409: "already exists (early check, or the unique constraint in a real race)",
}


def parse_args():
    parser = argparse.ArgumentParser(description="Send many identical create requests at once.")
    parser.add_argument("--url", default="http://localhost:8080", help="backend base URL (default %(default)s)")
    parser.add_argument("--requests", type=int, default=1000, help="how many requests to send (default %(default)s)")
    parser.add_argument("--workers", type=int, default=50, help="how many to send at the same time (default %(default)s)")
    parser.add_argument("--ref", default="STRESS-" + str(int(time.time())),
                        help="trade reference used by every request (default: STRESS-<timestamp>, new each run)")
    parser.add_argument("--firm", default="STRESS TEST FIRM", help="firm used by every request (default %(default)s)")
    parser.add_argument("--no-verify", action="store_true", help="skip the check of what the service stored")
    parser.add_argument("--allow-remote", action="store_true",
                        help="allow a target that is not on this computer (off by default, as a safety guard)")
    return parser.parse_args()


def is_local(url):
    return urllib.parse.urlparse(url).hostname in LOCAL_HOSTS


def call(method, url, body=None, timeout=60):
    """Make one HTTP call. Returns (status, parsed JSON or None, milliseconds)."""
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    except Exception as error:  # connection refused, timeout, ...
        return type(error).__name__, None, (time.perf_counter() - started) * 1000
    elapsed = (time.perf_counter() - started) * 1000
    try:
        return status, json.loads(raw), elapsed
    except ValueError:
        return status, None, elapsed


def percentile(sorted_values, fraction):
    return sorted_values[min(len(sorted_values) - 1, int(len(sorted_values) * fraction))]


def verify(base, firm, ref):
    """Ask the service what it stored. Returns the number of reports with this firm and reference."""
    print("\nverification (asking the service what it stored)")
    query = urllib.parse.urlencode({"size": 200, "firm": firm, "tradeRefId": ref})
    status, page, _ = call("GET", base + "/getReports?" + query)
    if status != 200 or page is None:
        print(f"  could not list reports (status {status})")
        return None
    reports = page["content"]

    matches = [r for r in reports if r.get("tradeRefId") == ref and r.get("firm") == firm]
    print(f"  reports with this firm and reference: {len(matches)}   (expected 1)")

    if matches:
        report_id = matches[0]["id"]
        audits = []
        for _ in range(20):  # the audit row is written by the Kafka consumer, so give it up to 10 seconds
            status, found, _ = call("GET", f"{base}/getReportAudits/{report_id}")
            audits = found if status == 200 and found else []
            if audits:
                break
            time.sleep(0.5)
        print(f"  audit rows for report {report_id}: {len(audits)}   (expected 1)")
        if not audits:
            print("  warning: no audit row yet. Is the Kafka consumer running?")
    return len(matches)


def main():
    args = parse_args()
    base = args.url.rstrip("/")

    if not is_local(base) and not args.allow_remote:
        print(f"Refusing to send {args.requests} requests to {base}, which is not on this computer.")
        print("Add --allow-remote only if you own the target and are allowed to stress test it.")
        return 2

    status, _, _ = call("GET", base + "/getReports?size=1")
    if status != 200:
        print(f"The backend is not answering at {base} (got {status}). Start it first.")
        return 2

    body = {"amount": 100.00, "firm": args.firm, "tradeRefId": args.ref, "user": "stress"}
    print(f"sending {args.requests} identical creates to {base}{CREATE_PATH}, {args.workers} at a time")
    print(f"firm='{args.firm}'  tradeRefId='{args.ref}'")

    began = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(lambda _: call("POST", base + CREATE_PATH, body), range(args.requests)))
    elapsed = time.perf_counter() - began

    codes = Counter(status for status, _, _ in results)
    latencies = sorted(ms for _, _, ms in results)

    print(f"\ntotal time:   {elapsed:.2f}s  ({args.requests / elapsed:.0f} requests/second)")
    print("status codes:")
    for code in sorted(codes, key=str):
        print(f"  {code}: {codes[code]:>5}   {MEANING.get(code, '')}")
    print(f"latency ms:   min {latencies[0]:.0f}   p50 {percentile(latencies, 0.50):.0f}   "
          f"p95 {percentile(latencies, 0.95):.0f}   p99 {percentile(latencies, 0.99):.0f}   max {latencies[-1]:.0f}")

    stored = None if args.no_verify else verify(base, args.firm, args.ref)

    # Pass: no server errors or dropped connections, and exactly one report stored.
    failures = [code for code in codes if not isinstance(code, int) or code >= 500]
    passed = not failures and (stored is None or stored == 1)
    print("\nRESULT: " + ("PASS" if passed else "FAIL"))
    if failures:
        print(f"  server errors or connection failures: {failures}")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
