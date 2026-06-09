/**
 * Driver Dashboard
 */

(function () {
  const user = API.getUser();
  if (!user || !API.getToken()) {
    window.location.href = 'index.html';
    return;
  }
  if (user.role !== 'ROLE_DRIVER') {
    window.location.href = 'user.html';
    return;
  }
  document.getElementById('sidebar-username').textContent = user.username;
  document.getElementById('user-avatar-sidebar').textContent = user.username.charAt(0).toUpperCase();
})();

document.getElementById('logout-btn').addEventListener('click', () => API.logout());
document.getElementById('refresh-btn').addEventListener('click', () => {
  const btn = document.getElementById('refresh-btn');
  btn.classList.add('spinning');
  loadDashboard().finally(() => btn.classList.remove('spinning'));
});

document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', e => {
    e.preventDefault();
    switchPanel(item.dataset.panel);
  });
});

let cachedStats = null;
loadDashboard();
setInterval(() => loadDashboard(true), 15000);

function switchPanel(name) {
  document.querySelectorAll('.nav-item').forEach(i => i.classList.toggle('active', i.dataset.panel === name));
  document.querySelectorAll('.panel').forEach(p => p.classList.add('hidden'));
  document.getElementById(`panel-${name}`).classList.remove('hidden');

  const titles = { requests: 'Ride Requests', history: 'Ride History' };
  const subs = {
    requests: 'Pending rides matching your vehicle type',
    history: 'Scheduled, completed, cancelled, and paid rides',
  };
  document.getElementById('panel-title').textContent = titles[name] || name;
  document.getElementById('panel-subtitle').textContent = subs[name] || '';

  if (name === 'history') renderDriverHistory();
}

async function loadDashboard(silent = false) {
  const list = document.getElementById('requests-list');
  const alert = document.getElementById('driver-alert');

  if (!silent) list.innerHTML = `<div class="loading-state"><div class="spinner"></div><p>Fetching ride requests...</p></div>`;
  hideAlert(alert);

  try {
    const [rides, stats] = await Promise.all([API.getPendingRides(), API.getDriverStats()]);
    cachedStats = stats;
    updateStats(stats);
    renderRequests(list, rides);
    if (!document.getElementById('panel-history').classList.contains('hidden')) renderDriverHistory();
  } catch (err) {
    list.innerHTML = '';
    showAlert(alert, 'error', err.message);
  }
}

function renderRequests(container, rides) {
  if (!rides || rides.length === 0) {
    container.innerHTML = `<div class="empty-state"><p>No pending rides</p><span>New matching requests will appear here automatically.</span></div>`;
    return;
  }

  container.innerHTML = rides.map(r => requestCardHTML(r, true)).join('');
  attachActionHandlers(container);
}

function renderDriverHistory() {
  const container = document.getElementById('driver-history-list');
  const rides = cachedStats?.rideHistory || [];
  if (!rides.length) {
    container.innerHTML = `<div class="empty-state"><p>No ride history yet</p><span>Accepted rides will appear here.</span></div>`;
    return;
  }
  container.innerHTML = rides.map(r => requestCardHTML(r, false, true)).join('');
  attachActionHandlers(container);
}

function requestCardHTML(ride, withActions, historyActions = false) {
  const date = formatDate(ride.createdAt);
  const scheduled = ride.scheduledAt ? `<span>Scheduled: ${formatDate(ride.scheduledAt)}</span>` : '';
  const routeMeta = ride.distanceMeters && ride.durationSeconds
    ? `<span>${(ride.distanceMeters / 1000).toFixed(2)} km</span><span>${formatEta(ride.durationSeconds / 60)}</span>`
    : '';
  const fare = ride.fare ? `<span>Fare: ${formatMoney(ride.fare)}</span>` : '';
  const payment = ride.paymentStatus ? `<span>Payment: ${escapeHtml(ride.paymentStatus)}</span>` : '';

  return `
    <div class="ride-card" data-id="${ride.id}">
      <div class="ride-icon"><span>${escapeHtml(ride.vehicleType || 'R').charAt(0)}</span></div>
      <div class="ride-info">
        <div class="ride-route">
          <span>${escapeHtml(ride.pickupLocation)}</span><span class="arrow">-></span><span>${escapeHtml(ride.dropLocation)}</span>
        </div>
        <div class="ride-meta">
          <span class="status-badge status-${ride.status}">${ride.status}</span>
          <span>${date}</span>
          <span>${escapeHtml(ride.vehicleType || '-')}</span>
          ${routeMeta}${fare}${scheduled}${payment}
        </div>
      </div>
      ${withActions ? `<div class="ride-actions"><button class="btn-action btn-accept" data-action="accept" data-ride-id="${ride.id}">Accept</button></div>` : ''}
      ${historyActions && ride.status === 'ACCEPTED' ? `<div class="ride-actions"><button class="btn-action btn-complete" data-action="complete" data-ride-id="${ride.id}">Complete</button></div>` : ''}
    </div>`;
}

function attachActionHandlers(container) {
  container.querySelectorAll('[data-action]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const rideId = btn.dataset.rideId;
      const card = container.querySelector(`[data-id="${rideId}"]`);
      const allBtns = card?.querySelectorAll('.btn-action');

      allBtns?.forEach(b => { b.disabled = true; });
      btn.textContent = btn.dataset.action === 'complete' ? 'Completing...' : 'Accepting...';

      try {
        if (btn.dataset.action === 'complete') {
          await API.completeRide(rideId);
        } else {
          await API.acceptRide(rideId);
        }
        await loadDashboard(true);
      } catch (err) {
        allBtns?.forEach(b => { b.disabled = false; });
        btn.textContent = btn.dataset.action === 'complete' ? 'Complete' : 'Accept';
        showAlert(document.getElementById('driver-alert'), 'error', err.message);
      }
    });
  });
}

function updateStats(stats) {
  document.getElementById('stat-pending').textContent = stats.pending ?? 0;
  document.getElementById('stat-accepted').textContent = stats.accepted ?? 0;
  document.getElementById('stat-completed').textContent = stats.completed ?? 0;
  document.getElementById('stat-earnings').textContent = formatMoney(stats.totalEarnings ?? 0);
  document.getElementById('stat-rating').textContent = Number(stats.averageRating ?? 0).toFixed(1);
  document.getElementById('stat-ratings-count').textContent = stats.totalRatings ?? 0;
}

function formatEta(minutes) {
  const rounded = Math.max(1, Math.round(minutes));
  if (rounded < 60) return `${rounded} min`;
  const hours = Math.floor(rounded / 60);
  const mins = rounded % 60;
  return mins ? `${hours} hr ${mins} min` : `${hours} hr`;
}
function formatMoney(value) {
  return `₹${Number(value || 0).toFixed(2)}`;
}
function formatDate(value) {
  return value ? new Date(value).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '-';
}
function showAlert(el, type, msg) {
  el.className = `alert ${type}`;
  el.textContent = msg;
}
function hideAlert(el) {
  el.className = 'alert';
  el.textContent = '';
}
function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
