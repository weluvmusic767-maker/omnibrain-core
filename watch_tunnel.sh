#!/bin/bash
# Background script to verify tunnel status and auto-reconnect if dropped

LOG_FILE="docs/tunnel_watch.log"

while true; do
    if ! pgrep -f "cloudflared" > /dev/null; then
        echo "[$(date)] Tunnel process lost. Restarting..." >> "$LOG_FILE"
        bash ~/dev/start_extension_pack.sh >> "$LOG_FILE" 2>&1
    fi
    sleep 30
done
