# AWS Cognito Authentication Setup

## Overview
This application has been migrated from file-based authentication to AWS Cognito User Pools for cloud-native user identity management.

## Fixed Issue: cr-java-0090 - File-based Authentication

### Previous Implementation
- Used MD5 hash for generating confirmation codes (insecure)
- No centralized user management
- Authentication credentials potentially stored in local files
- Not suitable for distributed cloud environments

### New Implementation
- **AWS Cognito User Pools**: Centralized user identity management
- **AWS Secrets Manager**: Secure storage of Cognito client secrets
- **Cryptographically Secure Tokens**: HMAC-SHA256 based confirmation codes
- **Stateless Authentication**: JWT tokens for API authentication
- **Horizontal Scalability**: No local state dependencies

## AWS Resources Required

### 1. AWS Cognito User Pool
Create a Cognito User Pool with the following settings:
```bash
aws cognito-idp create-user-pool \
  --pool-name resortslite-users \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true}" \
  --auto-verified-attributes email \
  --username-attributes email
```

### 2. Cognito App Client
Create an app client for the application:
```bash
aws cognito-idp create-user-pool-client \
  --user-pool-id <USER_POOL_ID> \
  --client-name resortslite-app \
  --generate-secret \
  --explicit-auth-flows USER_PASSWORD_AUTH ADMIN_NO_SRP_AUTH
```

### 3. Store Client Secret in AWS Secrets Manager
```bash
aws secretsmanager create-secret \
  --name resortslite/cognito/client-secret \
  --secret-string "<CLIENT_SECRET_FROM_STEP_2>"
```

## Environment Variables

Set the following environment variables in your ECS task definition, EKS deployment, or Elastic Beanstalk configuration:

```bash
# AWS Region
AWS_REGION=us-east-1

# Cognito Configuration
COGNITO_USER_POOL_ID=us-east-1_XXXXXXXXX
COGNITO_CLIENT_ID=your-client-id-here
COGNITO_CLIENT_SECRET_NAME=resortslite/cognito/client-secret

# AWS Credentials (automatically provided by IAM roles in ECS/EKS)
# AWS_ACCESS_KEY_ID=<not needed with IAM roles>
# AWS_SECRET_ACCESS_KEY=<not needed with IAM roles>
```

## IAM Permissions Required

The application's IAM role (ECS task role or EKS pod role) needs the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "cognito-idp:InitiateAuth",
        "cognito-idp:GetUser",
        "cognito-idp:AdminGetUser",
        "cognito-idp:AdminInitiateAuth"
      ],
      "Resource": "arn:aws:cognito-idp:us-east-1:ACCOUNT_ID:userpool/USER_POOL_ID"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:us-east-1:ACCOUNT_ID:secret:resortslite/cognito/client-secret*"
    }
  ]
}
```

## Usage Examples

### 1. User Authentication
```java
@Autowired
private CognitoAuthenticationService authService;

public void authenticateUser(String username, String password) {
    try {
        AuthenticationResult result = authService.authenticateUser(username, password);
        String accessToken = result.accessToken();
        String idToken = result.idToken();
        // Use tokens for subsequent API calls
    } catch (CognitoAuthenticationService.AuthenticationException e) {
        // Handle authentication failure
    }
}
```

### 2. Token Validation
```java
public void validateUserToken(String accessToken) {
    try {
        GetUserResponse user = authService.validateToken(accessToken);
        String username = user.username();
        // User is authenticated
    } catch (CognitoAuthenticationService.AuthenticationException e) {
        // Token is invalid or expired
    }
}
```

### 3. Secure Confirmation Code Generation
```java
// Old (insecure MD5 hash):
// String confirmCode = md5Hash(bookingId + guestName);

// New (secure Cognito-based):
String confirmCode = cognitoAuthService.generateSecureConfirmationCode(guestName, bookingId);
```

## Benefits

1. **Security**: 
   - No credentials stored in files or code
   - Cryptographically secure token generation
   - Centralized credential management

2. **Scalability**:
   - Stateless authentication
   - Horizontal scaling without session affinity
   - Distributed user management

3. **Compliance**:
   - Audit trail via CloudTrail
   - Encrypted credential storage
   - Industry-standard authentication protocols

4. **Maintainability**:
   - Centralized user lifecycle management
   - Built-in password policies
   - MFA support (can be enabled)

## Migration Notes

- The `md5Hash()` method has been removed from `BookingService.java`
- Confirmation codes now use HMAC-SHA256 instead of MD5
- All authentication operations now go through AWS Cognito
- Client secrets are retrieved from AWS Secrets Manager at runtime
- No authentication credentials are stored in application files

## Testing

For local development, you can use AWS CLI to create test users:

```bash
# Create a test user
aws cognito-idp admin-create-user \
  --user-pool-id <USER_POOL_ID> \
  --username testuser@example.com \
  --temporary-password TempPass123! \
  --message-action SUPPRESS

# Set permanent password
aws cognito-idp admin-set-user-password \
  --user-pool-id <USER_POOL_ID> \
  --username testuser@example.com \
  --password SecurePass123! \
  --permanent
```

## Troubleshooting

### Issue: "Client secret not found"
- Verify the secret exists in AWS Secrets Manager
- Check IAM permissions for `secretsmanager:GetSecretValue`
- Verify the `COGNITO_CLIENT_SECRET_NAME` environment variable

### Issue: "User pool not found"
- Verify the `COGNITO_USER_POOL_ID` environment variable
- Check IAM permissions for Cognito operations
- Ensure the user pool exists in the correct AWS region

### Issue: "Invalid authentication flow"
- Ensure the app client has `USER_PASSWORD_AUTH` enabled
- Check that the client secret is correctly configured
- Verify the SECRET_HASH calculation is correct
