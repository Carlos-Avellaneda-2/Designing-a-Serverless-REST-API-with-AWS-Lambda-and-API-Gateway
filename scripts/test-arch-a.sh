#!/usr/bin/env bash
# Pruebas de la ARQUITECTURA A (integración no-proxy con mapping templates).
# Uso:  API_URL=https://xxxx.execute-api.us-east-1.amazonaws.com/prod ./scripts/test-arch-a.sh
# En Windows ejecútalo desde Git Bash.
set -u
: "${API_URL:?Define API_URL con la URL del stage, sin / final}"

run() {
  echo
  echo "=================================================================="
  echo "# $1"
  echo "=================================================================="
  shift
  curl -s -i "$@"
  echo
}

run "GET existente -> 200"            "$API_URL/orders/ORD-100"
run "GET inexistente -> 404"          "$API_URL/orders/ORD-999"
run "Buscar CUST-42 -> 200 [..]"      "$API_URL/orders?customerId=CUST-42"
run "Buscar CUST-100 -> 200 []"       "$API_URL/orders?customerId=CUST-100"
run "Buscar sin customerId -> 400"    "$API_URL/orders"
run "POST valido -> 201"              -X POST "$API_URL/orders" -H "Content-Type: application/json" \
                                      -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}'
run "POST invalido -> 400"            -X POST "$API_URL/orders" -H "Content-Type: application/json" \
                                      -d '{"customerId":"CUST-42","product":"","quantity":0}'
run "POST tipo incorrecto -> 400"     -X POST "$API_URL/orders" -H "Content-Type: application/json" \
                                      -d '{"customerId":"CUST-42","product":"X","quantity":"abc"}'
run "DELETE existente -> 204"          -X DELETE "$API_URL/orders/ORD-102"
run "DELETE inexistente -> 404"        -X DELETE "$API_URL/orders/ORD-999"
run "GET despues del DELETE (en A puede dar 200: otra Lambda, otra memoria)" "$API_URL/orders/ORD-102"
