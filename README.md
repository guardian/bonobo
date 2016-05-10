# bonobo

[![Circle CI](https://circleci.com/gh/guardian/bonobo/tree/master.svg?style=svg)](https://circleci.com/gh/guardian/bonobo/tree/master)

Key management for Kong

## Prerequisites for development

Bonobo needs an instance of Kong to connect to, so you will need Kong and Cassandra running somewhere. We recommend running them in Docker containers. See instructions below on how to set this up.

## To run locally using Docker

Install Docker Toolkit, following the instructions on the [Docker website](http://docs.docker.com/).

1. Make sure your Docker VM is running:

  ```
  $ docker-machine ls
  NAME   ACTIVE   DRIVER       STATE     URL                         SWARM
  dev    *        virtualbox   Running   tcp://192.168.99.100:2376
  ```

2. Make sure `$DOCKER_HOST` is set:

  ```
  $ echo $DOCKER_HOST
  tcp://192.168.99.100:2376
  ```
  
  If it is not, type `docker-machine env <machine-name>` to find out how to set it:
  
  ```
  $ docker-machine env dev
  export DOCKER_TLS_VERIFY="1"
  export DOCKER_HOST="tcp://192.168.99.100:2376"
  export DOCKER_CERT_PATH="/Users/cbirchall/.docker/machine/machines/dev"
  export DOCKER_MACHINE_NAME="dev"
  # Run this command to configure your shell:
  # eval "$(docker-machine env dev)"
  ```

3. Run the `configure-docker.sh` script:

  ```
  ./scripts/configure-docker.sh
  ```
  This will create two containers - one for Cassandra and one for Kong - and start them. Then it will add an API in Kong with the key-auth plugin enabled.

4. Edit `conf/application.conf` to point `kong.apiAddress` at your Kong cluster (in this example: `http://192.168.99.100:8001`). In the same file you should configure `aws.dynamo.usersTableName`, `aws.dynamo.keysTableName` and `aws.dynamo.labelsTableName` to point to the DynamoDB tables you are going to use.

5. Then start the Play app:

  ```
  $ sbt run
  ```
  
  It should now be running at http://localhost:9000/
  
  You will need a Guardian Google account in order to login.

## To run the integration tests

The integration tests rely on Docker to run Kong.

Make sure you have completed step 1 and 2 from the previous section and then run:

```
$ sbt it:test
```
