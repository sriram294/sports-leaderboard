import { describe, expect, it } from 'vitest';
import { pushConfigFrom, pushNotificationFrom } from './push-core';

const fullConfig = {
  VITE_FIREBASE_API_KEY: 'k', VITE_FIREBASE_AUTH_DOMAIN: 'p.firebaseapp.com', VITE_FIREBASE_PROJECT_ID: 'p',
  VITE_FIREBASE_MESSAGING_SENDER_ID: '123', VITE_FIREBASE_APP_ID: '1:123:web:abc', VITE_FCM_VAPID_KEY: 'vapid',
};

describe('pushConfigFrom', () => {
  it('builds a config when the essentials are present', () => {
    expect(pushConfigFrom(fullConfig)).toMatchObject({ apiKey: 'k', appId: '1:123:web:abc', vapidKey: 'vapid', messagingSenderId: '123' });
  });

  it('returns null when any essential (apiKey / appId / senderId / vapid) is missing', () => {
    expect(pushConfigFrom({})).toBeNull();
    expect(pushConfigFrom({ ...fullConfig, VITE_FCM_VAPID_KEY: '' })).toBeNull();
    expect(pushConfigFrom({ ...fullConfig, VITE_FIREBASE_APP_ID: undefined })).toBeNull();
    expect(pushConfigFrom({ ...fullConfig, VITE_FIREBASE_MESSAGING_SENDER_ID: '' })).toBeNull();
  });
});

describe('pushNotificationFrom', () => {
  it('prefers the notification block, then data, then the app name', () => {
    expect(pushNotificationFrom({ notification: { title: 'New match', body: 'You won!' } })).toMatchObject({ title: 'New match', body: 'You won!' });
    expect(pushNotificationFrom({ data: { title: 'From data', body: 'b' } })).toMatchObject({ title: 'From data', body: 'b' });
    expect(pushNotificationFrom({})).toMatchObject({ title: 'Playboard', body: '' });
    expect(pushNotificationFrom(null)).toMatchObject({ title: 'Playboard' });
  });

  it('routes a tap to the deep link the payload names', () => {
    expect(pushNotificationFrom({ data: { url: '/stats' } }).data.url).toBe('/stats');
    expect(pushNotificationFrom({ data: { matchId: 'm1' } }).data.url).toBe('/matches');
    expect(pushNotificationFrom({ data: { groupId: 'g1' } }).data.url).toBe('/board');
    expect(pushNotificationFrom({ notification: { title: 'x' } }).data.url).toBe('/');
  });
});
