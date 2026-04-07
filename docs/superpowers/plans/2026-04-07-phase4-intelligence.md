# Phase 4: Intelligence Implementation Plan (Weeks 12-17)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Enable natural language queries, vector search for clinical narratives, and bulk data export.

**Architecture:** NL-to-FHIR translation with rule-based NLP (LLM fallback optional). pgvector for embeddings. Async $export with NDJSON streaming.

**Tech Stack:** pgvector, Spring AI (embeddings), HAPI FHIR $export

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md` - Pillar 3, Pillar 5

---

## File Structure

```
fhir4java-core/src/main/java/org/fhirframework/core/
├── nlsearch/
│   ├── NaturalLanguageSearchService.java  # NL-to-FHIR translation
│   ├── QueryDecomposer.java               # Break query into sub-queries
│   ├── ClinicalConceptExtractor.java      # Extract SNOMED/LOINC codes
│   └── FhirSearchBuilder.java             # Build FHIR search params

fhir4java-persistence/src/main/java/org/fhirframework/persistence/
├── embedding/
│   ├── EmbeddingService.java              # Generate/store embeddings
│   ├── EmbeddingProvider.java             # SPI interface
│   ├── OpenAiEmbeddingProvider.java       # OpenAI implementation
│   └── VectorSearchRepository.java        # pgvector queries

fhir4java-api/src/main/java/org/fhirframework/api/
├── controller/
│   ├── NLSearchController.java            # POST /api/ai/search
│   ├── SemanticSearchController.java      # POST /api/ai/semantic-search
│   └── BulkExportController.java          # GET /$export
├── export/
│   ├── BulkExportService.java             # Async export job
│   └── NdjsonWriter.java                  # NDJSON streaming

fhir4java-plugin/src/main/java/org/fhirframework/plugin/
├── embedding/
│   └── EmbeddingPlugin.java               # AFTER plugin generates embeddings
```

---

## Tasks

### Task 1: NL-to-FHIR Search Service

**Files:** `fhir4java-core/.../nlsearch/NaturalLanguageSearchService.java`

- [ ] **Step 1:** Write failing test for simple query translation
- [ ] **Step 2:** Implement pattern matching (Tier 1) for common queries
- [ ] **Step 3:** Implement rule-based NLP (Tier 2) with clinical concept extraction
- [ ] **Step 4:** Add SNOMED/LOINC code lookup from SearchParameterRegistry
- [ ] **Step 5:** Commit `feat(core): add NL-to-FHIR search translation`

### Task 2: NL Search API Endpoint

**Files:** `fhir4java-api/.../NLSearchController.java`

- [ ] **Step 1:** Write failing test for `POST /api/ai/search`
- [ ] **Step 2:** Implement controller calling NaturalLanguageSearchService
- [ ] **Step 3:** Return FHIR Bundle with matched resources
- [ ] **Step 4:** Commit `feat(api): add natural language search endpoint`

### Task 3: Embedding Provider SPI

**Files:** `fhir4java-persistence/.../embedding/EmbeddingProvider.java`

- [ ] **Step 1:** Define EmbeddingProvider interface (embed, embedBatch, getDimensions)
- [ ] **Step 2:** Implement OpenAiEmbeddingProvider
- [ ] **Step 3:** Add configuration for provider selection and API keys
- [ ] **Step 4:** Commit `feat(persistence): add embedding provider SPI`

### Task 4: Vector Storage with pgvector

**Files:** `fhir4java-persistence/.../embedding/VectorSearchRepository.java`

- [ ] **Step 1:** Add Flyway migration for `fhir_resource_embedding` table with pgvector
- [ ] **Step 2:** Implement VectorSearchRepository with cosine similarity search
- [ ] **Step 3:** Write test for vector similarity query
- [ ] **Step 4:** Commit `feat(persistence): add pgvector-based embedding storage`

### Task 5: Embedding Plugin

**Files:** `fhir4java-plugin/.../embedding/EmbeddingPlugin.java`

- [ ] **Step 1:** Implement AFTER plugin that extracts narrative text
- [ ] **Step 2:** Generate embeddings async via EmbeddingProvider
- [ ] **Step 3:** Store in fhir_resource_embedding table
- [ ] **Step 4:** Commit `feat(plugin): add embedding generation plugin`

### Task 6: Semantic Search Endpoint

**Files:** `fhir4java-api/.../SemanticSearchController.java`

- [ ] **Step 1:** Write failing test for `POST /api/ai/semantic-search`
- [ ] **Step 2:** Embed query text, search via VectorSearchRepository
- [ ] **Step 3:** Return results with similarity scores
- [ ] **Step 4:** Commit `feat(api): add semantic search endpoint`

### Task 7: Bulk Export ($export)

**Files:** `fhir4java-api/.../BulkExportController.java`, `BulkExportService.java`

- [ ] **Step 1:** Write test for `GET /$export` returning async job URL
- [ ] **Step 2:** Implement BulkExportService with async job processing
- [ ] **Step 3:** Implement NdjsonWriter for streaming output
- [ ] **Step 4:** Add job status endpoint `GET /$export/{jobId}`
- [ ] **Step 5:** Commit `feat(api): add FHIR Bulk Data Export`

---

## Summary

| Task | Deliverable |
|------|-------------|
| 1-2 | NL-to-FHIR search (rule-based) |
| 3-6 | Vector search with pgvector |
| 7 | Bulk data export |

**Total: 7 tasks**
