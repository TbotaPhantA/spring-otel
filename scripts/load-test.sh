#!/bin/bash

# Load Testing Script for Otel Application
# Runs continuous load tests against all endpoints with state management
# Press Ctrl+C to stop

set -o pipefail

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENT="${CONCURRENT:-5}"
SUMMARY_INTERVAL="${SUMMARY_INTERVAL:-50}"
MAX_PRODUCTS="${MAX_PRODUCTS:-50}"
ERROR_RATE="${ERROR_RATE:-15}"

# State tracking
declare -a created_products=()

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Statistics
declare -A stats_method
declare -A stats_status
declare -A stats_endpoint
declare -A stats_response_time

total_requests=0
total_success=0
total_errors=0
total_response_time=0
start_time=$(date +%s)

# Print colored status
print_status() {
    local status=$1
    local response_time=$2
    local method=$3
    local endpoint=$4
    
    local color=$NC
    local status_text="UNKNOWN"
    
    if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
        color=$GREEN
        status_text="OK"
    elif [[ "$status" =~ ^4[0-9][0-9]$ ]]; then
        color=$YELLOW
        status_text="CLIENT_ERROR"
    elif [[ "$status" =~ ^5[0-9][0-9]$ ]]; then
        color=$RED
        status_text="SERVER_ERROR"
    fi
    
    printf "${color}[%s] %-6s %-35s -> %s (%sms)${NC}\n" \
        "$(date +'%H:%M:%S')" \
        "$method" \
        "$endpoint" \
        "$status_text ($status)" \
        "$response_time"
}

# Make HTTP request and return status code and response time
make_request_simple() {
    local method=$1
    local endpoint=$2
    local body=$3
    
    local start_ns=$(date +%s%N)
    local response
    local status
    
    if [[ -n "$body" ]]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -d "$body" \
            "${BASE_URL}${endpoint}" 2>/dev/null)
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            "${BASE_URL}${endpoint}" 2>/dev/null)
    fi
    
    local end_ns=$(date +%s%N)
    local response_time=$(( (end_ns - start_ns) / 1000000 ))
    
    status=$(echo "$response" | tail -1)
    
    echo "$status $response_time"
}

# Make HTTP POST request and return status, response time, and body
make_request_with_body() {
    local method=$1
    local endpoint=$2
    local body=$3
    
    local start_ns=$(date +%s%N)
    local response=$(curl -s -w "\n%{http_code}" -X "$method" \
        -H "Content-Type: application/json" \
        -d "$body" \
        "${BASE_URL}${endpoint}" 2>/dev/null)
    local end_ns=$(date +%s%N)
    local response_time=$(( (end_ns - start_ns) / 1000000 ))
    
    local status=$(echo "$response" | tail -1)
    local body_response=$(echo "$response" | sed '$d')
    
    echo "$status $response_time"
    echo "$body_response"
}

# Get a random product ID from state
get_random_product_id() {
    if [[ ${#created_products[@]} -eq 0 ]]; then
        return 1
    fi
    local random_index=$((RANDOM % ${#created_products[@]}))
    echo "${created_products[$random_index]}"
}

# Ensure at least one product exists in state
ensure_product_exists() {
    if [[ ${#created_products[@]} -eq 0 ]]; then
        echo "State empty, creating initial product..." >&2
        test_create_product
        return
    fi
    
    # Verify at least one product actually exists
    local valid=false
    local attempt
    for attempt in {1..3}; do
        local product_id
        product_id=$(get_random_product_id)
        if [[ -z "$product_id" ]]; then
            break
        fi
        
        local result=$(make_request_simple "GET" "/api/products/$product_id")
        local status=$(echo "$result" | awk '{print $1}')
        
        if [[ "$status" == "200" ]]; then
            valid=true
            break
        else
            # Product doesn't exist, remove from state
            remove_product_from_state "$product_id"
        fi
    done
    
    # If no valid products, create one
    if [[ "$valid" == "false" ]]; then
        echo "No valid products in state, creating new..." >&2
        test_create_product
    fi
}

# Add product ID to state
add_product_to_state() {
    local product_id=$1
    [[ -z "$product_id" ]] && return
    [[ "$product_id" =~ [^0-9] ]] && return  # Only numeric IDs
    
    # Check if already exists
    for id in "${created_products[@]}"; do
        [[ "$id" == "$product_id" ]] && return
    done
    
    created_products+=("$product_id")
}

# Remove product ID from state
remove_product_from_state() {
    local product_id=$1
    local new_products=()
    
    for id in "${created_products[@]}"; do
        [[ "$id" != "$product_id" ]] && new_products+=("$id")
    done
    
    created_products=("${new_products[@]}")
}

# Should we send an invalid request (to generate 400 errors)?
should_send_invalid_request() {
    local chance=$((RANDOM % 100))
    if [[ $chance -lt $ERROR_RATE ]]; then
        return 0  # true
    fi
    return 1  # false
}

# Generate invalid request body (to cause 400 errors)
generate_invalid_body() {
    case $((RANDOM % 5)) in
        0) echo '{"name":""}' ;;
        1) echo '{"name":"Test","price":""}' ;;
        2) echo '{"description":"Missing required fields"}' ;;
        3) echo '{"name":"Test","price":"abc"}' ;;
        4) echo '{"name":"","price":""}' ;;
    esac
}

# Update statistics
update_stats() {
    local status=$1
    local response_time=$2
    local method=$3
    local endpoint=$4
    
    ((total_requests++))
    total_response_time=$((total_response_time + response_time))
    
    # Count by status
    local status_key="${status}"
    ((stats_status[$status_key]++))
    
    # Count by method
    ((stats_method[$method]++))
    
    # Count by endpoint
    ((stats_endpoint[$endpoint]++))
    
    # Success/error tracking
    if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
        ((total_success++))
    else
        ((total_errors++))
    fi
}

# Test GET /api/products
test_get_all_products() {
    local result=$(make_request_simple "GET" "/api/products")
    local status=$(echo "$result" | awk '{print $1}')
    local response_time=$(echo "$result" | awk '{print $2}')
    print_status "$status" "$response_time" "GET" "/api/products"
    update_stats "$status" "$response_time" "GET" "/api/products"
}

# Test GET /api/products/{id}
test_get_product_by_id() {
    ensure_product_exists
    local product_id
    product_id=$(get_random_product_id)
    
    local result=$(make_request_simple "GET" "/api/products/$product_id")
    local status=$(echo "$result" | awk '{print $1}')
    local response_time=$(echo "$result" | awk '{print $2}')
    print_status "$status" "$response_time" "GET" "/api/products/{id}"
    update_stats "$status" "$response_time" "GET" "/api/products/{id}"
}

# Test POST /api/products
test_create_product() {
    # Cleanup if over limit
    while [[ ${#created_products[@]} -ge $MAX_PRODUCTS ]]; do
        local oldest_id="${created_products[0]}"
        remove_product_from_state "$oldest_id"
    done
    
    local unique_name="LoadTest-$(date +%s%N)-$RANDOM"
    
    # Determine if we should send invalid request (to generate 400 errors)
    local body
    if should_send_invalid_request; then
        body=$(generate_invalid_body)
    else
        body="{\"name\":\"$unique_name\",\"description\":\"Load test product\",\"price\":\"$RANDOM.99\",\"quantity\":$((RANDOM % 100))}"
    fi
    
    local output=$(make_request_with_body "POST" "/api/products" "$body")
    local status=$(echo "$output" | head -1 | awk '{print $1}')
    local response_time=$(echo "$output" | head -1 | awk '{print $2}')
    local body_response=$(echo "$output" | tail -n +2)
    
    # Extract product ID from response
    local product_id=$(echo "$body_response" | jq -r '.id // empty' 2>/dev/null)
    if [[ -n "$product_id" ]] && [[ "$status" == "201" ]]; then
        add_product_to_state "$product_id"
    fi
    
    print_status "$status" "$response_time" "POST" "/api/products"
    update_stats "$status" "$response_time" "POST" "/api/products"
}

# Test PUT /api/products/{id}
test_update_product() {
    ensure_product_exists
    local product_id
    product_id=$(get_random_product_id)
    
    # Determine if we should send invalid request (to generate 400 errors)
    local body
    if should_send_invalid_request; then
        body=$(generate_invalid_body)
    else
        body="{\"name\":\"Updated-$(date +%s%N)\",\"description\":\"Updated via load test\",\"price\":\"$RANDOM.99\",\"quantity\":$((RANDOM % 100))}"
    fi
    
    local result=$(make_request_simple "PUT" "/api/products/$product_id" "$body")
    local status=$(echo "$result" | awk '{print $1}')
    local response_time=$(echo "$result" | awk '{print $2}')
    
    # If 404, remove from state
    if [[ "$status" == "404" ]]; then
        remove_product_from_state "$product_id"
    fi
    
    print_status "$status" "$response_time" "PUT" "/api/products/{id}"
    update_stats "$status" "$response_time" "PUT" "/api/products/{id}"
}

# Test DELETE /api/products/{id}
test_delete_product() {
    ensure_product_exists
    local product_id
    product_id=$(get_random_product_id)
    
    local result=$(make_request_simple "DELETE" "/api/products/$product_id")
    local status=$(echo "$result" | awk '{print $1}')
    local response_time=$(echo "$result" | awk '{print $2}')
    
    # If successful, remove from state
    if [[ "$status" == "204" ]]; then
        remove_product_from_state "$product_id"
    fi
    
    print_status "$status" "$response_time" "DELETE" "/api/products/{id}"
    update_stats "$status" "$response_time" "DELETE" "/api/products/{id}"
}

# Test GET /api/products/test-500 (random 500 errors)
test_random_500() {
    local result=$(make_request_simple "GET" "/api/products/test-500")
    local status=$(echo "$result" | awk '{print $1}')
    local response_time=$(echo "$result" | awk '{print $2}')
    print_status "$status" "$response_time" "GET" "/api/products/test-500"
    update_stats "$status" "$response_time" "GET" "/api/products/test-500"
}

# Print summary
print_summary() {
    local current_time=$(date +%s)
    local elapsed=$((current_time - start_time))
    local rps=0
    
    if [[ $elapsed -gt 0 ]]; then
        rps=$(awk "BEGIN {printf \"%.2f\", $total_requests / $elapsed}")
    fi
    
    local avg_response=0
    if [[ $total_requests -gt 0 ]]; then
        avg_response=$((total_response_time / total_requests))
    fi
    
    echo ""
    echo -e "${CYAN}=== LOAD TEST SUMMARY ===${NC}"
    echo -e "Total Requests:   ${BLUE}${total_requests}${NC}"
    echo -e "Products in State: ${BLUE}${#created_products[@]}${NC}"
    echo -e "Time Elapsed:     ${BLUE}${elapsed}s${NC}"
    echo -e "RPS:              ${BLUE}${rps}${NC}"
    echo -e "Success (2xx):    ${GREEN}${total_success}${NC}"
    echo -e "Errors (4xx/5xx): ${RED}${total_errors}${NC}"
    echo -e "Avg Response:     ${BLUE}${avg_response}ms${NC}"
    echo ""
    
    echo -e "${YELLOW}=== BY STATUS CODE ===${NC}"
    for status in "${!stats_status[@]}"; do
        local count=${stats_status[$status]}
        local color=$NC
        if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
            color=$GREEN
        elif [[ "$status" =~ ^4[0-9][0-9]$ ]]; then
            color=$YELLOW
        elif [[ "$status" =~ ^5[0-9][0-9]$ ]]; then
            color=$RED
        fi
        echo -e "  ${color}${status}${NC}: ${count}"
    done
    echo ""
    
    echo -e "${YELLOW}=== BY METHOD ===${NC}"
    for method in "${!stats_method[@]}"; do
        local count=${stats_method[$method]}
        echo -e "  ${method}: ${count}"
    done
    echo ""
    
    echo -e "${YELLOW}=== BY ENDPOINT ===${NC}"
    for endpoint in "${!stats_endpoint[@]}"; do
        local count=${stats_endpoint[$endpoint]}
        echo -e "  ${endpoint}: ${count}"
    done
    echo ""
    echo -e "${CYAN}Press Ctrl+C to stop${NC}"
    echo "-------------------------------------------"
}

# Cleanup on exit
cleanup() {
    echo ""
    echo -e "${RED}Stopping load test...${NC}"
    
    # Delete all products in state
    echo -e "${YELLOW}Cleaning up ${#created_products[@]} products...${NC}"
    for product_id in "${created_products[@]}"; do
        curl -s -X "DELETE" "${BASE_URL}/api/products/$product_id" >/dev/null 2>&1
    done
    
    print_summary
    exit 0
}

trap cleanup SIGINT SIGTERM

# Array of test functions
declare -a tests=(
    "test_get_all_products"
    "test_get_product_by_id"
    "test_create_product"
    "test_update_product"
    "test_delete_product"
)

# Main loop
main() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}   LOAD TESTING SCRIPT${NC}"
    echo -e "${CYAN}   Target: ${BASE_URL}${NC}"
    echo -e "${CYAN}   Concurrent: ${CONCURRENT}${NC}"
    echo -e "${CYAN}   Max Products: ${MAX_PRODUCTS}${NC}"
    echo -e "${CYAN}   Summary every ${SUMMARY_INTERVAL} requests${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
    
    # Create initial products
    echo -e "${YELLOW}Creating initial products...${NC}"
    for i in {1..5}; do
        test_create_product >/dev/null 2>&1
    done
    echo -e "${GREEN}Created ${#created_products[@]} products in state${NC}"
    echo ""
    echo -e "${YELLOW}Starting load test... Press Ctrl+C to stop${NC}"
    echo "-------------------------------------------"
    
    while true; do
        # Pick random test
        local random_index=$((RANDOM % ${#tests[@]}))
        local test_func=${tests[$random_index]}
        
        # Run the test
        $test_func
        
        # Occasionally test random 500 (10% chance)
        if [[ $((RANDOM % 10)) -eq 0 ]]; then
            test_random_500
        fi
        
        # Print summary periodically
        if [[ $((total_requests % SUMMARY_INTERVAL)) -eq 0 ]] && [[ $total_requests -gt 0 ]]; then
            print_summary
        fi
        
        # Small delay to prevent overwhelming
        sleep 0.05
    done
}

main "$@"
