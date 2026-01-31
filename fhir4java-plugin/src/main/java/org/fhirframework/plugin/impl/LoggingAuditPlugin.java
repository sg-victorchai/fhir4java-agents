package org.fhirframework.plugin.impl;

import org.fhirframework.plugin.audit.AuditPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An audit plugin that logs audit events using SLF4J.
 * <p>
 * Suitable for development and testing. For production use,
 * consider a database-backed or dedicated audit system.
 * </p>
 */
public class LoggingAuditPlugin implements AuditPlugin {

    private static final Logger auditLog = LoggerFactory.getLogger("audit");

    private boolean enabled = true;

    @Override
    public String getName() {
        return "logging-audit";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void recordAuditEvent(AuditEvent event) {
        String logMessage = formatAuditEvent(event);

        switch (event.outcome()) {
            case SUCCESS -> auditLog.info(logMessage);
            case FAILURE -> auditLog.warn(logMessage);
            case DENIED -> auditLog.warn(logMessage);
        }
    }

    private String formatAuditEvent(AuditEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("AUDIT | ");
        sb.append("id=").append(event.eventId());
        sb.append(" | op=").append(event.operationType());
        sb.append(" | resource=").append(event.resourceType());
        if (event.resourceId() != null) {
            sb.append("/").append(event.resourceId());
        }
        sb.append(" | version=").append(event.fhirVersion());
        sb.append(" | outcome=").append(event.outcome());
        if (event.userId() != null) {
            sb.append(" | user=").append(event.userId());
        }
        if (event.clientId() != null) {
            sb.append(" | client=").append(event.clientId());
        }
        if (event.tenantId() != null) {
            sb.append(" | tenant=").append(event.tenantId());
        }
        if (event.outcomeDescription() != null) {
            sb.append(" | desc=").append(event.outcomeDescription());
        }
        return sb.toString();
    }
}
