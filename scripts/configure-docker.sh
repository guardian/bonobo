#! /bin/bash -e

echo Starting Kong Docker images ...
docker-compose up -d

echo Waiting for Kong
wget -O - http://localhost:8001/services --retry-connrefused --no-verbose
echo Kong looks responsive

echo Adding a Service ...
curl -i -X POST \
  --url http://localhost:8001/services/ \
  --data 'name=content' \
  --data 'url=http://capi.dev.guardianapis.com'

# Defining a Route for this Service requires the service.id
echo Querying for Service id
SERVICE_ID=`curl -q http://localhost:8001/services | jq -r '.data[0].id'`

echo Adding Route to Service
curl -i -X POST \
  --url http://localhost:8001/routes/ \
  --data 'hosts[]=content.dev.guardianapis.com' \
  --data "service.id=$SERVICE_ID"


echo Activating key-auth plugin for route...
# This has questionable idempotency
ROUTE_ID=`curl -q http://localhost:8001/routes | jq -r '.data[0].id'`

curl -i -X POST \
  --url "http://localhost:8001/routes/$ROUTE_ID/plugins/" \
  --data 'name=key-auth'
