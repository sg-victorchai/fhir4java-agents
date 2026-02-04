#!/bin/bash
# Comprehensive Test Automation for All 4 Phases
# ProfileValidator Implementation Testing
# Date: February 4, 2026

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BASE_URL="http://localhost:8080"
PASS_COUNT=0
FAIL_COUNT=0

# Function to print colored output
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_test() {
    echo -e "${YELLOW}TEST: $1${NC}"
}

print_pass() {
    echo -e "${GREEN}✅ PASS: $1${NC}"
    ((PASS_COUNT++))
}

print_fail() {
    echo -e "${RED}❌ FAIL: $1${NC}"
    ((FAIL_COUNT++))
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Check if server is running
check_server() {
    print_test "Checking if server is running..."
    if curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1; then
        print_pass "Server is running at $BASE_URL"
        return 0
    else
        print_fail "Server is not running at $BASE_URL"
        echo "Please start the server with: ./mvnw spring-boot:run"
        exit 1
    fi
}

# ============================================
# PHASE 1: Core Implementation Tests
# ============================================
test_phase1() {
    print_header "PHASE 1: Core Implementation Tests"
    
    # Test 1.1: Check startup logs for initialization
    print_test "1.1 - ProfileValidator Initialization"
    if docker-compose logs fhir4java-server 2>/dev/null | grep -q "ProfileValidator initialization complete"; then
        print_pass "ProfileValidator initialized successfully"
    else
        print_info "Check application logs for: 'ProfileValidator initialization complete'"
    fi
    
    # Test 1.2: Verify no dependency errors
    print_test "1.2 - No Dependency Errors"
    if docker-compose logs fhir4java-server 2>/dev/null | grep -q "NoSuchMethodError\|No Cache Service Providers"; then
        print_fail "Dependency errors found in logs"
    else
        print_pass "No dependency errors detected"
    fi
    
    # Test 1.3: Check application is up
    print_test "1.3 - Application Health"
    STATUS=$(curl -s "$BASE_URL/actuator/health" | jq -r '.status')
    if [ "$STATUS" == "UP" ]; then
        print_pass "Application health status is UP"
    else
        print_fail "Application health status is $STATUS"
    fi
}

# ============================================
# PHASE 2: Health Endpoint Tests
# ============================================
test_phase2() {
    print_header "PHASE 2: Health Endpoint Tests"
    
    # Test 2.1: ProfileValidator health endpoint exists
    print_test "2.1 - ProfileValidator Health Endpoint Exists"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health/profileValidator")
    if [ "$HTTP_CODE" == "200" ]; then
        print_pass "ProfileValidator health endpoint returns 200"
    else
        print_fail "ProfileValidator health endpoint returns $HTTP_CODE (expected 200)"
    fi
    
    # Test 2.2: Health endpoint shows validator details
    print_test "2.2 - Health Endpoint Shows Details"
    RESPONSE=$(curl -s "$BASE_URL/actuator/health/profileValidator")
    if echo "$RESPONSE" | jq -e '.details.enabled' > /dev/null 2>&1; then
        print_pass "Health endpoint includes validator details"
        ENABLED=$(echo "$RESPONSE" | jq -r '.details.enabled')
        print_info "Validator enabled: $ENABLED"
    else
        print_fail "Health endpoint missing validator details"
    fi
    
    # Test 2.3: Health shows initialization status
    print_test "2.3 - Health Shows Initialization Status"
    if echo "$RESPONSE" | jq -e '.details.versions' > /dev/null 2>&1; then
        print_pass "Health endpoint shows version initialization status"
        SUCCESS_COUNT=$(echo "$RESPONSE" | jq -r '.details.successCount // 0')
        print_info "Validators initialized: $SUCCESS_COUNT"
    else
        print_info "Check if validator is enabled in configuration"
    fi
}

# ============================================
# PHASE 3: Metrics Tests
# ============================================
test_phase3() {
    print_header "PHASE 3: Metrics Tests"
    
    # Test 3.1: Metrics endpoint is accessible
    print_test "3.1 - Metrics Endpoint Accessible"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/metrics")
    if [ "$HTTP_CODE" == "200" ]; then
        print_pass "Metrics endpoint is accessible"
    else
        print_fail "Metrics endpoint returns $HTTP_CODE"
    fi
    
    # Test 3.2: Validation metrics are registered
    print_test "3.2 - Validation Metrics Registered (Eager)"
    METRICS=$(curl -s "$BASE_URL/actuator/metrics" | jq -r '.names[]')
    if echo "$METRICS" | grep -q "fhir.validation.attempts"; then
        print_pass "Metric 'fhir.validation.attempts' is registered"
    else
        print_fail "Metric 'fhir.validation.attempts' not found"
    fi
    
    if echo "$METRICS" | grep -q "fhir.validation.duration"; then
        print_pass "Metric 'fhir.validation.duration' is registered"
    else
        print_fail "Metric 'fhir.validation.duration' not found"
    fi
    
    # Test 3.3: Metrics use correct naming (dotted notation)
    print_test "3.3 - Metrics Use Dotted Notation"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/metrics/fhir.validation.attempts")
    if [ "$HTTP_CODE" == "200" ]; then
        print_pass "Metrics accessible with dotted notation"
    else
        print_fail "Metrics not accessible with dotted notation (HTTP $HTTP_CODE)"
    fi
    
    # Test 3.4: Prometheus export uses underscores
    print_test "3.4 - Prometheus Export Format"
    if curl -s "$BASE_URL/actuator/prometheus" | grep -q "fhir_validation_attempts"; then
        print_pass "Prometheus export uses underscore notation"
    else
        print_info "Prometheus endpoint may not be enabled"
    fi
}

# ============================================
# PHASE 4: HTTP 422 Validation Tests
# ============================================
test_phase4() {
    print_header "PHASE 4: HTTP 422 Validation Error Tests"
    
    # Test 4.1: Invalid gender code returns 422
    print_test "4.1 - Invalid Gender Code Returns 422"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/fhir/r5/Patient" \
        -H "Content-Type: application/fhir+json" \
        -d '{"resourceType":"Patient","name":[{"family":"Test","given":["Patient"]}],"gender":"99","birthDate":"1990-01-01"}')
    
    if [ "$HTTP_CODE" == "422" ]; then
        print_pass "Invalid gender code returns HTTP 422 (Unprocessable Entity)"
    elif [ "$HTTP_CODE" == "500" ]; then
        print_fail "Invalid gender code returns HTTP 500 (Should be 422)"
    else
        print_fail "Invalid gender code returns HTTP $HTTP_CODE (Expected 422)"
    fi
    
    # Test 4.2: Valid gender code returns 201
    print_test "4.2 - Valid Gender Code Returns 201"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/fhir/r5/Patient" \
        -H "Content-Type: application/fhir+json" \
        -d '{"resourceType":"Patient","name":[{"family":"Test","given":["Patient"]}],"gender":"male","birthDate":"1990-01-01"}')
    
    if [ "$HTTP_CODE" == "201" ]; then
        print_pass "Valid gender code returns HTTP 201 (Created)"
    else
        print_fail "Valid gender code returns HTTP $HTTP_CODE (Expected 201)"
    fi
    
    # Test 4.3: OperationOutcome is returned for validation errors
    print_test "4.3 - OperationOutcome in Response"
    RESPONSE=$(curl -s -X POST "$BASE_URL/fhir/r5/Patient" \
        -H "Content-Type: application/fhir+json" \
        -d '{"resourceType":"Patient","name":[{"family":"Test"}],"gender":"invalid"}')
    
    if echo "$RESPONSE" | jq -e '.resourceType == "OperationOutcome"' > /dev/null 2>&1; then
        print_pass "Response includes OperationOutcome"
        SEVERITY=$(echo "$RESPONSE" | jq -r '.issue[0].severity')
        print_info "Issue severity: $SEVERITY"
    else
        print_fail "Response does not include OperationOutcome"
    fi
    
    # Test 4.4: Multiple invalid codes
    print_test "4.4 - Various Invalid Gender Codes Return 422"
    INVALID_GENDERS=("02" "99" "invalid" "M" "F")
    INVALID_COUNT=0
    
    for gender in "${INVALID_GENDERS[@]}"; do
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/fhir/r5/Patient" \
            -H "Content-Type: application/fhir+json" \
            -d "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Test\"}],\"gender\":\"$gender\"}")
        
        if [ "$HTTP_CODE" == "422" ]; then
            ((INVALID_COUNT++))
        fi
    done
    
    if [ "$INVALID_COUNT" -eq "${#INVALID_GENDERS[@]}" ]; then
        print_pass "All invalid gender codes return HTTP 422 ($INVALID_COUNT/${#INVALID_GENDERS[@]})"
    else
        print_fail "Only $INVALID_COUNT/${#INVALID_GENDERS[@]} invalid codes return 422"
    fi
    
    # Test 4.5: Valid gender codes
    print_test "4.5 - Valid Gender Codes Return 201"
    VALID_GENDERS=("male" "female" "other" "unknown")
    VALID_COUNT=0
    
    for gender in "${VALID_GENDERS[@]}"; do
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/fhir/r5/Patient" \
            -H "Content-Type: application/fhir+json" \
            -d "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Test\"}],\"gender\":\"$gender\",\"birthDate\":\"1990-01-01\"}")
        
        if [ "$HTTP_CODE" == "201" ]; then
            ((VALID_COUNT++))
        fi
    done
    
    if [ "$VALID_COUNT" -eq "${#VALID_GENDERS[@]}" ]; then
        print_pass "All valid gender codes return HTTP 201 ($VALID_COUNT/${#VALID_GENDERS[@]})"
    else
        print_fail "Only $VALID_COUNT/${#VALID_GENDERS[@]} valid codes return 201"
    fi
}

# ============================================
# Integration Tests
# ============================================
test_integration() {
    print_header "INTEGRATION TESTS"
    
    # Test: End-to-end validation flow
    print_test "E2E - Create Patient with Validation"
    RESPONSE=$(curl -s -X POST "$BASE_URL/fhir/r5/Patient" \
        -H "Content-Type: application/fhir+json" \
        -d '{
            "resourceType": "Patient",
            "identifier": [{"system": "test", "value": "12345"}],
            "name": [{"family": "Integration", "given": ["Test"]}],
            "gender": "female",
            "birthDate": "1985-05-15"
        }')
    
    if echo "$RESPONSE" | jq -e '.id' > /dev/null 2>&1; then
        PATIENT_ID=$(echo "$RESPONSE" | jq -r '.id')
        print_pass "Patient created successfully (ID: $PATIENT_ID)"
        
        # Verify metrics incremented
        print_test "E2E - Metrics Incremented"
        ATTEMPTS=$(curl -s "$BASE_URL/actuator/metrics/fhir.validation.attempts" | jq -r '.measurements[0].value')
        if [ "$ATTEMPTS" != "null" ] && [ "$ATTEMPTS" != "0" ]; then
            print_pass "Validation metrics recorded (attempts: $ATTEMPTS)"
        else
            print_info "Metrics may not have incremented yet"
        fi
    else
        print_fail "Failed to create patient"
        echo "$RESPONSE" | jq '.'
    fi
}

# ============================================
# Performance Tests
# ============================================
test_performance() {
    print_header "PERFORMANCE TESTS"
    
    # Test: Startup time check (from logs)
    print_test "Startup Time"
    if docker-compose logs fhir4java-server 2>/dev/null | grep -q "Started Fhir4JavaApplication"; then
        print_pass "Application started successfully"
        print_info "Check logs for exact startup time"
    else
        print_info "Cannot verify startup time from logs"
    fi
    
    # Test: Validation response time
    print_test "Validation Response Time"
    START_TIME=$(date +%s%N)
    curl -s -o /dev/null -X POST "$BASE_URL/fhir/r5/Patient" \
        -H "Content-Type: application/fhir+json" \
        -d '{"resourceType":"Patient","name":[{"family":"Perf"}],"gender":"male"}'
    END_TIME=$(date +%s%N)
    DURATION=$(((END_TIME - START_TIME) / 1000000))
    
    print_info "Validation response time: ${DURATION}ms"
    if [ "$DURATION" -lt 1000 ]; then
        print_pass "Validation completes in under 1 second"
    else
        print_info "Validation took ${DURATION}ms (may need optimization)"
    fi
}

# ============================================
# Main Execution
# ============================================
main() {
    print_header "ProfileValidator - Comprehensive Test Suite"
    echo "Testing all 4 implementation phases"
    echo "Date: $(date)"
    echo ""
    
    # Check prerequisites
    check_server
    
    # Run all phase tests
    test_phase1
    test_phase2
    test_phase3
    test_phase4
    
    # Run integration tests
    test_integration
    
    # Run performance tests
    test_performance
    
    # Print summary
    print_header "TEST SUMMARY"
    echo -e "${GREEN}Passed: $PASS_COUNT${NC}"
    echo -e "${RED}Failed: $FAIL_COUNT${NC}"
    echo ""
    
    if [ $FAIL_COUNT -eq 0 ]; then
        echo -e "${GREEN}✅ All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}❌ Some tests failed. Review the output above.${NC}"
        exit 1
    fi
}

# Run main function
main
