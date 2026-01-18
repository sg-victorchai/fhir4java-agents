# FHIR4Java Agents

The project is to demonstrate the amazing capabilities of HL7 FHIR and AI. It will first use AI to generate the implementation plan which will implement a configuration driven FHIR services to support both HL7 FHIR defined resources and project defined custom resources.

It will also contain a set of SKILLS to allow project team to define new API for existing resources, and define new resources without any manual coding required.

## Plugin Architecture

The application supports a **hybrid plugin architecture** with two approaches:

### 1. Native Code Plugins (Spring Bean)
- Embedded plugins running in the same JVM
- Best for performance-critical operations
- Tight integration with Spring ecosystem

### 2. MCP (Model Context Protocol) Plugins
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

## Documentation

- [Implementation Plan](FHIR4JAVA-IMPLEMENTATION-PLAN.md) - Comprehensive design for the enterprise FHIR server
