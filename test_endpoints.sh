#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "1. TESTING HEALTH ENDPOINT (/status)"
echo "=========================================="
curl -s -X GET "$BASE_URL/status" | jq . || curl -s -X GET "$BASE_URL/status"
echo -e "\n\n"

echo "=========================================="
echo "2. TESTING MCP INITIALIZE (/mcp)"
echo "=========================================="
curl -s -X POST "$BASE_URL/mcp" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {}
  }' | jq . || true
echo -e "\n\n"

echo "=========================================="
echo "3. TESTING MCP TOOLS/LIST (/mcp)"
echo "=========================================="
curl -s -X POST "$BASE_URL/mcp" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list"
  }' | jq . || true
echo -e "\n\n"

echo "=========================================="
echo "4. TESTING MCP TOOLS/CALL [omnibrain_status]"
echo "=========================================="
curl -s -X POST "$BASE_URL/mcp" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "omnibrain_status"
    }
  }' | jq . || true
echo -e "\n\n"

echo "=========================================="
echo "5. TESTING MCP TOOLS/CALL [omnibrain_query_logs]"
echo "=========================================="
curl -s -X POST "$BASE_URL/mcp" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "omnibrain_query_logs",
      "arguments": {
        "limit": 5
      }
    }
  }' | jq . || true
echo -e "\n"
