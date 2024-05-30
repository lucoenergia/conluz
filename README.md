# Conluz

![Build & Test](https://github.com/lucoenergia/conluz/actions/workflows/java_ci_with_gradle.yml/badge.svg)

Welcome to Conluz, the energy community management application!

Conluz is a robust application designed for the efficient management of an energy community. This platform enables the administration of community members and their corresponding supply points. Functioning as an API-driven solution, Conluz facilitates the retrieval of consumption data for each supply point, production data from the energy plant, and real-time electricity prices.

The application focuses on seamless interaction with the underlying infrastructure through a RESTful API, offering a streamlined and programmatic approach to community energy management.

## Features

- **Member Management:** Easily manage community members and their associated information.
- **Supply Point Administration:** Efficiently handle points of supplies within the energy network.
- **Consumption Data Visualization:** Retrieve consumption data for each supply point to understand energy usage patterns.
- **Production Metrics Monitoring:** Track real-time data on energy production metrics from the plant.
- **Electricity Price Information:** Stay informed about current electricity prices for better decision-making.

## Getting Started

### Prerequisites

- Java 17+

### Configuration

#### JWT secret key

   The JWT secret key must be provided to be able to run the application and make calls to its API.

   You have to provide the secret key as an environment variable called `CONLUZ_JWT_SECRET_KEY`

   ```
   export CONLUZ_JWT_SECRET_KEY="b5f86373ba5d7593f4c6eab57862bf4be76369c1adbe263ae2d50ddae40b8ca2"
   ```

   The secret key must be compatible with [HMAC-SHA algorithms](https://datatracker.ietf.org/doc/html/rfc7518#section-3.2) and must have a length of 256 bits (32 bytes) or more.

   > **Note:**
   >
   > You can use the class `org.lucoenergia.conluz.infrastructure.shared.security.JwtSecretKeyGenerator` to generate a random JWT secret key.

#### Data storage
1. **PostgreSQL database**

Conluz uses a relational database mainly to store information about users and supplies.

So, to be able to use it, you need to have up and running a PostgreSQL database. 

You don't need to do an extra effort of creating all the table manually, because Conluz will do that for you during its bootstrap. Every time the app starts, will apply all the necessary changes to the database transparently using [Liquibase](https://www.liquibase.org/) changesets.

> **Note:**
>
> To have a PostgreSQL database up and running in a few seconds, you can use the docker compose file `deploy/docker-compose.yaml`. This file will do automatically all the configurations required transparently.
> You just need to navigate to the `deploy` folder and execute the command `docker compose up -d`

2. **InfluxDB database**

Conluz uses InfluxDB as a time series database to store consumption, production and energy prices data gathered from different sources like datadis.es, Shelly meters or Huawei inverters.

#### Monitoring

You can monitor the resources of the server where Conluz is running by using Prometheus + Node Exporter.

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/lucoenergia/conluz.git
   cd conluz
   ```

2. Build the application

    ```bash
   ./gradlew build
    ```

3. Configure Postgres database

#### New Postgres installation

To have a PostgreSQL database up and running in a few seconds, you can use the docker compose file `deploy/docker-compose.yaml`. This file will do automatically all the configurations required transparently.
You just need to execute the command `docker compose up -d postgres`

#### Already existing Postgres installation

If you already have a Postgres database up and running, you need to execute these commands:

```sql
CREATE DATABASE conluz_db; 
CREATE DATABASE conluz_db_test;
CREATE USER luz WITH PASSWORD 'blank';
GRANT ALL PRIVILEGES ON DATABASE conluz_db TO luz;
GRANT ALL PRIVILEGES ON DATABASE conluz_db_test TO luz;
```
These commands will:
- Create two databases: `conluz_db` for running Conluz locally and `conluz_db_test` for integration tests. 
- Create a user called `luz` which will be used by Conluz.
- Grant privileges to the user `luz` over the databases `conluz_db` and `conluz_db_test`.

Then, you have to configure the database connection settings by defining the following environment variables:

```
SPRING_DATASOURCE_URL="jdbc:postgresql://postgres_ip:5432/conluz_db"
```

If you are running Postgres locally on `localhost:5432` you don't need to provide this environment variable because that is the default configuration.

4. Configure InfluxDB database

#### New InfluxDB installation

To have an InfluxDB database up and running in a few seconds, you can use the docker compose file `deploy/docker-compose.yaml`. This file will do automatically all the configurations required transparently.
You just need to execute the command `docker compose up -d influxdb`

#### Already existing InfluxDB installation
If you already have an InfluxDB database up and running, you need to execute these commands:

```sql
CREATE DATABASE conluz_db
CREATE USER luz WITH PASSWORD 'blank'
GRANT ALL ON conluz_db TO luz
CREATE RETENTION POLICY one_month ON conluz_db DURATION 30d REPLICATION 1
CREATE RETENTION POLICY one_year ON conluz_db DURATION 365d REPLICATION 1
CREATE RETENTION POLICY forever ON conluz_db DURATION INF REPLICATION 1 DEFAULT
```
These commands will:
- Create a database called "conluz_db".
- Create a user called "luz".
- Grant privileges to the user "luz" over the database "conluz_db".
- Create a set of policies required by the app.

By default, the InfluxDB settings are:
```
url=http://localhost:8086
username=luz
password=blank
database=conluz_db
```

If you have your InfluxDB running with a different server, port, user credentials or database name, you would need to update the file `src/main/resources/application.properties` with your values.  

5. Run the application
   
   > **Important:**
   > 
   > Remember to apply all the configuration steps described above before start using the app.

    ```bash
   ./gradlew bootRun
    ```

    The application will be accessible at http://localhost:8080.


## Usage

### Initialize configuration

#### Default admin user

To be able to start using Conluz you must configure a user with administrative privileges.

This admin user will serve as the starting point for configuring additional users and the rest of the features related to the energy community.

The information that is required to provide to set up this admin user is:
- personal id
- password
- fullName
- email
- address

To be able to create that user you can use the `POST /api/v1/init` endpoint that does not require authentication providing a body like this:

```json
   {
      "defaultAdminUser":
      {
         "personalId": "01234567Z",
         "password": "a secure password!!",
         "fullName": "Energy Community Acme",
         "email": "adminemail@email.com",
         "address": "Fake Street 123 66633 Teruel (Spain)"
      }
   }
```

> **Important:**
>
> Once you initialize the default admin user for the first time, you will be unable to call this endpoint again. 
> 
> Instead, you should use the provided endpoints that require authentication.

### User Authentication

   When a user logs in or requests access to Conluz API, the authentication server generates a JWT token after verifying the user's credentials.

   The token contains information about the user (such as user ID, role, and expiration time) and is signed with a secret key known only to the authentication server.

   Example token payload:
   ```json
    {
         "sub": "92bd8615-f472-4331-8c90-8276cfb9441d",
         "iat": 1702663671,
         "exp": 1702665471,
         "role": "PARTNER"
    }
   ```

**Token Issuance**

   To get a valid token, firstly, a user must be configured in the application.
   
   Once the user is configured in the application, then you can use the `POST /api/v1/login` endpoint providing a body like this:
   ```json
    {
      "username": "01234567Z",
      "password": "a secure password!!"
    }
   ```

   If the login is successful, the server issues the JWT token to the client, which securely stores the token.

   Example login endpoint response:

   ```json
   {"token":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o"}
   ```

**Token usage**

   The client includes the JWT token in the _Authorization_ header of subsequent API requests.

   ```
   GET /api/v1/users
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiMTIzNDU2IiwidXNlcm5hbWUiOiJleGFtcGxlX3VzZXIiLCJyb2xlIjoiYWRtaW4iLCJleHAiOjE2NzI1MzExOTl9.TI6nlzA1J7WV2rZq2ZC1U4FiG7YXYp3JO0_TPKKmWNE
   ```

**Token Verification**

   The Conluz API server receives the request and verifies the JWT signature using a secret key.

   If the signature is valid, the server extracts and decodes the information from the token to identify the user and determine their access rights.

   If the token is expired or the signature is invalid, the server denies access.

> JWT authentication provides a stateless and secure way to authenticate and authorize users in the Conluz application without the need for sessions or storing user information on the server.

### API docs

Conluz provides API documentation in OpenAPI format, allowing anyone to understand and interact with the available endpoints. 

> **Important!**
> 
> To be able to access the documentation, first of all, you have to **run the app**.

You can access the documentation using two methods:

### 1. Download OpenAPI Specification

You can download the OpenAPI specification in either YAML or JSON format to view the detailed documentation. Follow the steps below:

- Navigate to the [API Documentation](http://localhost:8080/api-docs) section.

Then you can download and inspect the specification using your preferred tool.

### 2. Swagger UI

Alternatively, you can access the API documentation through the Swagger UI, a user-friendly graphical interface. Follow these steps:

- Navigate to the [Swagger UI](http://localhost:8080/api-docs/swagger-ui/index.html) link.
- Explore and interact with the API endpoints in a visually appealing and intuitive way.
- Swagger UI provides an easy way to understand request and response formats, and even allows you to make sample requests directly from the documentation.

## Deployment

> **Important!**
>
> Docker: Ensure that Docker is installed on your system. If not, you can download it from Docker's [official website](https://www.docker.com/get-started/).
> Docker Compose: This is a tool for defining and managing multi-container Docker applications. If you have Docker Desktop on your machine, Docker Compose should be pre-installed. If not, you can get it from [here](https://docs.docker.com/compose/install/).

### Deploy the app from the scratch

Here are the steps to deploy the app:
1. Clone the repository
```shell
   git clone git@github.com:lucoenergia/conluz.git
   ```
2. Navigate to the deploy folder within the project directory
```shell
   cd conluz/deploy
   ```
3. Place an `.env` file in the same directory as your `docker-compose.yml` with the following content:
```shell
CONLUZ_JWT_SECRET_KEY=your-secret-key
```
Replace your-secret-key with your actual secret key.
4. Navigate back to the project root folder and build the Docker image
```shell
   cd ..
   docker build -t conluz:1.0 -f Dockerfile .
   ```
This command builds the Docker image from the Dockerfile and tags it with the name conluz:1.0. You have to replace the version number by the one you want to deploy.
Here's what's happening in the command:
- `-t conluz:1.0` names (or "tags") the image.
- `-f Dockerfile` tells Docker where to find your Dockerfile.
- `.` tells Docker to use the current directory as the context for the build (i.e., sets the "build context").
To confirm if the Docker image has been built successfully, you can list all available Docker images with the command:
```shell
docker images
```
You should see the image (conluz:1.0) in the resulting list.
5. Start the services with Docker Compose
```shell
   docker compose up -d
   ```
This command will start all your services in the background. Docker Compose will start all the services defined in the `docker-compose.yml` file, in the correct order.

At this point, the application should be running at http://localhost:8080.

To stop the application, you can `run docker stop conluz`.

To delete the application container, you can run `docker rm conluz`.

### Re-deploy a new version of the app

1. Stop the currently running Docker container.

You can do this with the `docker stop <container_id> command`. <container_id> is the id of your currently running Docker container.

2. Remove the stopped Docker container 

This isn't strictly necessary if you're using anonymous/non-persistent volumes, but it's a good practice nonetheless to clean up after yourself. You can use the `docker rm <container_id>` command to remove the stopped container.

3. Build a new Docker image with the version of the app you want to deploy 

This can be done using the `docker build` command. Make sure to tag your image with a new version tag, something like:
 ```shell
docker build -t conluz:2.0 -f Dockerfile .
```
4. Run a new Docker container with the new image

This can be done running this command:

```shell
docker compose up conluz -d
```

## Contributing

Pull requests are welcome. For major changes, please open an issue first
to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License

This project is licensed under the [Apache 2 License](https://www.apache.org/licenses/LICENSE-2.0).

## Contact

For inquiries, please contact Luco Energía at [lucoenergia@gmail.com]().
