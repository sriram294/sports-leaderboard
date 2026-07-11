package com.org.playboard.dto.group;

import java.util.List;

/**
 * The group roster. {@code members} are the real players; {@code guests} are
 * the per-group filler identities (role 'guest') — kept separate so they never
 * count as players, but still available to drop into a match on the client.
 */
public record MembersResponse(List<MemberDto> members, List<MemberDto> guests) {}
