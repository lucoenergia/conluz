package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenScope;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Every token operation refuses a purpose of the other scope before it reaches a repository: a user-scoped operation
 * refuses a purpose bound to a membership, and a membership-scoped one a purpose bound only to a user.
 */
class OneTimeTokenPurposeScopeTest {

    private static final String RAW_TOKEN = "a-raw-token-that-must-not-appear-in-the-message";
    private static final UserId USER = UserId.of(UUID.randomUUID());
    private static final UUID COMMUNITY = UUID.randomUUID();

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CreateOneTimeTokenRepository createRepository = mock(CreateOneTimeTokenRepository.class);
    private final GetOneTimeTokenRepository getRepository = mock(GetOneTimeTokenRepository.class);
    private final ConsumeOneTimeTokenRepository consumeRepository = mock(ConsumeOneTimeTokenRepository.class);
    private final RevokeOneTimeTokenRepository revokeRepository = mock(RevokeOneTimeTokenRepository.class);
    private final CreateOneTimeTokenServiceImpl create = new CreateOneTimeTokenServiceImpl(createRepository, clock);
    private final GetOneTimeTokenServiceImpl get = new GetOneTimeTokenServiceImpl(getRepository, clock);
    private final ConsumeOneTimeTokenServiceImpl consume = new ConsumeOneTimeTokenServiceImpl(consumeRepository, clock);
    private final RevokeOneTimeTokenServiceImpl revoke = new RevokeOneTimeTokenServiceImpl(revokeRepository, clock);

    static Stream<OneTimeTokenPurpose> boundToMembership() {
        return Arrays.stream(OneTimeTokenPurpose.values()).filter(purpose -> purpose.scope() == OneTimeTokenScope.MEMBERSHIP);
    }

    static Stream<OneTimeTokenPurpose> boundOnlyToUser() {
        return Arrays.stream(OneTimeTokenPurpose.values()).filter(purpose -> purpose.scope() == OneTimeTokenScope.USER);
    }

    static Stream<Arguments> userScopedOperations() {
        return boundToMembership().flatMap(purpose -> Stream.of(
                Arguments.of("issue", purpose),
                Arguments.of("findOwner", purpose),
                Arguments.of("findOwner of a blank token", purpose),
                Arguments.of("countIssuedSince", purpose),
                Arguments.of("consume", purpose),
                Arguments.of("consume a blank token", purpose)));
    }

    static Stream<Arguments> membershipScopedOperations() {
        return boundOnlyToUser().flatMap(purpose -> Stream.of(
                Arguments.of("issueForMembership", purpose),
                Arguments.of("findMembership", purpose),
                Arguments.of("findMembership of a blank token", purpose),
                Arguments.of("countIssuedForMembershipSince", purpose),
                Arguments.of("findEarliestIssuedForMembershipSince", purpose),
                Arguments.of("findValidExpiryForMembership", purpose),
                Arguments.of("findValidExpiriesInCommunity", purpose),
                Arguments.of("consumeForMembership", purpose),
                Arguments.of("consumeForMembership a blank token", purpose),
                Arguments.of("revokeForMembership", purpose)));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("userScopedOperations")
    @DisplayName("OTT-003 a user-scoped operation refuses a purpose bound to a membership")
    void userScopedOperation_refusesAPurposeBoundToAMembership(String operation, OneTimeTokenPurpose purpose) {
        assertRefused(switch (operation) {
            case "issue" -> () -> create.issue(USER, purpose);
            case "findOwner" -> () -> get.findOwner(RAW_TOKEN, purpose);
            case "findOwner of a blank token" -> () -> get.findOwner(" ", purpose);
            case "countIssuedSince" -> () -> get.countIssuedSince(USER, purpose, clock.instant());
            case "consume" -> () -> consume.consume(RAW_TOKEN, purpose);
            case "consume a blank token" -> () -> consume.consume(null, purpose);
            default -> throw new IllegalStateException(operation);
        });
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("membershipScopedOperations")
    @DisplayName("OTT-003 a membership-scoped operation refuses a purpose bound only to a user")
    void membershipScopedOperation_refusesAPurposeBoundOnlyToAUser(String operation, OneTimeTokenPurpose purpose) {
        assertRefused(switch (operation) {
            case "issueForMembership" -> () -> create.issueForMembership(USER, COMMUNITY, purpose);
            case "findMembership" -> () -> get.findMembership(RAW_TOKEN, purpose);
            case "findMembership of a blank token" -> () -> get.findMembership("", purpose);
            case "countIssuedForMembershipSince" ->
                    () -> get.countIssuedForMembershipSince(USER, COMMUNITY, purpose, clock.instant());
            case "findEarliestIssuedForMembershipSince" ->
                    () -> get.findEarliestIssuedForMembershipSince(USER, COMMUNITY, purpose, clock.instant());
            case "findValidExpiryForMembership" -> () -> get.findValidExpiryForMembership(USER, COMMUNITY, purpose);
            case "findValidExpiriesInCommunity" -> () -> get.findValidExpiriesInCommunity(COMMUNITY, purpose);
            case "consumeForMembership" -> () -> consume.consumeForMembership(RAW_TOKEN, purpose);
            case "consumeForMembership a blank token" -> () -> consume.consumeForMembership(null, purpose);
            case "revokeForMembership" -> () -> revoke.revokeForMembership(USER, COMMUNITY, purpose);
            default -> throw new IllegalStateException(operation);
        });
    }

    private void assertRefused(Executable operation) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, operation);
        assertFalse(refused.getMessage().contains(RAW_TOKEN), "The refusal contains the token");
        assertFalse(refused.getMessage().contains(OneTimeTokenHash.of(RAW_TOKEN)), "The refusal contains the hash");
        verifyNoInteractions(createRepository, getRepository, consumeRepository, revokeRepository);
    }
}
