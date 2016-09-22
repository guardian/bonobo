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

if docker ps | grep postgres -q; then
    echo Postgres container already exists
else
    echo Creating Postgres container ...
    docker create -p 5432:5432 -e "POSTGRES_USER=kong" -e "POSTGRES_DB=kong" --name postgres mashape/postgres:9.4
fi

if docker ps | grep kong-0.9.2 -q; then
    echo Kong 0.9.2 container already exists
else
    echo Creating kong 0.9.2 container ...
    docker create -p 8000:8000 -p 8001:8001 -p 8443:8443 -p 7946:7946 -p 7946:7946/udp --name kong-0.9.2 --link postgres:postgres -e "KONG_DATABASE=postgres" mashape/kong:0.9.2
fi

start_service postgres 5432
start_service kong-0.9.2 8001

echo Adding API ...
curl -sS -X POST http://${container_host}:8001/apis -d name=internal -d request_host=foo.com -d upstream_url=http://example.com

echo Activating key-auth plugin ...
curl -sS -X POST http://${container_host}:8001/apis/internal/plugins/ -d name=key-auth


