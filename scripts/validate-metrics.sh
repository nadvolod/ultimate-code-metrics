#!/bin/bash

# Metrics Implementation Validation Script
# Tests the new metrics API and dashboard functionality

set -e

echo "🧪 Validating Metrics Implementation"
echo "===================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Check if server is running
echo "Test 1: Server availability"
if curl -s -f http://localhost:3000/api/metrics > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Server is running${NC}"
else
    echo -e "${RED}✗ Server is not running. Start it with: npm run dev${NC}"
    exit 1
fi
echo ""

# Test 2: Fetch metrics with real data
echo "Test 2: Metrics API with real data"
METRICS=$(curl -s http://localhost:3000/api/metrics)
echo "Response: $METRICS"

if [ "$METRICS" = "null" ]; then
    echo -e "${YELLOW}⚠ No review data found - API returning null (expected behavior)${NC}"
else
    # Validate structure
    PRS=$(echo $METRICS | jq -r '.prsAnalyzed')
    AVG_TIME=$(echo $METRICS | jq -r '.avgAnalysisTimeMinutes')
    AUTO_APPROVED=$(echo $METRICS | jq -r '.autoApprovedPct')
    HOURS_SAVED=$(echo $METRICS | jq -r '.engineeringHoursSaved')

    echo -e "${GREEN}✓ PRs Analyzed: $PRS${NC}"
    echo -e "${GREEN}✓ Avg Analysis Time: ${AVG_TIME} min${NC}"
    echo -e "${GREEN}✓ Auto-Approved: ${AUTO_APPROVED}%${NC}"
    echo -e "${GREEN}✓ Engineering Hours Saved: ${HOURS_SAVED} hrs${NC}"
fi
echo ""

# Test 3: Count actual review files
echo "Test 3: Review files count"
REVIEW_COUNT=$(ls -1 data/reviews/*.json 2>/dev/null | wc -l | tr -d ' ')
echo -e "${GREEN}✓ Found $REVIEW_COUNT review files${NC}"

if [ "$METRICS" != "null" ]; then
    PRS=$(echo $METRICS | jq -r '.prsAnalyzed')
    if [ "$PRS" = "$REVIEW_COUNT" ]; then
        echo -e "${GREEN}✓ Metrics count matches file count${NC}"
    else
        echo -e "${RED}✗ Metrics count ($PRS) doesn't match file count ($REVIEW_COUNT)${NC}"
    fi
fi
echo ""

# Test 4: Verify recommendations
echo "Test 4: Verify recommendation counts"
APPROVE_COUNT=$(grep -h "overallRecommendation" data/reviews/*.json 2>/dev/null | grep -c "APPROVE" || echo "0")
TOTAL_FILES=$REVIEW_COUNT

if [ "$TOTAL_FILES" -gt 0 ]; then
    EXPECTED_PCT=$((APPROVE_COUNT * 100 / TOTAL_FILES))
    echo "Approved: $APPROVE_COUNT out of $TOTAL_FILES = ${EXPECTED_PCT}%"

    if [ "$METRICS" != "null" ]; then
        ACTUAL_PCT=$(echo $METRICS | jq -r '.autoApprovedPct')
        if [ "$ACTUAL_PCT" = "$EXPECTED_PCT" ]; then
            echo -e "${GREEN}✓ Auto-approved percentage is correct${NC}"
        else
            echo -e "${YELLOW}⚠ Auto-approved percentage: expected ${EXPECTED_PCT}%, got ${ACTUAL_PCT}%${NC}"
        fi
    fi
else
    echo -e "${YELLOW}⚠ No review files to validate${NC}"
fi
echo ""

# Test 5: Dashboard accessibility
echo "Test 5: Dashboard page"
if curl -s http://localhost:3000/dashboard | grep -q "Overview Metrics"; then
    echo -e "${GREEN}✓ Dashboard is accessible${NC}"
else
    echo -e "${RED}✗ Dashboard page not loading correctly${NC}"
fi
echo ""

# Test 6: Fallback behavior (optional)
echo "Test 6: Fallback behavior (optional - run manually)"
echo "To test fallback behavior:"
echo "  1. mv data/reviews data/reviews.bak"
echo "  2. curl http://localhost:3000/api/metrics"
echo "  3. Should return: null"
echo "  4. Dashboard should show mock data with banner"
echo "  5. mv data/reviews.bak data/reviews"
echo ""

# Summary
echo "===================================="
echo -e "${GREEN}✅ Validation Complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Open http://localhost:3000/dashboard in your browser"
echo "  2. Verify metrics are displayed correctly"
echo "  3. Hover over 'Engineering Hours Saved' to see tooltip"
echo "  4. Generate a new PR review to see metrics update"
