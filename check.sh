#!/usr/bin/env bash
# check.sh — Alle Quality-Gates für f1-analytics in Reihe.
# Aus dem Projekt-Root ausführen: ./check.sh
#
# Optionen:
#   --frontend-only   nur Frontend-Checks (kein Maven/Backend)
#   --backend-only    nur Backend-Compile
#   --quick           skip Build und Tests (nur type-check + lints + compile)

set -u
set -o pipefail

GREEN=$'\033[0;32m'
RED=$'\033[0;31m'
YELLOW=$'\033[1;33m'
BLUE=$'\033[0;34m'
GRAY=$'\033[0;90m'
BOLD=$'\033[1m'
RESET=$'\033[0m'

ROOT="$(cd "$(dirname "$0")" && pwd)"
FRONTEND="$ROOT/frontend"
BACKEND="$ROOT/backend"

MODE="all"
QUICK=0
for arg in "$@"; do
  case "$arg" in
    --frontend-only) MODE="frontend" ;;
    --backend-only)  MODE="backend" ;;
    --quick)         QUICK=1 ;;
    -h|--help)
      echo "Usage: ./check.sh [--frontend-only|--backend-only] [--quick]"
      exit 0
      ;;
    *)
      echo "Unbekannte Option: $arg"
      exit 1
      ;;
  esac
done

labels=()
results=()
durations=()

run_check() {
  local label="$1"
  local dir="$2"
  shift 2
  local start end dur
  echo
  echo "${BLUE}${BOLD}── $label${RESET} ${GRAY}($dir)${RESET}"
  start=$(date +%s)
  if (cd "$dir" && "$@") 2>&1 | sed "s/^/  /"; then
    end=$(date +%s); dur=$((end - start))
    echo "${GREEN}✓ $label${RESET} ${GRAY}(${dur}s)${RESET}"
    results+=("ok")
  else
    end=$(date +%s); dur=$((end - start))
    echo "${RED}✗ $label fehlgeschlagen${RESET} ${GRAY}(${dur}s)${RESET}"
    results+=("fail")
  fi
  labels+=("$label")
  durations+=("$dur")
}

echo "${BOLD}f1-analytics · Check-Alles${RESET}"
echo "${GRAY}Root: $ROOT${RESET}"
echo "${GRAY}Modus: $MODE   Quick: $QUICK${RESET}"

# ───── Frontend ─────
if [[ "$MODE" == "all" || "$MODE" == "frontend" ]]; then
  if [[ ! -d "$FRONTEND" ]]; then
    echo "${RED}Frontend-Ordner nicht gefunden: $FRONTEND${RESET}"
    exit 1
  fi
  if [[ ! -d "$FRONTEND/node_modules" ]]; then
    echo "${YELLOW}node_modules fehlt — führe 'npm install' aus …${RESET}"
    (cd "$FRONTEND" && npm install --no-audit --no-fund) || {
      echo "${RED}npm install fehlgeschlagen${RESET}"; exit 1;
    }
  fi

  run_check "Frontend: TypeScript"    "$FRONTEND" npm run type-check
  run_check "Frontend: ESLint"        "$FRONTEND" npm run lint:eslint
  run_check "Frontend: oxlint"        "$FRONTEND" npm run lint:oxlint

  if [[ "$QUICK" -eq 0 ]]; then
    run_check "Frontend: Build (Vite)" "$FRONTEND" npm run build-only
    run_check "Frontend: Unit-Tests"   "$FRONTEND" npx vitest --run
  fi
fi

# ───── Backend ─────
if [[ "$MODE" == "all" || "$MODE" == "backend" ]]; then
  if [[ ! -d "$BACKEND" ]]; then
    echo "${YELLOW}Backend-Ordner nicht gefunden ($BACKEND) — übersprungen${RESET}"
  elif [[ ! -x "$BACKEND/mvnw" ]]; then
    echo "${YELLOW}mvnw nicht ausführbar — versuche chmod +x …${RESET}"
    chmod +x "$BACKEND/mvnw" 2>/dev/null || true
    if [[ ! -x "$BACKEND/mvnw" ]]; then
      echo "${RED}mvnw nicht ausführbar, überspringe Backend${RESET}"
    fi
  fi

  if [[ -x "$BACKEND/mvnw" ]]; then
    if [[ "$QUICK" -eq 1 ]]; then
      run_check "Backend: Compile" "$BACKEND" ./mvnw -q -DskipTests compile
    else
      run_check "Backend: Compile" "$BACKEND" ./mvnw -q -DskipTests compile
      run_check "Backend: Tests"   "$BACKEND" ./mvnw -q test
    fi
  fi
fi

# ───── Summary ─────
echo
echo "${BOLD}─────────────────────────────${RESET}"
echo "${BOLD}Ergebnis${RESET}"
echo "${BOLD}─────────────────────────────${RESET}"

failed=0
total=${#labels[@]}
for i in "${!labels[@]}"; do
  if [[ "${results[$i]}" == "ok" ]]; then
    printf "  ${GREEN}✓${RESET} %-32s ${GRAY}(%ss)${RESET}\n" "${labels[$i]}" "${durations[$i]}"
  else
    printf "  ${RED}✗${RESET} %-32s ${GRAY}(%ss)${RESET}\n" "${labels[$i]}" "${durations[$i]}"
    failed=$((failed + 1))
  fi
done

echo
if [[ "$failed" -eq 0 ]]; then
  echo "${GREEN}${BOLD}Alle $total Checks bestanden.${RESET}"
  exit 0
else
  passed=$((total - failed))
  echo "${RED}${BOLD}$failed von $total Checks fehlgeschlagen${RESET} ${GRAY}($passed bestanden)${RESET}"
  exit 1
fi