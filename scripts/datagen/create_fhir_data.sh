#!/bin/bash

# Local FHIR R5 Server
BASE_URL="http://localhost:8080/fhir"

# Arrays to store created resource IDs
declare -a PATIENT_IDS
declare -a ENCOUNTER_IDS
ORGANIZATION_ID=""

# Organization data
ORGANIZATION_NAME="Good Faith Clinic"

# Sample patient data - Singaporean names
FIRST_NAMES=("Vicky" "Jeremy" "Vincent" "Michelle" "Wei Ming" "Jia Hui" "Jun Jie" "Xiu Ling" "Kai Wen" "Mei Ling")
LAST_NAMES=("Tan" "Tan" "Leong" "Lim" "Lee" "Ng" "Ong" "Wong" "Goh" "Chua")
GENDERS=("female" "male" "male" "female" "male" "female" "male" "female" "male" "female")

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
    local first_name=${FIRST_NAMES[$index]}
    local last_name=${LAST_NAMES[$index]}
    local gender=${GENDERS[$index]}
    local birth_year=$((1955 + index * 3))

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

    # Date range: Oct 2025 to Jan 2026
    local months=("2025-10" "2025-11" "2025-12" "2026-01" "2026-01")
    local days=("15" "10" "05" "08" "20")
    local start_date="${months[$enc_index]}-${days[$enc_index]}T09:00:00Z"
    local end_date="${months[$enc_index]}-${days[$enc_index]}T10:00:00Z"

    # Encounter reason codes (SNOMED CT)
    local reason_codes=("185347001" "185349003" "185389009" "185387006" "390906007")
    local reason_displays=("Encounter for check up" "Encounter for follow-up" "Follow-up encounter" "Follow-up visit" "Follow-up encounter")

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
            "code": "${reason_codes[$enc_index]}",
            "display": "${reason_displays[$enc_index]}"
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

    local code=${OBS_LOINC_CODES[$obs_index]}
    local display=${OBS_LOINC_NAMES[$obs_index]}
    local unit=${OBS_UNITS[$obs_index]}
    local value=${OBS_VALUES[$obs_index]}
    local category=${OBS_CATEGORIES[$obs_index]}

    # Date range: Oct 2025 to Jan 2026
    local obs_dates=("2025-10-15T09:30:00Z" "2025-11-10T09:30:00Z" "2025-12-05T09:30:00Z" "2026-01-08T09:30:00Z" "2026-01-20T09:30:00Z")
    local obs_date="${obs_dates[$obs_index]}"

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

    local title=${CAREPLAN_TITLES[$plan_index]}
    local goal_code=${GOAL_CODES[$plan_index]}
    local goal_display=${GOAL_DISPLAYS[$plan_index]}
    local goal_target=${GOAL_TARGETS[$plan_index]}
    local goal_detail=${GOAL_DETAILS[$plan_index]}
    local activity_code=${ACTIVITY_CODES[$plan_index]}
    local activity_display=${ACTIVITY_DISPLAYS[$plan_index]}
    local condition_code=${CAREPLAN_CONDITION_CODES[$((plan_index % 3))]}
    local condition_name=${CAREPLAN_CONDITION_NAMES[$((plan_index % 3))]}

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

# Main execution
echo ""
echo "Creating Organization: ${ORGANIZATION_NAME}..."
echo "------------------------------------------------"
ORGANIZATION_ID=$(create_organization)
echo "Created Organization: ${ORGANIZATION_NAME} (ID: $ORGANIZATION_ID)"

echo ""
echo "Creating 10 Patients..."
echo "------------------------"

for i in {0..9}; do
    patient_id=$(create_patient $i "$ORGANIZATION_ID")
    PATIENT_IDS+=("$patient_id")
    echo "Created Patient $((i+1)): ${FIRST_NAMES[$i]} ${LAST_NAMES[$i]} (ID: $patient_id)"
done

echo ""
echo "Creating Encounters, Observations, MedicationRequests, and CarePlans..."
echo "------------------------------------------------------------------------"

for p in {0..9}; do
    patient_id=${PATIENT_IDS[$p]}
    echo ""
    echo "Patient $((p+1)) (ID: $patient_id):"

    for e in {0..4}; do
        # Create Encounter
        encounter_id=$(create_encounter "$patient_id" $e "$ORGANIZATION_ID")
        echo "  Encounter $((e+1)): $encounter_id"

        # Create Observation with LOINC code
        obs_id=$(create_observation "$patient_id" "$encounter_id" $e)
        echo "    -> Observation [LOINC ${OBS_LOINC_CODES[$e]}]: $obs_id (${OBS_LOINC_NAMES[$e]})"

        # Create MedicationRequest with RxNorm code
        med_id=$(create_medication_request "$patient_id" "$encounter_id" $e)
        echo "    -> MedicationRequest [RxNorm ${MED_CODES[$e]}]: $med_id (${MED_NAMES[$e]})"

        # Create CarePlan with SNOMED CT codes
        plan_id=$(create_careplan "$patient_id" "$encounter_id" $e)
        echo "    -> CarePlan [SNOMED CT]: $plan_id (${CAREPLAN_TITLES[$e]})"
    done
done

echo ""
echo "=========================================="
echo "SUMMARY"
echo "=========================================="
echo "Organization: ${ORGANIZATION_NAME} (ID: ${ORGANIZATION_ID})"
echo "Date Range: Oct 2025 - Jan 2026"
echo ""
echo "Created:"
echo "  - 1 Organization (Good Faith Clinic)"
echo "  - 10 Patients (Singaporean names)"
echo "  - 50 Encounters (5 per patient)"
echo "  - 50 Observations with LOINC codes:"
echo "      * 4548-4: Hemoglobin A1c (Diabetes)"
echo "      * 1558-6: Fasting glucose (Diabetes)"
echo "      * 8480-6: Systolic BP (Hypertension)"
echo "      * 8462-4: Diastolic BP (Hypertension)"
echo "      * 2093-3: Total Cholesterol (Hyperlipidemia)"
echo "  - 50 MedicationRequests with RxNorm codes:"
echo "      * 860975: Metformin 500 MG (Diabetes)"
echo "      * 314076: Lisinopril 10 MG (Hypertension)"
echo "      * 617311: Atorvastatin 20 MG (Cholesterol)"
echo "      * 329528: Amlodipine 5 MG (Hypertension)"
echo "      * 859751: Rosuvastatin 10 MG (Cholesterol)"
echo "  - 50 CarePlans with SNOMED CT coded goals:"
echo "      * Diabetes Management (HbA1c target < 7.0%)"
echo "      * Hypertension Management (BP target < 130/80)"
echo "      * Hyperlipidemia Management (LDL target < 100)"
echo ""
echo "Total: 211 FHIR Resources"
echo "=========================================="
echo ""
echo "You can verify the data at: http://locahost:8080/fhir"
