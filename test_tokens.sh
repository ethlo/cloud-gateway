#!/bin/bash

# Check if all required arguments are provided
if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <token-url> <client_id> <username> <password>"
  exit 1
fi

# Input arguments
URL="$1"
CLIENT="$2"
USERNAME="$3"
PASSWORD="$4"

# Fetch offline refresh token
echo "Fetching offline refresh token..."
REFRESH_TOKEN_RESPONSE=$(curl --fail-with-body 2>&1 -Ss -X POST "$URL" -d "client_id=${CLIENT}" -d "username=${USERNAME}" -d "password=${PASSWORD}" -d "grant_type=password" -d "scope=openid offline_access")
curl_exit_code=$?
if [ $curl_exit_code -ne 0 ]; then
  echo "Curl error ($curl_exit_code):"
  echo "$REFRESH_TOKEN_RESPONSE"
  exit $curl_exit_code
fi

OFFLINE_REFRESH_TOKEN=$(echo $REFRESH_TOKEN_RESPONSE | jq -r '.refresh_token')

if [ -z "$OFFLINE_REFRESH_TOKEN" ] || [ "$OFFLINE_REFRESH_TOKEN" == "null" ]; then
  echo "Failed to fetch offline refresh token."
  exit 1
fi

echo "Offline refresh token: $OFFLINE_REFRESH_TOKEN"

# Fetch access token using the refresh token
echo "Fetching access token..."
ACCESS_TOKEN_RESPONSE=$(curl --fail-with-body 2>&1 -Ss -X POST "$URL" -d "client_id=${CLIENT}" -d "refresh_token=${OFFLINE_REFRESH_TOKEN}" -d "grant_type=refresh_token" -d "scope=openid offline_access")
curl_exit_code=$?
if [ $curl_exit_code -ne 0 ]; then
  echo "Curl error ($curl_exit_code):"
  echo "$ACCESS_TOKEN_RESPONSE"
  exit $curl_exit_code
fi

ACCESS_TOKEN=$(echo $ACCESS_TOKEN_RESPONSE | jq -r '.access_token')
echo "Access token: $ACCESS_TOKEN"
