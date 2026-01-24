#!/bin/bash
# Generate review input JSON from PR metadata
# Usage: ./generate-review-input.sh <PR_NUMBER> <PR_TITLE> <AUTHOR> <BASE_SHA> <HEAD_SHA> <OUTPUT_FILE>

set -e

PR_NUMBER=$1
PR_TITLE=$2
AUTHOR=$3
BASE_SHA=$4
HEAD_SHA=$5
OUTPUT_FILE=$6

if [ -z "$OUTPUT_FILE" ]; then
  echo "Usage: $0 <PR_NUMBER> <PR_TITLE> <AUTHOR> <BASE_SHA> <HEAD_SHA> <OUTPUT_FILE>"
  exit 1
fi

# Get the diff between base and head and write to temp file
DIFF_FILE=$(mktemp)
git diff "$BASE_SHA".."$HEAD_SHA" > "$DIFF_FILE"

# Generate valid JSON using jq to properly escape all strings
# Use --rawfile to read diff from file instead of command-line arg
jq -n \
  --arg prNumber "$PR_NUMBER" \
  --arg prTitle "$PR_TITLE" \
  --arg author "$AUTHOR" \
  --rawfile diff "$DIFF_FILE" \
  '{
    prNumber: ($prNumber | tonumber),
    prTitle: $prTitle,
    author: $author,
    prDescription: "",
    diff: $diff,
    testSummary: null
  }' > "$OUTPUT_FILE"

# Clean up temp file
rm "$DIFF_FILE"

echo "Generated review input at $OUTPUT_FILE"
