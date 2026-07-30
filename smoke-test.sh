#!/usr/bin/env bash
# Quick smoke test of the whole stack through the API Gateway (localhost:8080).
set -e
GATEWAY=http://localhost:8080

echo "== Register/login as admin =="
ADMIN_TOKEN=$(curl -s -X POST "$GATEWAY/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")
echo "admin token acquired"

echo "== Login as student =="
STUDENT_TOKEN=$(curl -s -X POST "$GATEWAY/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"student","password":"student123"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")
echo "student token acquired"

echo "== Unauthorised request (no token) -> expect 401 =="
curl -s -o /dev/null -w "status: %{http_code}\n" "$GATEWAY/api/equipment"

echo "== Create equipment as ADMIN =="
EQUIP_ID=$(curl -s -X POST "$GATEWAY/api/equipment" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Oscilloscope","category":"Electronics","location":"Lab A","status":"AVAILABLE","conditionNotes":"Good"}' \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
echo "created equipment id=$EQUIP_ID"

echo "== Student attempts to create equipment -> expect 403 =="
curl -s -o /dev/null -w "status: %{http_code}\n" -X POST "$GATEWAY/api/equipment" -H "Authorization: Bearer $STUDENT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"X","category":"Y","location":"Z","status":"AVAILABLE"}'

echo "== Student creates a booking (sync call to Equipment Service) =="
START=$(date -u -v+1H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ)
END=$(date -u -v+2H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+2 hour' +%Y-%m-%dT%H:%M:%SZ)
BOOKING_ID=$(curl -s -X POST "$GATEWAY/api/bookings" -H "Authorization: Bearer $STUDENT_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"equipmentId\":$EQUIP_ID,\"startTime\":\"$START\",\"endTime\":\"$END\"}" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
echo "created booking id=$BOOKING_ID"

echo "== Complete booking with fault reported -> publishes MaintenanceRequested event =="
curl -s -X PUT "$GATEWAY/api/bookings/$BOOKING_ID/complete" -H "Authorization: Bearer $STUDENT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"faultReported":true,"issueDescription":"Screen flickering"}'
echo ""

sleep 2
echo "== Check equipment status flipped to UNDER_MAINTENANCE =="
curl -s "$GATEWAY/api/equipment/$EQUIP_ID" -H "Authorization: Bearer $STUDENT_TOKEN"
echo ""

echo "== Check maintenance record was created =="
curl -s "$GATEWAY/api/maintenance-records" -H "Authorization: Bearer $STUDENT_TOKEN"
echo ""

echo "Smoke test complete."
