/**
 * Auth page — handles login, register, tab switching
 */

// Redirect if already logged in
(function () {
  const user = API.getUser();
  if (user && API.getToken()) {
    redirectByRole(user.role);
  }
})();

function redirectByRole(role) {
  if (role === 'ROLE_DRIVER') {
    window.location.href = 'driver.html';
  } else {
    window.location.href = 'user.html';
  }
}

// ── Tab switching ──────────────────────────────────────────────
const loginForm    = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');

document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

document.querySelectorAll('.auth-switch a').forEach(link => {
  link.addEventListener('click', e => {
    e.preventDefault();
    switchTab(link.dataset.switch);
  });
});

function switchTab(tab) {
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
  loginForm.classList.toggle('hidden', tab !== 'login');
  registerForm.classList.toggle('hidden', tab !== 'register');
  clearAlerts();
}

// ── Role selection (register) ──────────────────────────────────
let selectedRole = 'ROLE_USER';
document.querySelectorAll('.role-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.role-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    selectedRole = btn.dataset.role;
    document.getElementById('driver-fields')?.classList.toggle('hidden', selectedRole !== 'ROLE_DRIVER');
  });
});

// ── Login ──────────────────────────────────────────────────────
document.getElementById('login-btn').addEventListener('click', async () => {
  const username = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;
  const alert    = document.getElementById('login-alert');

  if (!username || !password) {
    showAlert(alert, 'error', 'Please enter username and password.');
    return;
  }

  setLoading('login-btn', true);
  clearAlerts();

  try {
    const data = await API.login(username, password);
    showAlert(alert, 'success', 'Signed in! Redirecting…');
    const user = API.getUser();
    setTimeout(() => redirectByRole(user ? user.role : null), 800);
  } catch (err) {
    showAlert(alert, 'error', err.message);
  } finally {
    setLoading('login-btn', false);
  }
});

// Allow Enter key
document.getElementById('login-password').addEventListener('keydown', e => {
  if (e.key === 'Enter') document.getElementById('login-btn').click();
});

// ── Register ───────────────────────────────────────────────────
document.getElementById('register-btn').addEventListener('click', async () => {
  const username = document.getElementById('reg-username').value.trim();
  const password = document.getElementById('reg-password').value;
  const alert    = document.getElementById('register-alert');
  const driverProfile = selectedRole === 'ROLE_DRIVER' ? {
    phoneNumber: document.getElementById('reg-phone').value.trim(),
    vehicleType: document.getElementById('reg-vehicle-type').value,
    vehicleName: document.getElementById('reg-vehicle-name').value.trim(),
    vehicleNumber: document.getElementById('reg-vehicle-number').value.trim().toUpperCase(),
  } : {};

  if (!username || !password) {
    showAlert(alert, 'error', 'Please fill in all fields.');
    return;
  }
  if (selectedRole === 'ROLE_DRIVER' && (!driverProfile.phoneNumber || !driverProfile.vehicleType || !driverProfile.vehicleName || !driverProfile.vehicleNumber)) {
    showAlert(alert, 'error', 'Driver phone and vehicle details are required.');
    return;
  }
  if (selectedRole === 'ROLE_DRIVER' && !/^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$/.test(driverProfile.vehicleNumber)) {
    showAlert(alert, 'error', 'Vehicle number must match MP09AB1234.');
    return;
  }
  if (password.length < 4) {
    showAlert(alert, 'error', 'Password must be at least 4 characters.');
    return;
  }

  setLoading('register-btn', true);
  clearAlerts();

  try {
    await API.register(username, password, selectedRole, driverProfile);
    showAlert(alert, 'success', 'Account created! Signing you in…');
    // Auto-login after register
    await API.login(username, password);
    const user = API.getUser();
    setTimeout(() => redirectByRole(user ? user.role : null), 800);
  } catch (err) {
    showAlert(alert, 'error', err.message);
  } finally {
    setLoading('register-btn', false);
  }
});

document.getElementById('reg-password').addEventListener('keydown', e => {
  if (e.key === 'Enter') document.getElementById('register-btn').click();
});

// ── Helpers ────────────────────────────────────────────────────
function showAlert(el, type, message) {
  el.className = `alert ${type}`;
  el.textContent = message;
}

function clearAlerts() {
  document.querySelectorAll('.alert').forEach(a => {
    a.className = 'alert';
    a.textContent = '';
  });
}

function setLoading(btnId, loading) {
  const btn    = document.getElementById(btnId);
  const text   = btn.querySelector('.btn-text');
  const loader = btn.querySelector('.btn-loader');
  btn.disabled = loading;
  text.classList.toggle('hidden', loading);
  loader.classList.toggle('hidden', !loading);
}
