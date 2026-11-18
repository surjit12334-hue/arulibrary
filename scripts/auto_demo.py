#!/usr/bin/env python3
"""Fully scripted viva demo. Typed menu choices are shown, so the examiner sees each step."""
import os
import pty
import select
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ENV = os.environ.copy()
for d in ["/tmp/opencode/tools/jdk-25.0.5+8/bin", "/tmp/opencode/tools/apache-maven-3.9.9/bin"]:
    if Path(d).exists():
        ENV["PATH"] = d + ":" + ENV.get("PATH", "")


def pty_session(inputs, delay=0.7):
    master, slave = pty.openpty()
    proc = subprocess.Popen("./scripts/run.sh", shell=True, stdin=slave, stdout=slave,
                            stderr=slave, cwd=ROOT, env=ENV, close_fds=True)
    os.close(slave)
    start = time.time()
    last_read = start
    next_input = 0
    while True:
        now = time.time()
        readable, _, _ = select.select([master], [], [], 0.1)
        if readable:
            try:
                chunk = os.read(master, 65536)
            except OSError:
                break
            if not chunk:
                break
            os.write(1, chunk)
            last_read = time.time()
        if next_input < len(inputs) and now - start >= 1.0 + next_input * delay:
            os.write(master, (inputs[next_input] + "\n").encode())
            next_input += 1
        if proc.poll() is not None and time.time() - last_read > 0.4:
            break
        if now - start > 40:
            break
    if proc.poll() is None:
        proc.terminate()
    os.close(master)


def main():
    print("Resetting sample data ...")
    subprocess.run("./scripts/run.sh --setup", shell=True, cwd=ROOT, env=ENV)
    print("\nStarting scripted demonstration ...\n")
    pty_session([
        "5", "1",              # collection summary
        "0",
        "1", "2", "design",    # search
        "0",
        "3", "1", "1",         # issue book 1 to member 1
        "5", "2",              # active loans
        "0",
        "4", "1",              # return loan 1
        "0",
    ])
    print("\nDemo complete. Run './scripts/verify.sh' to show the 24 automated checks.")


if __name__ == "__main__":
    main()
