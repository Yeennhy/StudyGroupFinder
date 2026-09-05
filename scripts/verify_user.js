/**
 * Firebase Account Verification Script
 *
 * Usage:
 * 1. Place your service-account.json in this directory.
 * 2. Run: npm install firebase-admin
 * 3. Run: node verify_user.js <email>
 */

const { initializeApp, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const path = require('path');

const serviceAccountPath = path.join(__dirname, 'service-account.json');
const emailToVerify = process.argv[2];

if (!emailToVerify) {
    console.error('Error: Please provide an email address as an argument.');
    console.log('Usage: node verify_user.js <email>');
    process.exit(1);
}

try {
    const serviceAccount = require(serviceAccountPath);

    initializeApp({
        credential: cert(serviceAccount)
    });

    const auth = getAuth();

    auth.getUserByEmail(emailToVerify)
        .then((userRecord) => {
            return auth.updateUser(userRecord.uid, {
                emailVerified: true
            });
        })
        .then((userRecord) => {
            console.log(`Successfully verified account for: ${userRecord.email}`);
            console.log(`UID: ${userRecord.uid}`);
            console.log(`Email Verified: ${userRecord.emailVerified}`);
            process.exit(0);
        })
        .catch((error) => {
            console.error('Error updating user:', error.message);
            process.exit(1);
        });
} catch (error) {
    console.error(`Error: Could not find or read 'service-account.json' at ${serviceAccountPath}`);
    console.error(`Details: ${error.message}`);
    console.log('Please download your service account key from the Firebase Console.');
    process.exit(1);
}
