# Apontaja Backend API

Backend REST API for Apontaja - Booking and appointment management platform for beauty salons.

## Technologies

- Java 21
- Spring Boot 3.2.0
- Spring Security with JWT Authentication
- Spring Data JPA
- H2 Database (development)
- PostgreSQL (production ready)
- Maven

## Features

- JWT-based authentication with access and refresh tokens
- User registration and login
- Token refresh mechanism
- Secure password encryption with BCrypt
- Role-based access control (USER, ADMIN, SALON_OWNER)
- RESTful API endpoints
- Exception handling with custom error responses

## Project Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/apontaja/backend/
│   │   │   ├── config/          # Security and application configuration
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── exception/       # Custom exceptions and global exception handler
│   │   │   ├── model/           # Entity models
│   │   │   ├── repository/      # JPA repositories
│   │   │   ├── security/jwt/    # JWT utilities and filters
│   │   │   ├── service/         # Business logic services
│   │   │   └── BackendApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/apontaja/backend/
└── pom.xml
```

## Getting Started

### Prerequisites

- Java 21
- Maven 3.6+

### Build the project

```bash
mvn clean install
```

### Run the application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### Run tests

```bash
mvn test
```

## API Endpoints

### Authentication

#### Register a new user
```
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe"
}
```

#### Login
```
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

#### Refresh Token
```
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Logout
```
POST /api/auth/logout
Content-Type: application/json

{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Test Endpoints

#### Public endpoint (no authentication required)
```
GET /api/test/public
```

#### Protected endpoint (authentication required)
```
GET /api/test/protected
Authorization: Bearer {accessToken}
```

#### Admin endpoint (admin role required)
```
GET /api/test/admin
Authorization: Bearer {accessToken}
```

## Security Configuration

- JWT access tokens expire after 15 minutes (900000 ms)
- JWT refresh tokens expire after 7 days (604800000 ms)
- Passwords are encrypted using BCrypt
- Stateless session management
- CORS can be configured in SecurityConfig

## Database

### H2 Console (Development)

Access the H2 console at: `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:apontaja`
- Username: `sa`
- Password: (empty)

### PostgreSQL (Production)

Update `application.properties` with PostgreSQL configuration:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/apontaja
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
```

## Security Best Practices

- ✅ JWT tokens are signed with a secure secret key
- ✅ Passwords are encrypted with BCrypt
- ✅ Refresh tokens are stored securely in the database
- ✅ Token expiration is properly enforced
- ✅ Old refresh tokens are revoked on new login
- ✅ Input validation on all endpoints
- ✅ Global exception handling
- ✅ Stateless authentication (no server-side sessions)
- ✅ Role-based access control

## Clean Code Practices

- Single Responsibility Principle: Each class has a single, well-defined purpose
- Dependency Injection: Using constructor injection with Lombok's @RequiredArgsConstructor
- DTOs for data transfer: Separating domain models from API contracts
- Service layer: Business logic separated from controllers
- Repository pattern: Data access abstraction
- Exception handling: Centralized with @RestControllerAdvice
- Validation: Using Bean Validation annotations
- Lombok: Reducing boilerplate code

## License

This project is licensed under the MIT License.
