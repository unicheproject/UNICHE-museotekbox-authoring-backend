package com.museotek.box.application.player;

import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase.RedeemedScreen;
import com.museotek.box.application.screen.ScreenSecrets;
import com.museotek.box.domain.screen.InvalidScreenKeyException;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.domain.screen.ScreenSetupCode;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
import com.museotek.box.infrastructure.repository.ScreenRepository;
import com.museotek.box.infrastructure.repository.ScreenSetupCodeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The player side of screens: redeeming a setup code and verifying a screen key.
class PlayerScreenUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CODE_HASH = ScreenSecrets.sha256Hex("K7QFM2XR");

    private final ScreenSetupCodeRepository setupCodeRepository = mock(ScreenSetupCodeRepository.class);
    private final ScreenRepository screenRepository = mock(ScreenRepository.class);
    private final RedeemScreenSetupCodeUseCase redeem = new RedeemScreenSetupCodeUseCase(setupCodeRepository, screenRepository, CLOCK);
    private final VerifyScreenKeyUseCase verify = new VerifyScreenKeyUseCase(screenRepository, CLOCK);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void redeem_createsTheScreenOfTheCodesOrg_andReturnsAKeyStoredOnlyAsAHash() {
        ScreenSetupCode setupCode = new ScreenSetupCode();
        setupCode.setOrgId(orgId);
        setupCode.setScreenName("Hall A screen");
        setupCode.setCreatedBy("manager-1");
        when(setupCodeRepository.markUsed(CODE_HASH, NOW)).thenReturn(1);
        when(setupCodeRepository.findByCodeHash(CODE_HASH)).thenReturn(Optional.of(setupCode));
        ArgumentCaptor<Screen> saved = ArgumentCaptor.forClass(Screen.class);
        when(screenRepository.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        RedeemedScreen redeemed = redeem.execute("k7qf-m2xr");

        Screen screen = saved.getValue();
        assertThat(redeemed.screen()).isEqualTo(new PlayerScreenView(screen.getId(), orgId, "Hall A screen"));
        assertThat(screen.getOrgId()).isEqualTo(orgId);
        assertThat(screen.getName()).isEqualTo("Hall A screen");
        assertThat(screen.getCreatedBy()).isEqualTo("manager-1");
        assertThat(screen.getCreatedAt()).isEqualTo(NOW);
        assertThat(screen.getKeyHash()).isEqualTo(ScreenSecrets.sha256Hex(redeemed.key()));
        assertThat(setupCode.getScreenId()).isEqualTo(screen.getId());
    }

    @Test
    void redeem_aCodeThatIsNotOpen_createsNoScreen() {
        when(setupCodeRepository.markUsed(CODE_HASH, NOW)).thenReturn(0);

        assertThatThrownBy(() -> redeem.execute("K7QF-M2XR")).isInstanceOf(SetupCodeNotFoundException.class);
        verify(screenRepository, never()).save(any());
    }

    @Test
    void redeem_nothingButDashes_isNotLookedUp() {
        assertThatThrownBy(() -> redeem.execute(" - ")).isInstanceOf(SetupCodeNotFoundException.class);
        verifyNoInteractions(setupCodeRepository, screenRepository);
    }

    @Test
    void verify_aLiveKey_marksTheScreenSeen() {
        Screen screen = new Screen();
        screen.setId(UUID.randomUUID());
        screen.setOrgId(orgId);
        screen.setName("Hall A screen");
        when(screenRepository.findByKeyHashAndRevokedAtIsNull(ScreenSecrets.sha256Hex("the-key"))).thenReturn(Optional.of(screen));

        assertThat(verify.execute("the-key")).isEqualTo(new PlayerScreenView(screen.getId(), orgId, "Hall A screen"));
        assertThat(screen.getLastSeenAt()).isEqualTo(NOW);
    }

    @Test
    void verify_anUnknownOrRevokedKey_throws() {
        when(screenRepository.findByKeyHashAndRevokedAtIsNull(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verify.execute("nope")).isInstanceOf(InvalidScreenKeyException.class);
    }
}
