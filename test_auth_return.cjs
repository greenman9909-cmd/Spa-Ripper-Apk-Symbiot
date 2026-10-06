'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('docs/return.js', 'utf8');
function fixture(hash, search = '', readyState = 'complete') {
  const nodes = {heading: {}, message: {}, 'auth-return': {}, site: {}};
  const events = {};
  const location = {hash, search, pathname: '/Spa-Ripper-Apk-Symbiot/'};
  const window = {
    location,
    history: {replaceState(_state, _title, path) {
      assert.equal(path, location.pathname);
      location.hash = ''; location.search = '';
    }},
    addEventListener(name, handler) {events[name] = handler;}
  };
  const document = {readyState, getElementById(id) {return nodes[id];},
    addEventListener(name, handler) {events[name] = handler;}};
  vm.runInNewContext(source, {window, document, URLSearchParams});
  if (window.authReturnActive || !hash) { assert.equal(location.hash, ''); assert.equal(location.search, ''); }
  return {nodes, events, location, window};
}
let state = fixture('#access_token=synthetic&refresh_token=synthetic&type=signup');
assert.equal(state.nodes.heading.textContent, 'Return to the app');
assert.doesNotMatch(state.nodes.message.textContent, /synthetic/);
state.location.hash = '#error_code=synthetic-expired';
state.events.hashchange();
assert.equal(state.location.hash, '');
assert.equal(state.nodes.heading.textContent, 'This link is no longer valid');
assert.match(state.nodes.message.textContent, /First return to the Android app and try signing in/);
assert.match(state.nodes.message.textContent, /cannot check your account status/);
state.location.hash = '#access_token=synthetic&type=signup';
state.events.hashchange();
assert.equal(state.nodes.heading.textContent, 'Return to the app');
state = fixture('', '?error=synthetic&error_description=%3Cscript%3E');
assert.equal(state.nodes.heading.textContent, 'This link is no longer valid');
assert.doesNotMatch(state.nodes.message.textContent, /script|synthetic/);
state = fixture('#access_token=synthetic&type=recovery');
assert.equal(state.nodes.heading.textContent, 'Password recovery is not ready');
state = fixture('#error=synthetic', '', 'loading');
assert.equal(state.nodes.heading.textContent, undefined);
state.events.DOMContentLoaded();
assert.equal(state.nodes.heading.textContent, 'This link is no longer valid');
assert.equal(state.nodes['auth-return'].hidden, false);
assert.equal(state.nodes.site.hidden, true);
state = fixture('#catalogo');
assert.equal(state.location.hash, '#catalogo');
assert.equal(state.window.authReturnActive, false);
assert.equal(state.nodes['auth-return'].hidden, true);
assert.equal(state.nodes.site.hidden, false);
state = fixture('#access_token=synthetic&refresh_token=synthetic&type=signup');
vm.runInNewContext(fs.readFileSync('docs/landing.js', 'utf8'), {
  window: state.window,
  document: {getElementById(){throw Error('Landing touched the callback DOM');}},
  fetch(){throw Error('Catalog requested during authentication callback');},
  localStorage: {getItem(){throw Error('Storage read during authentication callback');}}
});
console.log('Confirmation return: six original cases plus landing/anchor/network isolation passed');
