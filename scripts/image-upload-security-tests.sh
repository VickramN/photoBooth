#!/usr/bin/env bash
# Manual API test suite for the image upload security hardening work
# (commits f3d5261..25fd112). Requires ClamAV, Redis, and Postgres running
# per src/main/resources/application.properties, a real .env, and curl.
# jq is optional -- JSON field extraction doesn't depend on it, but if it's
# installed, the visual-inspection dumps get pretty-printed.
#
# Usage: BASE=http://localhost:9999 ./image-upload-security-tests.sh
#
# Every response is logged to scripts/logs/, and checks with a known
# expected HTTP status print PASS/FAIL with a summary at the end -- so the
# result is still readable even if the terminal window closes as soon as
# the script exits (e.g. double-clicking the file on Windows).

set -uo pipefail
set +H  # disable bash history expansion -- otherwise "!" in the JSON
        # payloads below (e.g. "Sup3rSecret!") gets misparsed as a
        # history-event reference if this ever runs in an interactive
        # shell, e.g. via double-clicking the file on Windows.

BASE="${BASE:-http://localhost:9999}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
# Fixture paths below are deliberately RELATIVE (not $SCRIPT_DIR-prefixed).
# The curl on this machine (mingw32-target, /mingw64/bin/curl) does not
# understand MSYS-style absolute paths like "/c/Users/..." for `-F
# file=@...` -- it silently fails with curl error 26 ("Failed to open/read
# local data"), which -w reports as HTTP status 000 with no response body.
# A relative path resolves against the real Win32 cwd and works correctly.
FIXTURES="test-fixtures"
LOG_DIR="logs"
mkdir -p "$FIXTURES" "$LOG_DIR"
LOG_FILE="$LOG_DIR/run-$(date +%Y%m%d-%H%M%S).log"
RUN_ID="$(date +%s)"

exec > >(tee -a "$LOG_FILE") 2>&1
echo "Logging full output to: $SCRIPT_DIR/$LOG_FILE"

if command -v jq >/dev/null 2>&1; then
  HAVE_JQ=1
else
  HAVE_JQ=0
  echo "Note: jq not found -- pretty-printing disabled, raw JSON will be shown instead."
fi

PASS_COUNT=0
FAIL_COUNT=0
FAILED_TESTS=()

# check_status <description> <expected_http_status> <curl args...>
# Runs curl, compares the response status to what's expected, and records
# a PASS/FAIL line. On failure the response body is also printed so the
# reason is visible without re-running anything.
#
# If curl itself never completed the request (exit code 26: "Failed to
# open/read local data from file/application" -- a fixture file vanished,
# got locked, or was quarantined by antivirus between being written and
# being read for upload), that's reported as SKIP rather than FAIL, since
# it's an environment/timing issue, not the API returning the wrong thing.
check_status() {
  local description="$1" expected="$2"
  shift 2
  local body status curl_exit
  body=$(mktemp)
  status=$(curl -s -o "$body" -w '%{http_code}' "$@")
  curl_exit=$?
  if [ "$curl_exit" -eq 26 ]; then
    echo "[SKIP] $description (curl couldn't read the local file -- exit 26; likely antivirus quarantine or a missing/locked fixture)"
  elif [ "$status" = "$expected" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "[PASS] $description (expected $expected, got $status)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    FAILED_TESTS+=("$description (expected $expected, got $status)")
    echo "[FAIL] $description (expected $expected, got $status)"
    echo "       response body: $(cat "$body")"
  fi
  rm -f "$body"
}

# extract_json_field <field name>
# Reads JSON from stdin and prints the first string value of that field.
# Works for a flat object ({"token":"..."}) or an array of flat objects
# ([{"id":"..."},...], where the array's first element's field comes first
# in the raw text) without depending on jq being installed.
extract_json_field() {
  grep -o "\"$1\":\"[^\"]*\"" | head -1 | sed -E 's/.*:"([^"]*)"/\1/'
}

# pretty_print
# Pipes stdin through jq if available, otherwise prints it as-is.
pretty_print() {
  if [ "$HAVE_JQ" = "1" ]; then
    jq .
  else
    cat
  fi
}

echo "=== Auth ==="
check_status "register tester1" 201 -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"tester1_$RUN_ID\",\"password\":\"Sup3rSecret!\"}"

TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"tester1_$RUN_ID\",\"password\":\"Sup3rSecret!\"}" | extract_json_field token)
echo "TOKEN=$TOKEN"

echo "=== reset-password route sanity check ==="
check_status "reset-password with bogus token" 400 -X POST "$BASE/auth/reset-password" \
  -H "Content-Type: application/json" \
  -d '{"token":"bogus","newPassword":"NewPass123!"}'

check_status "forgot-password for tester1" 200 -X POST "$BASE/auth/forgot-password" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"tester1_$RUN_ID\"}"

echo "=== Album setup ==="
ALBUM_ID=$(curl -s -X POST "$BASE/albums" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"albumName":"Test Trip","cityName":"Lisbon","countryName":"Portugal"}' | extract_json_field id)
echo "ALBUM_ID=$ALBUM_ID"

echo "=== Album responses should include a usable image url (inspect visually) ==="
curl -s "$BASE/albums/$ALBUM_ID" -H "Authorization: Bearer $TOKEN" | pretty_print
curl -s "$BASE/albums" -H "Authorization: Bearer $TOKEN" | pretty_print

echo "=== Happy path uploads ==="
# Uses the two real PNG fixtures checked in under test-fixtures/ (test1.png,
# test2.png) instead of synthetic/placeholder files that don't exist.
check_status "upload test1.png" 201 -X POST "$BASE/albums/$ALBUM_ID/images" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$FIXTURES/test1.png;type=image/png"

check_status "upload test2.png" 201 -X POST "$BASE/albums/$ALBUM_ID/images" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$FIXTURES/test2.png;type=image/png"

echo "=== List images (each should have a real presigned url, inspect visually) ==="
curl -s "$BASE/albums/$ALBUM_ID/images" -H "Authorization: Bearer $TOKEN" | pretty_print

echo "=== Rejected content types ==="
# These need real image.webp / image.gif fixtures under test-fixtures/, which
# aren't checked in -- skip cleanly instead of failing on a missing file if
# they're absent, but run for real the moment someone adds them.
if [ -f "$FIXTURES/image.webp" ]; then
  check_status "reject webp upload" 400 -X POST "$BASE/albums/$ALBUM_ID/images" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$FIXTURES/image.webp;type=image/webp"
else
  echo "[SKIP] reject webp upload (add test-fixtures/image.webp to enable)"
fi

if [ -f "$FIXTURES/image.gif" ]; then
  check_status "reject gif upload" 400 -X POST "$BASE/albums/$ALBUM_ID/images" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$FIXTURES/image.gif;type=image/gif"
else
  echo "[SKIP] reject gif upload (add test-fixtures/image.gif to enable)"
fi

echo "=== Content-type spoofing ==="
if [ -f "$FIXTURES/image.webp" ]; then
  # A disallowed type (webp) renamed to .jpg with a lying Content-Type --
  # should still be rejected, since validation sniffs real magic bytes.
  cp "$FIXTURES/image.webp" "$FIXTURES/spoofed.jpg"
  check_status "reject spoofed webp claiming to be jpeg" 400 -X POST "$BASE/albums/$ALBUM_ID/images" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$FIXTURES/spoofed.jpg;type=image/jpeg"
else
  echo "[SKIP] reject spoofed webp claiming to be jpeg (add test-fixtures/image.webp to enable)"
fi

# The inverse spoof: an ALLOWED type (test1.png) mislabeled as .webp with a
# lying Content-Type header. Should still be ACCEPTED, since content
# sniffing goes by real bytes, not the extension or the client's header.
cp "$FIXTURES/test1.png" "$FIXTURES/mislabeled.webp"
check_status "accept real png mislabeled as webp" 201 -X POST "$BASE/albums/$ALBUM_ID/images" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$FIXTURES/mislabeled.webp;type=image/webp"

echo "=== Oversized upload (>20MB) ==="
dd if=/dev/urandom of="$FIXTURES/huge.jpg" bs=1M count=25 2>/dev/null
check_status "reject oversized upload" 400 -X POST "$BASE/albums/$ALBUM_ID/images" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$FIXTURES/huge.jpg;type=image/jpeg"

# Malware detection is intentionally NOT exercised here with a file like
# EICAR: content-type validation (Tika, on the original bytes) runs before
# the AV scan, so a non-image "malicious" file like EICAR gets rejected as
# INVALID_IMAGE_TYPE and never reaches ClamAV -- and re-encoding (which runs
# after content-type validation but before the scan) regenerates the image
# from decoded pixel data, stripping any non-pixel payload anyway. So there's
# no black-box file that can reach the scanner via the public API to prove
# it flags something bad. ClamAV's behavior (including the fail-closed path
# on scanner errors) is covered by the unit tests in ClamAvClientTest and
# ImageServiceTest instead -- run `./mvnw -o test` for that coverage.
#
# To confirm fail-closed behavior manually anyway: stop the ClamAV
# service/container, then run this call and confirm it's rejected with a
# typed 503 SCAN_UNAVAILABLE instead of a raw 500:
#   check_status "reject upload when ClamAV is down" 503 -X POST "$BASE/albums/$ALBUM_ID/images" \
#     -H "Authorization: Bearer $TOKEN" -F "file=@$FIXTURES/test1.png;type=image/png"

echo "=== Rate limiting (expect 429 after upload.rate-limit.max-per-hour, default 100) ==="
for i in $(seq 1 105); do
  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/albums/$ALBUM_ID/images" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$FIXTURES/test1.png;type=image/png")
  echo "upload #$i -> $status"
done

echo "=== Ownership / cross-tenant checks ==="
check_status "register tester2" 201 -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"username\":\"tester2_$RUN_ID\",\"password\":\"Sup3rSecret2!\"}"
TOKEN2=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"tester2_$RUN_ID\",\"password\":\"Sup3rSecret2!\"}" | extract_json_field token)

check_status "tester2 cannot list tester1's album images" 404 \
  "$BASE/albums/$ALBUM_ID/images" -H "Authorization: Bearer $TOKEN2"
check_status "tester2 cannot upload into tester1's album" 404 -X POST "$BASE/albums/$ALBUM_ID/images" \
  -H "Authorization: Bearer $TOKEN2" \
  -F "file=@$FIXTURES/test1.png;type=image/png"

echo "=== Delete ==="
IMAGE_ID=$(curl -s "$BASE/albums/$ALBUM_ID/images" -H "Authorization: Bearer $TOKEN" | extract_json_field id)
check_status "tester1 deletes own image" 204 -X DELETE "$BASE/albums/$ALBUM_ID/images/$IMAGE_ID" \
  -H "Authorization: Bearer $TOKEN"
check_status "tester2 cannot delete tester1's (already-deleted) image" 404 \
  -X DELETE "$BASE/albums/$ALBUM_ID/images/$IMAGE_ID" -H "Authorization: Bearer $TOKEN2"

echo "=== Admin-only endpoint ==="
check_status "non-admin tester1 forbidden from admin endpoint" 403 \
  "$BASE/albums/admin/all" -H "Authorization: Bearer $TOKEN"

echo "=== No auth header at all ==="
# No httpBasic/formLogin is configured, so Spring Security's default
# Http403ForbiddenEntryPoint fires for unauthenticated requests (403, not 401).
check_status "no auth header rejected" 403 "$BASE/albums"

echo "=== SUMMARY ==="
echo "Passed: $PASS_COUNT  Failed: $FAIL_COUNT"
if [ "$FAIL_COUNT" -gt 0 ]; then
  printf '  - %s\n' "${FAILED_TESTS[@]}"
fi
echo "Full log: $LOG_FILE"

read -rp "Press Enter to close..." _ || true
