/**
 * RideGo API Client
 * Automatically targets the Spring Boot backend during local development.
 * Change API_BASE_URL if you deploy the backend elsewhere.
 */

const API = (() => {
  const API_BASE_URL = 'http://localhost:9091';

  function resolveBaseUrl() {
    const { protocol, hostname, port } = window.location;

    // Same origin works when Spring Boot serves src/main/resources/static.
    if ((hostname === 'localhost' || hostname === '127.0.0.1') && port === '9091') {
      return '';
    }

    // file:// or separate local frontend servers must call Spring Boot explicitly.
    if (
      protocol === 'file:' ||
      hostname === 'localhost' ||
      hostname === '127.0.0.1' ||
      hostname === ''
    ) {
      return API_BASE_URL;
    }

    // Production default: expect frontend and backend on the same origin.
    return '';
  }

  const BASE_URL = resolveBaseUrl();

  // ── Token helpers ──────────────────────────────────────────────
  function getToken() {
    return localStorage.getItem('ridego_token');
  }
  function setToken(token) {
    localStorage.setItem('ridego_token', token);
  }
  function getUser() {
    const raw = localStorage.getItem('ridego_user');
    return raw ? JSON.parse(raw) : null;
  }
  function setUser(user) {
    localStorage.setItem('ridego_user', JSON.stringify(user));
  }
  function clearSession() {
    localStorage.removeItem('ridego_token');
    localStorage.removeItem('ridego_user');
  }

  // ── Core fetch wrapper ────────────────────────────────────────
  async function request(method, path, body, requiresAuth = true) {
    const headers = { 'Content-Type': 'application/json' };
    if (requiresAuth) {
      const token = getToken();
      if (!token) throw new Error('Not authenticated');
      headers['Authorization'] = `Bearer ${token}`;
    }

    const opts = { method, headers };
    if (body) opts.body = JSON.stringify(body);

    let res;
    try {
      res = await fetch(`${BASE_URL}${path}`, opts);
    } catch (err) {
      throw new Error('Cannot connect to server. Make sure the backend is running on port 9091.');
    }

    // Parse response body
    let data;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
      data = await res.json();
    } else {
      data = await res.text();
    }

    if (!res.ok) {
      // Backend error shapes: { message, error } or plain string
      const msg = (typeof data === 'object' && data.message)
        ? data.message
        : (typeof data === 'string' && data)
          ? data
          : `Request failed (${res.status})`;
      throw new Error(msg);
    }

    return data;
  }

  // ── Auth endpoints ────────────────────────────────────────────
  async function register(username, password, role, driverProfile = {}) {
    const data = await request('POST', '/api/auth/register', { username, password, role, ...driverProfile }, false);
    return data;
  }

  async function login(username, password) {
    const data = await request('POST', '/api/auth/login', { username, password }, false);
    // Store token and user info
    setToken(data.token);
    // Decode role from JWT payload (middle segment)
    try {
      const payload = JSON.parse(atob(data.token.split('.')[1]));
      setUser({ username, role: payload.role });
    } catch {
      setUser({ username, role: null });
    }
    return data;
  }

  function logout() {
    clearSession();
    window.location.href = 'index.html';
  }

  // ── Ride endpoints (User) ─────────────────────────────────────
  async function createRide(pickupLocation, dropLocation, routeDetails = {}) {
    return request('POST', '/api/v1/rides', { pickupLocation, dropLocation, ...routeDetails });
  }

  async function cancelRide(rideId) {
    return request('POST', `/api/v1/rides/${rideId}/cancel`);
  }

  async function rateDriver(rideId, rating, feedback) {
    return request('POST', `/api/v1/rides/${rideId}/rating`, { rating, feedback });
  }

  async function createPaymentOrder(rideId) {
    return request('POST', `/api/v1/rides/${rideId}/payments/order`);
  }

  async function verifyPayment(rideId, payload) {
    return request('POST', `/api/v1/rides/${rideId}/payments/verify`, payload);
  }

  async function getUserRides() {
    return request('GET', '/api/v1/user/rides');
  }

  async function completeRide(rideId) {
    return request('POST', `/api/v1/rides/${rideId}/complete`);
  }

  // ── Ride endpoints (Driver) ───────────────────────────────────
  async function getPendingRides() {
    return request('GET', '/api/v1/driver/rides/requests');
  }

  async function getDriverStats() {
    return request('GET', '/api/v1/driver/stats');
  }

  async function acceptRide(rideId) {
    return request('POST', `/api/v1/driver/rides/${rideId}/accept`);
  }

  async function getDriverRides() {
    return request('GET', '/api/v1/driver/rides');
  }

  // ── Route planning endpoints ─────────────────────────────────
  async function geocodeLocation(text) {
    return request('POST', '/api/v1/routes/geocode', { text });
  }

  async function planRoute(payload) {
    return request('POST', '/api/v1/routes/plan', payload);
  }

  async function getRidePlan(payload) {
    return request('POST', '/api/v1/ai/recommendation', payload);
  }

  // ── Public API ────────────────────────────────────────────────
  return {
    getToken, setToken,
    getUser, setUser, clearSession,
    register, login, logout,
    createRide, getUserRides, completeRide, cancelRide, rateDriver, createPaymentOrder, verifyPayment,
    getPendingRides, getDriverStats, acceptRide, getDriverRides,
    geocodeLocation, planRoute, getRidePlan,
  };
})();
