# Security Documentation

## Security Decisions

### CSRF Protection

**Decision:** CSRF protection is disabled in the Spring Security configuration.

**Justification:** This is a stateless REST API that uses JWT tokens stored in the `Authorization` header for authentication. CSRF (Cross-Site Request Forgery) attacks specifically target cookie-based authentication sessions. Since this API:
- Uses JWT tokens in Authorization headers (not cookies)
- Is completely stateless
- Does not use session-based authentication
- Follows REST API best practices

CSRF protection is not necessary and would actually interfere with API functionality.

**References:**
- [OWASP: CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [Spring Security Documentation on CSRF](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)

### JWT Token Security

The application implements the following JWT security best practices:

1. **Token Signing:** Tokens are signed using HMAC-SHA384 with a secure secret key
2. **Token Expiration:** Access tokens expire after 15 minutes to limit exposure
3. **Refresh Tokens:** Long-lived refresh tokens (7 days) are stored in the database and can be revoked
4. **Token Rotation:** Old refresh tokens are deleted when a user logs in again
5. **Secure Storage:** Refresh tokens are stored in the database, not on the client
6. **Token Validation:** All tokens are validated before granting access

### Password Security

1. **BCrypt Hashing:** All passwords are hashed using BCrypt with automatic salt generation
2. **No Plaintext Storage:** Passwords are never stored in plaintext
3. **Minimum Length:** Password must be at least 8 characters (enforced via validation)

### Database Security

1. **Parameterized Queries:** JPA/Hibernate uses parameterized queries to prevent SQL injection
2. **Connection Pooling:** HikariCP connection pooling is used for efficient connection management
3. **H2 Console:** H2 console is enabled only for development and should be disabled in production

### API Security

1. **Input Validation:** All API endpoints validate input using Bean Validation annotations
2. **Exception Handling:** Global exception handler prevents information leakage
3. **Role-Based Access Control:** Endpoints can be secured by roles (USER, ADMIN, SALON_OWNER)
4. **Stateless Sessions:** No server-side session state is maintained

## Production Deployment Recommendations

Before deploying to production:

1. **Change JWT Secret:** Generate a new, cryptographically secure secret key
   ```bash
   openssl rand -base64 64
   ```
   Update `jwt.secret` in `application.properties`

2. **Disable H2 Console:** Set `spring.h2.console.enabled=false` in production configuration

3. **Use PostgreSQL:** Configure PostgreSQL database instead of H2
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/apontaja
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

4. **Enable HTTPS:** Always use HTTPS in production to protect tokens in transit

5. **Configure CORS:** Properly configure CORS to allow only trusted origins
   ```java
   @Bean
   public CorsConfigurationSource corsConfigurationSource() {
       CorsConfiguration configuration = new CorsConfiguration();
       configuration.setAllowedOrigins(List.of("https://yourdomain.com"));
       configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
       configuration.setAllowedHeaders(List.of("*"));
       UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
       source.registerCorsConfiguration("/**", configuration);
       return source;
   }
   ```

6. **Rate Limiting:** Implement rate limiting to prevent brute force attacks

7. **Logging:** Configure proper logging but avoid logging sensitive information

8. **Security Headers:** Additional security headers can be configured if needed

## Known Limitations

1. **Single Secret Key:** Currently uses a single secret key for all JWT tokens. Consider implementing key rotation for enhanced security.

2. **No Account Lockout:** The system doesn't currently implement account lockout after failed login attempts. Consider adding this feature to prevent brute force attacks.

3. **No Email Verification:** User registration doesn't require email verification. Consider adding this for production use.

## Security Updates

Stay updated with security advisories:
- [Spring Security Advisories](https://spring.io/security/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
