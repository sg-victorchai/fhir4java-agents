#!/bin/bash

# =============================================================================
# FHIR TEST DATA GENERATOR
# =============================================================================

# Local FHIR R5 Server
BASE_URL="${BASE_URL:-http://localhost:8080/fhir}"

echo "=========================================="
echo "FHIR Test Data Generator"
echo "=========================================="
echo ""

# =============================================================================
# INTERACTIVE CONFIGURATION PROMPTS
# =============================================================================

# Function to prompt for integer input with default value
# Note: All prompts/messages go to stderr, only result goes to stdout
prompt_integer() {
    local prompt_text=$1
    local default_value=$2
    local min_value=$3
    local max_value=$4
    local result

    while true; do
        # Prompt to stderr so it displays but isn't captured
        echo -n "${prompt_text} [${default_value}]: " >&2
        read result
        result=${result:-$default_value}

        # Validate integer
        if ! [[ "$result" =~ ^[0-9]+$ ]]; then
            echo "  Please enter a valid number." >&2
            continue
        fi

        # Validate range
        if [ -n "$min_value" ] && [ "$result" -lt "$min_value" ]; then
            echo "  Value must be at least ${min_value}." >&2
            continue
        fi
        if [ -n "$max_value" ] && [ "$result" -gt "$max_value" ]; then
            echo "  Value must be at most ${max_value}." >&2
            continue
        fi

        # Only this goes to stdout (captured by command substitution)
        echo "$result"
        return
    done
}

# Function to prompt for string input with default value
# Note: All prompts/messages go to stderr, only result goes to stdout
prompt_string() {
    local prompt_text=$1
    local default_value=$2
    local result

    # Prompt to stderr so it displays but isn't captured
    echo -n "${prompt_text} [${default_value}]: " >&2
    read result
    result=${result:-$default_value}
    echo "$result"
}

echo "Please enter configuration values (press Enter for defaults):"
echo ""

# Batch identifier for this test data run
echo "--- Batch Identifier ---"
BATCH_NUMBER=$(prompt_string "Batch identifier (e.g., TEST01, DEMO, UAT01)" "TEST01")

echo ""
# Date range configuration
echo "--- Date Range ---"
START_YEAR=$(prompt_integer "Start year" "2021" "2000" "2030")
END_YEAR=$(prompt_integer "End year" "${START_YEAR}" "${START_YEAR}" "2030")

echo ""
echo "--- Resource Counts ---"

# Patient count (no limit since names are dynamically generated)
NUM_PATIENTS=$(prompt_integer "Number of patients" "10" "1" "10000")

# Encounters per patient
NUM_ENCOUNTERS=$(prompt_integer "Number of encounters per patient" "5" "1" "20")

# Observations per encounter
NUM_OBSERVATIONS=$(prompt_integer "Number of observations per encounter" "1" "0" "200")

# MedicationRequests per encounter
NUM_MEDICATIONS=$(prompt_integer "Number of medication requests per encounter" "1" "0" "10")

# CarePlans per encounter
NUM_CAREPLANS=$(prompt_integer "Number of care plans per encounter" "1" "0" "5")

# Procedures per patient (2-3 range)
NUM_PROCEDURES=$(prompt_integer "Number of procedures per patient (2-3)" "3" "2" "5")

echo ""
echo "=========================================="
echo "Configuration Summary:"
echo "  Batch Identifier:        ${BATCH_NUMBER}"
echo "  Date Range:              ${START_YEAR} - ${END_YEAR}"
echo "  Patients:                ${NUM_PATIENTS}"
echo "  Encounters per patient:  ${NUM_ENCOUNTERS}"
echo "  Observations per enc:    ${NUM_OBSERVATIONS}"
echo "  Medications per enc:     ${NUM_MEDICATIONS}"
echo "  CarePlans per enc:       ${NUM_CAREPLANS}"
echo "  Procedures per patient:  ${NUM_PROCEDURES}"
echo "=========================================="
echo ""

read -p "Press Enter to continue or Ctrl+C to cancel..."

# Arrays to store created resource IDs
declare -a PATIENT_IDS
declare -a ENCOUNTER_IDS
declare -a PROCEDURE_ENCOUNTER_IDS
ORGANIZATION_ID=""

echo ""

# Organization data
ORGANIZATION_NAME="Good Faith Clinic"

# =============================================================================
# SAMPLE PATIENT DATA - Multi-ethnic Singaporean Names
# =============================================================================

# Chinese names (first names and surnames)
CHINESE_FIRST_NAMES_MALE=("Wei Ming" "Jun Jie" "Kai Wen" "Zhi Hao" "Yi Xuan" "Jia Jun" "Hao Yu" "Zi Yang" "Jing Wei" "Xiang Yu")
CHINESE_FIRST_NAMES_FEMALE=("Jia Hui" "Xiu Ling" "Mei Ling" "Xin Yi" "Hui Min" "Li Ying" "Wen Xin" "Yu Ting" "Shi Min" "Jia Ying")
CHINESE_SURNAMES=("Tan" "Lim" "Lee" "Ng" "Ong" "Wong" "Goh" "Chua" "Koh" "Teo" "Ang" "Yeo" "Sim" "Chong" "Tay")

# Malay names (first names and surnames)
MALAY_FIRST_NAMES_MALE=("Ahmad" "Muhammad" "Mohd" "Abdul" "Ismail" "Yusof" "Ibrahim" "Hassan" "Rahman" "Aziz")
MALAY_FIRST_NAMES_FEMALE=("Siti" "Nur" "Fatimah" "Aminah" "Zainab" "Halimah" "Aishah" "Khadijah" "Ramlah" "Mariam")
MALAY_SURNAMES=("bin Abdullah" "bin Ibrahim" "bin Ismail" "bin Hassan" "bin Ahmad" "binte Abdullah" "binte Ibrahim" "binte Ismail" "binte Hassan" "binte Ahmad")

# Indian names (first names and surnames)
INDIAN_FIRST_NAMES_MALE=("Rajesh" "Suresh" "Kumar" "Ravi" "Arun" "Vijay" "Ganesh" "Prakash" "Mohan" "Krishna")
INDIAN_FIRST_NAMES_FEMALE=("Priya" "Lakshmi" "Devi" "Anitha" "Kamala" "Meena" "Radha" "Shalini" "Kavitha" "Deepa")
INDIAN_SURNAMES=("Krishnan" "Nair" "Pillai" "Menon" "Kumar" "Sharma" "Patel" "Singh" "Rao" "Reddy" "Naidu" "Iyer" "Muthu" "Rajan" "Suppiah")

# Function to generate a random patient name
# Returns: "FirstName|LastName|Gender"
generate_random_name() {
    local index=$1

    # Determine ethnicity based on index (rotate through ethnicities)
    local ethnicity=$((index % 3))

    # Determine gender (alternating)
    local gender_num=$((index % 2))
    local gender
    if [ $gender_num -eq 0 ]; then
        gender="male"
    else
        gender="female"
    fi

    local first_name
    local last_name

    case $ethnicity in
        0) # Chinese
            if [ "$gender" = "male" ]; then
                first_name=${CHINESE_FIRST_NAMES_MALE[$((RANDOM % ${#CHINESE_FIRST_NAMES_MALE[@]}))]}
            else
                first_name=${CHINESE_FIRST_NAMES_FEMALE[$((RANDOM % ${#CHINESE_FIRST_NAMES_FEMALE[@]}))]}
            fi
            last_name=${CHINESE_SURNAMES[$((RANDOM % ${#CHINESE_SURNAMES[@]}))]}
            ;;
        1) # Malay
            if [ "$gender" = "male" ]; then
                first_name=${MALAY_FIRST_NAMES_MALE[$((RANDOM % ${#MALAY_FIRST_NAMES_MALE[@]}))]}
                last_name=${MALAY_SURNAMES[$((RANDOM % 5))]}  # Use male surnames (bin)
            else
                first_name=${MALAY_FIRST_NAMES_FEMALE[$((RANDOM % ${#MALAY_FIRST_NAMES_FEMALE[@]}))]}
                last_name=${MALAY_SURNAMES[$((5 + RANDOM % 5))]}  # Use female surnames (binte)
            fi
            ;;
        2) # Indian
            if [ "$gender" = "male" ]; then
                first_name=${INDIAN_FIRST_NAMES_MALE[$((RANDOM % ${#INDIAN_FIRST_NAMES_MALE[@]}))]}
            else
                first_name=${INDIAN_FIRST_NAMES_FEMALE[$((RANDOM % ${#INDIAN_FIRST_NAMES_FEMALE[@]}))]}
            fi
            last_name=${INDIAN_SURNAMES[$((RANDOM % ${#INDIAN_SURNAMES[@]}))]}
            ;;
    esac

    echo "${first_name}|${last_name}|${gender}"
}

# Arrays to store generated patient data (populated during execution)
declare -a GENERATED_FIRST_NAMES
declare -a GENERATED_LAST_NAMES
declare -a GENERATED_GENDERS

# =============================================================================
# OBSERVATIONS - LOINC Codes for Chronic Disease Monitoring
# =============================================================================
# Index 0-1: Diabetes monitoring
# Index 2-3: Hypertension monitoring
# Index 4: Cholesterol monitoring

OBS_LOINC_CODES=("4548-4" "1558-6" "8480-6" "8462-4" "2093-3")
OBS_LOINC_NAMES=("Hemoglobin A1c" "Fasting glucose" "Systolic blood pressure" "Diastolic blood pressure" "Total Cholesterol")
OBS_UNITS=("%" "mg/dL" "mm[Hg]" "mm[Hg]" "mg/dL")
OBS_VALUES=(7.2 126 145 92 220)
OBS_CATEGORIES=("laboratory" "laboratory" "vital-signs" "vital-signs" "laboratory")

# Additional observations for variety
OBS_LOINC_CODES_ALT=("2339-0" "2085-9" "2089-1" "13457-7" "9830-1")
OBS_LOINC_NAMES_ALT=("Blood Glucose" "HDL Cholesterol" "LDL Cholesterol" "LDL Cholesterol calculated" "Cholesterol/HDL ratio")
OBS_UNITS_ALT=("mg/dL" "mg/dL" "mg/dL" "mg/dL" "{ratio}")
OBS_VALUES_ALT=(185 42 148 145 5.2)

# =============================================================================
# MEDICATIONS - RxNorm Codes for Chronic Disease Treatment
# =============================================================================

# Diabetes Medications (RxNorm)
DIABETES_MED_CODES=("860975" "861004" "897122")
DIABETES_MED_NAMES=("Metformin 500 MG Oral Tablet" "Metformin 1000 MG Oral Tablet" "Glipizide 5 MG Oral Tablet")
DIABETES_MED_DOSAGE=("Take 500mg twice daily with meals" "Take 1000mg twice daily with meals" "Take 5mg once daily before breakfast")

# Hypertension Medications (RxNorm)
HYPERTENSION_MED_CODES=("314076" "329528" "979480")
HYPERTENSION_MED_NAMES=("Lisinopril 10 MG Oral Tablet" "Amlodipine 5 MG Oral Tablet" "Losartan 50 MG Oral Tablet")
HYPERTENSION_MED_DOSAGE=("Take 10mg once daily" "Take 5mg once daily" "Take 50mg once daily")

# Cholesterol Medications (RxNorm)
CHOLESTEROL_MED_CODES=("617311" "859751" "859747")
CHOLESTEROL_MED_NAMES=("Atorvastatin 20 MG Oral Tablet" "Rosuvastatin 10 MG Oral Tablet" "Simvastatin 20 MG Oral Tablet")
CHOLESTEROL_MED_DOSAGE=("Take 20mg once daily at bedtime" "Take 10mg once daily" "Take 20mg once daily in the evening")

# Combined medication arrays for rotation
MED_CODES=("860975" "314076" "617311" "329528" "859751")
MED_NAMES=("Metformin 500 MG Oral Tablet" "Lisinopril 10 MG Oral Tablet" "Atorvastatin 20 MG Oral Tablet" "Amlodipine 5 MG Oral Tablet" "Rosuvastatin 10 MG Oral Tablet")
MED_DOSAGE=("Take 500mg twice daily with meals" "Take 10mg once daily" "Take 20mg once daily at bedtime" "Take 5mg once daily" "Take 10mg once daily")
MED_CONDITIONS=("Diabetes" "Hypertension" "Hyperlipidemia" "Hypertension" "Hyperlipidemia")

# =============================================================================
# CARE PLANS - SNOMED CT Coded Goals for Chronic Disease Management
# =============================================================================

# CarePlan titles and conditions (SNOMED CT)
CAREPLAN_CONDITION_CODES=("44054006" "38341003" "13644009")
CAREPLAN_CONDITION_NAMES=("Diabetes mellitus type 2" "Hypertensive disorder" "Hypercholesterolemia")

# Goals with SNOMED CT codes and realistic targets
# Goal 1: Diabetes - HbA1c target
GOAL1_CODE="43396009"
GOAL1_DISPLAY="Hemoglobin A1c measurement"
GOAL1_TARGET="Reduce HbA1c to below 7.0%"
GOAL1_DETAIL="Target HbA1c < 7.0% within 6 months through medication adherence and lifestyle modifications"

# Goal 2: Diabetes - Blood glucose target
GOAL2_CODE="33747003"
GOAL2_DISPLAY="Blood glucose measurement"
GOAL2_TARGET="Maintain fasting blood glucose 80-130 mg/dL"
GOAL2_DETAIL="Achieve fasting glucose 80-130 mg/dL and post-meal glucose < 180 mg/dL"

# Goal 3: Hypertension - Blood pressure target
GOAL3_CODE="75367002"
GOAL3_DISPLAY="Blood pressure taking"
GOAL3_TARGET="Reduce blood pressure to below 130/80 mmHg"
GOAL3_DETAIL="Achieve BP < 130/80 mmHg through medication, diet modification (DASH diet), and regular exercise"

# Goal 4: Hypertension - Lifestyle modification
GOAL4_CODE="266948004"
GOAL4_DISPLAY="Lifestyle education"
GOAL4_TARGET="Reduce sodium intake to less than 2300mg daily"
GOAL4_DETAIL="Dietary sodium restriction < 2300mg/day, increase physical activity to 150 min/week"

# Goal 5: Cholesterol - LDL target
GOAL5_CODE="121868005"
GOAL5_DISPLAY="Total cholesterol measurement"
GOAL5_TARGET="Reduce LDL cholesterol to below 100 mg/dL"
GOAL5_DETAIL="Achieve LDL < 100 mg/dL through statin therapy, dietary changes, and increased physical activity"

# Arrays for rotation
CAREPLAN_TITLES=("Type 2 Diabetes Management Plan" "Hypertension Management Plan" "Hyperlipidemia Management Plan" "Cardiovascular Risk Reduction Plan" "Metabolic Syndrome Care Plan")
GOAL_CODES=("$GOAL1_CODE" "$GOAL3_CODE" "$GOAL5_CODE" "$GOAL4_CODE" "$GOAL2_CODE")
GOAL_DISPLAYS=("$GOAL1_DISPLAY" "$GOAL3_DISPLAY" "$GOAL5_DISPLAY" "$GOAL4_DISPLAY" "$GOAL2_DISPLAY")
GOAL_TARGETS=("$GOAL1_TARGET" "$GOAL3_TARGET" "$GOAL5_TARGET" "$GOAL4_TARGET" "$GOAL2_TARGET")
GOAL_DETAILS=("$GOAL1_DETAIL" "$GOAL3_DETAIL" "$GOAL5_DETAIL" "$GOAL4_DETAIL" "$GOAL2_DETAIL")

# Activity codes (SNOMED CT)
ACTIVITY_CODES=("409073007" "386463000" "226234005" "183301007" "410177006")
ACTIVITY_DISPLAYS=("Patient education" "Physical exercise" "Healthy diet" "Physical therapy" "Special diet education")

# =============================================================================
# PROCEDURES - SNOMED CT Codes for Common Medical Procedures
# =============================================================================

# Procedure codes (SNOMED CT) - common outpatient procedures for chronic disease
PROCEDURE_CODES=("252416005" "710824005" "413467001" "271442007" "170258001" "165278009" "104326007")
PROCEDURE_NAMES=("Blood pressure measurement" "Electrocardiogram monitoring" "Blood glucose monitoring" "Foot examination" "Diabetic retinal screening" "Lipid panel" "Injection of insulin")

# Procedure status options
PROCEDURE_STATUSES=("completed" "completed" "completed")

# Body sites for procedures (SNOMED CT)
BODY_SITE_CODES=("368209003" "80891009" "7569003" "56459004" "81745001" "368209003" "14975008")
BODY_SITE_NAMES=("Right upper arm" "Heart" "Finger" "Foot" "Eye" "Left upper arm" "Forearm")

# Reason codes for procedures (SNOMED CT) - chronic conditions
PROCEDURE_REASON_CODES=("38341003" "44054006" "13644009")
PROCEDURE_REASON_NAMES=("Hypertensive disorder" "Diabetes mellitus type 2" "Hypercholesterolemia")

echo "=========================================="
echo "Creating FHIR Resources on FHIR R5"
echo "Chronic Disease Management Data"
echo "=========================================="

# Function to create an Organization
create_organization() {
    local org_json=$(cat <<EOF
{
    "resourceType": "Organization",
    "active": true,
    "type": [{
        "coding": [{
            "system": "http://terminology.hl7.org/CodeSystem/organization-type",
            "code": "prov",
            "display": "Healthcare Provider"
        }]
    }],
    "name": "${ORGANIZATION_NAME}",
    "address": [{
        "use": "work",
        "type": "physical",
        "line": ["123 Orchard Road"],
        "city": "Singapore",
        "postalCode": "238867",
        "country": "Singapore"
    }],
    "contact": [{
        "purpose": {
            "coding": [{
                "system": "http://terminology.hl7.org/CodeSystem/contactentity-type",
                "code": "ADMIN",
                "display": "Administrative"
            }]
        },
        "telecom": [{
            "system": "phone",
            "value": "+65 6789 0123",
            "use": "work"
        }, {
            "system": "email",
            "value": "admin@goodfaithclinic.sg",
            "use": "work"
        }]
    }]
}
EOF
)

    response=$(curl -s -X POST "${BASE_URL}/Organization" \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "${org_json}")

    org_id=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    echo "$org_id"
}

# Function to create a Patient
create_patient() {
    local index=$1
    local org_id=$2

    # Generate random name using the dynamic name generator
    local name_data=$(generate_random_name $index)
    local base_first_name=$(echo "$name_data" | cut -d'|' -f1)
    local last_name=$(echo "$name_data" | cut -d'|' -f2)
    local gender=$(echo "$name_data" | cut -d'|' -f3)

    # Add batch identifier suffix to first name
    local first_name="${base_first_name}-${BATCH_NUMBER}"

    # Store for later reference in output
    GENERATED_FIRST_NAMES[$index]="$first_name"
    GENERATED_LAST_NAMES[$index]="$last_name"
    GENERATED_GENDERS[$index]="$gender"

    # Generate random birth year (patients aged 30-75)
    local current_year=$(date +%Y)
    local birth_year=$((current_year - 30 - RANDOM % 46))

    local patient_json=$(cat <<EOF
{
    "resourceType": "Patient",
    "active": true,
    "name": [{
        "use": "official",
        "family": "${last_name}",
        "given": ["${first_name}"]
    }],
    "gender": "${gender}",
    "birthDate": "${birth_year}-01-15",
    "address": [{
        "use": "home",
        "type": "physical",
        "city": "Singapore",
        "postalCode": "123456",
        "country": "Singapore"
    }],
    "managingOrganization": {
        "reference": "Organization/${org_id}",
        "display": "${ORGANIZATION_NAME}"
    }
}
EOF
)

    response=$(curl -s -X POST "${BASE_URL}/Patient" \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "${patient_json}")

    patient_id=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    echo "$patient_id"
}

# Function to create an Encounter
create_encounter() {
    local patient_id=$1
    local enc_index=$2
    local org_id=$3

    # Generate random date within configured year range
    local year_range=$((END_YEAR - START_YEAR + 1))
    local random_year=$((START_YEAR + RANDOM % year_range))
    local random_month=$(printf "%02d" $((RANDOM % 12 + 1)))
    local random_day=$(printf "%02d" $((RANDOM % 28 + 1)))
    local start_date="${random_year}-${random_month}-${random_day}T09:00:00Z"
    local end_date="${random_year}-${random_month}-${random_day}T10:00:00Z"

    # Encounter reason codes (SNOMED CT)
    local reason_codes=("185347001" "185349003" "185389009" "185387006" "390906007")
    local reason_displays=("Encounter for check up" "Encounter for follow-up" "Follow-up encounter" "Follow-up visit" "Follow-up encounter")
    local reason_index=$((enc_index % ${#reason_codes[@]}))

    local encounter_json=$(cat <<EOF
{
    "resourceType": "Encounter",
    "status": "completed",
    "class": [{
        "coding": [{
            "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
            "code": "AMB",
            "display": "ambulatory"
        }]
    }],
    "type": [{
        "coding": [{
            "system": "http://snomed.info/sct",
            "code": "${reason_codes[$reason_index]}",
            "display": "${reason_displays[$reason_index]}"
        }]
    }],
    "subject": {
        "reference": "Patient/${patient_id}"
    },
    "actualPeriod": {
        "start": "${start_date}",
        "end": "${end_date}"
    },
    "serviceProvider": {
        "reference": "Organization/${org_id}",
        "display": "${ORGANIZATION_NAME}"
    },
    "reason": [{
        "use": {
            "coding": [{
                "system": "http://terminology.hl7.org/CodeSystem/encounter-reason-use",
                "code": "RV",
                "display": "Reason for Visit"
            }]
        },
        "value": [{
            "concept": {
                "coding": [{
                    "system": "http://snomed.info/sct",
                    "code": "394701000",
                    "display": "Chronic disease management"
                }]
            }
        }]
    }]
}
EOF
)

    response=$(curl -s -X POST "${BASE_URL}/Encounter" \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "${encounter_json}")

    encounter_id=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    echo "$encounter_id"
}

# Function to create an Observation with LOINC codes
create_observation() {
    local patient_id=$1
    local encounter_id=$2
    local obs_index=$3

    local array_index=$((obs_index % ${#OBS_LOINC_CODES[@]}))
    local code=${OBS_LOINC_CODES[$array_index]}
    local display=${OBS_LOINC_NAMES[$array_index]}
    local unit=${OBS_UNITS[$array_index]}
    local value=${OBS_VALUES[$array_index]}
    local category=${OBS_CATEGORIES[$array_index]}

    # Generate random date within configured year range
    local year_range=$((END_YEAR - START_YEAR + 1))
    local random_year=$((START_YEAR + RANDOM % year_range))
    local random_month=$(printf "%02d" $((RANDOM % 12 + 1)))
    local random_day=$(printf "%02d" $((RANDOM % 28 + 1)))
    local obs_date="${random_year}-${random_month}-${random_day}T09:30:00Z"

    # Set display value based on category
    local category_display
    if [ "$category" = "laboratory" ]; then
        category_display="Laboratory"
    else
        category_display="Vital Signs"
    fi

    local observation_json=$(cat <<EOF
{
    "resourceType": "Observation",
    "status": "final",
    "category": [{
        "coding": [{
            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
            "code": "${category}",
            "display": "${category_display}"
        }]
    }],
    "code": {
        "coding": [{
            "system": "http://loinc.org",
            "code": "${code}",
            "display": "${display}"
        }],
        "text": "${display}"
    },
    "subject": {
        "reference": "Patient/${patient_id}"
    },
    "encounter": {
        "reference": "Encounter/${encounter_id}"
    },
    "effectiveDateTime": "${obs_date}",
    "valueQuantity": {
        "value": ${value},
        "unit": "${unit}",
        "system": "http://unitsofmeasure.org",
        "code": "${unit}"
    },
    "interpretation": [{
        "coding": [{
            "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
            "code": "H",
            "display": "High"
        }]
    }]
}
EOF
)

    response=$(curl -s -X POST "${BASE_URL}/Observation" \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "${observation_json}")

    obs_id=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    echo "$obs_id"
}

# Function to create a MedicationRequest with RxNorm codes
create_medication_request() {
    local patient_id=$1
    local encounter_id=$2
    local med_index=$3

    local code=${MED_CODES[$med_index]}
    local display=${MED_NAMES[$med_index]}
    local dosage=${MED_DOSAGE[$med_index]}
    local condition=${MED_CONDITIONS[$med_index]}

    local med_request_json=$(cat <<EOF
{
    "resourceType": "MedicationRequest",
    "status": "active",
    "intent": "order",
    "medication": {
        "concept": {
            "coding": [{
                "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
                "code": "${code}",
                "display": "${display}"
            }],
            "text": "${display}"
        }
    },
    "subject": {
        "reference": "Patient/${patient_id}"
    },
    "encounter": {
        "reference": "Encounter/${encounter_id}"
    },
    "authoredOn": "2025-10-15",
    "reason": [{
        "concept": {
            "coding": [{
                "system": "http://snomed.info/sct",
                "code": "${CAREPLAN_CONDITION_CODES[$((med_index % 3))]}",
                "display": "${CAREPLAN_CONDITION_NAMES[$((med_index % 3))]}"
            }],
            "text": "${condition}"
        }
    }],
    "dosageInstruction": [{
        "text": "${dosage}",
        "timing": {
            "repeat": {
                "frequency": 1,
                "period": 1,
                "periodUnit": "d"
            }
        },
        "route": {
            "coding": [{
                "system": "http://snomed.info/sct",
                "code": "26643006",
                "display": "Oral route"
            }]
        }
    }],
    "dispenseRequest": {
        "numberOfRepeatsAllowed": 3,
        "quantity": {
            "value": 90,
            "unit": "tablets",
            "system": "http://terminology.hl7.org/CodeSystem/v3-orderableDrugForm",
            "code": "TAB"
        },
        "expectedSupplyDuration": {
            "value": 90,
            "unit": "days",
            "system": "http://unitsofmeasure.org",
            "code": "d"
        }
    }
}
EOF
)

    response=$(curl -s -X POST "${BASE_URL}/MedicationRequest" \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "${med_request_json}")

    med_id=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    echo "$med_id"
}

# Function to create a CarePlan with SNOMED CT codes and goals
create_careplan() {
    local patient_id=$1
    local encounter_id=$2
    local plan_index=$3

    local array_index=$((plan_index % ${#CAREPLAN_TITLES[@]}))
    local title=${CAREPLAN_TITLES[$array_index]}
    local goal_code=${GOAL_CODES[$array_index]}
    local goal_display=${GOAL_DISPLAYS[$array_index]}
    local goal_target=${GOAL_TARGETS[$array_index]}
    local goal_detail=${GOAL_DETAILS[$array_index]}
    local activity_code=${ACTIVITY_CODES[$array_index]}
    local activity_display=${ACTIVITY_DISPLAYS[$array_index]}
    local condition_code=${CAREPLAN_CONDITION_CODES[$((array_index % 3))]}
    local condition_name=${CAREPLAN_CONDITION_NAMES[$((array_index % 3))]}

    # Generate dates based on configured year range
    local start_date="${START_YEAR}-01-01"
    local end_date="${END_YEAR}-12-31"

    local careplan_json=$(cat <<EOF
{
    "resourceType": "CarePlan",
    "status": "active",
    "intent": "plan",
    "title": "${title}",
    "description": "${goal_detail}",
    "subject": {
        "reference": "Patient/${patient_id}"
    },
    "encounter": {
        "reference": "Encounter/${encounter_id}"
    },
    "period": {
        "start": "${start_date}",
        "end": "${end_date}"
    },
    "category": [{
        "coding": [{
            "system": "http://hl7.org/fhir/us/core/CodeSystem/careplan-category",
            "code": "assess-plan",
            "display": "Assessment and Plan of Treatment"
        }]
    }],
    "addresses": [{
        "concept": {
            "coding": [{
                "system": "http://snomed.info/sct",
                "code": "${condition_code}",
                "display": "${condition_name}"
            }],
            "text": "${condition_name}"
        }
    }],
    "goal": [{
        "reference": "#goal-${plan_index}"
    }],
    "contained": [{
        "resourceType": "Goal",
        "id": "goal-${plan_index}",
        "lifecycleStatus": "active",
        "achievementStatus": {
            "coding": [{
                "system": "http://terminology.hl7.org/CodeSystem/goal-achievement",
                "code": "in-progress",
                "display": "In Progress"
            }]
        },
        "category": [{
            "coding": [{
                "system": "http://terminology.hl7.org/CodeSystem/goal-category",
                "code": "physiotherapy",
                "display": "Physiotherapy"
            }]
        }],
        "description": {
            "coding": [{
                "system": "http://snomed.info/sct",
                "code": "${goal_code}",
                "display": "${goal_display}"
            }],
            "text": "${goal_target}"
        },
        "subject": {
            "reference": "Patient/${patient_id}"
        },
        "target": [{
            "measure": {
                "coding": [{
                    "system": "http://snomed.info/sct",
                    "code": "${goal_code}",
                    "display": "${goal_display}"
                }]
            },
            "dueDate": "2026-04-15"
        }],
        "statusDate": "2025-10-15",
        "note": [{
            "text": "${goal_detail}"
        }]
    }],
    "activity": [{
        "plannedActivityReference": {
            "reference": "#activity-${plan_index}"
        }
    }],
    "note": [{
        "text": "Care plan created for chronic disease management. Patient to follow medication regimen, dietary modifications, and lifestyle changes. Regular follow-up appointments scheduled for monitoring progress."
    }]
}
EOF
)

    # First create the activity as a contained resource workaround - just include in careplan
    # For R5, we'll create a simpler version

    local careplan_simple_json=$(cat <<EOF
{
    "resourceType": "CarePlan",
    "status": "active",
    "intent": "plan",
    "title": "${title}",
    "description": "${goal_detail}",
    "subject": {
        "reference": "Patient/${patient_id}"
    },
    "encounter": {
        "reference": "Encounter/${encounter_id}"
    },
    "period": {
        "start": "2025-10-15",
        "end": "2026-04-30"
    },
    "category": [{
        "coding": [{
            "system": "http://hl7.org/fhir/us/core/CodeSystem/careplan-category",
            "code": "assess-plan",
            "display": "Assessment and Plan of Treatment"
        }]
    }],
    "addresses": [{
        "concept": {
            "coding": [{
                "system": "http://snomed.info/sct",
                "code": "${condition_code}",
                "display": "${condition_name}"
            }],
            "text": "${condition_name}"
        }
    }],
    "activity": [{
        "plannedActivityDetail": {
            "kind": "ServiceRequest",
            "code": {
                "coding": [{
                    "system": "http://snomed.info/sct",
                    "code": "${activity_code}",
                    "display": "${activity_display}"
                }]
            },
            "status": "in-progress",
            "description": "${goal_target}",
            "goal": [{
                "coding": [{
                    "system": "http://snomed.info/sct",
                    "code": "${goal_code}",
                    "display": "${goal_display}"
                }],
                "text": "${goal_target}"
            }]
        }
    }],
    "note": [{
        "text": "${goal_detail} Regular monitoring and follow-up appointments scheduled."
    }]
}
EOF
)

    response=$(curl -s -X POST "${BASE_URL}/CarePlan" \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "${careplan_simple_json}")

    plan_id=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    echo "$plan_id"
}

# Function to create a Procedure with SNOMED CT codes
create_procedure() {
    local patient_id=$1
    local encounter_id=$2
    local proc_index=$3

    # Select procedure based on index (rotate through available procedures)
    local proc_array_index=$((proc_index % ${#PROCEDURE_CODES[@]}))
    local code=${PROCEDURE_CODES[$proc_array_index]}
    local display=${PROCEDURE_NAMES[$proc_array_index]}
    local body_site_code=${BODY_SITE_CODES[$proc_array_index]}
    local body_site_name=${BODY_SITE_NAMES[$proc_array_index]}
    local reason_index=$((proc_index % ${#PROCEDURE_REASON_CODES[@]}))
    local reason_code=${PROCEDURE_REASON_CODES[$reason_index]}
    local reason_name=${PROCEDURE_REASON_NAMES[$reason_index]}

    # Generate random date within configured year range
    local year_range=$((END_YEAR - START_YEAR + 1))
    local random_year=$((START_YEAR + RANDOM % year_range))
    local random_month=$(printf "%02d" $((RANDOM % 12 + 1)))
    local random_day=$(printf "%02d" $((RANDOM % 28 + 1)))
    local proc_date="${random_year}-${random_month}-${random_day}"
    local proc_datetime="${proc_date}T10:00:00Z"

    local procedure_json=$(cat <<EOF
{
    "resourceType": "Procedure",
    "status": "completed",
    "code": {
        "coding": [{
            "system": "http://snomed.info/sct",
            "code": "${code}",
            "display": "${display}"
        }],
        "text": "${display}"
    },
    "subject": {
        "reference": "Patient/${patient_id}"
    },
    "encounter": {
        "reference": "Encounter/${encounter_id}"
    },
    "occurrenceDateTime": "${proc_datetime}",
    "recorder": {
        "display": "Good Faith Clinic Staff"
    },
    "performer": [{
        "actor": {
            "display": "Good Faith Clinic Staff"
        }
    }],
    "reason": [{
        "concept": {
            "coding": [{
                "system": "http://snomed.info/sct",
                "code": "${reason_code}",
                "display": "${reason_name}"
            }],
            "text": "${reason_name}"
        }
    }],
    "bodySite": [{
        "coding": [{
            "system": "http://snomed.info/sct",
            "code": "${body_site_code}",
            "display": "${body_site_name}"
        }]
    }],
    "note": [{
        "text": "Procedure performed as part of chronic disease management."
    }]
}
EOF
)

    response=$(curl -s -X POST "${BASE_URL}/Procedure" \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "${procedure_json}")

    proc_id=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    echo "$proc_id"
}

# Main execution
echo ""
echo "Creating Organization: ${ORGANIZATION_NAME}..."
echo "------------------------------------------------"
ORGANIZATION_ID=$(create_organization)
echo "Created Organization: ${ORGANIZATION_NAME} (ID: $ORGANIZATION_ID)"

echo ""
echo "Creating ${NUM_PATIENTS} Patients..."
echo "------------------------"

# Track total counts for summary
TOTAL_ENCOUNTERS=0
TOTAL_OBSERVATIONS=0
TOTAL_MEDICATIONS=0
TOTAL_CAREPLANS=0
TOTAL_PROCEDURES=0

for ((i=0; i<NUM_PATIENTS; i++)); do
    patient_id=$(create_patient $i "$ORGANIZATION_ID")
    PATIENT_IDS+=("$patient_id")
    echo "Created Patient $((i+1)): ${GENERATED_FIRST_NAMES[$i]} ${GENERATED_LAST_NAMES[$i]} (ID: $patient_id)"
done

echo ""
echo "Creating Encounters, Observations, MedicationRequests, CarePlans, and Procedures..."
echo "------------------------------------------------------------------------------------"

for ((p=0; p<NUM_PATIENTS; p++)); do
    patient_id=${PATIENT_IDS[$p]}
    echo ""
    echo "Patient $((p+1)) (ID: $patient_id):"

    # Store first encounter ID for procedures
    first_encounter_id=""

    for ((e=0; e<NUM_ENCOUNTERS; e++)); do
        # Create Encounter
        encounter_id=$(create_encounter "$patient_id" $e "$ORGANIZATION_ID")
        echo "  Encounter $((e+1)): $encounter_id"
        ((TOTAL_ENCOUNTERS++))

        # Store first encounter for procedures
        if [ $e -eq 0 ]; then
            first_encounter_id="$encounter_id"
        fi

        # Create Observations
        for ((o=0; o<NUM_OBSERVATIONS; o++)); do
            obs_index=$(( (e + o) % ${#OBS_LOINC_CODES[@]} ))
            obs_id=$(create_observation "$patient_id" "$encounter_id" $obs_index)
            echo "    -> Observation [LOINC ${OBS_LOINC_CODES[$obs_index]}]: $obs_id (${OBS_LOINC_NAMES[$obs_index]})"
            ((TOTAL_OBSERVATIONS++))
        done

        # Create MedicationRequests
        for ((m=0; m<NUM_MEDICATIONS; m++)); do
            med_index=$(( (e + m) % ${#MED_CODES[@]} ))
            med_id=$(create_medication_request "$patient_id" "$encounter_id" $med_index)
            echo "    -> MedicationRequest [RxNorm ${MED_CODES[$med_index]}]: $med_id (${MED_NAMES[$med_index]})"
            ((TOTAL_MEDICATIONS++))
        done

        # Create CarePlans
        for ((c=0; c<NUM_CAREPLANS; c++)); do
            plan_index=$(( (e + c) % ${#CAREPLAN_TITLES[@]} ))
            plan_id=$(create_careplan "$patient_id" "$encounter_id" $plan_index)
            echo "    -> CarePlan [SNOMED CT]: $plan_id (${CAREPLAN_TITLES[$plan_index]})"
            ((TOTAL_CAREPLANS++))
        done
    done

    # Create Procedures for this patient (2-3 per patient, associated with first encounter)
    echo "  Procedures for patient:"
    for ((pr=0; pr<NUM_PROCEDURES; pr++)); do
        proc_id=$(create_procedure "$patient_id" "$first_encounter_id" $pr)
        proc_index=$((pr % ${#PROCEDURE_CODES[@]}))
        echo "    -> Procedure [SNOMED CT ${PROCEDURE_CODES[$proc_index]}]: $proc_id (${PROCEDURE_NAMES[$proc_index]})"
        ((TOTAL_PROCEDURES++))
    done
done

# Calculate total resources
TOTAL_RESOURCES=$((1 + NUM_PATIENTS + TOTAL_ENCOUNTERS + TOTAL_OBSERVATIONS + TOTAL_MEDICATIONS + TOTAL_CAREPLANS + TOTAL_PROCEDURES))

echo ""
echo "=========================================="
echo "SUMMARY"
echo "=========================================="
echo "Organization: ${ORGANIZATION_NAME} (ID: ${ORGANIZATION_ID})"
echo "Date Range: ${START_YEAR} - ${END_YEAR}"
echo "Batch Number: ${BATCH_NUMBER}"
echo ""
echo "Created:"
echo "  - 1 Organization (Good Faith Clinic)"
echo "  - ${NUM_PATIENTS} Patients (Singaporean names with batch suffix -${BATCH_NUMBER})"
echo "  - ${TOTAL_ENCOUNTERS} Encounters (${NUM_ENCOUNTERS} per patient)"
echo "  - ${TOTAL_OBSERVATIONS} Observations with LOINC codes:"
echo "      * 4548-4: Hemoglobin A1c (Diabetes)"
echo "      * 1558-6: Fasting glucose (Diabetes)"
echo "      * 8480-6: Systolic BP (Hypertension)"
echo "      * 8462-4: Diastolic BP (Hypertension)"
echo "      * 2093-3: Total Cholesterol (Hyperlipidemia)"
echo "  - ${TOTAL_MEDICATIONS} MedicationRequests with RxNorm codes:"
echo "      * 860975: Metformin 500 MG (Diabetes)"
echo "      * 314076: Lisinopril 10 MG (Hypertension)"
echo "      * 617311: Atorvastatin 20 MG (Cholesterol)"
echo "      * 329528: Amlodipine 5 MG (Hypertension)"
echo "      * 859751: Rosuvastatin 10 MG (Cholesterol)"
echo "  - ${TOTAL_CAREPLANS} CarePlans with SNOMED CT coded goals:"
echo "      * Diabetes Management (HbA1c target < 7.0%)"
echo "      * Hypertension Management (BP target < 130/80)"
echo "      * Hyperlipidemia Management (LDL target < 100)"
echo "  - ${TOTAL_PROCEDURES} Procedures with SNOMED CT codes (${NUM_PROCEDURES} per patient):"
echo "      * 252416005: Blood pressure measurement"
echo "      * 710824005: Electrocardiogram monitoring"
echo "      * 413467001: Blood glucose monitoring"
echo "      * 271442007: Foot examination"
echo "      * 170258001: Diabetic retinal screening"
echo ""
echo "Total: ${TOTAL_RESOURCES} FHIR Resources"
echo "=========================================="
echo ""
echo "You can verify the data at: http://locahost:8080/fhir"
