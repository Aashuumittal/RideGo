# RideGo Backend

Spring Boot + MongoDB backend with a static HTML/CSS/JS frontend for a minimal ride sharing workflow with JWT-based auth, role-driven access control, validation, global error handling, and OpenRouteService route planning.

## Tech Stack
- Spring Boot 3.2.x (web, security, validation, data-mongodb)
- JWT (jjwt 0.12.x)
- MongoDB
- Lombok
- Java 21
- Leaflet + OpenStreetMap tiles for the passenger map
- OpenRouteService for geocoding, route geometry, distance, and ETA

## How It Fits Together
- Entry point: [src/main/java/com/example/ridego/RidegoApplication.java](src/main/java/com/example/ridego/RidegoApplication.java#L6-L13)
- Security: Stateless JWT filter + role checks in [config/SecurityConfig.java](src/main/java/com/example/ridego/config/SecurityConfig.java#L14-L44) and token parsing in [config/JwtFilter.java](src/main/java/com/example/ridego/config/JwtFilter.java#L22-L54).
- Auth: Register/login flows in [controller/AuthController.java](src/main/java/com/example/ridego/controller/AuthController.java#L11-L33) backed by [service/impl/AuthServiceImpl.java](src/main/java/com/example/ridego/service/impl/AuthServiceImpl.java#L15-L52) and JWT utility [util/JwtUtil.java](src/main/java/com/example/ridego/util/JwtUtil.java#L13-L41).
- Rides domain: Ride CRUD/use-cases in [controller/RideController.java](src/main/java/com/example/ridego/controller/RideController.java#L13-L40) and driver actions in [controller/DriverController.java](src/main/java/com/example/ridego/controller/DriverController.java#L11-L33) backed by [service/impl/RideServiceImpl.java](src/main/java/com/example/ridego/service/impl/RideServiceImpl.java#L17-L100).
- Route planning: Authenticated proxy endpoints in [controller/RouteController.java](src/main/java/com/example/ridego/controller/RouteController.java) backed by [service/impl/OpenRouteServiceRoutePlanningService.java](src/main/java/com/example/ridego/service/impl/OpenRouteServiceRoutePlanningService.java). The browser never receives the ORS API key.
- Persistence: Mongo repositories [repository/UserRepository.java](src/main/java/com/example/ridego/repository/UserRepository.java#L8-L11) and [repository/RideRepository.java](src/main/java/com/example/ridego/repository/RideRepository.java#L7-L10).
- Models: [model/User.java](src/main/java/com/example/ridego/model/User.java#L7-L17) and [model/Ride.java](src/main/java/com/example/ridego/model/Ride.java#L9-L25).
- DTOs with validation: [dto/RegisterRequest.java](src/main/java/com/example/ridego/dto/RegisterRequest.java#L6-L16), [dto/LoginRequest.java](src/main/java/com/example/ridego/dto/LoginRequest.java#L6-L12), [dto/CreateRideRequest.java](src/main/java/com/example/ridego/dto/CreateRideRequest.java#L6-L13).
- Errors: Central handling in [exception/GlobalExceptionHandler.java](src/main/java/com/example/ridego/exception/GlobalExceptionHandler.java#L11-L46) with custom [BadRequestException](src/main/java/com/example/ridego/exception/BadRequestException.java#L3-L6) and [NotFoundException](src/main/java/com/example/ridego/exception/NotFoundException.java#L3-L6).

## Domain Model
- User: `id`, `username`, `password` (BCrypt), `role` = `ROLE_USER` or `ROLE_DRIVER` ([model/User.java](src/main/java/com/example/ridego/model/User.java#L7-L17)).
- Ride: `id`, `userId`, `driverId`, `pickupLocation`, `dropLocation`, `status` (`REQUESTED` → `ACCEPTED` → `COMPLETED`), `createdAt` ([model/Ride.java](src/main/java/com/example/ridego/model/Ride.java#L9-L25)).

## Security & Auth Flow
- Login/Register are public under `/api/auth/**`.
- All other routes require JWT `Authorization: Bearer <token>`.
- Role gates: `/api/v1/driver/**` requires `ROLE_DRIVER`, `/api/v1/user/**` requires `ROLE_USER` ([config/SecurityConfig.java](src/main/java/com/example/ridego/config/SecurityConfig.java#L27-L33)).
- JWT claims include `sub` (username) and `role`; issued and verified via [util/JwtUtil.java](src/main/java/com/example/ridego/util/JwtUtil.java#L16-L41).

## Validation & Errors
- DTOs use Jakarta validation annotations; failures return `400` with `error=VALIDATION_ERROR` and message ([dto/*.java](src/main/java/com/example/ridego/dto/CreateRideRequest.java#L9-L13), [exception/GlobalExceptionHandler.java](src/main/java/com/example/ridego/exception/GlobalExceptionHandler.java#L11-L46)).
- Domain errors: `BAD_REQUEST` for invalid actions, `NOT_FOUND` for missing resources.

## API Endpoints
| Method | Path | Role | Description |
| --- | --- | --- | --- |
| POST | /api/auth/register | Public | Create user (role `ROLE_USER` or `ROLE_DRIVER`). |
| POST | /api/auth/login | Public | Login and receive JWT. |
| POST | /api/v1/rides | ROLE_USER | Create ride request. |
| GET | /api/v1/user/rides | ROLE_USER | List rides of current user. |
| POST | /api/v1/routes/geocode | Authenticated | Convert an address/search string to coordinates through ORS. |
| POST | /api/v1/routes/plan | Authenticated | Geocode route endpoints if needed, calculate driving route, distance, ETA, and return GeoJSON. |
| GET | /api/v1/driver/rides/requests | ROLE_DRIVER | List pending (`REQUESTED`) rides. |
| GET | /api/v1/driver/stats | ROLE_DRIVER | Driver dashboard counts for pending, accepted, and completed rides. |
| POST | /api/v1/driver/rides/{rideId}/accept | ROLE_DRIVER | Accept a pending ride. |
| POST | /api/v1/rides/{rideId}/complete | ROLE_USER or ROLE_DRIVER | Complete an accepted ride. |

## Sample Requests (replace `<TOKEN>`)
```bash
# Register user
curl -X POST http://localhost:9091/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"1234","role":"ROLE_USER"}'

# Register driver
curl -X POST http://localhost:9091/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"driver1","password":"abcd","role":"ROLE_DRIVER"}'

# Login (returns {"token":"..."})
curl -X POST http://localhost:9091/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"1234"}'

# Create ride (user)
curl -X POST http://localhost:9091/api/v1/rides \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"pickupLocation":"A","dropLocation":"B"}'

# Driver: view pending
curl -X GET http://localhost:9091/api/v1/driver/rides/requests \
  -H "Authorization: Bearer <TOKEN>"

# Driver: dashboard stats
curl -X GET http://localhost:9091/api/v1/driver/stats \
  -H "Authorization: Bearer <TOKEN>"

# Driver: accept ride
curl -X POST http://localhost:9091/api/v1/driver/rides/{rideId}/accept \
  -H "Authorization: Bearer <TOKEN>"

# Complete ride (driver or user)
curl -X POST http://localhost:9091/api/v1/rides/{rideId}/complete \
  -H "Authorization: Bearer <TOKEN>"
```

## Configuration
Add these to `application.properties` (or environment variables) before running:
```properties
spring.data.mongodb.uri=mongodb://localhost:27017/ridego
jwt.secret=change-me-to-a-256-bit-secret-value
jwt.expirationMs=86400000
server.port=9091
ORS_API_KEY=your-openrouteservice-api-key
```

`ORS_API_KEY` is read by Spring as `ors.api-key=${ORS_API_KEY:}` and is only used server-side.

## Running Locally
1) Ensure MongoDB is running locally or update `spring.data.mongodb.uri` accordingly.
2) Export the ORS API key: `export ORS_API_KEY=your-openrouteservice-api-key`.
3) Build and test: `./mvnw test`.
4) Run: `./mvnw spring-boot:run`.
5) Open `http://localhost:9091`, login as a passenger, use the current location or pickup field, enter a destination, and click `Preview Route`.

## Notes
- Passwords are stored BCrypt-hashed; JWT must be sent on every protected request.  
- Roles are string-based (`ROLE_USER`, `ROLE_DRIVER`) and drive access checks inside services ([service/impl/RideServiceImpl.java](src/main/java/com/example/ridego/service/impl/RideServiceImpl.java#L34-L99)).
- Error responses include `error`, `message`, `timestamp` via the global handler.
