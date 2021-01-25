# bonobo

Key management for Kong

## Prerequisites for development

Bonobo needs an instance of Kong  to connect to, so you will need Kong and it's PostgreSQL running somewhere. 
We recommend running them in Docker containers. See instructions below on how to set this up.

## To run locally using Docker

Ensure Docker Desktop is running.



2. Run the `configure-docker.sh` script:

  ```
  cd scripts
  ./configure-docker.sh
  ```
  This will create two containers for Kong and it's PostgreSQL/
  It will run Kong's database migration scripts to setup the schema.
 Then it will add an API in Kong with the key-auth plugin enabled.

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

## Developer Tier
<!---
This anchor is linked to in the Bonobo application. 
If you change this anchor, you should change the corresponding Bonobo source code. 
--->
Keys issued under the `Developer` tier can be periodically deleted by [Gibbons](https://github.com/guardian/gibbons).
Therefore, if an API key is intended to be used by a service, it should __not__ be issued under the `Developer` tier.
