package org.fhirframework.server.bdd.steps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to load test data JSON files from the classpath.
 * Files are located under {@code src/test/resources/testdata/}.
 */
public class TestDataLoader {

    /**
     * Load a single test data file as a String.
     *
     * @param relativePath path relative to the testdata/ directory
     * @return the file contents as a string
     */
    public static String load(String relativePath) throws IOException {
        try (InputStream is = TestDataLoader.class.getClassLoader()
                .getResourceAsStream("testdata/" + relativePath)) {
            if (is == null) {
                throw new IllegalArgumentException(
                        "Test data not found: testdata/" + relativePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Load multiple test data files, returning them in the same order.
     *
     * @param relativePaths paths relative to the testdata/ directory
     * @return list of file contents as strings
     */
    public static List<String> loadAll(String... relativePaths) throws IOException {
        List<String> results = new ArrayList<>(relativePaths.length);
        for (String path : relativePaths) {
            results.add(load(path));
        }
        return results;
    }
}
