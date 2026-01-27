# FHIR4Java Agents

> **A Modern, AI-Powered HL7 FHIR R5 Server Implementation in Java**

[![FHIR R5](https://img.shields.io/badge/FHIR-R5-blue)](https://hl7.org/fhir/R5/)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.x-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

---

## Overview

**FHIR4Java Agents** is an enterprise-grade [HL7 FHIR](https://hl7.org/fhir/) server implementation built with Java and Spring Boot. This project demonstrates the powerful combination of healthcare interoperability standards (FHIR R5) with modern AI capabilities.

The server is designed to be **configuration-driven**, allowing healthcare organizations to:
- Support standard [FHIR R5 resources](https://hl7.org/fhir/R5/resourcelist.html), [FHIR R4B resources](https://hl7.org/fhir/R4B/documentation.html) and any future FHIR versions out of the box
- Suppoert custom resources by defining custom resources without manual coding
- Extend functionality through a flexible plugin architecture
- Leverage AI for intelligent plugin discovery and orchestration

### What is HL7 FHIR?

[HL7 FHIR (Fast Healthcare Interoperability Resources)](https://hl7.org/fhir/) is the modern standard for exchanging healthcare information electronically. FHIR combines the best features of HL7's previous standards while leveraging the latest web technologies.

**Key FHIR Resources:**
- [Organization](https://hl7.org/fhir/R5/organization.html) - Healthcare oganization information
- [Patient](https://hl7.org/fhir/R5/patient.html) - Demographics and administrative information
- [Encounter](https://hl7.org/fhir/R5/encounter.html) - Healthcare interactions
- [Observation](https://hl7.org/fhir/R5/observation.html) - Measurements, vital signs, lab results
- [Condition](https://hl7.org/fhir/R5/condition.html) - Problems, diagnoses
- [Procedure](https://hl7.org/fhir/R5/procedure.html) - Clinical procedures
- [MedicationRequest](https://hl7.org/fhir/R5/medicationrequest.html) - Prescriptions
- [CarePlan](https://hl7.org/fhir/R5/careplan.html) - Intention of how one or more practitioners intend to deliver care for a particular patient, group or community for a period of time, possibly limited to care for a specific condition or set of conditions.

---

## Features

### Core FHIR Capabilities

| Feature | Description |
|---------|-------------|
| **FHIR R5 Support** | Full support for [HL7 FHIR R5](https://hl7.org/fhir/R5/) specification |
| **RESTful API** | Complete [FHIR RESTful API](https://hl7.org/fhir/R5/http.html) implementation |
| **CRUD Operations** | Create, Read, Update, Delete for all resource types |
| **Search** | Advanced [search](https://hl7.org/fhir/R5/search.html) with all standard parameters, modifiers, and prefixes |
| **Extended Operations** | Support for [$validate](https://hl7.org/fhir/R5/resource-operation-validate.html), $merge, $everything |
| **History** | Full [resource history](https://hl7.org/fhir/R5/http.html#history) and versioning |
| **Validation** | Resource validation against [StructureDefinitions](https://hl7.org/fhir/R5/structuredefinition.html) |
| **CapabilityStatement** | Auto-generated [CapabilityStatement](https://hl7.org/fhir/R5/capabilitystatement.html) |

### Search Parameters

Full support for [FHIR Search](https://hl7.org/fhir/R5/search.html) including:
- **Common Parameters**: `_id`, `_lastUpdated`, `_tag`, `_profile`, `_security`, `_source`, `_text`, `_content`, `_filter`
- **Search Modifiers**: `:exact`, `:contains`, `:missing`, `:not`, `:above`, `:below`, `:in`, `:not-in`, `:of-type`
- **Search Prefixes**: `eq`, `ne`, `gt`, `lt`, `ge`, `le`, `sa`, `eb`, `ap` for date/number/quantity
- **Chaining**: `subject:Patient.name=Smith`
- **Reverse Chaining**: `_has:Observation:subject:code=1234-5`

### Enterprise Features

- **Multi-tenancy Ready** - Support for multiple organizations
- **Caching** - Two-level caching (L1 in-memory + L2 Redis)
- **Audit Logging** - Comprehensive audit trail for compliance
- **Performance Monitoring** - Built-in telemetry and tracing
- **High Availability** - Designed for scalability with PostgreSQL

### Plugin Architecture

The application supports a **hybrid plugin architecture** with two approaches:

#### 1. Native Code Plugins (Spring Bean)
- Embedded plugins running in the same JVM
- Best for performance-critical operations
- Tight integration with Spring ecosystem
- Business logic plugins execute before/after FHIR operations

#### 2. MCP (Model Context Protocol) Plugins
- **Any Programming Language**: Write plugins in Python, Node.js, Go, Rust, or any language
- **Run Anywhere**: Plugins can run as separate processes, containers, or remote services
- **Hot-Pluggable**: Update plugins without restarting the main server
- **Fault Isolation**: Plugin crashes don't affect the main FHIR server

### AI Self-Discovery

With MCP protocol support, the application enables **AI-powered plugin discovery**:

- AI agents can dynamically discover available plugins and their capabilities at runtime
- Plugins expose their tools and resources through the standardized MCP interface
- AI can intelligently select and orchestrate plugins based on the task requirements
- No hardcoded plugin dependencies - the system adapts to available capabilities

This architecture aligns with the industry-standard [Model Context Protocol](https://modelcontextprotocol.io/) adopted by Anthropic, OpenAI, Microsoft, and the Linux Foundation.

### Detail feature description and implementation plan

Refer to this document for details [Design feature description and implementation plan](https://github.com/sg-victorchai/fhir4java-agents/blob/main/FHIR4JAVA-IMPLEMENTATION-PLAN.md)


---

## Technology Stack

| Component | Technology |
|-----------|------------|
| **Language** | Java 25 |
| **Framework** | Spring Boot 3.x |
| **FHIR Library** | [HAPI FHIR](https://hapifhir.io/hapi-fhir/docs/getting_started/downloading_and_importing.html) |
| **Database** | PostgreSQL 16 with JSONB |
| **Cache** | Redis 7.x |
| **Testing** | JUnit 5, Cucumber BDD, TestContainers |
| **Build** | Maven |
| **Containerization** | Docker, Docker Compose |

---

## Quick Start

### Prerequisites
- Java 25
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL 16+ (or use Docker)
- Redis 7+ (or use Docker)

### Run with Docker Compose

```bash
# Clone the repository
git clone https://github.com/sg-victorchai/fhir4java-agents.git
cd fhir4java-agents

# Start all services
docker compose up -d

# Server will be available at http://localhost:8080/fhir/r5
```
### Sample Test Data Generation

```bash
# Run this script to generate set of data for commmonly used FHIR resources
cd scripts/datagen
./create_fhir_data.sh
```

### Example API Calls

```bash
# Get CapabilityStatement
curl http://localhost:8080/fhir/r5/metadata

# Create a Patient
curl -X POST http://localhost:8080/fhir/r5/Patient \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"Patient","name":[{"family":"Smith","given":["John"]}]}'

# Search for Patients
curl "http://localhost:8080/fhir/r5/Patient?family=Smith"
```

---

## Project Structure

```
fhir4java/
├── db/                          # Database scripts
├── fhir4java-core/              # Core FHIR processing engine
├── fhir4java-persistence/       # JPA and database layer
├── fhir4java-api/               # REST API layer
├── fhir4java-plugin/            # Plugin framework
├── fhir4java-server/            # Spring Boot application
│   └── src/main/resources/
│       └── fhir-config/         # FHIR configuration
│           ├── searchparameters/  # SearchParameter definitions
│           ├── operations/        # OperationDefinition files
│           └── profiles/          # StructureDefinition files
└── docker/                      # Docker configuration
```

---

## Documentation

- [Implementation Plan](FHIR4JAVA-IMPLEMENTATION-PLAN.md) - Comprehensive design for the enterprise FHIR server

### External Resources

- [HL7 FHIR R5 Specification](https://hl7.org/fhir/R5/)
- [FHIR RESTful API](https://hl7.org/fhir/R5/http.html)
- [FHIR Search](https://hl7.org/fhir/R5/search.html)
- [FHIR Operations](https://hl7.org/fhir/R5/operations.html)
- [HAPI FHIR Documentation](https://hapifhir.io/hapi-fhir/docs/)
- [Model Context Protocol](https://modelcontextprotocol.io/)

---

## Use Cases

FHIR4Java Agents is suitable for:

- **Healthcare Information Exchange (HIE)** - Connecting disparate healthcare systems
- **Electronic Health Records (EHR)** - Building modern EHR backends
- **Clinical Decision Support** - Integrating AI with clinical data
- **Patient Portals** - Providing patients access to their health data
- **Research Platforms** - Supporting clinical research data management
- **Health App Development** - Backend for mobile health applications
- **Interoperability Solutions** - Connecting legacy systems with modern FHIR APIs

---

## Keywords

`FHIR` `HL7` `FHIR R5` `Healthcare Interoperability` `Java FHIR Server` `Spring Boot FHIR` `HAPI FHIR` `Healthcare API` `Medical Records` `EHR Integration` `Clinical Data` `Health Information Exchange` `REST API` `JSON` `Healthcare Standards` `Interoperability` `Patient Data` `Clinical Resources` `SMART on FHIR` `Healthcare IT`

---

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting pull requests.

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- [HL7 International](https://www.hl7.org/fhir) for the FHIR specification
- [HAPI FHIR](https://hapifhir.io/) for the excellent Java FHIR library
- [Anthropic](https://www.anthropic.com/) for Claude AI assistance in development
