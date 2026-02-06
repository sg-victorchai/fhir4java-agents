package org.fhirframework.core.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.fhirframework.core.version.FhirVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for FHIR conformance resources (StructureDefinition, SearchParameter, OperationDefinition).
 * <p>
 * Loads conformance resource definitions from JSON files organized by FHIR version
 * and provides in-memory lookup by ID, URL, and name with support for searching.
 * </p>
 * <p>
 * These resources are read-only and served directly from the file system.
 * </p>
 */
@Component
public class ConformanceResourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConformanceResourceRegistry.class);

    private final PathMatchingResourcePatternResolver resourceResolver;
    private final ObjectMapper objectMapper;

    // Version -> Type -> ID -> Raw JSON content
    private final Map<FhirVersion, Map<ConformanceResourceType, Map<String, String>>> resourcesById = new ConcurrentHashMap<>();

    // Version -> Type -> URL -> ID (for lookup by canonical URL)
    private final Map<FhirVersion, Map<ConformanceResourceType, Map<String, String>>> urlToIdIndex = new ConcurrentHashMap<>();

    // Version -> Type -> Name -> List of IDs (name may not be unique)
    private final Map<FhirVersion, Map<ConformanceResourceType, Map<String, List<String>>>> nameToIdsIndex = new ConcurrentHashMap<>();

    // Version -> Type -> Base Resource Type -> List of IDs (for SearchParameter base filtering)
    private final Map<FhirVersion, Map<ConformanceResourceType, Map<String, List<String>>>> baseToIdsIndex = new ConcurrentHashMap<>();

    @Value("${fhir4java.config.base-path:classpath:fhir-config/}")
    private String basePath;

    public ConformanceResourceRegistry() {
        this.resourceResolver = new PathMatchingResourcePatternResolver();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void loadConformanceResources() {
        log.info("Loading conformance resources from: {}", basePath);

        for (FhirVersion version : FhirVersion.values()) {
            resourcesById.put(version, new ConcurrentHashMap<>());
            urlToIdIndex.put(version, new ConcurrentHashMap<>());
            nameToIdsIndex.put(version, new ConcurrentHashMap<>());
            baseToIdsIndex.put(version, new ConcurrentHashMap<>());

            for (ConformanceResourceType type : ConformanceResourceType.values()) {
                loadResourcesForVersionAndType(version, type);
            }
        }

        logSummary();
    }

    private void loadResourcesForVersionAndType(FhirVersion version, ConformanceResourceType type) {
        String versionPath = basePath + version.getCode() + "/" + type.getDirectoryName() + "/";
        log.debug("Loading {} for version {} from: {}", type.getResourceTypeName(), version, versionPath);

        Map<String, String> idMap = new ConcurrentHashMap<>();
        Map<String, String> urlMap = new ConcurrentHashMap<>();
        Map<String, List<String>> nameMap = new ConcurrentHashMap<>();
        Map<String, List<String>> baseMap = new ConcurrentHashMap<>();

        resourcesById.get(version).put(type, idMap);
        urlToIdIndex.get(version).put(type, urlMap);
        nameToIdsIndex.get(version).put(type, nameMap);
        baseToIdsIndex.get(version).put(type, baseMap);

        try {
            String pattern = versionPath + type.getFilePattern();
            Resource[] files = resourceResolver.getResources(pattern);
            log.debug("Found {} {} files for version {}", files.length, type.getResourceTypeName(), version);

            for (Resource file : files) {
                loadConformanceResource(version, type, file, idMap, urlMap, nameMap, baseMap);
            }

        } catch (IOException e) {
            log.warn("Failed to load {} for version {}: {}", type.getResourceTypeName(), version, e.getMessage());
        }
    }

    private void loadConformanceResource(FhirVersion version, ConformanceResourceType type,
                                          Resource file, Map<String, String> idMap,
                                          Map<String, String> urlMap, Map<String, List<String>> nameMap,
                                          Map<String, List<String>> baseMap) {
        String filename = file.getFilename();
        try (InputStream is = file.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(content);

            String id = extractId(json, filename, type);
            if (id == null) {
                log.warn("Skipping {} - could not determine ID", filename);
                return;
            }

            // Store raw JSON by ID
            idMap.put(id, content);

            // Index by URL
            String url = getTextValue(json, "url");
            if (url != null) {
                urlMap.put(url, id);
            }

            // Index by name
            String name = getTextValue(json, "name");
            if (name != null) {
                nameMap.computeIfAbsent(name, k -> new ArrayList<>()).add(id);
            }

            // Index by base (for SearchParameter)
            if (type == ConformanceResourceType.SEARCH_PARAMETER) {
                JsonNode baseArray = json.get("base");
                if (baseArray != null && baseArray.isArray()) {
                    for (JsonNode baseNode : baseArray) {
                        String base = baseNode.asText();
                        if (base != null && !base.isEmpty()) {
                            baseMap.computeIfAbsent(base, k -> new ArrayList<>()).add(id);
                        }
                    }
                }
            }

            log.trace("Loaded {} {} with id={}, url={}, name={}",
                    type.getResourceTypeName(), filename, id, url, name);

        } catch (Exception e) {
            log.error("Failed to parse conformance resource file: {}", filename, e);
        }
    }

    private String extractId(JsonNode json, String filename, ConformanceResourceType type) {
        // First try to get ID from JSON content
        String id = getTextValue(json, "id");
        if (id != null) {
            return id;
        }
        // Fall back to extracting from filename
        return type.extractId(filename);
    }

    private String getTextValue(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node != null && node.isTextual()) {
            return node.asText();
        }
        return null;
    }

    private void logSummary() {
        for (FhirVersion version : FhirVersion.values()) {
            for (ConformanceResourceType type : ConformanceResourceType.values()) {
                int count = count(version, type);
                if (count > 0) {
                    log.info("Loaded {} {} resources for version {}", count, type.getResourceTypeName(), version);
                }
            }
        }
    }

    /**
     * Returns the raw JSON content for a conformance resource by ID.
     *
     * @param version the FHIR version
     * @param type the conformance resource type
     * @param id the resource ID
     * @return the raw JSON content, or empty if not found
     */
    public Optional<String> getById(FhirVersion version, ConformanceResourceType type, String id) {
        Map<String, String> idMap = resourcesById.getOrDefault(version, Collections.emptyMap())
                .getOrDefault(type, Collections.emptyMap());
        return Optional.ofNullable(idMap.get(id));
    }

    /**
     * Returns the raw JSON content for a conformance resource by canonical URL.
     *
     * @param version the FHIR version
     * @param type the conformance resource type
     * @param url the canonical URL
     * @return the raw JSON content, or empty if not found
     */
    public Optional<String> getByUrl(FhirVersion version, ConformanceResourceType type, String url) {
        Map<String, String> urlMap = urlToIdIndex.getOrDefault(version, Collections.emptyMap())
                .getOrDefault(type, Collections.emptyMap());
        String id = urlMap.get(url);
        if (id == null) {
            return Optional.empty();
        }
        return getById(version, type, id);
    }

    /**
     * Searches for conformance resources based on search parameters.
     *
     * @param version the FHIR version
     * @param type the conformance resource type
     * @param params search parameters (supports _id, url, name, base)
     * @param count maximum number of results to return
     * @param offset number of results to skip (for pagination)
     * @return list of raw JSON content for matching resources
     */
    public List<String> search(FhirVersion version, ConformanceResourceType type,
                               Map<String, String> params, int count, int offset) {
        Set<String> matchingIds = null;

        // Filter by _id
        if (params.containsKey("_id")) {
            String id = params.get("_id");
            if (getById(version, type, id).isPresent()) {
                matchingIds = Set.of(id);
            } else {
                return Collections.emptyList();
            }
        }

        // Filter by url
        if (params.containsKey("url")) {
            String url = params.get("url");
            Map<String, String> urlMap = urlToIdIndex.getOrDefault(version, Collections.emptyMap())
                    .getOrDefault(type, Collections.emptyMap());
            String id = urlMap.get(url);
            if (id == null) {
                return Collections.emptyList();
            }
            if (matchingIds == null) {
                matchingIds = new HashSet<>();
                matchingIds.add(id);
            } else {
                matchingIds = intersect(matchingIds, Set.of(id));
            }
        }

        // Filter by name
        if (params.containsKey("name")) {
            String name = params.get("name");
            Map<String, List<String>> nameMap = nameToIdsIndex.getOrDefault(version, Collections.emptyMap())
                    .getOrDefault(type, Collections.emptyMap());
            List<String> ids = nameMap.get(name);
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            if (matchingIds == null) {
                matchingIds = new HashSet<>(ids);
            } else {
                matchingIds = intersect(matchingIds, new HashSet<>(ids));
            }
        }

        // Filter by base (for SearchParameter)
        if (params.containsKey("base") && type == ConformanceResourceType.SEARCH_PARAMETER) {
            String base = params.get("base");
            Map<String, List<String>> baseMap = baseToIdsIndex.getOrDefault(version, Collections.emptyMap())
                    .getOrDefault(type, Collections.emptyMap());
            List<String> ids = baseMap.get(base);
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            if (matchingIds == null) {
                matchingIds = new HashSet<>(ids);
            } else {
                matchingIds = intersect(matchingIds, new HashSet<>(ids));
            }
        }

        // If no filters applied, return all
        if (matchingIds == null) {
            Map<String, String> idMap = resourcesById.getOrDefault(version, Collections.emptyMap())
                    .getOrDefault(type, Collections.emptyMap());
            matchingIds = idMap.keySet();
        }

        // Sort by ID for consistent ordering
        List<String> sortedIds = new ArrayList<>(matchingIds);
        Collections.sort(sortedIds);

        // Apply pagination
        List<String> results = new ArrayList<>();
        int start = offset;
        int end = Math.min(start + count, sortedIds.size());
        for (int i = start; i < end; i++) {
            getById(version, type, sortedIds.get(i)).ifPresent(results::add);
        }

        return results;
    }

    /**
     * Returns the total count of matching conformance resources.
     *
     * @param version the FHIR version
     * @param type the conformance resource type
     * @param params search parameters
     * @return total count of matching resources
     */
    public int searchCount(FhirVersion version, ConformanceResourceType type, Map<String, String> params) {
        // Simplified: just count the search results without pagination
        Set<String> matchingIds = null;

        if (params.containsKey("_id")) {
            String id = params.get("_id");
            return getById(version, type, id).isPresent() ? 1 : 0;
        }

        if (params.containsKey("url")) {
            String url = params.get("url");
            Map<String, String> urlMap = urlToIdIndex.getOrDefault(version, Collections.emptyMap())
                    .getOrDefault(type, Collections.emptyMap());
            String id = urlMap.get(url);
            if (id == null) return 0;
            matchingIds = new HashSet<>();
            matchingIds.add(id);
        }

        if (params.containsKey("name")) {
            String name = params.get("name");
            Map<String, List<String>> nameMap = nameToIdsIndex.getOrDefault(version, Collections.emptyMap())
                    .getOrDefault(type, Collections.emptyMap());
            List<String> ids = nameMap.get(name);
            if (ids == null || ids.isEmpty()) return 0;
            if (matchingIds == null) {
                matchingIds = new HashSet<>(ids);
            } else {
                matchingIds = intersect(matchingIds, new HashSet<>(ids));
            }
        }

        if (params.containsKey("base") && type == ConformanceResourceType.SEARCH_PARAMETER) {
            String base = params.get("base");
            Map<String, List<String>> baseMap = baseToIdsIndex.getOrDefault(version, Collections.emptyMap())
                    .getOrDefault(type, Collections.emptyMap());
            List<String> ids = baseMap.get(base);
            if (ids == null || ids.isEmpty()) return 0;
            if (matchingIds == null) {
                matchingIds = new HashSet<>(ids);
            } else {
                matchingIds = intersect(matchingIds, new HashSet<>(ids));
            }
        }

        if (matchingIds == null) {
            return count(version, type);
        }

        return matchingIds.size();
    }

    /**
     * Returns the total count of conformance resources for a version and type.
     *
     * @param version the FHIR version
     * @param type the conformance resource type
     * @return total count
     */
    public int count(FhirVersion version, ConformanceResourceType type) {
        return resourcesById.getOrDefault(version, Collections.emptyMap())
                .getOrDefault(type, Collections.emptyMap()).size();
    }

    /**
     * Returns all resource IDs for a version and type.
     *
     * @param version the FHIR version
     * @param type the conformance resource type
     * @return set of all resource IDs
     */
    public Set<String> getAllIds(FhirVersion version, ConformanceResourceType type) {
        return Collections.unmodifiableSet(
                resourcesById.getOrDefault(version, Collections.emptyMap())
                        .getOrDefault(type, Collections.emptyMap()).keySet());
    }

    private Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }
}
