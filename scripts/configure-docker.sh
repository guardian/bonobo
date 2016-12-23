#! /bin/bash

docker_host=${DOCKER_HOST?Docker host is not set}

container_host=$(echo ${docker_host} | sed -e 's/tcp:\/\/\(.*\):.*/\1/')

echo Starting Services ...
docker-compose up -d

echo Adding API ...
wget -O - http://${container_host}:8001/apis --post-data 'name=internal&request_host=foo.com&upstream_url=http://example.com' --retry-connrefused --no-verbose

echo Activating key-auth plugin ...
wget -O - http://${container_host}:8001/apis/internal/plugins/ --post-data 'name=key-auth' --retry-connrefused --no-verbose
