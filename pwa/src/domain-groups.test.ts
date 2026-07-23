import { describe, expect, it } from 'vitest';
import type { Group, Member } from './models';
import {
  canChangeRoles,
  canInviteGroup,
  canManageGroup,
  canRemoveMember,
  groupErrorMessage,
  isGroupOwner,
  parseHm,
  roleToggle,
  sessionValid,
  sessionWindowLabel,
} from './domain';

const group = (over: Partial<Group> = {}): Group => ({
  id: 'g1', name: 'Old Monk Badminton', avatarColor: '#9ADE28', sportCode: 'badminton_doubles',
  memberCount: 12, matchCount: 94, myRole: 'owner', ...over,
});

const member = (over: Partial<Member> = {}): Member => ({
  userId: 'u', displayName: 'Sugaram', avatarColor: '#888', role: 'member', ...over,
});

describe('group permission helpers', () => {
  it('owner and admin can manage/invite; member cannot', () => {
    expect(canManageGroup(group({ myRole: 'owner' }))).toBe(true);
    expect(canManageGroup(group({ myRole: 'admin' }))).toBe(true);
    expect(canManageGroup(group({ myRole: 'member' }))).toBe(false);
    expect(canInviteGroup(group({ myRole: 'admin' }))).toBe(true);
    expect(canInviteGroup(group({ myRole: 'member' }))).toBe(false);
  });

  it('only the owner may change roles', () => {
    expect(isGroupOwner(group({ myRole: 'owner' }))).toBe(true);
    expect(canChangeRoles(group({ myRole: 'owner' }))).toBe(true);
    expect(canChangeRoles(group({ myRole: 'admin' }))).toBe(false);
  });
});

describe('canRemoveMember', () => {
  const owner = group({ myRole: 'owner' });
  const admin = group({ myRole: 'admin' });

  it('never removes the owner, a guest, or yourself', () => {
    expect(canRemoveMember(owner, member({ userId: 'x', role: 'owner' }), 'me')).toBe(false);
    expect(canRemoveMember(owner, member({ userId: 'x', role: 'guest' }), 'me')).toBe(false);
    expect(canRemoveMember(owner, member({ userId: 'me' }), 'me')).toBe(false);
  });

  it('an owner can remove admins and members; an admin only members', () => {
    expect(canRemoveMember(owner, member({ userId: 'a', role: 'admin' }), 'me')).toBe(true);
    expect(canRemoveMember(owner, member({ userId: 'b', role: 'member' }), 'me')).toBe(true);
    expect(canRemoveMember(admin, member({ userId: 'a', role: 'admin' }), 'me')).toBe(false);
    expect(canRemoveMember(admin, member({ userId: 'b', role: 'member' }), 'me')).toBe(true);
  });
});

describe('roleToggle', () => {
  it('promotes a member and demotes an admin', () => {
    expect(roleToggle(member({ role: 'member' }))).toEqual({ label: 'Make admin', next: 'admin' });
    expect(roleToggle(member({ role: 'admin' }))).toEqual({ label: 'Demote', next: 'member' });
  });
});

describe('session window', () => {
  it('labels a set window and null when unset', () => {
    expect(sessionWindowLabel(group({ sessionStart: '06:00', sessionEnd: '08:00' }))).toBe('06:00 – 08:00');
    expect(sessionWindowLabel(group({ sessionStart: null, sessionEnd: null }))).toBeNull();
    expect(sessionWindowLabel(group({ sessionStart: '06:00' }))).toBeNull();
  });

  it('parses HH:mm and validates start < end', () => {
    expect(parseHm('06:30')).toBe(390);
    expect(parseHm(null)).toBeNull();
    expect(parseHm('nonsense')).toBeNull();
    expect(sessionValid('19:00', '21:00')).toBe(true);
    expect(sessionValid('21:00', '19:00')).toBe(false);
    expect(sessionValid('20:00', '20:00')).toBe(false);
  });
});

describe('groupErrorMessage', () => {
  it('maps stable codes and falls back otherwise', () => {
    expect(groupErrorMessage('GROUP_INVITE_INVALID', 'x')).toMatch(/wrong or expired/);
    expect(groupErrorMessage('GROUP_MEMBER_EXISTS', 'x')).toMatch(/already a member/);
    expect(groupErrorMessage('GROUP_SESSION_INVALID', 'x')).toMatch(/before end/);
    expect(groupErrorMessage(undefined, 'fallback here')).toBe('fallback here');
    expect(groupErrorMessage('SOMETHING_ELSE', 'fallback here')).toBe('fallback here');
  });
});
