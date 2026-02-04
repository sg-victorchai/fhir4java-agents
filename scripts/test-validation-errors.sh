#!/bin/bash
# Test Script for HTTP 422 Validation Error Fix
# Date: February 4, 2026

echo "=========================================="
echo "Testing FHIR Validation Error Responses"
echo "=========================================="
echo ""

BASE_URL="http://localhost:8080/fhir/r5"

# Test 1: Invalid Gender Code (should return 422)
echo "Test 1: Invalid Gender Code '02'"
echo "Expected: HTTP 422 Unprocessable Entity"
echo "------------------------------------------"
curl -X POST "$BASE_URL/Patient" \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "identifier": [{"system": "nric", "value": "M1122334Z"}],
    "name": [{"family": "Chalmers", "given": ["Peter", "James"]}],
    "telecom": [
        {"system": "phone", "value": "62504620", "use": "work"},
        {"system": "email", "value": "testpatient1@gmail.com"}
    ],
    "gender": "02",
    "birthDate": "1988-09-30",
    "address": [
        {
            "use": "work",
            "type": "physical",
            "line": ["#06-01, 11 beach road"],
            "postalCode": "189675",
            "country": "Singapore"
        }
    ]
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s | jq '.'

echo ""
echo ""

# Test 2: Valid Patient (should return 201)
echo "Test 2: Valid Patient with correct gender code"
echo "Expected: HTTP 201 Created"
echo "------------------------------------------"
curl -X POST "$BASE_URL/Patient" \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "name": [{"family": "Smith", "given": ["John"]}],
    "gender": "male",
    "birthDate": "1990-01-15"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s | jq '.'

echo ""
echo ""

# Test 3: Invalid Gender Code Variations
echo "Test 3: Other Invalid Gender Codes"
echo "Expected: HTTP 422 for all"
echo "------------------------------------------"

for invalid_gender in "99" "invalid" "M" "F"; do
    echo "Testing gender: $invalid_gender"
    STATUS_CODE=$(curl -X POST "$BASE_URL/Patient" \
      -H "Content-Type: application/fhir+json" \
      -d "{
        \"resourceType\": \"Patient\",
        \"name\": [{\"family\": \"Test\", \"given\": [\"Patient\"]}],
        \"gender\": \"$invalid_gender\",
        \"birthDate\": \"1990-01-01\"
      }" \
      -w "%{http_code}" \
      -s -o /dev/null)
    
    if [ "$STATUS_CODE" -eq 422 ]; then
        echo "  ✅ Returned 422 (Correct)"
    else
        echo "  ❌ Returned $STATUS_CODE (Expected 422)"
    fi
done

echo ""
echo ""

# Test 4: Missing Required Field
echo "Test 4: Missing Required Field (family name)"
echo "Expected: HTTP 422 Unprocessable Entity"
echo "------------------------------------------"
curl -X POST "$BASE_URL/Patient" \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "name": [{"given": ["John"]}],
    "gender": "male"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s | jq '.'

echo ""
echo ""

# Test 5: Valid Gender Codes (should all return 201)
echo "Test 5: Valid Gender Codes"
echo "Expected: HTTP 201 for all"
echo "------------------------------------------"

for valid_gender in "male" "female" "other" "unknown"; do
    echo "Testing gender: $valid_gender"
    STATUS_CODE=$(curl -X POST "$BASE_URL/Patient" \
      -H "Content-Type: application/fhir+json" \
      -d "{
        \"resourceType\": \"Patient\",
        \"name\": [{\"family\": \"Test\", \"given\": [\"Patient\"]}],
        \"gender\": \"$valid_gender\",
        \"birthDate\": \"1990-01-01\"
      }" \
      -w "%{http_code}" \
      -s -o /dev/null)
    
    if [ "$STATUS_CODE" -eq 201 ]; then
        echo "  ✅ Returned 201 (Correct)"
    else
        echo "  ❌ Returned $STATUS_CODE (Expected 201)"
    fi
done

echo ""
echo "=========================================="
echo "Testing Complete"
echo "=========================================="
