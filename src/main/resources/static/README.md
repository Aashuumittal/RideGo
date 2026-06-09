# RideGo Frontend

Static HTML/CSS/JS frontend for the RideGo Spring Boot backend.

## Structure

```
ridego-frontend/
├── index.html          ← Login / Register
├── user.html           ← Passenger dashboard
├── driver.html         ← Driver dashboard
├── css/
│   ├── style.css       ← Design system, auth page
│   └── dashboard.css   ← Sidebar, panels, ride cards
└── js/
    ├── api.js          ← All backend API calls
    ├── auth.js         ← Login / Register logic
    ├── user-dashboard.js
    └── driver-dashboard.js
```

## Quick Start

### 1. Enable CORS on the backend

Add this to your Spring Boot `SecurityConfig.java` or a dedicated `CorsConfig.java`:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("*"));          // restrict in production
    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

And register it in `SecurityConfig`:
```java
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

### 2. Start the backend

```bash
./mvnw spring-boot:run
# Backend runs on http://localhost:9091
```

### 3. Open the frontend

Option A — just open `index.html` in your browser (file://)

Option B — serve with any static server:
```bash
npx serve .
# or
python3 -m http.server 3000
```

Then visit http://localhost:3000

## Changing the backend URL

Edit the first line in `js/api.js`:
```js
const BASE_URL = 'http://localhost:9091';
```

Change this to your deployed backend URL when deploying to production.

## Features

### Passenger (ROLE_USER)
- Register / Login
- Book a ride (pickup + drop location)
- View ride status (REQUESTED → ACCEPTED → COMPLETED)
- Mark a ride as complete
- Full ride history

### Driver (ROLE_DRIVER)
- Register / Login
- View all pending (REQUESTED) ride requests
- Accept a ride
- Complete a ride
- Live auto-refresh every 15 seconds
- Stats strip showing pending count

## Deployment

Since this is purely static, you can deploy to:
- **Netlify / Vercel**: drag-and-drop the folder
- **GitHub Pages**: push to a `gh-pages` branch
- **Nginx / Apache**: copy files to the web root
- **Spring Boot static**: copy into `src/main/resources/static/` to serve from the same origin (no CORS needed)
