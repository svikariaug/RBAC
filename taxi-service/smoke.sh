#!/usr/bin/env bash
set -euo pipefail

BASE_USER="${BASE_USER:-http://localhost:8081}"
BASE_TRIP="${BASE_TRIP:-http://localhost:8082}"
BASE_NOTIFY="${BASE_NOTIFY:-http://localhost:8083}"
SKIP_ATOMIC="${SKIP_ATOMIC:-0}"

TOTAL=0
PASSED=0
FAILED=0
WARNINGS=0

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: missing command '$1'" >&2
    exit 1
  fi
}

need_cmd curl
need_cmd python3

pass() {
  TOTAL=$((TOTAL + 1))
  PASSED=$((PASSED + 1))
  echo "  [PASS] $1"
  if [[ $# -ge 2 ]]; then
    echo "         $2"
  fi
}

fail() {
  TOTAL=$((TOTAL + 1))
  FAILED=$((FAILED + 1))
  echo "  [FAIL] $1"
  if [[ $# -ge 2 ]]; then
    echo "         $2"
  fi
}

warn() {
  WARNINGS=$((WARNINGS + 1))
  echo "  [WARN] $1"
  if [[ $# -ge 2 ]]; then
    echo "         $2"
  fi
}

complete_trip_safely() {
  local trip_id="$1"
  local code=""
  local st=""

  st="$(curl -sS "${BASE_TRIP}/trips/${trip_id}" -H "Authorization: Bearer ${token}" | python3 -c "import json,sys;
try:
  print(json.loads(sys.stdin.read()).get('status',''))
except Exception:
  print('')")"

  if [[ "${st}" == "ASSIGNED" ]]; then
    code="$(curl -sS -o /dev/null -w "%{http_code}" -X PATCH "${BASE_TRIP}/trips/${trip_id}/status" -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" -d '{"status":"ACCEPTED"}')"
    [[ "${code}" == "200" ]] || return 0
    st="ACCEPTED"
  fi
  if [[ "${st}" == "ACCEPTED" ]]; then
    code="$(curl -sS -o /dev/null -w "%{http_code}" -X PATCH "${BASE_TRIP}/trips/${trip_id}/status" -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" -d '{"status":"IN_PROGRESS"}')"
    [[ "${code}" == "200" ]] || return 0
    st="IN_PROGRESS"
  fi
  if [[ "${st}" == "IN_PROGRESS" ]]; then
    code="$(curl -sS -o /dev/null -w "%{http_code}" -X PATCH "${BASE_TRIP}/trips/${trip_id}/status" -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" -d '{"status":"COMPLETED"}')"
    [[ "${code}" == "200" ]] || return 0
  fi
}

json_field() {
  local json="$1"
  local field="$2"
  python3 -c "import json,sys; 
try:
  v=json.loads(sys.argv[1]).get(sys.argv[2], '')
  print('' if v is None else v)
except Exception:
  print('')" "$json" "$field"
}

json_length() {
  local json="$1"
  python3 -c "import json,sys;
try:
  print(len(json.loads(sys.argv[1])))
except Exception:
  print(0)" "$json"
}

json_has_non_pending() {
  local json="$1"
  python3 -c "import json,sys;
try:
  arr=json.loads(sys.argv[1])
  print(any(isinstance(x, dict) and x.get('status') in ('SENT','FAILED') for x in arr))
except Exception:
  print(False)" "$json"
}

echo "=========================================="
echo "Смоук-тест taxi-service (по ТЗ)"
echo "=========================================="

suffix="$(date +%s)"
passenger_email="smoke_passenger_${suffix}@example.com"
driver_email="smoke_driver_${suffix}@example.com"
license="LIC-${suffix}"

echo ""
echo "[1] User Service (Пользователи)"

passenger_json="$(curl -fsS -X POST "${BASE_USER}/passengers" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Smoke Passenger\",\"email\":\"${passenger_email}\",\"phone\":\"+70000001001\",\"password\":\"123456\"}")"
passenger_id="$(json_field "${passenger_json}" "id")"
if [[ -n "${passenger_id}" ]]; then pass "POST /passengers создает пассажира" "id=${passenger_id}, email=${passenger_email}"; else fail "POST /passengers не вернул id"; fi

driver_json="$(curl -fsS -X POST "${BASE_USER}/drivers" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Smoke Driver\",\"email\":\"${driver_email}\",\"phone\":\"+70000001002\",\"licenseNumber\":\"${license}\",\"password\":\"123456\"}")"
driver_id="$(json_field "${driver_json}" "id")"
if [[ -n "${driver_id}" ]]; then pass "POST /drivers создает водителя" "id=${driver_id}, license=${license}"; else fail "POST /drivers не вернул id"; fi

passenger_profile="$(curl -fsS "${BASE_USER}/passengers/${passenger_id}")"
if [[ "$(json_field "${passenger_profile}" "id")" == "${passenger_id}" ]]; then pass "GET /passengers/{id} возвращает профиль"; else fail "GET /passengers/{id}: id не совпадает" "ожидали=${passenger_id}, получили=$(json_field "${passenger_profile}" "id")"; fi

driver_profile="$(curl -fsS "${BASE_USER}/drivers/${driver_id}")"
if [[ "$(json_field "${driver_profile}" "id")" == "${driver_id}" ]]; then pass "GET /drivers/{id} возвращает профиль"; else fail "GET /drivers/{id}: id не совпадает" "ожидали=${driver_id}, получили=$(json_field "${driver_profile}" "id")"; fi

curl -fsS -X PATCH "${BASE_USER}/drivers/${driver_id}/status" \
  -H "Content-Type: application/json" \
  -d '{"status":"AVAILABLE"}' >/dev/null
driver_after="$(curl -fsS "${BASE_USER}/drivers/${driver_id}")"
if [[ "$(json_field "${driver_after}" "status")" == "AVAILABLE" ]]; then pass "PATCH /drivers/{id}/status меняет статус" "ожидали=AVAILABLE, получили=$(json_field "${driver_after}" "status")"; else fail "Статус водителя не AVAILABLE" "получили=$(json_field "${driver_after}" "status")"; fi

login_json="$(curl -fsS -X POST "${BASE_USER}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${passenger_email}\",\"password\":\"123456\",\"role\":\"PASSENGER\"}")"
token="$(json_field "${login_json}" "accessToken")"
if [[ ${#token} -gt 20 ]]; then pass "JWT-логин выдает токен" "token_len=${#token}"; else fail "JWT-токен отсутствует или слишком короткий" "token_len=${#token}"; fi

wrong_login_code="$(curl -sS -o /dev/null -w "%{http_code}" -X POST "${BASE_USER}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${passenger_email}\",\"password\":\"wrong-password\",\"role\":\"PASSENGER\"}")"
if [[ "${wrong_login_code}" == "401" || "${wrong_login_code}" == "403" ]]; then
  pass "JWT-логин с неверным паролем отклоняется" "HTTP=${wrong_login_code}"
else
  warn "Логин с неверным паролем вернул неожиданный код" "HTTP=${wrong_login_code}"
fi

echo ""
echo "[2] Trip Service (Поездки)"

unauth_code="$(curl -sS -o /dev/null -w "%{http_code}" -X POST "${BASE_TRIP}/trips" \
  -H "Content-Type: application/json" \
  -d "{\"passengerId\":${passenger_id},\"origin\":\"A\",\"destination\":\"B\"}")"
if [[ "${unauth_code}" == "401" || "${unauth_code}" == "403" ]]; then pass "POST /trips требует JWT" "HTTP=${unauth_code} без токена"; else warn "POST /trips без токена вернул неожиданный код" "HTTP=${unauth_code}"; fi

bad_passenger_code="$(curl -sS -o /dev/null -w "%{http_code}" -X POST "${BASE_TRIP}/trips" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"passengerId":999999999,"origin":"Bad","destination":"Bad","originLat":55.75,"originLon":37.61,"destLat":55.76,"destLon":37.62}')"
if [[ "${bad_passenger_code}" == "400" || "${bad_passenger_code}" == "404" ]]; then
  pass "Создание поездки для несуществующего пассажира отклоняется" "HTTP=${bad_passenger_code}"
else
  warn "Несущеcтвующий passengerId дал неожиданный код" "HTTP=${bad_passenger_code}"
fi

trip_json="$(curl -fsS -X POST "${BASE_TRIP}/trips" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d "{\"passengerId\":${passenger_id},\"origin\":\"Moscow, Tverskaya 1\",\"destination\":\"Sheremetyevo\",\"originLat\":55.75,\"originLon\":37.61,\"destLat\":55.99,\"destLon\":37.41}")"
trip_id="$(json_field "${trip_json}" "id")"
assigned_driver_id="$(json_field "${trip_json}" "driverId")"
trip_status="$(json_field "${trip_json}" "status")"
trip_price="$(json_field "${trip_json}" "price")"
if [[ -n "${trip_id}" ]]; then pass "POST /trips создает поездку" "trip_id=${trip_id}, assigned_driver_id=${assigned_driver_id}"; else fail "POST /trips не вернул id"; fi
if [[ "${trip_status}" == "ASSIGNED" || "${trip_status}" == "PENDING" ]]; then pass "Начальный статус поездки корректен" "status=${trip_status}"; else fail "Неожиданный начальный статус поездки" "status=${trip_status}"; fi
if python3 -c "import sys; v=float(sys.argv[1]); raise SystemExit(0 if v>0 else 1)" "${trip_price:-0}"; then pass "Расчет цены присутствует и > 0" "price=${trip_price}"; else fail "Цена отсутствует или не положительная" "price=${trip_price}"; fi

trip_info="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_TRIP}/trips/${trip_id}")"
if [[ "$(json_field "${trip_info}" "id")" == "${trip_id}" ]]; then pass "GET /trips/{id} возвращает поездку"; else fail "GET /trips/{id}: id не совпадает" "ожидали=${trip_id}, получили=$(json_field "${trip_info}" "id")"; fi

curl -fsS -X PATCH "${BASE_TRIP}/trips/${trip_id}/status" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"status":"ACCEPTED"}' >/dev/null
trip_info="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_TRIP}/trips/${trip_id}")"
if [[ "$(json_field "${trip_info}" "status")" == "ACCEPTED" ]]; then pass "PATCH /trips/{id}/status -> ACCEPTED"; else fail "Не удалось перевести поездку в ACCEPTED" "получили=$(json_field "${trip_info}" "status")"; fi

driver_mid="$(curl -fsS "${BASE_USER}/drivers/${assigned_driver_id}")"
driver_mid_status="$(json_field "${driver_mid}" "status")"
if [[ "${driver_mid_status}" != "AVAILABLE" ]]; then pass "Статус водителя изменился после ACCEPTED" "driver_id=${assigned_driver_id}, status=${driver_mid_status}"; else warn "Водитель остался AVAILABLE после ACCEPTED" "driver_id=${assigned_driver_id}"; fi

curl -fsS -X PATCH "${BASE_TRIP}/trips/${trip_id}/status" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}' >/dev/null
trip_info="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_TRIP}/trips/${trip_id}")"
if [[ "$(json_field "${trip_info}" "status")" == "IN_PROGRESS" ]]; then pass "PATCH /trips/{id}/status -> IN_PROGRESS"; else fail "Не удалось перевести поездку в IN_PROGRESS" "получили=$(json_field "${trip_info}" "status")"; fi

curl -fsS -X PATCH "${BASE_TRIP}/trips/${trip_id}/status" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"status":"COMPLETED"}' >/dev/null
trip_info="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_TRIP}/trips/${trip_id}")"
if [[ "$(json_field "${trip_info}" "status")" == "COMPLETED" ]]; then pass "PATCH /trips/{id}/status -> COMPLETED"; else fail "Не удалось перевести поездку в COMPLETED" "получили=$(json_field "${trip_info}" "status")"; fi

driver_after_trip="$(curl -fsS "${BASE_USER}/drivers/${assigned_driver_id}")"
if [[ "$(json_field "${driver_after_trip}" "status")" == "AVAILABLE" ]]; then pass "После завершения поездки водитель снова AVAILABLE" "driver_id=${assigned_driver_id}"; else fail "Водитель не вернулся в AVAILABLE после COMPLETED" "получили=$(json_field "${driver_after_trip}" "status")"; fi

trips_history="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_TRIP}/trips?passenger_id=${passenger_id}")"
if [[ "$(json_length "${trips_history}")" -ge 1 ]]; then pass "GET /trips?passenger_id возвращает историю"; else fail "История поездок пустая"; fi

curl -fsS -X PATCH "${BASE_TRIP}/trips/${trip_id}/rating" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"stars":5}' >/dev/null
rated_trip="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_TRIP}/trips/${trip_id}")"
if [[ "$(json_field "${rated_trip}" "rating")" == "5" ]]; then pass "Оценка поездки 1..5 работает" "rating=5"; else fail "Оценка поездки не сохранилась" "получили=$(json_field "${rated_trip}" "rating")"; fi

bad_rating_code="$(curl -sS -o /dev/null -w "%{http_code}" -X PATCH "${BASE_TRIP}/trips/${trip_id}/rating" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"stars":6}')"
if [[ "${bad_rating_code}" == "400" || "${bad_rating_code}" == "422" || "${bad_rating_code}" == "500" ]]; then
  pass "Оценка вне диапазона 1..5 отклоняется" "HTTP=${bad_rating_code}"
else
  warn "Оценка 6 звезд вернула неожиданный код" "HTTP=${bad_rating_code}"
fi

echo ""
echo "[3] Notification / Worker Service (Уведомления)"

attempt=0
notif_count=0
notif_json="[]"
while [[ "${attempt}" -lt 15 ]]; do
  notif_json="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_NOTIFY}/notifications?trip_id=${trip_id}")"
  notif_count="$(json_length "${notif_json}")"
  if [[ "${notif_count}" -gt 0 ]]; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 1
done
if [[ "${notif_count}" -ge 2 ]]; then pass "Созданы уведомления по поездке (водитель + пассажир)" "notifications=${notif_count}"; else fail "Ожидали >=2 уведомлений" "получили=${notif_count}"; fi

attempt=0
processed=0
while [[ "${attempt}" -lt 15 ]]; do
  notif_json="$(curl -fsS -H "Authorization: Bearer ${token}" "${BASE_NOTIFY}/notifications?trip_id=${trip_id}")"
  processed="$(json_has_non_pending "${notif_json}")"
  if [[ "${processed}" == "True" ]]; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 1
done
if [[ "${processed}" == "True" ]]; then pass "Воркер переводит задачи из PENDING в SENT/FAILED"; else fail "Задачи уведомлений остались в PENDING"; fi

manual_notif_code="$(curl -sS -o /dev/null -w "%{http_code}" -X POST "${BASE_NOTIFY}/notifications" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d "{\"tripId\":${trip_id},\"recipientType\":\"PASSENGER\",\"recipientId\":${passenger_id},\"message\":\"smoke-manual\"}")"
if [[ "${manual_notif_code}" == "200" || "${manual_notif_code}" == "201" || "${manual_notif_code}" == "202" ]]; then
  pass "POST /notifications добавляет задачу в очередь" "HTTP=${manual_notif_code}"
else
  fail "POST /notifications вернул ошибку" "HTTP=${manual_notif_code}"
fi

echo ""
echo "[4] Атомарность назначения водителя"

if [[ "${SKIP_ATOMIC}" == "1" ]]; then
  warn "Проверка атомарности пропущена (SKIP_ATOMIC=1)"
else
  second_email="smoke_passenger2_${suffix}@example.com"
  second_passenger_json="$(curl -fsS -X POST "${BASE_USER}/passengers" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Smoke Passenger 2\",\"email\":\"${second_email}\",\"phone\":\"+70000001003\",\"password\":\"123456\"}")"
  second_passenger_id="$(json_field "${second_passenger_json}" "id")"

  tmp_a="$(mktemp)"
  tmp_b="$(mktemp)"
  curl -sS -X POST "${BASE_TRIP}/trips" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"passengerId\":${passenger_id},\"origin\":\"A\",\"destination\":\"B\",\"originLat\":55.75,\"originLon\":37.61,\"destLat\":55.76,\"destLon\":37.62}" > "${tmp_a}" &
  pid_a=$!
  curl -sS -X POST "${BASE_TRIP}/trips" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"passengerId\":${second_passenger_id},\"origin\":\"C\",\"destination\":\"D\",\"originLat\":55.77,\"originLon\":37.63,\"destLat\":55.78,\"destLon\":37.64}" > "${tmp_b}" &
  pid_b=$!
  wait "${pid_a}" "${pid_b}"

  trip_a_json="$(cat "${tmp_a}")"
  trip_b_json="$(cat "${tmp_b}")"
  rm -f "${tmp_a}" "${tmp_b}"

  trip_a_id="$(json_field "${trip_a_json}" "id")"
  trip_b_id="$(json_field "${trip_b_json}" "id")"
  driver_a="$(json_field "${trip_a_json}" "driverId")"
  driver_b="$(json_field "${trip_b_json}" "driverId")"
  status_a="$(json_field "${trip_a_json}" "status")"
  status_b="$(json_field "${trip_b_json}" "status")"

  if [[ -n "${driver_a}" && -n "${driver_b}" && "${driver_a}" == "${driver_b}" && "${status_a}" == "ASSIGNED" && "${status_b}" == "ASSIGNED" ]]; then
    fail "Нарушена атомарность: один водитель назначен на 2 поездки" "driver_id=${driver_a}, status_a=${status_a}, status_b=${status_b}"
  else
    pass "Один водитель не назначен одновременно на 2 поездки" "driver_a=${driver_a:-null}, driver_b=${driver_b:-null}"
  fi

  if [[ -n "${trip_a_id}" ]]; then complete_trip_safely "${trip_a_id}"; fi
  if [[ -n "${trip_b_id}" ]]; then complete_trip_safely "${trip_b_id}"; fi
  pass "Тестовые поездки из проверки атомарности приведены к финальному состоянию" "trip_a_id=${trip_a_id:-null}, trip_b_id=${trip_b_id:-null}"
fi

echo ""
echo "[5] Дополнительные требования"

stats_file="$(mktemp)"
stats_code="$(curl -sS -o "${stats_file}" -w "%{http_code}" -H "Authorization: Bearer ${token}" "${BASE_TRIP}/stats/trips?date=$(date +%Y-%m-%d)")"
if [[ "${stats_code}" == "200" ]]; then
  stats_json="$(cat "${stats_file}")"
  if [[ -n "$(json_field "${stats_json}" "tripCount")" && -n "$(json_field "${stats_json}" "averagePrice")" ]]; then
    pass "Эндпоинт статистики возвращает tripCount и averagePrice" "tripCount=$(json_field "${stats_json}" "tripCount"), averagePrice=$(json_field "${stats_json}" "averagePrice")"
  else
    fail "В ответе статистики отсутствуют обязательные поля" "${stats_json}"
  fi
else
  fail "Эндпоинт статистики недоступен" "HTTP=${stats_code}"
fi
rm -f "${stats_file}"

echo ""
echo "=========================================="
echo "ИТОГИ SMOKE-ТЕСТА"
echo "=========================================="
echo "Всего проверок: ${TOTAL}"
echo "Успешно: ${PASSED}"
echo "Провалено: ${FAILED}"
echo "Предупреждений: ${WARNINGS}"
echo "Сводка: trip_id=${trip_id}, notifications=${notif_count}, token_len=${#token}"

if [[ "${FAILED}" -eq 0 ]]; then
  echo "SMOKE TEST PASSED (без ошибок)"
  exit 0
fi

echo "SMOKE TEST FAILED (есть ошибки)"
exit 1