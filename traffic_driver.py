"""
Traffic Driver — controlled wave traffic for autoscaling demo
A single POST /login triggers the full chain: auth -> course -> seat

Phases:
  1.  2 req/s  x 2 min  — baseline, no scaling expected
  2. 15 req/s  x 2 min  — moderate load, scale-out expected
  3. 30 req/s  x 3 min  — peak load
  4. 15 req/s  x 2 min  — ramp down
  5.  2 req/s  x 3 min  — cool-down, scale-in expected after 3 cycles

Usage:
  py -3 traffic_driver.py
  py -3 traffic_driver.py --dry-run
"""

import argparse
import threading
import time
import sys

try:
    import requests
except ImportError:
    print("[ERROR] requests not installed. Run: pip install requests")
    sys.exit(1)

GATEWAY_URL  = "http://localhost:8080/login"
GATEWAY_BODY = {"username": "test", "password": "test"}
TIMEOUT      = 5

WAVES = [
    {"rps":  2, "duration": 120, "label": "Baseline   —  2 req/s  (2 min)"},
    {"rps":  25, "duration": 120, "label": "Ramp-up    — 25 req/s  (2 min)"},
    {"rps": 50, "duration": 180, "label": "Peak       — 50 req/s  (3 min)"},
    {"rps":  25, "duration": 120, "label": "Ramp-down  — 25 req/s  (2 min)"},
    {"rps":  2, "duration": 180, "label": "Cool-down  —  2 req/s  (3 min)"},
]

_lock  = threading.Lock()
_stats = {"sent": 0, "ok": 0, "err": 0}


def _send():
    try:
        r = requests.post(GATEWAY_URL, json=GATEWAY_BODY, timeout=TIMEOUT)
        with _lock:
            _stats["sent"] += 1
            if r.status_code < 400:
                _stats["ok"] += 1
            else:
                _stats["err"] += 1
    except Exception:
        with _lock:
            _stats["sent"] += 1
            _stats["err"] += 1


def _run_wave(rps: int, duration: int, label: str):
    print(f"\n{'='*60}")
    print(f"  PHASE: {label}")
    print(f"{'='*60}")

    with _lock:
        _stats["sent"] = 0
        _stats["ok"]   = 0
        _stats["err"]  = 0

    interval   = 1.0 / rps   # seconds between requests
    phase_end  = time.monotonic() + duration
    next_send  = time.monotonic()

    while True:
        now = time.monotonic()
        if now >= phase_end:
            break

        # Fire request in a daemon thread so it doesn't block the rate loop
        threading.Thread(target=_send, daemon=True).start()
        next_send += interval

        # Sleep until next scheduled send (or phase end, whichever is sooner)
        sleep_for = min(next_send - time.monotonic(), phase_end - time.monotonic())
        if sleep_for > 0:
            time.sleep(sleep_for)

        elapsed = now - (phase_end - duration)
        with _lock:
            sent, ok, err = _stats["sent"], _stats["ok"], _stats["err"]
        print(f"  [{int(elapsed):>4}s / {duration}s]  rps={rps}  sent={sent}  ok={ok}  err={err}",
              end="\r", flush=True)

    # Wait briefly for in-flight requests to land
    time.sleep(TIMEOUT)
    with _lock:
        sent, ok, err = _stats["sent"], _stats["ok"], _stats["err"]
    print(f"\n  Phase complete — sent={sent}  ok={ok}  err={err}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="Print plan only, send nothing")
    args = parser.parse_args()

    total_sec = sum(w["duration"] for w in WAVES)
    print("\nTraffic Driver — Course Registration Autoscaling Demo")
    print(f"Gateway   : {GATEWAY_URL}")
    print(f"Chain     : auth-service -> course-service -> seat-service")
    print(f"Duration  : {total_sec // 60} min ({total_sec}s)")
    print("\nWave plan:")
    for i, w in enumerate(WAVES, 1):
        print(f"  Phase {i}: {w['label']}")

    if args.dry_run:
        print("\n[DRY RUN] No requests sent.")
        return

    print("\nStarting in 3 seconds... (Ctrl+C to abort)\n")
    time.sleep(3)

    try:
        for w in WAVES:
            _run_wave(w["rps"], w["duration"], w["label"])
    except KeyboardInterrupt:
        print("\n\n[ABORTED] Stopped by user.")
        sys.exit(0)

    with _lock:
        sent, ok, err = _stats["sent"], _stats["ok"], _stats["err"]
    print(f"\n{'='*60}")
    print(f"  RUN COMPLETE")
    print(f"  Total sent : {sent}")
    print(f"  Success    : {ok}  ({100 * ok // sent if sent else 0}%)")
    print(f"  Errors     : {err}")
    print(f"{'='*60}\n")


if __name__ == "__main__":
    main()
