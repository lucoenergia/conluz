# Conluz

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

TBD

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
   
3. Run the application
   
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

To be able to use the application you need to create at least one user with administrative privileges.

To be able to create that user you can user the `/api/v1/init` endpoint that does not require authentication:

```shell

```



> **Important:**
>
> This configuration step will be required only the first time you run the app.


To be able to configure an Energy Community and admin user is required.

To initiate the use of Conluz, it is essential to configure an admin user first. This admin user will serve as the starting point for configuring additional users and specific features related to the energy community.

The information that is required to provide to set up this admin user is:
- number
- id
- fullName
- email
- address

This information must be provided to the app in the shape of these env vars:
- `CONLUZ_USER_DEFAULT_ADMIN_NUMBER`
- `CONLUZ_USER_DEFAULT_ADMIN_ID`
- `CONLUZ_USER_DEFAULT_ADMIN_FULL_NAME`
- `CONLUZ_USER_DEFAULT_ADMIN_EMAIL`
- `CONLUZ_USER_DEFAULT_ADMIN_ADDRESS`

For instance:

```
   export CONLUZ_USER_DEFAULT_ADMIN_ADDRESS="Fake Streen 123"
   export CONLUZ_USER_DEFAULT_ADMIN_EMAIL="youremail@email.com"
   export CONLUZ_USER_DEFAULT_ADMIN_FULL_NAME="Acme Energy Community"
   export CONLUZ_USER_DEFAULT_ADMIN_ID="12345678Z"
   export CONLUZ_USER_DEFAULT_ADMIN_NUMBER="0"
```

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

   The server issues the JWT token to the client, which securely stores the token.

   Example JWT token (encoded):

   ```eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3OFoiLCJpYXQiOjE3MDI2NjM2NzEsImV4cCI6MTcwMjY2NTQ3MX0.Mdgr_x8q9yEf20ZbkRna7OU1LH5-1ol6UPXr3dmYW1o```

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



## Contributing

Pull requests are welcome. For major changes, please open an issue first
to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License

This project is licensed under the [MIT License](https://choosealicense.com/licenses/mit/).

## Contact

For inquiries, please contact Luco Energía at [lucoenergia@gmail.com]().