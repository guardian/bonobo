#! /bin/bash

docker_host=${DOCKER_HOST?Docker host is not set}

container_host=$(echo ${docker_host} | sed -e 's/tcp:\/\/\(.*\):.*/\1/')

function start_service {
    name=${1?Name parameter missing}
    port_number=${2?Port parameter missing}

    docker start ${name}

    nc -z ${container_host} ${port_number}
    while [ $? -ne 0 ]; do
        echo Waiting for ${name} to start listening ...
        sleep 1
        nc -z ${container_host} ${port_number}
    done
}

# Create Docker container for existing Kong

if docker ps | grep cassandra -q; then
    echo Cassandra container already exists
else
    echo Creating cassandra container ...
    docker create -p 9042:9042 --name cassandra mashape/cassandra
fi

if docker ps | grep kong-0.7.0 -q; then
    echo Kong 0.7.0 container already exists
else
    echo Creating kong 0.7.0 container ...
    docker create -p 8000:8000 -p 8001:8001 --name kong-0.7.0 --link cassandra:cassandra mashape/kong:0.7.0
fi

start_service cassandra 9042
start_service kong-0.7.0 8001

echo Adding API ...
curl -sS -X POST http://${container_host}:8001/apis -d name=internal -d request_host=foo.com -d upstream_url=http://example.com

echo Activating key-auth plugin ...
curl -sS -X POST http://${container_host}:8001/apis/internal/plugins/ -d name=key-auth

# Create Docker container for new Kong being migrated to.

if docker ps | grep postgres -q; then
    echo Postgres container already exists
else
    echo Creating Postgres container ...
    docker create -p 5434:5432 -e "POSTGRES_USER=kong" -e "POSTGRES_DB=kong" --name postgres postgres:9.4
fi

if docker ps | grep kong-0.9.0 -q; then
    echo Kong 0.9.0 container already exists
else
    echo Creating kong 0.9.0 container ...
    docker create -p 8003:8000 -p 8002:8001 -p 8443:8443 -p 7946:7946 -p 7946:7946/udp --name kong-0.9.0 --link postgres:postgres -e "KONG_DATABASE=postgres" -e "KONG_PG_HOST=postgres" mashape/kong:0.9.0
fi

start_service postgres 5434
start_service kong-0.9.0 8002

echo Adding API ...
curl -sS -X POST http://${container_host}:8002/apis -d name=internal -d request_host=foo.com -d upstream_url=http://example.com

echo Activating key-auth plugin ...
curl -sS -X POST http://${container_host}:8002/apis/internal/plugins/ -d name=key-auth

