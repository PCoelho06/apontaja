# Apontaja Backend API

Backend REST API for Apontaja - Booking and appointment management platform for beauty salons.

## Technologies

- Java 21
- Spring Boot 3.2.0
- Spring Security with JWT Authentication
- Spring Data JPA
- H2 Database (development/testing)
- PostgreSQL (production ready)
- Flyway (database migrations)
- MapStruct (DTO mapping)
- Springdoc OpenAPI (Swagger documentation)
- Maven

## Features

- JWT-based authentication with access and refresh tokens
- User registration and login with email notifications
- Token refresh mechanism
- Secure password encryption with BCrypt
- Role-based access control (USER, ADMIN, SALON_OWNER)
- RESTful API endpoints
- Exception handling with custom error responses
- Interactive API documentation with Swagger UI
- Multi-environment configuration (dev, test, prod)
- Email enumeration protection during registration
- Secure token deletion on logout

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
│   │   │   ├── service/         # Business logic services (interfaces + implementations)
│   │   │   └── BackendApplication.java
│   │   └── resources/
│   │       ├── application.yml           # Base configuration
│   │       ├── application-dev.yml       # Development environment
│   │       ├── application-test.yml      # Test environment
│   │       └── application-prod.yml      # Production environment
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
# Default profile (using H2 in-memory database)
mvn spring-boot:run

# Development profile (PostgreSQL)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Production profile (PostgreSQL + Flyway)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

The application will start on `http://localhost:8080`

### API Documentation

Once the application is running, access the interactive Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

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

**Note**: Test endpoints have been removed. Use Swagger UI for testing authenticated endpoints.

## Security Configuration

### JWT Configuration

JWT settings can be configured via environment variables:

```yaml
jwt:
  secret: ${JWT_SECRET:default_secret_key}
  access-token-expiration: ${JWT_ACCESS_TOKEN_EXPIRATION:900000}    # 15 minutes
  refresh-token-expiration: ${JWT_REFRESH_TOKEN_EXPIRATION:604800000}  # 7 days
```

- JWT access tokens expire after 15 minutes (900000 ms)
- JWT refresh tokens expire after 7 days (604800000 ms)
- Passwords are encrypted using BCrypt
- Stateless session management
- CORS can be configured in SecurityConfig
- Explicit HS256 signature algorithm for JWT tokens
- Comprehensive JWT validation with logging

## Database

### H2 Console (Development)

Access the H2 console at: `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:apontaja`
- Username: `sa`
- Password: (empty)

### PostgreSQL Configuration

PostgreSQL is configured for development and production environments. Set these environment variables:

**Development (application-dev.yml):**
```bash
export DB_USERNAME=apontaja
export DB_PASSWORD=apontaja
```

**Production (application-prod.yml):**
```bash
export DATABASE_URL=jdbc:postgresql://your-host:5432/apontaja
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
export JWT_SECRET=your_secure_secret_key
```

## Security Best Practices

- ✅ JWT tokens are signed with explicit HS256 algorithm
- ✅ JWT secret configurable via environment variables
- ✅ Passwords are encrypted with BCrypt
- ✅ Refresh tokens are stored securely in the database
- ✅ Token expiration is properly enforced
- ✅ Old refresh tokens are deleted (not revoked) on logout
- ✅ Email enumeration protection during registration
- ✅ Email notifications for security events
- ✅ Input validation on all endpoints
- ✅ Global exception handling with logging
- ✅ Stateless authentication (no server-side sessions)
- ✅ Role-based access control
- ✅ Comprehensive error logging without exposing sensitive data

## Clean Code Practices

- Single Responsibility Principle: Each class has a single, well-defined purpose
- Interface Segregation: Service layer uses interfaces for better testability
- Dependency Injection: Using constructor injection with Lombok's @RequiredArgsConstructor
- DTOs for data transfer: Separating domain models from API contracts
- Service layer: Business logic separated from controllers with clear interfaces
- Repository pattern: Data access abstraction
- Exception handling: Centralized with @RestControllerAdvice
- Validation: Using Bean Validation annotations
- Lombok: Reducing boilerplate code
- MapStruct: Type-safe DTO mapping
- Comprehensive logging: Using SLF4J for debugging and audit trails

## License

This project is licensed under the MIT License.
