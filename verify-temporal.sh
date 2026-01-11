#!/bin/bash

echo "=== Temporal Verification Script ==="
echo ""

# Check if Temporal CLI is installed
echo "1. Checking Temporal CLI installation..."
if command -v temporal &> /dev/null; then
    echo "   ✓ Temporal CLI found: $(temporal --version 2>&1 | head -1)"
else
    echo "   ✗ Temporal CLI not found. Install with: brew install temporal"
    exit 1
fi

# Check if Temporal server is running
echo ""
echo "2. Checking Temporal server connection..."
if temporal operator namespace list &> /dev/null; then
    echo "   ✓ Temporal server is running"
    echo "   Namespaces:"
    temporal operator namespace list 2>&1 | sed 's/^/     /'
else
    echo "   ✗ Cannot connect to Temporal server"
    echo "   Start server with: temporal server start-dev"
    exit 1
fi

# Check Web UI
echo ""
echo "3. Checking Temporal Web UI..."
if curl -s http://localhost:8233 &> /dev/null; then
    echo "   ✓ Web UI is accessible at http://localhost:8233"
else
    echo "   ⚠ Web UI not accessible (this may be normal if using headless mode)"
fi

# Check Java
echo ""
echo "4. Checking Java installation..."
if command -v java &> /dev/null; then
    echo "   ✓ Java found: $(java -version 2>&1 | head -1)"
else
    echo "   ✗ Java not found. Install Java 11 or higher."
    exit 1
fi

# Check Maven
echo ""
echo "5. Checking Maven installation..."
if command -v mvn &> /dev/null; then
    echo "   ✓ Maven found: $(mvn -version 2>&1 | head -1)"
else
    echo "   ✗ Maven not found. Install with: brew install maven"
    exit 1
fi

# Check OpenAI API key
echo ""
echo "6. Checking environment variables..."
if [ -z "$OPENAI_API_KEY" ]; then
    echo "   ⚠ OPENAI_API_KEY not set (required for LLM agents)"
    echo "     Set with: export OPENAI_API_KEY=\"your-key-here\""
    echo "     Or use DUMMY_MODE for testing: export DUMMY_MODE=true"
else
    echo "   ✓ OPENAI_API_KEY is set"
fi

if [ -z "$OPENAI_MODEL" ]; then
    echo "   ℹ OPENAI_MODEL not set (will default to gpt-4o-mini)"
else
    echo "   ✓ OPENAI_MODEL set to: $OPENAI_MODEL"
fi

echo ""
echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "  1. Build the project: cd java && mvn clean install"
echo "  2. Run a test review: cd java/temporal-review && mvn exec:java -Dexec.args=\"../../sample-input.json ../../sample-output.json\""
echo "  3. View results: cat sample-output.json"
echo "  4. View in Temporal UI: open http://localhost:8233"
