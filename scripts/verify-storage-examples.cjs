// Development verification only. The mobile apps display fixed public examples.
// Run with Node.js >= 24.7.0 (built-in Argon2; no package dependency).
const assert = require('node:assert/strict');
const { argon2Sync } = require('node:crypto');
const { readFileSync } = require('node:fs');
const path = require('node:path');

assert.equal(typeof argon2Sync, 'function', 'Node.js >= 24.7.0 is required');
// RFC 9106, section 5.3: validate the Argon2id implementation before the examples.
const vector = argon2Sync('argon2id', {
  message: Buffer.alloc(32, 1), nonce: Buffer.alloc(16, 2),
  secret: Buffer.alloc(8, 3), associatedData: Buffer.alloc(12, 4),
  memory: 32, passes: 3, parallelism: 4, tagLength: 32,
});
assert.equal(vector.toString('hex'), '0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659');

const source = readFileSync(path.join(__dirname, '../shared/src/commonMain/kotlin/jp/passgen/core/StorageLessonExamples.kt'), 'utf8');
const examples = Object.fromEntries([...source.matchAll(/const val (\w+) = "([^"]*)"/g)]
  .map(([, name, value]) => [name, value.replace(/\\\$/g, '$')]));
assert.equal(new Set([examples.PASSWORD, examples.PASSWORD_B, examples.PASSWORD_C]).size, 3);
for (const name of ['COMMON_SALT_HASH', 'INDIVIDUAL_SALT_HASH_A', 'INDIVIDUAL_SALT_HASH_B']) {
  const encoded = examples[name];
  assert(!encoded.includes('…') && !encoded.includes('...') && !/\s/.test(encoded));
  const fields = encoded.split('$');
  assert.deepEqual(fields.slice(0, 4), ['', 'argon2id', 'v=19', 'm=19456,t=2,p=1']);
  assert.equal(fields.length, 6);
  assert.match(fields[4], /^[A-Za-z0-9+/]{22}$/);
  assert.match(fields[5], /^[A-Za-z0-9+/]{43}$/);
  const salt = Buffer.from(fields[4], 'base64');
  const stored = Buffer.from(fields[5], 'base64');
  assert.equal(salt.length, 16); assert.equal(stored.length, 32);
  const calculate = message => argon2Sync('argon2id', {
    message, nonce: salt, memory: 19456, passes: 2, parallelism: 1, tagLength: 32,
  });
  assert.deepEqual(calculate(examples.PASSWORD), stored);
  assert.notDeepEqual(calculate(examples.PASSWORD_B), stored);
}
assert.equal(new Set(['COMMON_SALT_HASH', 'INDIVIDUAL_SALT_HASH_A', 'INDIVIDUAL_SALT_HASH_B'].map(key => examples[key])).size, 3);
console.log('Verified RFC 9106 vector and all 3 full Argon2id teaching examples.');
