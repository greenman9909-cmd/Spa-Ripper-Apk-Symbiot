// Auth fragments stay in this browser tab. Never send, log or persist them.
'use strict';
(() => {
  const fragment = new URLSearchParams(window.location.hash.slice(1));
  const query = new URLSearchParams(window.location.search);
  const failed = fragment.has('error') || fragment.has('error_code') || query.has('error') || query.has('error_code');
  const recovery = fragment.get('type') === 'recovery' || query.get('type') === 'recovery';
  window.history.replaceState(null, '', window.location.pathname);
  document.addEventListener('DOMContentLoaded', () => {
    if (failed) {
      document.getElementById('heading').textContent = 'This link could not be completed';
      document.getElementById('message').textContent = 'The link may be expired or already used. Return to the app and request a new confirmation email.';
    } else if (recovery) {
      document.getElementById('heading').textContent = 'Password recovery is not ready';
      document.getElementById('message').textContent = 'This preview does not yet provide a password reset screen for recovery links. Contact the app administrator.';
    }
  }, {once: true});
})();
