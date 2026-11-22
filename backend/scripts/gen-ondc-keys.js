// Generate the key material needed to register as an ONDC buyer app.
//   node scripts/gen-ondc-keys.js
// Register the *public* keys with ONDC; put the *private* keys in Render env vars. Never commit them.
const { generateKeys } = require('../src/ondc/crypto');
const k = generateKeys();
console.log(`
== Give these to ONDC when registering (public) ==
signing_public_key     : ${k.signing_public_key}
encryption_public_key  : ${k.encryption_public_key}

== Set these as SECRET env vars on Render (private) ==
ONDC_SIGNING_PRIVATE_KEY     = ${k.signing_private_key}
ONDC_ENCRYPTION_PRIVATE_KEY  = ${k.encryption_private_key}
`);
