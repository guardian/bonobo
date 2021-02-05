#! /bin/bash -e

echo Starting Kong Docker images ...
docker-compose up -d

echo Waiting for Kong
wget -O - http://localhost:8001/services --retry-connrefused --no-verbose
echo Kong looks responsive

echo Adding a service ...
curl -X POST \
  --url http://localhost:8001/services/ \
  --data 'name=internal' \
  --data 'url=http://capi.dev.guardianapis.com'

echo Enabling keyauth plugin on service
curl -X POST \
  --url "http://localhost:8001/services/internal/plugins" \
  --data 'name=key-auth'

echo Adding Route to service
curl -X POST \
  --url http://localhost:8001/services/internal/routes \
  --data 'hosts[]=content.dev.guardianapis.com'
