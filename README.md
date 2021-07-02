# bonobo

Self serve key management for Kong

Bonobo provides the interface for the general public to request Content API keys
and Guardian staff to manage them.

ie.
```
https://open-platform.theguardian.com/access/
https://bonobo.capi.gutools.co.uk/register/developer
```

Bonobo manages the keys in Kong via the Kong API.


## Prerequisites for development

Bonobo needs an instance of Kong to connect to, so you will need Kong and it's PostgreSQL running somewhere. 
We recommend running them in Docker containers. See instructions below on how to set this up.

## To run locally using Docker

- Ensure Docker Desktop is running.
- Ensure you have Content API developer credentials [from Janus]
- Ensure you are compiling and running the application using Java 8. (It is best to run the application in an sbt shell that uses JDK 1.8)

2. Run the `configure-docker.sh` script:

  ```
  cd scripts
  ./configure-docker.sh
  ```
  This will create two containers for Kong and it's PostgreSQL/
  It will run Kong's database migration scripts to setup the schema.
  Then it will add an API in Kong with the key-auth plugin enabled.

4. Edit `conf/application.conf` to point `kong.apiAddress` at your Kong cluster (in this example: `http://192.168.99.100:8001`). In the same file you should configure `aws.dynamo.usersTableName`, `aws.dynamo.keysTableName` and `aws.dynamo.labelsTableName` to point to the DynamoDB tables you are going to use.
You do not need to change the play.http.secret.key to run the app locally.
   
Note: In CODE and PROD the app does NOT use the configurations in conf/application.conf. Instead, it pulls its configurations from a bonobo.conf file which you can find on the live instance(s).

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


