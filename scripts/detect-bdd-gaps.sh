#!/usr/bin/env bash
# ==============================================================================
# BDD Gap Detection Script (Full Audit)
# ==============================================================================
# Cross-references YAML resource configs against BDD feature files to identify
# untested resources, search parameters, operations, and HTTP protocol scenarios.
#
# Usage: ./scripts/detect-bdd-gaps.sh [--verbose]
# ==============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RESOURCE_CONFIG_DIR="$PROJECT_ROOT/fhir4java-server/src/main/resources/fhir-config/resources"
FEATURES_DIR="$PROJECT_ROOT/fhir4java-server/src/test/resources/features"
OPERATIONS_DIR="$PROJECT_ROOT/fhir4java-server/src/main/resources/fhir-config/r5/operations"
TESTDATA_DIR="$PROJECT_ROOT/fhir4java-server/src/test/resources/testdata"

VERBOSE="${1:-}"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

total_gaps=0
total_covered=0

# ==============================================================================
# Helper functions
# ==============================================================================

print_header() {
    echo ""
    echo "================================================================"
    echo "  $1"
    echo "================================================================"
}

check_mark() {
    echo -e "  ${GREEN}✓${NC} $1"
    total_covered=$((total_covered + 1))
}

cross_mark() {
    echo -e "  ${RED}✗${NC} $1"
    total_gaps=$((total_gaps + 1))
}

warn_mark() {
    echo -e "  ${YELLOW}?${NC} $1"
}

# Check if a resource type is mentioned in any feature file
resource_in_features() {
    local resource_type="$1"
    grep -rl "$resource_type" "$FEATURES_DIR" --include="*.feature" 2>/dev/null | head -1
}

# Check if a specific interaction is tested for a resource
interaction_tested() {
    local resource_type="$1"
    local interaction="$2"
    grep -rl "$interaction.*$resource_type\|$resource_type.*$interaction" "$FEATURES_DIR" --include="*.feature" 2>/dev/null | head -1
}

# ==============================================================================
# 1. Resource CRUD Coverage
# ==============================================================================

print_header "1. Resource CRUD Coverage"
echo ""
printf "  %-25s %-8s %-8s %-8s %-8s %-8s %-8s\n" "Resource" "Create" "Read" "Update" "Patch" "Delete" "Search"
printf "  %-25s %-8s %-8s %-8s %-8s %-8s %-8s\n" "--------" "------" "----" "------" "-----" "------" "------"

for config_file in "$RESOURCE_CONFIG_DIR"/*.yml; do
    resource_type=$(grep '^resourceType:' "$config_file" | awk '{print $2}')
    if [ -z "$resource_type" ]; then continue; fi

    # Check each interaction
    create=""; read=""; update=""; patch=""; delete=""; search=""

    if grep -qr "create.*$resource_type\|I create a $resource_type" "$FEATURES_DIR" --include="*.feature" 2>/dev/null; then
        create="${GREEN}✓${NC}"; ((total_covered++))
    else
        create="${RED}✗${NC}"; ((total_gaps++))
    fi

    if grep -qr "read.*$resource_type\|I read.*$resource_type" "$FEATURES_DIR" --include="*.feature" 2>/dev/null; then
        read="${GREEN}✓${NC}"; ((total_covered++))
    else
        read="${RED}✗${NC}"; ((total_gaps++))
    fi

    if grep -qr "update.*$resource_type\|I update.*$resource_type" "$FEATURES_DIR" --include="*.feature" 2>/dev/null; then
        update="${GREEN}✓${NC}"; ((total_covered++))
    else
        update="${RED}✗${NC}"; ((total_gaps++))
    fi

    # Check if patch is enabled in config
    patch_enabled=$(grep -A1 'patch:' "$config_file" 2>/dev/null | grep -o 'true\|false' | head -1)
    if [ "$patch_enabled" = "true" ]; then
        if grep -qr "patch.*$resource_type\|I patch.*$resource_type" "$FEATURES_DIR" --include="*.feature" 2>/dev/null; then
            patch="${GREEN}✓${NC}"; ((total_covered++))
        else
            patch="${RED}✗${NC}"; ((total_gaps++))
        fi
    else
        patch="${YELLOW}-${NC}"
    fi

    # Check if delete is enabled in config
    delete_enabled=$(grep -A1 'delete:' "$config_file" 2>/dev/null | grep -o 'true\|false' | head -1)
    if [ "$delete_enabled" = "true" ]; then
        if grep -qr "delete.*$resource_type\|I delete.*$resource_type" "$FEATURES_DIR" --include="*.feature" 2>/dev/null; then
            delete="${GREEN}✓${NC}"; ((total_covered++))
        else
            delete="${RED}✗${NC}"; ((total_gaps++))
        fi
    else
        delete="${YELLOW}-${NC}"
    fi

    if grep -qr "search.*$resource_type\|I search.*$resource_type" "$FEATURES_DIR" --include="*.feature" 2>/dev/null; then
        search="${GREEN}✓${NC}"; ((total_covered++))
    else
        search="${RED}✗${NC}"; ((total_gaps++))
    fi

    printf "  %-25s %-17b %-17b %-17b %-17b %-17b %-17b\n" \
        "$resource_type" "$create" "$read" "$update" "$patch" "$delete" "$search"
done

# ==============================================================================
# 2. Search Parameter Coverage
# ==============================================================================

print_header "2. Search Parameter Coverage"
echo ""

for config_file in "$RESOURCE_CONFIG_DIR"/*.yml; do
    resource_type=$(grep '^resourceType:' "$config_file" | awk '{print $2}')
    if [ -z "$resource_type" ]; then continue; fi

    mode=$(grep 'mode:' "$config_file" 2>/dev/null | awk '{print $2}' | head -1)
    if [ -z "$mode" ]; then continue; fi

    echo "  $resource_type (mode: $mode):"

    # Extract resource-specific search params from config
    in_resource_specific=false
    while IFS= read -r line; do
        if echo "$line" | grep -q 'resourceSpecific:'; then
            in_resource_specific=true
            continue
        fi
        if $in_resource_specific; then
            # Stop if we hit a non-list line
            if echo "$line" | grep -q '^\s*-\s' ; then
                param=$(echo "$line" | sed 's/.*-\s*//' | tr -d '[:space:]')
                if [ -n "$param" ]; then
                    if grep -qr "$param" "$FEATURES_DIR/search/" --include="*.feature" 2>/dev/null; then
                        [ -n "$VERBOSE" ] && check_mark "$param"
                    else
                        cross_mark "$param (not tested)"
                    fi
                fi
            else
                in_resource_specific=false
            fi
        fi
    done < "$config_file"

    echo ""
done

# ==============================================================================
# 3. Operation Coverage
# ==============================================================================

print_header "3. Operation Coverage"
echo ""

# Check known operations
declare -a operations=("validate" "everything" "merge" "patch")
declare -a operation_labels=("\$validate" "\$everything" "\$merge" "JSON Patch")

for i in "${!operations[@]}"; do
    op="${operations[$i]}"
    label="${operation_labels[$i]}"
    if grep -qr "$op" "$FEATURES_DIR/operations/" --include="*.feature" 2>/dev/null; then
        check_mark "$label"
    else
        cross_mark "$label (no feature file)"
    fi
done

# Check for operation YAML definitions
if [ -d "$OPERATIONS_DIR" ]; then
    echo ""
    echo "  Registered operation definitions:"
    for op_file in "$OPERATIONS_DIR"/*.yml "$OPERATIONS_DIR"/*.yaml; do
        [ -f "$op_file" ] 2>/dev/null || continue
        op_name=$(basename "$op_file" | sed 's/\.\(yml\|yaml\)$//')
        if grep -qr "$op_name" "$FEATURES_DIR" --include="*.feature" 2>/dev/null; then
            check_mark "$op_name"
        else
            cross_mark "$op_name (no test coverage)"
        fi
    done
fi

# ==============================================================================
# 4. HTTP Protocol Coverage
# ==============================================================================

print_header "4. HTTP Protocol Coverage"
echo ""

declare -a http_checks=(
    "ETag:response-headers"
    "Last-Modified:response-headers"
    "Location:response-headers"
    "Content-Type:content-negotiation"
    "Content-Location:response-headers"
    "If-None-Match:conditional-operations"
    "404:error-responses"
    "405:error-responses"
    "410:error-responses"
    "400:error-responses"
)

for check in "${http_checks[@]}"; do
    keyword="${check%%:*}"
    expected_file="${check##*:}"
    if [ -f "$FEATURES_DIR/http/$expected_file.feature" ]; then
        if grep -q "$keyword" "$FEATURES_DIR/http/$expected_file.feature" 2>/dev/null; then
            check_mark "$keyword (in $expected_file.feature)"
        else
            cross_mark "$keyword (file exists but keyword not found)"
        fi
    else
        cross_mark "$keyword ($expected_file.feature missing)"
    fi
done

# ==============================================================================
# 5. Test Data Coverage
# ==============================================================================

print_header "5. Test Data Coverage"
echo ""

for config_file in "$RESOURCE_CONFIG_DIR"/*.yml; do
    resource_type=$(grep '^resourceType:' "$config_file" | awk '{print $2}')
    if [ -z "$resource_type" ]; then continue; fi

    resource_lower=$(echo "$resource_type" | tr '[:upper:]' '[:lower:]')
    missing=""

    for variant in "" "-update" "-minimum-set" "-common-set" "-maximum-set"; do
        expected_file="$TESTDATA_DIR/crud/${resource_lower}${variant}.json"
        if [ ! -f "$expected_file" ]; then
            missing="$missing ${resource_lower}${variant}.json"
        fi
    done

    if [ -z "$missing" ]; then
        check_mark "$resource_type (all variants present)"
    else
        cross_mark "$resource_type (missing:$missing)"
    fi
done

# ==============================================================================
# 6. Feature Directory Coverage
# ==============================================================================

print_header "6. Feature Directory Structure"
echo ""

declare -a expected_dirs=("crud" "search" "operations" "plugins" "versioning" "http" "validation" "conformance" "tenancy")

for dir in "${expected_dirs[@]}"; do
    if [ -d "$FEATURES_DIR/$dir" ]; then
        count=$(find "$FEATURES_DIR/$dir" -name "*.feature" | wc -l)
        check_mark "$dir/ ($count feature files)"
    else
        cross_mark "$dir/ (directory missing)"
    fi
done

# ==============================================================================
# Summary
# ==============================================================================

print_header "Summary"
echo ""
echo -e "  ${GREEN}Covered:${NC} $total_covered"
echo -e "  ${RED}Gaps:${NC}    $total_gaps"
echo ""

if [ "$total_gaps" -gt 0 ]; then
    echo -e "  ${YELLOW}Run with --verbose for more details on covered items${NC}"
    exit 1
else
    echo -e "  ${GREEN}All checks passed!${NC}"
    exit 0
fi
