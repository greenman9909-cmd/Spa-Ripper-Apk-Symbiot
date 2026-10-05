'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('docs/return.js', 'utf8');
function fixture(hash, search = '', readyState = 'complete') {
  const nodes = {heading: {}, message: {}};
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
  assert.equal(location.hash, ''); assert.equal(location.search, '');
  return {nodes, events, location};
}
let state = fixture('#access_token=synthetic&refresh_token=synthetic&type=signup');
assert.equal(state.nodes.heading.textContent, 'Return to the app');
assert.doesNotMatch(state.nodes.message.textContent, /synthetic/);
state.location.hash = '#error_code=synthetic-expired';
state.events.hashchange();
assert.equal(state.location.hash, '');
assert.equal(state.nodes.heading.textContent, 'This link could not be completed');
state.location.hash = '#access_token=synthetic&type=signup';
state.events.hashchange();
assert.equal(state.nodes.heading.textContent, 'Return to the app');
state = fixture('', '?error=synthetic&error_description=%3Cscript%3E');
assert.equal(state.nodes.heading.textContent, 'This link could not be completed');
assert.doesNotMatch(state.nodes.message.textContent, /script|synthetic/);
state = fixture('#access_token=synthetic&type=recovery');
assert.equal(state.nodes.heading.textContent, 'Password recovery is not ready');
state = fixture('#error=synthetic', '', 'loading');
assert.equal(state.nodes.heading.textContent, undefined);
state.events.DOMContentLoaded();
assert.equal(state.nodes.heading.textContent, 'This link could not be completed');
console.log('Confirmation return: six privacy/state cases passed');
