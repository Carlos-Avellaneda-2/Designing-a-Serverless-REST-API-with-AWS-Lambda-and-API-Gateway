#!/usr/bin/env bash
# =============================================================================
# Despliegue reproducible del Orders API en AWS (Lambda + API Gateway REST).
#
# Pensado para ejecutarse en AWS CloudShell (ya trae aws-cli, jq y curl).
#
#   ./deploy.sh arch-a        Arquitectura A: 4 Lambdas + API no-proxy con mapping templates
#   ./deploy.sh arch-b        Arquitectura B: 1 Lambda + API con Lambda proxy integration
#   ./deploy.sh update-code   Sube el JAR a todas las Lambdas existentes
#   ./deploy.sh urls          Muestra las URLs de ambas APIs
#
# Variables opcionales:
#   JAR       Ruta al fat JAR (por defecto ./orders-api-1.0.0.jar). Si no existe, se
#             descarga el código de la Lambda orders-create ya desplegada.
#   ROLE_ARN  Rol de ejecución (por defecto LabRole, el único disponible en AWS Academy).
#
# El script es idempotente: si se ejecuta dos veces, recrea los métodos y vuelve a desplegar.
# =============================================================================
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
export AWS_DEFAULT_REGION="$REGION"
ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
ROLE_ARN="${ROLE_ARN:-arn:aws:iam::${ACCOUNT}:role/LabRole}"
JAR="${JAR:-orders-api-1.0.0.jar}"
STAGE="prod"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

# ----------------------------------------------------------------------------- Lambda

ensure_jar() {
  if [[ ! -f "$JAR" ]]; then
    log "No se encontró $JAR: se descarga el código de la Lambda orders-create"
    curl -sSf -o "$JAR" "$(aws lambda get-function --function-name orders-create \
      --query Code.Location --output text)"
  fi
}

# deploy_function <nombre> <handler>
deploy_function() {
  local name="$1" handler="$2"
  ensure_jar
  if aws lambda get-function --function-name "$name" >/dev/null 2>&1; then
    log "Actualizando Lambda $name"
    aws lambda update-function-code --function-name "$name" --zip-file "fileb://$JAR" >/dev/null
    aws lambda wait function-updated --function-name "$name"
    aws lambda update-function-configuration --function-name "$name" --handler "$handler" \
      --memory-size 512 --timeout 15 >/dev/null
    aws lambda wait function-updated --function-name "$name"
  else
    log "Creando Lambda $name ($handler)"
    aws lambda create-function --function-name "$name" --runtime java17 \
      --role "$ROLE_ARN" --handler "$handler" --memory-size 512 --timeout 15 \
      --zip-file "fileb://$JAR" >/dev/null
    aws lambda wait function-active --function-name "$name"
  fi
}

function_arn() {
  aws lambda get-function --function-name "$1" --query Configuration.FunctionArn --output text
}

# allow_api_gateway <función> <api-id>: permite que el API invoque la Lambda
allow_api_gateway() {
  aws lambda add-permission --function-name "$1" --statement-id "apigw-$2" \
    --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:${REGION}:${ACCOUNT}:$2/*" >/dev/null 2>&1 || true
}

# ----------------------------------------------------------------------------- API Gateway

# ensure_api <nombre> -> imprime el id del REST API (lo crea si no existe)
ensure_api() {
  local id
  id="$(aws apigateway get-rest-apis --query "items[?name=='$1'].id | [0]" --output text)"
  if [[ "$id" == "None" || -z "$id" ]]; then
    id="$(aws apigateway create-rest-api --name "$1" \
      --endpoint-configuration types=REGIONAL --query id --output text)"
  fi
  echo "$id"
}

# ensure_resource <api-id> <path completo> <parent-id> <path-part> -> id del recurso
ensure_resource() {
  local id
  id="$(aws apigateway get-resources --rest-api-id "$1" \
        --query "items[?path=='$2'].id | [0]" --output text)"
  if [[ "$id" == "None" || -z "$id" ]]; then
    id="$(aws apigateway create-resource --rest-api-id "$1" --parent-id "$3" \
          --path-part "$4" --query id --output text)"
  fi
  echo "$id"
}

root_resource() {
  aws apigateway get-resources --rest-api-id "$1" --query "items[?path=='/'].id | [0]" --output text
}

# reset_method <api> <resource> <verbo> [request-parameters]
reset_method() {
  aws apigateway delete-method --rest-api-id "$1" --resource-id "$2" --http-method "$3" \
    >/dev/null 2>&1 || true
  if [[ -n "${4:-}" ]]; then
    aws apigateway put-method --rest-api-id "$1" --resource-id "$2" --http-method "$3" \
      --authorization-type NONE --request-parameters "$4" >/dev/null
  else
    aws apigateway put-method --rest-api-id "$1" --resource-id "$2" --http-method "$3" \
      --authorization-type NONE >/dev/null
  fi
}

lambda_uri() {
  echo "arn:aws:apigateway:${REGION}:lambda:path/2015-03-31/functions/$1/invocations"
}

# json_template <archivo-vtl> -> JSON {"application/json": "<contenido>"}
json_template() {
  jq -n --rawfile t "$1" '{"application/json": $t}' > "$1.json"
  echo "file://$1.json"
}

deploy_stage() {
  log "Desplegando stage $STAGE"
  aws apigateway create-deployment --rest-api-id "$1" --stage-name "$STAGE" >/dev/null
  echo "URL: https://$1.execute-api.${REGION}.amazonaws.com/${STAGE}"
}

# =============================================================================
# ARQUITECTURA A — integración no-proxy (API Gateway transforma requests/responses)
# =============================================================================

write_templates() {
  cat > "$TMP/req-id.vtl" <<'EOF'
{
  "id": "$util.escapeJavaScript($input.params('id'))"
}
EOF
  cat > "$TMP/req-customer.vtl" <<'EOF'
{
  "customerId": "$util.escapeJavaScript($input.params('customerId'))"
}
EOF
  cat > "$TMP/req-body.vtl" <<'EOF'
$input.json('$')
EOF
  cat > "$TMP/resp-created.vtl" <<'EOF'
#set($id = $input.path('$.id'))
#set($context.responseOverride.header.Location = "/orders/$id")
$input.json('$')
EOF
  # 204 No Content: la plantilla no produce nada (sin body).
  cat > "$TMP/resp-empty.vtl" <<'EOF'
#set($empty = "")
EOF
  # errorMessage ya es nuestro JSON de error. Si no lo es (error del runtime),
  # se reemplaza por uno con el formato del API.
  cat > "$TMP/err-400.vtl" <<'EOF'
#set($msg = $input.path('$.errorMessage'))
#if($msg.startsWith('{'))
$msg
#else
{"error":"MALFORMED_JSON","message":"Request body could not be parsed"}
#end
EOF
  cat > "$TMP/err-404.vtl" <<'EOF'
$input.path('$.errorMessage')
EOF
  cat > "$TMP/err-500.vtl" <<'EOF'
#set($msg = $input.path('$.errorMessage'))
#if($msg.startsWith('{'))
$msg
#else
{"error":"INTERNAL_ERROR","message":"Unexpected internal error"}
#end
EOF
}

PATTERN_400='.*("INVALID_REQUEST"|JSON).*'
PATTERN_404='.*"ORDER_NOT_FOUND".*'
PATTERN_500='.*("INTERNAL_ERROR"|Task timed out).*'

# method_response <api> <res> <verbo> <status> [headers]
method_response() {
  if [[ -n "${5:-}" ]]; then
    aws apigateway put-method-response --rest-api-id "$1" --resource-id "$2" --http-method "$3" \
      --status-code "$4" --response-models '{"application/json":"Empty"}' \
      --response-parameters "$5" >/dev/null
  else
    aws apigateway put-method-response --rest-api-id "$1" --resource-id "$2" --http-method "$3" \
      --status-code "$4" --response-models '{"application/json":"Empty"}' >/dev/null
  fi
}

# error_response <api> <res> <verbo> <status> <regex> <plantilla>
error_response() {
  method_response "$1" "$2" "$3" "$4"
  aws apigateway put-integration-response --rest-api-id "$1" --resource-id "$2" \
    --http-method "$3" --status-code "$4" --selection-pattern "$5" \
    --response-templates "$(json_template "$6")" >/dev/null
}

# mapping_method <api> <res> <verbo> <lambda-arn> <plantilla-request> <status-éxito> [req-params]
mapping_method() {
  local api="$1" res="$2" verb="$3" fn="$4" req="$5" ok="$6"
  reset_method "$api" "$res" "$verb" "${7:-}"
  aws apigateway put-integration --rest-api-id "$api" --resource-id "$res" --http-method "$verb" \
    --type AWS --integration-http-method POST --uri "$(lambda_uri "$fn")" \
    --passthrough-behavior WHEN_NO_TEMPLATES \
    --request-templates "$(json_template "$req")" >/dev/null
}

deploy_arch_a() {
  deploy_function orders-create          com.orders.lambda.mapping.CreateOrderHandler::handleRequest
  deploy_function orders-get             com.orders.lambda.mapping.GetOrderHandler::handleRequest
  deploy_function orders-find-by-customer com.orders.lambda.mapping.FindOrdersByCustomerHandler::handleRequest
  deploy_function orders-delete          com.orders.lambda.mapping.DeleteOrderHandler::handleRequest

  local api root orders order create get find del
  log "Configurando REST API orders-api-mapping"
  api="$(ensure_api orders-api-mapping)"
  root="$(root_resource "$api")"
  orders="$(ensure_resource "$api" /orders "$root" orders)"
  order="$(ensure_resource "$api" '/orders/{id}' "$orders" '{id}')"
  create="$(function_arn orders-create)"
  get="$(function_arn orders-get)"
  find="$(function_arn orders-find-by-customer)"
  del="$(function_arn orders-delete)"
  write_templates

  log "GET /orders/{id} -> orders-get"
  mapping_method "$api" "$order" GET "$get" "$TMP/req-id.vtl" 200
  method_response "$api" "$order" GET 200
  aws apigateway put-integration-response --rest-api-id "$api" --resource-id "$order" \
    --http-method GET --status-code 200 >/dev/null
  error_response "$api" "$order" GET 400 "$PATTERN_400" "$TMP/err-400.vtl"
  error_response "$api" "$order" GET 404 "$PATTERN_404" "$TMP/err-404.vtl"
  error_response "$api" "$order" GET 500 "$PATTERN_500" "$TMP/err-500.vtl"

  log "GET /orders?customerId= -> orders-find-by-customer"
  mapping_method "$api" "$orders" GET "$find" "$TMP/req-customer.vtl" 200 \
    'method.request.querystring.customerId=false'
  method_response "$api" "$orders" GET 200
  aws apigateway put-integration-response --rest-api-id "$api" --resource-id "$orders" \
    --http-method GET --status-code 200 >/dev/null
  error_response "$api" "$orders" GET 400 "$PATTERN_400" "$TMP/err-400.vtl"
  error_response "$api" "$orders" GET 500 "$PATTERN_500" "$TMP/err-500.vtl"

  log "POST /orders -> orders-create"
  mapping_method "$api" "$orders" POST "$create" "$TMP/req-body.vtl" 201
  method_response "$api" "$orders" POST 201 'method.response.header.Location=false'
  aws apigateway put-integration-response --rest-api-id "$api" --resource-id "$orders" \
    --http-method POST --status-code 201 \
    --response-templates "$(json_template "$TMP/resp-created.vtl")" >/dev/null
  error_response "$api" "$orders" POST 400 "$PATTERN_400" "$TMP/err-400.vtl"
  error_response "$api" "$orders" POST 500 "$PATTERN_500" "$TMP/err-500.vtl"

  log "DELETE /orders/{id} -> orders-delete"
  mapping_method "$api" "$order" DELETE "$del" "$TMP/req-id.vtl" 204
  method_response "$api" "$order" DELETE 204
  aws apigateway put-integration-response --rest-api-id "$api" --resource-id "$order" \
    --http-method DELETE --status-code 204 \
    --response-templates "$(json_template "$TMP/resp-empty.vtl")" >/dev/null
  error_response "$api" "$order" DELETE 400 "$PATTERN_400" "$TMP/err-400.vtl"
  error_response "$api" "$order" DELETE 404 "$PATTERN_404" "$TMP/err-404.vtl"
  error_response "$api" "$order" DELETE 500 "$PATTERN_500" "$TMP/err-500.vtl"

  for fn in orders-create orders-get orders-find-by-customer orders-delete; do
    allow_api_gateway "$fn" "$api"
  done
  deploy_stage "$api"
}

# =============================================================================
# ARQUITECTURA B — Lambda proxy integration (el adaptador Java hace todo lo HTTP)
# =============================================================================

proxy_method() {
  reset_method "$1" "$2" "$3"
  aws apigateway put-integration --rest-api-id "$1" --resource-id "$2" --http-method "$3" \
    --type AWS_PROXY --integration-http-method POST --uri "$(lambda_uri "$4")" >/dev/null
}

deploy_arch_b() {
  deploy_function orders-api com.orders.lambda.proxy.OrdersApiHandler::handleRequest

  local api root orders order fn
  log "Configurando REST API orders-api-proxy"
  api="$(ensure_api orders-api-proxy)"
  root="$(root_resource "$api")"
  orders="$(ensure_resource "$api" /orders "$root" orders)"
  order="$(ensure_resource "$api" '/orders/{id}' "$orders" '{id}')"
  fn="$(function_arn orders-api)"

  proxy_method "$api" "$orders" GET  "$fn"
  proxy_method "$api" "$orders" POST "$fn"
  proxy_method "$api" "$order"  GET  "$fn"
  proxy_method "$api" "$order"  DELETE "$fn"

  allow_api_gateway orders-api "$api"
  deploy_stage "$api"
}

# =============================================================================

update_code() {
  ensure_jar
  for fn in orders-create orders-get orders-find-by-customer orders-delete orders-api; do
    if aws lambda get-function --function-name "$fn" >/dev/null 2>&1; then
      log "Subiendo JAR a $fn"
      aws lambda update-function-code --function-name "$fn" --zip-file "fileb://$JAR" >/dev/null
      aws lambda wait function-updated --function-name "$fn"
    fi
  done
}

show_urls() {
  for name in orders-api-mapping orders-api-proxy; do
    id="$(aws apigateway get-rest-apis --query "items[?name=='$name'].id | [0]" --output text)"
    if [[ "$id" != "None" ]]; then
      echo "$name: https://$id.execute-api.${REGION}.amazonaws.com/${STAGE}"
    fi
  done
}

case "${1:-}" in
  arch-a)      deploy_arch_a ;;
  arch-b)      deploy_arch_b ;;
  update-code) update_code ;;
  urls)        show_urls ;;
  *) echo "Uso: $0 {arch-a|arch-b|update-code|urls}"; exit 1 ;;
esac
