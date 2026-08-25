#!/usr/bin/env bash
set -euo pipefail

# Build, install, and launch debug APKs for phone and Wear OS devices.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOBILE_APK="$ROOT_DIR/mobile/build/outputs/apk/debug/mobile-debug.apk"
WEAR_APK="$ROOT_DIR/wear/build/outputs/apk/debug/wear-debug.apk"
APP_PKG="com.totonoi.sauna"
MOBILE_ACTIVITY="com.totonoi.sauna.mobile.MainActivity"
WEAR_ACTIVITY="com.totonoi.sauna.wear.MainActivity"

PHONE_SERIAL="${PHONE_SERIAL:-}"
WATCH_SERIAL="${WATCH_SERIAL:-}"
SKIP_BUILD="${SKIP_BUILD:-0}"
WATCH_IP="${WATCH_IP:-}"
WATCH_PAIR_PORT="${WATCH_PAIR_PORT:-}"
WATCH_PAIR_CODE="${WATCH_PAIR_CODE:-}"
WATCH_ADB_PORT="${WATCH_ADB_PORT:-}"
WATCH_CONNECT_ONLY="${WATCH_CONNECT_ONLY:-}"
ADB_RETRY_COUNT="${ADB_RETRY_COUNT:-3}"
ADB_RETRY_DELAY_SEC="${ADB_RETRY_DELAY_SEC:-2}"
ADB_CMD_TIMEOUT_SEC="${ADB_CMD_TIMEOUT_SEC:-20}"
LOG_FILE="${LOG_FILE:-}"
INSTALL_TARGET="${INSTALL_TARGET:-auto}"

usage() {
  cat <<'EOF'
Usage:
  scripts/debug-install-and-run.sh [--phone <serial>] [--watch <serial>] [--skip-build]
  scripts/debug-install-and-run.sh --watch-connect <ip:port> [--phone <serial>] [--skip-build]
  scripts/debug-install-and-run.sh --watch-ip <ip> --pair-port <port> --pair-code <code> [--adb-port <port>] [--phone <serial>] [--skip-build]

Options:
  --phone <serial>   adb serial for phone (optional if exactly one non-watch device is connected)
  --watch <serial>   adb serial for watch (optional if exactly one watch device is connected)
  --watch-connect <ip:port>
                     run 'adb connect <ip:port>' before install and use it as watch serial
  --watch-ip <ip>    watch IP for wireless pairing/debugging (example: 192.168.1.24)
  --pair-port <port> watch pairing port shown in Wireless debugging UI
  --pair-code <code> watch pairing code shown in Wireless debugging UI
  --adb-port <port>  watch ADB debug port (from watch's 'IP address and port')
  --retry <n>        retry count for adb connect/pair/install/launch (default: 3)
  --retry-delay <s>  delay between retries in seconds (default: 2)
  --timeout <s>      timeout for each adb command in seconds (default: 20)
  --target <mode>    install target: auto|both|watch-only|phone-only (default: auto)
  --log-file <path>  write output logs to file (default: logs/debug-<timestamp>.log)
  --skip-build       skip gradle build and use existing APKs

Environment variables:
  PHONE_SERIAL, WATCH_SERIAL, SKIP_BUILD=1,
  WATCH_IP, WATCH_PAIR_PORT, WATCH_PAIR_CODE, WATCH_ADB_PORT, WATCH_CONNECT_ONLY,
  ADB_RETRY_COUNT, ADB_RETRY_DELAY_SEC, ADB_CMD_TIMEOUT_SEC, LOG_FILE, INSTALL_TARGET

Examples:
  scripts/debug-install-and-run.sh
  scripts/debug-install-and-run.sh --target watch-only --watch-ip 192.168.1.6 --adb-port 44259
  scripts/debug-install-and-run.sh --phone R58M... --watch 192.168.1.24:39861
  scripts/debug-install-and-run.sh --watch-connect 192.168.1.24:39861
  scripts/debug-install-and-run.sh --watch-ip 192.168.1.24 --pair-port 37425 --pair-code 123456 --adb-port 39861
EOF
}

run_with_retry() {
  local label="$1"
  shift

  local attempt=1
  while (( attempt <= ADB_RETRY_COUNT )); do
    echo "[$label] attempt ${attempt}/${ADB_RETRY_COUNT}"
    if command -v timeout >/dev/null 2>&1; then
      if timeout "${ADB_CMD_TIMEOUT_SEC}s" "$@"; then
        return 0
      fi
    else
      if "$@"; then
        return 0
      fi
    fi

    if (( attempt == ADB_RETRY_COUNT )); then
      break
    fi
    sleep "$ADB_RETRY_DELAY_SEC"
    attempt=$((attempt + 1))
  done

  echo "[$label] failed after ${ADB_RETRY_COUNT} attempts." >&2
  return 1
}

run_with_retry_no_timeout() {
  local label="$1"
  shift

  local attempt=1
  while (( attempt <= ADB_RETRY_COUNT )); do
    echo "[$label] attempt ${attempt}/${ADB_RETRY_COUNT}"
    if "$@"; then
      return 0
    fi

    if (( attempt == ADB_RETRY_COUNT )); then
      break
    fi
    sleep "$ADB_RETRY_DELAY_SEC"
    attempt=$((attempt + 1))
  done

  echo "[$label] failed after ${ADB_RETRY_COUNT} attempts." >&2
  return 1
}

setup_logging() {
  if [[ -z "$LOG_FILE" ]]; then
    local ts
    ts="$(date +%Y%m%d-%H%M%S)"
    LOG_FILE="$ROOT_DIR/logs/debug-${ts}.log"
  fi

  mkdir -p "$(dirname "$LOG_FILE")"
  touch "$LOG_FILE"
  exec > >(tee -a "$LOG_FILE") 2>&1
  echo "[log] Writing logs to: $LOG_FILE"
}

normalize_watch_endpoint() {
  local endpoint="$1"
  if [[ "$endpoint" == *:* ]]; then
    echo "$endpoint"
  else
    echo "${endpoint}:5555"
  fi
}

extract_watch_ip() {
  local endpoint="$1"
  echo "${endpoint%%:*}"
}

extract_watch_port() {
  local endpoint="$1"
  if [[ "$endpoint" == *:* ]]; then
    echo "${endpoint##*:}"
  else
    echo "5555"
  fi
}

detect_watch_adb_port_from_mdns() {
  local ip="$1"
  if ! adb mdns services >/dev/null 2>&1; then
    return 1
  fi

  local line
  line="$(adb mdns services 2>/dev/null | grep "_adb-tls-connect" | grep "${ip}:" | head -n 1 || true)"
  if [[ -z "$line" ]]; then
    return 1
  fi

  echo "$line" | sed -E 's/.*:([0-9]{2,5}).*/\1/'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --phone)
      PHONE_SERIAL="$2"
      shift 2
      ;;
    --watch)
      WATCH_SERIAL="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --watch-connect)
      WATCH_CONNECT_ONLY="$2"
      shift 2
      ;;
    --watch-ip)
      WATCH_IP="$2"
      shift 2
      ;;
    --pair-port)
      WATCH_PAIR_PORT="$2"
      shift 2
      ;;
    --pair-code)
      WATCH_PAIR_CODE="$2"
      shift 2
      ;;
    --adb-port)
      WATCH_ADB_PORT="$2"
      shift 2
      ;;
    --retry)
      ADB_RETRY_COUNT="$2"
      shift 2
      ;;
    --retry-delay)
      ADB_RETRY_DELAY_SEC="$2"
      shift 2
      ;;
    --timeout)
      ADB_CMD_TIMEOUT_SEC="$2"
      shift 2
      ;;
    --target)
      INSTALL_TARGET="$2"
      shift 2
      ;;
    --log-file)
      LOG_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

setup_logging

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Add Android platform-tools to PATH." >&2
  exit 1
fi

if [[ -n "$WATCH_CONNECT_ONLY" ]]; then
  if [[ "$WATCH_CONNECT_ONLY" == *:* ]]; then
    WATCH_CONNECT_ONLY="$(normalize_watch_endpoint "$WATCH_CONNECT_ONLY")"
  else
    mdns_port="$(detect_watch_adb_port_from_mdns "$WATCH_CONNECT_ONLY" || true)"
    if [[ -n "$mdns_port" ]]; then
      WATCH_CONNECT_ONLY="${WATCH_CONNECT_ONLY}:${mdns_port}"
      echo "[prep] Auto-detected watch adb port via mDNS: $mdns_port"
    else
      WATCH_CONNECT_ONLY="${WATCH_CONNECT_ONLY}:5555"
      echo "[prep] Could not auto-detect adb port; falling back to :5555"
    fi
  fi
  echo "[prep] Connecting to watch over Wi-Fi: $WATCH_CONNECT_ONLY"
  run_with_retry "watch-connect" adb connect "$WATCH_CONNECT_ONLY"
  WATCH_SERIAL="$WATCH_CONNECT_ONLY"
fi

if [[ -n "$WATCH_IP" ]]; then
  if [[ -z "$WATCH_ADB_PORT" ]]; then
    WATCH_ADB_PORT="$(detect_watch_adb_port_from_mdns "$WATCH_IP" || true)"
    if [[ -n "$WATCH_ADB_PORT" ]]; then
      echo "[prep] Auto-detected watch adb port via mDNS: $WATCH_ADB_PORT"
    else
      read -r -p "Enter watch adb port (from watch 'IP address and port'): " WATCH_ADB_PORT
    fi
  fi

  if [[ -z "$WATCH_ADB_PORT" ]]; then
    echo "Watch adb port is required." >&2
    exit 1
  fi

  if [[ -z "$WATCH_PAIR_PORT" && -z "$WATCH_PAIR_CODE" ]]; then
    read -r -p "Need adb pair first? [y/N]: " pair_choice
    if [[ "$pair_choice" == "y" || "$pair_choice" == "Y" ]]; then
      read -r -p "Enter watch pair port: " WATCH_PAIR_PORT
      read -r -p "Enter watch pair code: " WATCH_PAIR_CODE
    fi
  fi

  if [[ -n "$WATCH_PAIR_PORT" || -n "$WATCH_PAIR_CODE" ]]; then
    if [[ -z "$WATCH_PAIR_PORT" ]]; then
      read -r -p "Enter watch pair port: " WATCH_PAIR_PORT
    fi
    if [[ -z "$WATCH_PAIR_CODE" ]]; then
      read -r -p "Enter watch pair code: " WATCH_PAIR_CODE
    fi
    echo "[prep] Pairing watch: ${WATCH_IP}:${WATCH_PAIR_PORT}"
    run_with_retry "watch-pair" adb pair "${WATCH_IP}:${WATCH_PAIR_PORT}" "$WATCH_PAIR_CODE"
  fi

  echo "[prep] Connecting watch adb endpoint: ${WATCH_IP}:${WATCH_ADB_PORT}"
  run_with_retry "watch-connect" adb connect "${WATCH_IP}:${WATCH_ADB_PORT}" >/dev/null
  WATCH_SERIAL="${WATCH_IP}:${WATCH_ADB_PORT}"
fi

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "[build] Building debug APKs..."
  (
    cd "$ROOT_DIR"
    ./gradlew :mobile:assembleDebug :wear:assembleDebug
  )
fi

if [[ ! -f "$MOBILE_APK" ]]; then
  echo "Mobile APK not found: $MOBILE_APK" >&2
  exit 1
fi
if [[ ! -f "$WEAR_APK" ]]; then
  echo "Wear APK not found: $WEAR_APK" >&2
  exit 1
fi

mapfile -t DEV_LINES < <(adb devices -l | tail -n +2 | grep -E '\sdevice\b' || true)
if [[ ${#DEV_LINES[@]} -eq 0 ]]; then
  echo "No adb devices in 'device' state." >&2
  exit 1
fi

if [[ "$INSTALL_TARGET" != "auto" && "$INSTALL_TARGET" != "both" && "$INSTALL_TARGET" != "watch-only" && "$INSTALL_TARGET" != "phone-only" ]]; then
  echo "Invalid --target: $INSTALL_TARGET (use auto|both|watch-only|phone-only)" >&2
  exit 1
fi

if [[ -z "$PHONE_SERIAL" || -z "$WATCH_SERIAL" ]]; then
  declare -a WATCH_CANDIDATES=()
  declare -a PHONE_CANDIDATES=()

  for line in "${DEV_LINES[@]}"; do
    serial="${line%% *}"
    if [[ "$line" == *"features:watch"* ]] || [[ "$line" == *"model:Pixel_Watch"* ]] || [[ "$line" == *"product:wear"* ]]; then
      WATCH_CANDIDATES+=("$serial")
    else
      PHONE_CANDIDATES+=("$serial")
    fi
  done

  if [[ -z "$PHONE_SERIAL" ]]; then
    if [[ ${#PHONE_CANDIDATES[@]} -eq 1 ]]; then
      PHONE_SERIAL="${PHONE_CANDIDATES[0]}"
    fi
  fi
  if [[ -z "$WATCH_SERIAL" ]]; then
    if [[ ${#WATCH_CANDIDATES[@]} -eq 1 ]]; then
      WATCH_SERIAL="${WATCH_CANDIDATES[0]}"
    fi
  fi
fi

if [[ -n "$WATCH_SERIAL" ]]; then
  watch_present=0
  for line in "${DEV_LINES[@]}"; do
    serial="${line%% *}"
    if [[ "$serial" == "$WATCH_SERIAL" ]]; then
      watch_present=1
      break
    fi
  done

  if [[ $watch_present -eq 0 ]]; then
    watch_ip="$(extract_watch_ip "$WATCH_SERIAL")"
    for line in "${DEV_LINES[@]}"; do
      serial="${line%% *}"
      if [[ "$serial" == "${watch_ip}:"* ]]; then
        echo "[target] Remapping watch serial from '$WATCH_SERIAL' to '$serial'"
        WATCH_SERIAL="$serial"
        watch_present=1
        break
      fi
    done
  fi

  if [[ $watch_present -eq 0 ]]; then
    echo "Watch serial '$WATCH_SERIAL' not found in adb device list." >&2
    echo "Connected devices:" >&2
    adb devices -l >&2

    watch_port="$(extract_watch_port "$WATCH_SERIAL")"
    if [[ "$watch_port" != "5555" ]]; then
      echo "Tip: this port may be a PAIRING port, not an ADB debug port." >&2
      echo "Use one of these:" >&2
      echo "  1) scripts/debug-install-and-run.sh --watch-ip ${WATCH_SERIAL%%:*}  (interactive pair/connect)" >&2
      echo "  2) scripts/debug-install-and-run.sh --watch-ip ${WATCH_SERIAL%%:*} --pair-port <pair_port> --pair-code <code> --adb-port <adb_port>" >&2
      echo "  3) scripts/debug-install-and-run.sh --watch-connect <watch_ip>:<adb_port>" >&2
    else
      echo "Tip: use the exact 'IP address and port' shown on watch Wireless debugging screen." >&2
    fi
    exit 1
  fi
fi

if [[ "$INSTALL_TARGET" == "both" ]]; then
  if [[ -z "$PHONE_SERIAL" || -z "$WATCH_SERIAL" ]]; then
    echo "Target=both requires both phone and watch devices." >&2
    echo "Connected devices:" >&2
    adb devices -l >&2
    exit 1
  fi
elif [[ "$INSTALL_TARGET" == "watch-only" ]]; then
  if [[ -z "$WATCH_SERIAL" ]]; then
    echo "Target=watch-only requires a watch device." >&2
    echo "Connected devices:" >&2
    adb devices -l >&2
    exit 1
  fi
elif [[ "$INSTALL_TARGET" == "phone-only" ]]; then
  if [[ -z "$PHONE_SERIAL" ]]; then
    echo "Target=phone-only requires a phone device." >&2
    echo "Connected devices:" >&2
    adb devices -l >&2
    exit 1
  fi
else
  # auto mode: prefer both, otherwise whichever is available
  if [[ -n "$PHONE_SERIAL" && -n "$WATCH_SERIAL" ]]; then
    INSTALL_TARGET="both"
  elif [[ -n "$WATCH_SERIAL" ]]; then
    INSTALL_TARGET="watch-only"
  elif [[ -n "$PHONE_SERIAL" ]]; then
    INSTALL_TARGET="phone-only"
  else
    echo "Could not resolve target devices." >&2
    adb devices -l >&2
    exit 1
  fi
fi

echo "[target] Mode: $INSTALL_TARGET"
if [[ -n "$PHONE_SERIAL" ]]; then
  echo "[target] Using phone: $PHONE_SERIAL"
fi
if [[ -n "$WATCH_SERIAL" ]]; then
  echo "[target] Using watch: $WATCH_SERIAL"
fi

if [[ "$INSTALL_TARGET" == "both" || "$INSTALL_TARGET" == "phone-only" ]]; then
  echo "[install] Installing mobile app..."
  run_with_retry_no_timeout "install-mobile" adb -s "$PHONE_SERIAL" install -r "$MOBILE_APK"
fi

if [[ "$INSTALL_TARGET" == "both" || "$INSTALL_TARGET" == "watch-only" ]]; then
  echo "[install] Installing wear app..."
  run_with_retry_no_timeout "install-wear" adb -s "$WATCH_SERIAL" install -r "$WEAR_APK"
fi

echo "[launch] Launching apps..."
if [[ "$INSTALL_TARGET" == "both" || "$INSTALL_TARGET" == "phone-only" ]]; then
  run_with_retry_no_timeout "launch-mobile" adb -s "$PHONE_SERIAL" shell am start -W -n "${APP_PKG}/${MOBILE_ACTIVITY}" >/dev/null
fi
if [[ "$INSTALL_TARGET" == "both" || "$INSTALL_TARGET" == "watch-only" ]]; then
  run_with_retry_no_timeout "launch-wear" adb -s "$WATCH_SERIAL" shell am start -W -n "${APP_PKG}/${WEAR_ACTIVITY}" >/dev/null
fi

echo "Done."
if [[ -n "$PHONE_SERIAL" ]]; then
  echo "Phone logs: adb -s $PHONE_SERIAL logcat"
fi
if [[ -n "$WATCH_SERIAL" ]]; then
  echo "Watch logs: adb -s $WATCH_SERIAL logcat"
fi
echo "Session log file: $LOG_FILE"
