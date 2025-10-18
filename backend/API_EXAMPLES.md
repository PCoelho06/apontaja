# API Usage Examples

This document provides practical examples for testing the Apontaja Backend API.

## Base URL

```
http://localhost:8080
```

## Authentication Endpoints

### 1. Register a New User

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "securePassword123",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

### 2. Login

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "securePassword123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "edf49e2f-5200-4b3e-9256-268360bb95d1",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

### 3. Refresh Access Token

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "edf49e2f-5200-4b3e-9256-268360bb95d1"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "edf49e2f-5200-4b3e-9256-268360bb95d1",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

### 4. Logout

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "edf49e2f-5200-4b3e-9256-268360bb95d1"
  }'
```

**Response:**
```
HTTP 204 No Content
```

## Test Endpoints

### 1. Public Endpoint (No Authentication Required)

**Request:**
```bash
curl http://localhost:8080/api/test/public
```

**Response:**
```json
{
  "message": "This is a public endpoint"
}
```

### 2. Protected Endpoint (Authentication Required)

**Request:**
```bash
curl http://localhost:8080/api/test/protected \
  -H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

**Response:**
```json
{
  "message": "This is a protected endpoint - you are authenticated!"
}
```

### 3. Admin Endpoint (Admin Role Required)

**Request:**
```bash
curl http://localhost:8080/api/test/admin \
  -H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

**Response (if user has ADMIN role):**
```json
{
  "message": "This is an admin endpoint"
}
```

**Response (if user doesn't have ADMIN role):**
```json
{
  "timestamp": "2025-10-17T21:00:00.000Z",
  "status": 403,
  "error": "Access Denied"
}
```

## Error Responses

### Validation Error

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "invalid-email",
    "password": "short",
    "firstName": "",
    "lastName": ""
  }'
```

**Response:**
```json
{
  "timestamp": "2025-10-17T21:00:00.000Z",
  "status": 400,
  "errors": {
    "email": "Email must be valid",
    "password": "Password must be at least 8 characters",
    "firstName": "First name is required",
    "lastName": "Last name is required"
  }
}
```

### Invalid Credentials

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "wrongpassword"
  }'
```

**Response:**
```json
{
  "timestamp": "2025-10-17T21:00:00.000Z",
  "status": 401,
  "error": "Invalid email or password"
}
```

### Expired/Invalid Token

**Request:**
```bash
curl http://localhost:8080/api/test/protected \
  -H "Authorization: Bearer invalid_or_expired_token"
```

**Response:**
```
HTTP 403 Forbidden
```

### Expired Refresh Token

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "expired_token"
  }'
```

**Response:**
```json
{
  "timestamp": "2025-10-17T21:00:00.000Z",
  "status": 403,
  "error": "Failed for [expired_token]: Refresh token was expired. Please make a new signin request"
}
```

## Complete Workflow Example

Here's a complete example of a typical authentication flow:

```bash
# 1. Register a new user
REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane.smith@example.com",
    "password": "mySecurePass123",
    "firstName": "Jane",
    "lastName": "Smith"
  }')

# Extract tokens
ACCESS_TOKEN=$(echo $REGISTER_RESPONSE | jq -r '.accessToken')
REFRESH_TOKEN=$(echo $REGISTER_RESPONSE | jq -r '.refreshToken')

echo "Access Token: $ACCESS_TOKEN"
echo "Refresh Token: $REFRESH_TOKEN"

# 2. Access a protected endpoint
curl http://localhost:8080/api/test/protected \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 3. After 15 minutes, access token expires. Use refresh token to get a new one
NEW_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")

NEW_ACCESS_TOKEN=$(echo $NEW_RESPONSE | jq -r '.accessToken')

# 4. Use new access token
curl http://localhost:8080/api/test/protected \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN"

# 5. Logout (revoke refresh token)
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

## Using Postman

### Import Collection

You can create a Postman collection with these endpoints:

1. Create a new collection called "Apontaja API"
2. Add an environment variable `baseUrl` = `http://localhost:8080`
3. Add environment variables for `accessToken` and `refreshToken`
4. Create requests for each endpoint above
5. Use `{{baseUrl}}` and `{{accessToken}}` in your requests
6. Add a test script to automatically save tokens:

```javascript
// In Register/Login request Tests tab
var jsonData = pm.response.json();
pm.environment.set("accessToken", jsonData.accessToken);
pm.environment.set("refreshToken", jsonData.refreshToken);
```

### Authorization Header

For protected endpoints, add this header:
```
Authorization: Bearer {{accessToken}}
```

## Testing with HTTPie

If you prefer HTTPie over curl:

```bash
# Register
http POST localhost:8080/api/auth/register \
  email=test@example.com \
  password=password123 \
  firstName=Test \
  lastName=User

# Login
http POST localhost:8080/api/auth/login \
  email=test@example.com \
  password=password123

# Protected endpoint
http GET localhost:8080/api/test/protected \
  Authorization:"Bearer YOUR_ACCESS_TOKEN"
```

## H2 Database Console

For development, you can access the H2 console to view the database:

1. Navigate to: http://localhost:8080/h2-console
2. Use these settings:
   - JDBC URL: `jdbc:h2:mem:apontaja`
   - User Name: `sa`
   - Password: (leave empty)
3. Click "Connect"

You can then run SQL queries to inspect users and refresh tokens:

```sql
-- View all users
SELECT * FROM users;

-- View refresh tokens
SELECT * FROM refresh_tokens;

-- Check a specific user
SELECT u.*, rt.token, rt.expiry_date 
FROM users u 
LEFT JOIN refresh_tokens rt ON u.id = rt.user_id 
WHERE u.email = 'test@example.com';
```

## Notes

- Access tokens expire after 15 minutes (900000 milliseconds)
- Refresh tokens expire after 7 days (604800000 milliseconds)
- All timestamps are in UTC
- Passwords must be at least 8 characters long
- Email addresses must be unique and valid
- The H2 console should be disabled in production
