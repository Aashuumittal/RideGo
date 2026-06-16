/**
 * User (Passenger) Dashboard
 */

let routeMap;
let routeLayer;
let pickupMarker;
let dropMarker;
let currentLocationMarker;
let currentPickupCoordinate = null;
let activeRoutePlan = null;
let activePlanner = null;
let plannerRequestId = 0;

(function () {
  const user = API.getUser();
  if (!user || !API.getToken()) {
    window.location.href = 'index.html';
    return;
  }
  if (user.role === 'ROLE_DRIVER') {
    window.location.href = 'driver.html';
    return;
  }
  document.getElementById('sidebar-username').textContent = user.username;
  document.getElementById('user-avatar-sidebar').textContent = user.username.charAt(0).toUpperCase();
})();

const today = new Date().toISOString().slice(0, 10);
document.getElementById('schedule-date').min = today;

document.getElementById('logout-btn').addEventListener('click', () => API.logout());
document.getElementById('current-location-btn').addEventListener('click', useCurrentLocation);
document.getElementById('preview-route-btn').addEventListener('click', previewRoute);
document.getElementById('book-btn').addEventListener('click', bookRide);

['pickup', 'drop'].forEach(id => {
  document.getElementById(id).addEventListener('input', resetPreview);
});

['passenger-count', 'luggage-count'].forEach(id => {
  document.getElementById(id).addEventListener('input', () => {
    if (!activeRoutePlan) return;
    showVehicleSelection(activeRoutePlan);
    // Re-run upfront planner with updated passengers/luggage
    triggerUPfrontPlanner(activeRoutePlan);
  });
});

['schedule-date', 'schedule-time'].forEach(id => {
  const input = document.getElementById(id);
  input.addEventListener('click', () => {
    try { input.showPicker?.(); } catch { input.focus(); }
  });
  input.addEventListener('change', () => {
    if (!activeRoutePlan) return;
    // If a vehicle is already selected, refresh for that vehicle; otherwise refresh upfront
    const vehicleType = document.getElementById('vehicle-type').value;
    if (vehicleType) {
      loadVehicleRecommendation(activeRoutePlan, vehicleType);
    } else {
      triggerUPfrontPlanner(activeRoutePlan);
    }
  });
});

document.querySelectorAll('.vehicle-card').forEach(card => {
  card.addEventListener('click', () => selectVehicle(card.dataset.vehicle));
});

document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', e => {
    e.preventDefault();
    switchPanel(item.dataset.panel);
  });
});

document.getElementById('refresh-btn').addEventListener('click', () => {
  const btn = document.getElementById('refresh-btn');
  const active = document.querySelector('.nav-item.active');
  btn.classList.add('spinning');
  if (active?.dataset.panel === 'history') {
    loadRideHistory().finally(() => btn.classList.remove('spinning'));
    return;
  }
  const canPreview = document.getElementById('pickup').value.trim() && document.getElementById('drop').value.trim();
  (canPreview ? previewRoute() : Promise.resolve()).finally(() => btn.classList.remove('spinning'));
});

function switchPanel(name) {
  document.querySelectorAll('.nav-item').forEach(i => i.classList.toggle('active', i.dataset.panel === name));
  document.querySelectorAll('.panel').forEach(p => p.classList.add('hidden'));
  document.getElementById(`panel-${name}`).classList.remove('hidden');

  const titles = { book: 'Book a Ride', history: 'My Rides' };
  const subs = { book: 'Preview route, fare, and ETA before booking', history: 'All your past and active rides' };
  document.getElementById('panel-title').textContent = titles[name] || name;
  document.getElementById('panel-subtitle').textContent = subs[name] || '';

  if (name === 'book' && !document.getElementById('map-shell').classList.contains('hidden')) {
    setTimeout(() => routeMap?.invalidateSize(), 50);
  }
  if (name === 'history') loadRideHistory();
}

function initRouteMap() {
  if (routeMap || !window.L) return;
  routeMap = L.map('route-map', { zoomControl: true, scrollWheelZoom: true }).setView([20.5937, 78.9629], 5);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(routeMap);
}

function showMap() {
  document.getElementById('map-shell').classList.remove('hidden');
  initRouteMap();
  setTimeout(() => routeMap?.invalidateSize(), 50);
}

function resetPreview() {
  activeRoutePlan = null;
  activePlanner = null;
  hideRouteSummary();
  hideAiRecommendation();
  hideVehicleSelection();
  clearRoute();
}

function useCurrentLocation() {
  const alert = document.getElementById('book-alert');
  const btn = document.getElementById('current-location-btn');

  if (!navigator.geolocation) {
    showAlert(alert, 'error', 'Geolocation is not supported by this browser.');
    return;
  }

  btn.disabled = true;
  hideAlert(alert);

  navigator.geolocation.getCurrentPosition(
      position => {
        const { latitude, longitude } = position.coords;
        currentPickupCoordinate = { latitude, longitude };
        document.getElementById('pickup').value = 'Current location';
        resetPreview();
        showMap();
        showCurrentLocation(latitude, longitude);
        showAlert(alert, 'success', 'Current location selected as pickup.');
        btn.disabled = false;
      },
      error => {
        currentPickupCoordinate = null;
        btn.disabled = false;
        const message = error.code === error.PERMISSION_DENIED
            ? 'Location permission denied. Enter your pickup address manually.'
            : 'Could not detect your location. Enter your pickup address manually.';
        showAlert(alert, 'error', message);
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
  );
}

async function previewRoute() {
  const pickup = document.getElementById('pickup').value.trim();
  const drop = document.getElementById('drop').value.trim();
  const alert = document.getElementById('book-alert');

  if ((!pickup && !currentPickupCoordinate) || !drop) {
    showAlert(alert, 'error', 'Enter pickup and destination before previewing.');
    return;
  }

  setRouteLoading(true);
  hideAlert(alert);

  try {
    const routePlan = await API.planRoute({
      pickupLocation: pickup,
      dropLocation: drop,
      pickupCoordinate: currentPickupCoordinate,
    });
    activeRoutePlan = routePlan;
    showMap();
    renderRoute(routePlan);
    showRouteSummary(routePlan);
    showVehicleSelection(routePlan);

    // Automatically trigger upfront AI recommendation before any vehicle is selected
    triggerUPfrontPlanner(routePlan);
  } catch (err) {
    activeRoutePlan = null;
    activePlanner = null;
    clearRoute();
    hideRouteSummary();
    hideAiRecommendation();
    hideVehicleSelection();
    showAlert(alert, 'error', err.message || 'Route calculation failed.');
  } finally {
    setRouteLoading(false);
  }
}

/**
 * Fires the AI planner using the backend-recommended vehicle (or Sedan as fallback)
 * so the user gets guidance BEFORE selecting a vehicle card.
 */
function triggerUPfrontPlanner(routePlan) {
  const passengers = getPassengerCount();
  const luggage = getLuggageCount();
  const options = getVehicleOptions(routePlan);

  // Pick the smallest suitable vehicle as the "selected" vehicle for the upfront call
  const recommendationOrder = ['Sedan', 'Premium', 'SUV'];
  const upfrontVehicle = recommendationOrder
      .map(type => options.find(o => o.type === type))
      .find(o => o && passengers <= o.seatingCapacity && luggage <= o.luggageCapacity);

  // If nothing fits (e.g. 10 passengers), just use Sedan so Gemini can still warn them
  const vehicleForCall = upfrontVehicle?.type || 'Sedan';

  // Don't change the selected card highlight — only update the planner panel
  loadVehicleRecommendation(routePlan, vehicleForCall, /* upfront= */ true);
}

function showCurrentLocation(latitude, longitude) {
  if (!routeMap) return;
  if (currentLocationMarker) currentLocationMarker.remove();
  currentLocationMarker = L.marker([latitude, longitude]).addTo(routeMap).bindPopup('Your current location');
  routeMap.setView([latitude, longitude], 14);
}

function renderRoute(routePlan) {
  if (!routeMap || !routePlan?.routeGeoJson) return;
  clearRoute();

  routeLayer = L.geoJSON(routePlan.routeGeoJson, {
    style: { color: '#2563eb', weight: 5, opacity: 0.95 },
  }).addTo(routeMap);

  pickupMarker = L.marker([routePlan.pickup.latitude, routePlan.pickup.longitude])
      .addTo(routeMap)
      .bindPopup(`Pickup: ${escapeHtml(routePlan.pickup.label)}`);
  dropMarker = L.marker([routePlan.drop.latitude, routePlan.drop.longitude])
      .addTo(routeMap)
      .bindPopup(`Destination: ${escapeHtml(routePlan.drop.label)}`);

  const bounds = routeLayer.getBounds();
  if (bounds.isValid()) routeMap.fitBounds(bounds, { padding: [28, 28] });
}

function clearRoute() {
  [routeLayer, pickupMarker, dropMarker].forEach(layer => layer?.remove());
  routeLayer = null;
  pickupMarker = null;
  dropMarker = null;
}

function showRouteSummary(routePlan) {
  document.getElementById('route-distance').textContent = `${routePlan.distanceKilometers.toFixed(2)} km`;
  document.getElementById('route-eta').textContent = formatEta(routePlan.durationMinutes);
  document.getElementById('sedan-fare').textContent = formatMoney(routePlan.sedanFare);
  document.getElementById('suv-fare').textContent = formatMoney(routePlan.suvFare);
  document.getElementById('premium-fare').textContent = formatMoney(routePlan.premiumFare);
  document.getElementById('route-summary').classList.remove('hidden');
}

function hideRouteSummary() {
  document.getElementById('route-summary').classList.add('hidden');
  document.getElementById('route-distance').textContent = '-';
  document.getElementById('route-eta').textContent = '-';
  document.getElementById('sedan-fare').textContent = '-';
  document.getElementById('suv-fare').textContent = '-';
  document.getElementById('premium-fare').textContent = '-';
}

/**
 * @param {object} routePlan
 * @param {string} vehicleType
 * @param {boolean} upfront - if true, don't change the selected card state
 */
async function loadVehicleRecommendation(routePlan, vehicleType, upfront = false) {
  const requestId = ++plannerRequestId;
  activePlanner = null;
  showAiLoading(upfront);

  try {
    const recommendation = await API.getRidePlan({
      pickupLocation: document.getElementById('pickup').value.trim(),
      destination: document.getElementById('drop').value.trim(),
      distanceKm: routePlan.distanceKilometers,
      selectedVehicle: vehicleType,
      passengerCount: getPassengerCount(),
      luggageCount: getLuggageCount(),
      scheduledPickupAt: getScheduledPickupAt() || null,
    });

    if (requestId !== plannerRequestId) return; // stale response

    // For upfront call: only update if no card has been selected yet
    const currentSelected = document.getElementById('vehicle-type').value;
    if (upfront && currentSelected) return;

    // For vehicle-card call: only update if the vehicle still matches
    if (!upfront && currentSelected !== vehicleType) return;

    activePlanner = recommendation;
    showAiRecommendation(recommendation, upfront);
  } catch (err) {
    if (requestId !== plannerRequestId) return;
    activePlanner = null;
    showAiUnavailable(err.message || 'Recommendation is unavailable.');
  }
}

function showAiLoading(upfront = false) {
  const section = document.getElementById('ai-recommendation');
  section.classList.remove('hidden');
  section.classList.add('loading');
  document.getElementById('ai-status').textContent = upfront ? 'Analysing trip...' : 'Generating...';
  document.getElementById('planner-facts').innerHTML = '';
  document.getElementById('recommendation-summary').textContent = upfront
      ? 'Getting AI recommendation for your trip...'
      : 'Building guidance from backend-calculated trip details...';
  document.getElementById('capacity-warning').classList.add('hidden');
}

function showAiRecommendation(recommendation, upfront = false) {
  const section = document.getElementById('ai-recommendation');
  section.classList.remove('hidden', 'loading', 'unavailable');

  if (upfront) {
    // Upfront view: show only the AI guidance text, no per-vehicle facts grid
    document.getElementById('ai-status').textContent = recommendation.aiGenerated ? 'AI Recommendation' : 'Recommendation';
    document.getElementById('planner-facts').innerHTML = '';
    document.getElementById('recommendation-summary').textContent = recommendation.plannerGuidance || '';
  } else {
    // Post-selection view: show full facts grid + guidance
    document.getElementById('ai-status').textContent = recommendation.aiGenerated ? 'AI guidance ready' : 'Plan ready';
    document.getElementById('planner-facts').innerHTML = `
      <div><span>Selected</span><strong>${escapeHtml(recommendation.selectedVehicle)}</strong></div>
      <div><span>ETA</span><strong>${formatEta(recommendation.etaMinutes)}</strong></div>
      <div><span>Recommended</span><strong>${escapeHtml(recommendation.recommendedVehicle || 'No single vehicle')}</strong></div>
      <div><span>Capacity</span><strong>${recommendation.seatingCapacity} seats · ${recommendation.luggageCapacity} bags</strong></div>
      <div><span>Fare</span><strong>${formatMoney(recommendation.fare)}</strong></div>
      <div><span>Est. Arrival</span><strong>${formatPlannerTime(recommendation.estimatedArrivalAt)}</strong></div>`;
    document.getElementById('recommendation-summary').textContent = recommendation.plannerGuidance || '';
  }

  const warning = document.getElementById('capacity-warning');
  warning.textContent = recommendation.capacityWarning || '';
  warning.classList.toggle('hidden', recommendation.suitable);
}

function showAiUnavailable(message) {
  const section = document.getElementById('ai-recommendation');
  section.classList.remove('hidden', 'loading');
  section.classList.add('unavailable');
  document.getElementById('ai-status').textContent = 'Unavailable';
  document.getElementById('planner-facts').innerHTML = '';
  document.getElementById('recommendation-summary').textContent = `${message} You can still choose a suitable vehicle.`;
}

function hideAiRecommendation() {
  const section = document.getElementById('ai-recommendation');
  section.classList.add('hidden');
  section.classList.remove('loading', 'unavailable');
  document.getElementById('ai-status').textContent = '';
}

function showVehicleSelection(routePlan) {
  document.getElementById('vehicle-selection').classList.remove('hidden');
  const passengers = getPassengerCount();
  const luggage = getLuggageCount();
  const options = getVehicleOptions(routePlan);
  const recommendationOrder = ['Sedan', 'Premium', 'SUV'];
  const recommended = recommendationOrder
      .map(type => options.find(option => option.type === type))
      .find(option => option && passengers <= option.seatingCapacity && luggage <= option.luggageCapacity);

  options.forEach(option => {
    const slug = option.type.toLowerCase();
    const suitable = passengers <= option.seatingCapacity && luggage <= option.luggageCapacity;
    document.getElementById(`vehicle-card-${slug}-fare`).textContent = formatMoney(option.fare);
    document.getElementById(`vehicle-card-${slug}-details`).textContent =
        `${option.seatingCapacity} passengers · ${option.luggageCapacity} bags · ${formatEta(option.etaMinutes)}`;
    document.getElementById(`vehicle-card-${slug}-suitability`).textContent = suitable ? '' : 'Does not fit this trip';
    const card = document.querySelector(`[data-vehicle="${option.type}"]`);
    card.classList.toggle('unsuitable', !suitable);
    card.classList.toggle('recommended', recommended?.type === option.type);
  });
}

function hideVehicleSelection() {
  document.getElementById('vehicle-selection').classList.add('hidden');
  document.getElementById('vehicle-type').value = '';
  document.querySelectorAll('.vehicle-card').forEach(card => card.classList.remove('selected', 'recommended', 'unsuitable'));
}

async function selectVehicle(vehicleType) {
  document.getElementById('vehicle-type').value = vehicleType;
  document.querySelectorAll('.vehicle-card').forEach(card => {
    card.classList.toggle('selected', card.dataset.vehicle === vehicleType);
  });
  // After selecting a card, load the full per-vehicle planner (upfront=false)
  await loadVehicleRecommendation(activeRoutePlan, vehicleType, false);
}

function getVehicleOptions(routePlan) {
  if (routePlan.vehicleOptions?.length) return routePlan.vehicleOptions;
  return [
    { type: 'Sedan', seatingCapacity: 4, luggageCapacity: 2, etaMinutes: routePlan.durationMinutes, fare: routePlan.sedanFare },
    { type: 'SUV', seatingCapacity: 6, luggageCapacity: 4, etaMinutes: routePlan.durationMinutes, fare: routePlan.suvFare },
    { type: 'Premium', seatingCapacity: 4, luggageCapacity: 3, etaMinutes: routePlan.durationMinutes, fare: routePlan.premiumFare },
  ];
}

function setRouteLoading(loading) {
  const btn = document.getElementById('preview-route-btn');
  btn.disabled = loading;
  btn.innerHTML = loading
      ? 'Calculating...'
      : `<svg width="16" height="16" viewBox="0 0 18 18" fill="none"><path d="M3 14c3-8 9 2 12-6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><circle cx="3" cy="14" r="1.5" fill="currentColor"/><circle cx="15" cy="8" r="1.5" fill="currentColor"/></svg>Preview Route`;
}

async function bookRide() {
  const pickup = document.getElementById('pickup').value.trim();
  const drop = document.getElementById('drop').value.trim();
  const vehicleType = document.getElementById('vehicle-type').value;
  const alert = document.getElementById('book-alert');
  const scheduledPickupAt = getScheduledPickupAt();

  if (!pickup || !drop || !vehicleType) {
    showAlert(alert, 'error', 'Please enter pickup, destination, and select a vehicle.');
    return;
  }
  if (!activeRoutePlan) {
    showAlert(alert, 'error', 'Preview the route before booking so distance, ETA, and fare can be saved.');
    return;
  }
  if (scheduledPickupAt === false) {
    showAlert(alert, 'error', 'Select both pickup date and time, or leave both empty.');
    return;
  }
  if (activePlanner && !activePlanner.suitable) {
    showAlert(alert, 'error', activePlanner.capacityWarning);
    return;
  }
  const selectedOption = getVehicleOptions(activeRoutePlan).find(option => option.type === vehicleType);
  if (getPassengerCount() > selectedOption.seatingCapacity || getLuggageCount() > selectedOption.luggageCapacity) {
    showAlert(alert, 'error', `${vehicleType} supports up to ${selectedOption.seatingCapacity} passengers and ${selectedOption.luggageCapacity} bags. Choose a suitable vehicle.`);
    return;
  }

  setBtnLoading('book-btn', true);
  hideAlert(alert);

  try {
    const ride = await API.createRide(pickup, drop, buildRideRouteDetails(vehicleType, scheduledPickupAt));
    showAlert(alert, 'success', 'Ride requested. A matching driver will see it now.');
    resetBookingForm();
    showRecentRide(ride);
  } catch (err) {
    showAlert(alert, 'error', err.message);
  } finally {
    setBtnLoading('book-btn', false);
  }
}

/**
 * Returns the user's chosen pickup datetime as an ISO string,
 * null if both fields are empty, or false if only one is filled.
 */
function getScheduledPickupAt() {
  const date = document.getElementById('schedule-date').value;
  const time = document.getElementById('schedule-time').value;
  if (!date && !time) return null;
  if (!date || !time) return false;
  return `${date}T${time}`;
}

function buildRideRouteDetails(vehicleType, scheduledPickupAt) {
  const option = getVehicleOptions(activeRoutePlan).find(item => item.type === vehicleType);
  return {
    pickupLatitude: activeRoutePlan.pickup.latitude,
    pickupLongitude: activeRoutePlan.pickup.longitude,
    dropLatitude: activeRoutePlan.drop.latitude,
    dropLongitude: activeRoutePlan.drop.longitude,
    distanceMeters: activeRoutePlan.distanceMeters,
    durationSeconds: option.etaMinutes * 60,
    vehicleType,
    scheduledAt: scheduledPickupAt || null,
    passengerCount: getPassengerCount(),
    luggageCount: getLuggageCount(),
  };
}

function resetBookingForm() {
  ['pickup', 'drop', 'vehicle-type', 'schedule-date', 'schedule-time'].forEach(id => {
    document.getElementById(id).value = '';
  });
  document.getElementById('passenger-count').value = '1';
  document.getElementById('luggage-count').value = '0';
  currentPickupCoordinate = null;
  activeRoutePlan = null;
  clearRoute();
  hideRouteSummary();
  hideAiRecommendation();
  hideVehicleSelection();
  document.getElementById('map-shell').classList.add('hidden');
}

function showRecentRide(ride) {
  const container = document.getElementById('recent-ride');
  const card = document.getElementById('recent-ride-card');
  container.classList.remove('hidden');
  card.innerHTML = rideCardHTML(ride, true);
  attachRideActionHandlers(card);
}

async function loadRideHistory() {
  const list = document.getElementById('rides-list');
  const alert = document.getElementById('history-alert');
  list.innerHTML = `<div class="loading-state"><div class="spinner"></div><p>Loading your rides...</p></div>`;
  hideAlert(alert);

  try {
    const rides = await API.getUserRides();
    renderRideList(list, rides, true);
  } catch (err) {
    list.innerHTML = '';
    showAlert(alert, 'error', err.message);
  }
}

function renderRideList(container, rides, withActions) {
  if (!rides || rides.length === 0) {
    container.innerHTML = `<div class="empty-state"><p>No rides yet</p><span>Book your first ride to get started</span></div>`;
    return;
  }
  const sorted = [...rides].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
  container.innerHTML = sorted.map(r => rideCardHTML(r, withActions)).join('');
  attachRideActionHandlers(container);
}

function rideCardHTML(ride, withActions) {
  const date = formatDate(ride.createdAt);
  const scheduled = ride.scheduledAt ? `<span>Pickup: ${formatDate(ride.scheduledAt)}</span>` : '';
  const canComplete = ride.status === 'ACCEPTED' && withActions;
  const canCancel = ['REQUESTED', 'ACCEPTED'].includes(ride.status) && withActions;
  const canPay = ride.status === 'COMPLETED' && ride.paymentStatus !== 'PAID' && withActions;
  const canRate = ['COMPLETED', 'PAID'].includes(ride.status) && !ride.driverRating && ride.driverId && withActions;
  const routeMeta = ride.distanceMeters && ride.durationSeconds
      ? `<span>${(ride.distanceMeters / 1000).toFixed(2)} km</span><span>${formatEta(ride.durationSeconds / 60)}</span>`
      : '';
  const fare = ride.fare ? `<span>Fare: ${formatMoney(ride.fare)}</span>` : '';
  const capacity = ride.passengerCount
      ? `<span>${ride.passengerCount} passenger${ride.passengerCount === 1 ? '' : 's'} · ${ride.luggageCount || 0} bag${ride.luggageCount === 1 ? '' : 's'}</span>`
      : '';
  const driver = ride.driverName ? `
    <div class="driver-detail-grid">
      <span>Driver: ${escapeHtml(ride.driverName)}</span>
      <span>Phone: ${escapeHtml(ride.driverPhoneNumber || '-')}</span>
      <span>Vehicle: ${escapeHtml(ride.driverVehicleType || ride.vehicleType || '-')} ${escapeHtml(ride.driverVehicleName || '')}</span>
      <span>Number: ${escapeHtml(ride.driverVehicleNumber || '-')}</span>
      <span>Rating: ${Number(ride.driverAverageRating || 0).toFixed(1)} (${ride.driverRatingsCount || 0})</span>
    </div>` : '';
  const rating = ride.driverRating ? `<span>Your rating: ${'★'.repeat(ride.driverRating)}</span>` : '';

  return `
    <div class="ride-card" data-id="${ride.id}">
      <div class="ride-icon"><span>${escapeHtml(ride.vehicleType || 'Ride').charAt(0)}</span></div>
      <div class="ride-info">
        <div class="ride-route">
          <span>${escapeHtml(ride.pickupLocation)}</span><span class="arrow">-></span><span>${escapeHtml(ride.dropLocation)}</span>
        </div>
        <div class="ride-meta">
          <span class="status-badge status-${ride.status}">${ride.status}</span>
          <span>${date}</span>
          <span>${escapeHtml(ride.vehicleType || '-')}</span>
          ${routeMeta}${fare}${capacity}${scheduled}${rating}
        </div>
        ${driver}
      </div>
      ${withActions ? rideActionsHTML(ride, { canComplete, canCancel, canPay, canRate }) : ''}
    </div>`;
}

function rideActionsHTML(ride, flags) {
  const buttons = [];
  if (flags.canCancel) buttons.push(`<button class="btn-action btn-cancel" data-action="cancel" data-ride-id="${ride.id}">Cancel Ride</button>`);
  if (flags.canComplete) buttons.push(`<button class="btn-action btn-complete" data-action="complete" data-ride-id="${ride.id}">Complete</button>`);
  if (flags.canPay) buttons.push(`<button class="btn-action btn-pay" data-action="pay" data-ride-id="${ride.id}">Pay</button>`);
  if (flags.canRate) buttons.push(`<button class="btn-action btn-rate" data-action="rate" data-ride-id="${ride.id}">Rate Driver</button>`);
  return buttons.length ? `<div class="ride-actions">${buttons.join('')}</div>` : '';
}

function attachRideActionHandlers(container) {
  container.querySelectorAll('[data-action]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const rideId = btn.dataset.rideId;
      const action = btn.dataset.action;
      if (action === 'cancel') return cancelRide(rideId);
      if (action === 'complete') return completeRide(rideId, btn);
      if (action === 'pay') return payForRide(rideId, btn);
      if (action === 'rate') return rateRide(rideId);
    });
  });
}

async function cancelRide(rideId) {
  if (!confirm('Cancel this ride?')) return;
  try {
    await API.cancelRide(rideId);
    await refreshVisibleRides();
  } catch (err) {
    alert('Error: ' + err.message);
  }
}

async function completeRide(rideId, btn) {
  btn.disabled = true;
  btn.textContent = 'Completing...';
  try {
    await API.completeRide(rideId);
    await refreshVisibleRides();
  } catch (err) {
    btn.disabled = false;
    btn.textContent = 'Complete';
    alert('Error: ' + err.message);
  }
}

async function payForRide(rideId, btn) {
  if (!window.Razorpay) {
    alert('Razorpay Checkout script is not loaded.');
    return;
  }
  btn.disabled = true;
  btn.textContent = 'Opening...';
  try {
    const order = await API.createPaymentOrder(rideId);
    const options = {
      key: order.keyId,
      amount: order.amountPaise,
      currency: order.currency,
      name: 'RideGo',
      description: 'Ride payment',
      order_id: order.orderId,
      handler: async response => {
        await API.verifyPayment(rideId, {
          razorpayPaymentId: response.razorpay_payment_id,
          razorpayOrderId: response.razorpay_order_id,
          razorpaySignature: response.razorpay_signature,
        });
        await refreshVisibleRides();
      },
      modal: { ondismiss: () => { btn.disabled = false; btn.textContent = 'Pay'; } },
      theme: { color: '#2563eb' },
    };
    new Razorpay(options).open();
  } catch (err) {
    btn.disabled = false;
    btn.textContent = 'Pay';
    alert('Payment error: ' + err.message);
  }
}

async function rateRide(rideId) {
  const rating = Number(prompt('Rate driver from 1 to 5 stars'));
  if (!Number.isInteger(rating) || rating < 1 || rating > 5) {
    alert('Rating must be a whole number from 1 to 5.');
    return;
  }
  const feedback = prompt('Optional feedback') || '';
  try {
    await API.rateDriver(rideId, rating, feedback);
    await refreshVisibleRides();
  } catch (err) {
    alert('Rating error: ' + err.message);
  }
}

async function refreshVisibleRides() {
  const active = document.querySelector('.nav-item.active')?.dataset.panel;
  if (active === 'history') {
    await loadRideHistory();
    return;
  }
  const rides = await API.getUserRides();
  const latest = [...rides].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))[0];
  if (latest) showRecentRide(latest);
}

function formatEta(minutes) {
  const rounded = Math.max(1, Math.round(minutes));
  if (rounded < 60) return `${rounded} min`;
  const hours = Math.floor(rounded / 60);
  const mins = rounded % 60;
  return mins ? `${hours} hr ${mins} min` : `${hours} hr`;
}
function formatMoney(value) { return `₹${Number(value || 0).toFixed(2)}`; }
function formatDate(value) {
  return value ? new Date(value).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '-';
}
function formatPlannerTime(value) {
  return value ? new Date(value).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : 'Not scheduled';
}
function getPassengerCount() {
  return Math.max(1, Number.parseInt(document.getElementById('passenger-count').value, 10) || 1);
}
function getLuggageCount() {
  return Math.max(0, Number.parseInt(document.getElementById('luggage-count').value, 10) || 0);
}
function showAlert(el, type, msg) { el.className = `alert ${type}`; el.textContent = msg; }
function hideAlert(el) { el.className = 'alert'; el.textContent = ''; }
function setBtnLoading(btnId, loading) {
  const btn = document.getElementById(btnId);
  const text = btn.querySelector('.btn-text');
  const loader = btn.querySelector('.btn-loader');
  btn.disabled = loading;
  text?.classList.toggle('hidden', loading);
  loader?.classList.toggle('hidden', !loading);
}
function escapeHtml(str) {
  return String(str ?? '')
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}