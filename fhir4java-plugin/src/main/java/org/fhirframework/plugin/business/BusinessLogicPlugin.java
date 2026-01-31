package org.fhirframework.plugin.business;

import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.OperationDescriptor;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;

import java.util.List;

/**
 * Plugin interface for custom business logic.
 * <p>
 * Business logic plugins can intercept CRUD operations and extended operations
 * to implement custom validation, transformation, side effects, and integrations.
 * </p>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Custom validation beyond FHIR profiles</li>
 *   <li>Resource transformation or enrichment</li>
 *   <li>External system integration (notifications, sync)</li>
 *   <li>Business rule enforcement</li>
 *   <li>Workflow triggers</li>
 * </ul>
 *
 * <h2>Execution Phases</h2>
 * <ul>
 *   <li>BEFORE: Validate input, transform resource, abort if necessary</li>
 *   <li>AFTER: Enrich result, trigger notifications, sync to external systems</li>
 * </ul>
 */
public interface BusinessLogicPlugin extends FhirPlugin {

    @Override
    default ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNC;
    }

    @Override
    default int getPriority() {
        // Business logic runs after auth/cache but before persistence
        return 50;
    }

    /**
     * Get the operation descriptors this plugin handles.
     * <p>
     * Override to specify which operations this plugin intercepts.
     * By default, matches all operations.
     * </p>
     */
    @Override
    default List<OperationDescriptor> getSupportedOperations() {
        return List.of(OperationDescriptor.matchAll());
    }

    /**
     * Execute business logic before the operation.
     * <p>
     * Use this to:
     * - Validate the input resource
     * - Transform or enrich the resource
     * - Abort the operation if business rules fail
     * </p>
     *
     * @param context The business context
     * @return Result indicating how to proceed
     */
    BusinessResult beforeOperation(BusinessContext context);

    /**
     * Execute business logic after the operation.
     * <p>
     * Use this to:
     * - Enrich the result resource
     * - Trigger notifications or webhooks
     * - Sync to external systems
     * - Log business events
     * </p>
     *
     * @param context The business context
     * @param result  The operation result
     * @return Result indicating how to proceed
     */
    BusinessResult afterOperation(BusinessContext context, OperationResult result);

    @Override
    default PluginResult executeBefore(PluginContext pluginContext) {
        BusinessContext businessContext = BusinessContext.from(pluginContext);
        BusinessResult result = beforeOperation(businessContext);
        return result.toPluginResult();
    }

    @Override
    default PluginResult executeAfter(PluginContext pluginContext) {
        BusinessContext businessContext = BusinessContext.from(pluginContext);

        // Create operation result from context
        OperationResult opResult = pluginContext.getOutputResource()
                .map(r -> OperationResult.success(r, 200))
                .orElse(OperationResult.successNoContent());

        BusinessResult result = afterOperation(businessContext, opResult);
        return result.toPluginResult();
    }

    /**
     * Result of business logic execution.
     */
    class BusinessResult {

        public enum Action {
            /** Continue processing normally */
            CONTINUE,
            /** Continue with modified resource */
            MODIFIED,
            /** Abort the operation */
            ABORT
        }

        private final Action action;
        private final org.hl7.fhir.instance.model.api.IBaseResource modifiedResource;
        private final String errorMessage;
        private final int httpStatus;

        private BusinessResult(Action action, org.hl7.fhir.instance.model.api.IBaseResource modifiedResource,
                               String errorMessage, int httpStatus) {
            this.action = action;
            this.modifiedResource = modifiedResource;
            this.errorMessage = errorMessage;
            this.httpStatus = httpStatus;
        }

        /**
         * Continue processing normally.
         */
        public static BusinessResult proceed() {
            return new BusinessResult(Action.CONTINUE, null, null, 0);
        }

        /**
         * Continue with a modified resource.
         */
        public static BusinessResult proceedWithResource(org.hl7.fhir.instance.model.api.IBaseResource resource) {
            return new BusinessResult(Action.MODIFIED, resource, null, 0);
        }

        /**
         * Abort with a validation error.
         */
        public static BusinessResult abort(String errorMessage) {
            return new BusinessResult(Action.ABORT, null, errorMessage, 400);
        }

        /**
         * Abort with a custom HTTP status.
         */
        public static BusinessResult abort(String errorMessage, int httpStatus) {
            return new BusinessResult(Action.ABORT, null, errorMessage, httpStatus);
        }

        /**
         * Abort as forbidden.
         */
        public static BusinessResult forbidden(String errorMessage) {
            return new BusinessResult(Action.ABORT, null, errorMessage, 403);
        }

        /**
         * Abort due to conflict.
         */
        public static BusinessResult conflict(String errorMessage) {
            return new BusinessResult(Action.ABORT, null, errorMessage, 409);
        }

        /**
         * Abort as unprocessable.
         */
        public static BusinessResult unprocessable(String errorMessage) {
            return new BusinessResult(Action.ABORT, null, errorMessage, 422);
        }

        public Action getAction() {
            return action;
        }

        public boolean shouldContinue() {
            return action == Action.CONTINUE || action == Action.MODIFIED;
        }

        public boolean isAborted() {
            return action == Action.ABORT;
        }

        public org.hl7.fhir.instance.model.api.IBaseResource getModifiedResource() {
            return modifiedResource;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        /**
         * Convert to PluginResult.
         */
        public PluginResult toPluginResult() {
            return switch (action) {
                case CONTINUE -> PluginResult.continueProcessing();
                case MODIFIED -> PluginResult.continueWithResource(modifiedResource);
                case ABORT -> PluginResult.abort(httpStatus, errorMessage);
            };
        }
    }
}
