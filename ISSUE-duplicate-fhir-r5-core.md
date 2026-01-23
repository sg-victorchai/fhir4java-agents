# GitHub Issue: Remove duplicate fhir-r5-core directory

**Title:** Remove duplicate fhir-r5-core directory

**Labels:** cleanup, maintenance

---

## Description

The `fhir-r5-core/` directory at the project root is a duplicate of `fhir4java-server/src/main/resources/fhir-config/` and should be removed to reduce repository size and avoid confusion.

## Evidence

1. **Identical Contents**: The directories are byte-for-byte identical (verified with `diff -r`)

2. **Size**: Both directories are 51MB, containing 1,612 JSON files total:
   - 1,244 SearchParameter definitions
   - 307 StructureDefinition (profiles)
   - 61 OperationDefinition files

3. **Usage**: The application uses `fhir-config/` via `application.yml`:
   ```yaml
   fhir:
     resource:
       search-parameters-path: classpath:fhir-config/searchparameters/
       operations-path: classpath:fhir-config/operations/
       profiles-path: classpath:fhir-config/profiles/
   ```

4. **Already Excluded**: `.dockerignore` already excludes `fhir-r5-core/` with comment "not needed for build"

## Impact

- **Repository Size**: Removing this saves 51MB of repository space
- **Clarity**: Single source of truth for FHIR R5 definitions
- **Maintenance**: No risk of the two copies diverging over time

## Recommendation

Remove the `fhir-r5-core/` directory and keep only the copy in `fhir4java-server/src/main/resources/fhir-config/` which is properly packaged with the application.

## Steps to Resolve

```bash
git rm -r fhir-r5-core/
git commit -m "Remove duplicate fhir-r5-core directory

The fhir-r5-core directory is an exact duplicate of
fhir4java-server/src/main/resources/fhir-config/ (both 51MB).
Keeping only the copy in fhir-config which is used by the application."
git push
```

## Additional Checks

Before removing, verify no code references `fhir-r5-core` directly:
```bash
grep -r "fhir-r5-core" --exclude-dir=.git --exclude-dir=fhir-r5-core .
```
