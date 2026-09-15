package com.dropboxclone.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dropboxclone.backend.auth.model.RefreshToken;
import com.dropboxclone.backend.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class RefreshTokenServiceTest {

    private static final String USER_ID = "user-1";

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenProperties properties = new RefreshTokenProperties(30);
    private final RefreshTokenService service = new RefreshTokenService(repository, properties);

    private final AtomicInteger idSequence = new AtomicInteger();

    @BeforeEach
    void stubSaveAssignsId() {
        // Mimic MongoRepository#save assigning a generated id to a fresh RefreshToken.
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId("token-" + idSequence.incrementAndGet());
            }
            return token;
        });
    }

    @Test
    void issueGeneratesUniqueHashedTokenBoundToUser() {
        RefreshTokenService.Issued first = service.issue(USER_ID);
        RefreshTokenService.Issued second = service.issue(USER_ID);

        assertThat(first.rawToken()).isNotBlank();
        assertThat(second.rawToken()).isNotBlank();
        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());

        assertThat(first.record().getUserId()).isEqualTo(USER_ID);
        // The raw token is never persisted; only a hash of it should be stored.
        assertThat(first.record().getTokenHash()).isNotEqualTo(first.rawToken());
        assertThat(first.record().getRevokedAt()).isNull();
        assertThat(first.record().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void rotateReturnsNewTokenAndRevokesThePrevious() {
        RefreshTokenService.Issued issued = service.issue(USER_ID);
        RefreshToken stored = issued.record();
        when(repository.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        RefreshTokenService.Rotated rotated = service.rotate(issued.rawToken());

        assertThat(rotated.userId()).isEqualTo(USER_ID);
        assertThat(rotated.rawToken()).isNotEqualTo(issued.rawToken());
        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(stored.getReplacedByTokenId()).isNotNull();
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("does-not-exist"))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
            .id("expired-token")
            .userId(USER_ID)
            .tokenHash("hash")
            .createdAt(Instant.now().minus(31, ChronoUnit.DAYS))
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .build();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("raw-value"))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotateOnAlreadyRevokedTokenRevokesEntireFamilyAndRejects() {
        RefreshToken alreadyRotated = RefreshToken.builder()
            .id("rotated-token")
            .userId(USER_ID)
            .tokenHash("hash")
            .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .expiresAt(Instant.now().plus(29, ChronoUnit.DAYS))
            .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
            .build();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(alreadyRotated));

        RefreshToken otherActiveTokenForSameUser = RefreshToken.builder()
            .id("other-active-token")
            .userId(USER_ID)
            .tokenHash("other-hash")
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .build();
        when(repository.findByUserIdAndRevokedAtIsNull(USER_ID))
            .thenReturn(List.of(otherActiveTokenForSameUser));

        assertThatThrownBy(() -> service.rotate("stolen-raw-value"))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("reuse detected");

        // Reuse of a rotated token must revoke every other still-active token for that user.
        assertThat(otherActiveTokenForSameUser.getRevokedAt()).isNotNull();
        verify(repository, atLeastOnce()).save(otherActiveTokenForSameUser);
    }

    @Test
    void revokeMarksMatchingTokenAsRevoked() {
        RefreshTokenService.Issued issued = service.issue(USER_ID);
        RefreshToken stored = issued.record();
        when(repository.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        service.revoke(issued.rawToken());

        assertThat(stored.getRevokedAt()).isNotNull();
        // issue() already saved once; revoke() saves the now-revoked token again.
        verify(repository, times(2)).save(stored);
    }

    @Test
    void revokeOnUnknownTokenIsANoOp() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("does-not-exist");

        verify(repository, times(0)).save(any());
    }

    @Test
    void revokeAllForUserRevokesOnlyActiveTokensForThatUser() {
        RefreshToken active1 = RefreshToken.builder()
            .id("active-1").userId(USER_ID).tokenHash("h1")
            .createdAt(Instant.now()).expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .build();
        RefreshToken active2 = RefreshToken.builder()
            .id("active-2").userId(USER_ID).tokenHash("h2")
            .createdAt(Instant.now()).expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .build();
        when(repository.findByUserIdAndRevokedAtIsNull(USER_ID)).thenReturn(List.of(active1, active2));

        service.revokeAllForUser(USER_ID);

        assertThat(active1.getRevokedAt()).isNotNull();
        assertThat(active2.getRevokedAt()).isNotNull();
        verify(repository).save(active1);
        verify(repository).save(active2);
    }
}
