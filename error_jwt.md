# Spring Boot Startup Error Summary

## Root Cause

The application fails because the `JWT_SECRET_KEY` environment variable
is missing.

``` text
Could not resolve placeholder 'JWT_SECRET_KEY'
```

Spring is trying to resolve:

``` properties
security.jwt.secret-key=${JWT_SECRET_KEY}
```

Since `JWT_SECRET_KEY` is not defined, Spring cannot create the
`JwtUtil` bean.

## Error Chain

``` text
JWT_SECRET_KEY missing
        ↓
JwtUtil bean creation fails
        ↓
JwtFilter dependency fails
        ↓
Embedded Tomcat cannot start
        ↓
Spring Boot application startup fails
```

## How to Fix

### Option 1 (Recommended)

Define the environment variable:

``` text
JWT_SECRET_KEY=your-secure-secret-key
```

### Option 2 (Local Development)

``` properties
security.jwt.secret-key=mySecretKey123456789
```

### Option 3 (Default Value)

``` properties
security.jwt.secret-key=${JWT_SECRET_KEY:myDefaultSecret}
```

## Deployment Note

If the application works locally but fails after deployment, verify that
your hosting platform has the `JWT_SECRET_KEY` environment variable
configured.
