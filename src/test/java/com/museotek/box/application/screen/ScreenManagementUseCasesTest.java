package com.museotek.box.application.screen;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.application.screen.CreateScreenSetupCodeUseCase.CreatedSetupCode;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.domain.screen.ScreenNotFoundException;
import com.museotek.box.domain.screen.ScreenSetupCode;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
import com.museotek.box.infrastructure.repository.ScreenRepository;
import com.museotek.box.infrastructure.repository.ScreenSetupCodeRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The manager side of screens: setup codes and removing screens.
class ScreenManagementUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScreenSetupCodeRepository setupCodeRepository = mock(ScreenSetupCodeRepository.class);
    private final ScreenRepository screenRepository = mock(ScreenRepository.class);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void create_storesOnlyTheHashOfTheCode_validForTheConfiguredDays() {
        when(setupCodeRepository.save(any(ScreenSetupCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateScreenSetupCodeUseCase useCase = new CreateScreenSetupCodeUseCase(orgAccessGuard, setupCodeRepository, CLOCK, 3);

        CreatedSetupCode created = useCase.execute(orgId, "  Hall A screen ");

        verify(orgAccessGuard).requireManager(orgId);
        ScreenSetupCode stored = created.setupCode();
        assertThat(stored.getOrgId()).isEqualTo(orgId);
        assertThat(stored.getScreenName()).isEqualTo("Hall A screen");
        assertThat(stored.getCodeHash())
                .isEqualTo(ScreenSecrets.sha256Hex(ScreenSecrets.normaliseSetupCode(created.code())))
                .doesNotContain(created.code());
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
    }

    @Test
    void create_byANonManager_storesNothing() {
        doThrow(new OrgManagerRequiredException("managers only")).when(orgAccessGuard).requireManager(orgId);
        CreateScreenSetupCodeUseCase useCase = new CreateScreenSetupCodeUseCase(orgAccessGuard, setupCodeRepository, CLOCK, 3);

        assertThatThrownBy(() -> useCase.execute(orgId, "Hall A")).isInstanceOf(OrgManagerRequiredException.class);
        verifyNoInteractions(setupCodeRepository);
    }

    @Test
    void listOpenCodes_isManagerOnly_asOfNow() {
        new ListScreenSetupCodesQuery(orgAccessGuard, setupCodeRepository, CLOCK).execute(orgId);

        verify(orgAccessGuard).requireManager(orgId);
        verify(setupCodeRepository).findOpen(orgId, NOW);
    }

    @Test
    void cancel_aCodeThatIsNotOpen_throws() {
        UUID codeId = UUID.randomUUID();
        when(setupCodeRepository.cancel(codeId, orgId, NOW)).thenReturn(0);
        CancelScreenSetupCodeUseCase useCase = new CancelScreenSetupCodeUseCase(orgAccessGuard, setupCodeRepository, CLOCK);

        assertThatThrownBy(() -> useCase.execute(orgId, codeId)).isInstanceOf(SetupCodeNotFoundException.class);
        verify(orgAccessGuard).requireManager(orgId);
    }

    @Test
    void cancel_anOpenCode_passes() {
        UUID codeId = UUID.randomUUID();
        when(setupCodeRepository.cancel(codeId, orgId, NOW)).thenReturn(1);

        new CancelScreenSetupCodeUseCase(orgAccessGuard, setupCodeRepository, CLOCK).execute(orgId, codeId);

        verify(setupCodeRepository).cancel(codeId, orgId, NOW);
    }

    @Test
    void listScreens_isOpenToTheOrg() {
        when(screenRepository.findByOrgIdAndRevokedAtIsNullOrderByNameAsc(orgId)).thenReturn(List.of());

        assertThat(new ListScreensQuery(orgAccessGuard, screenRepository).execute(orgId)).isEmpty();
        verify(orgAccessGuard).requireAccess(orgId);
    }

    @Test
    void revoke_setsRevokedAt() {
        Screen screen = new Screen();
        UUID screenId = UUID.randomUUID();
        when(screenRepository.findByIdAndOrgIdAndRevokedAtIsNull(screenId, orgId)).thenReturn(Optional.of(screen));

        new RevokeScreenUseCase(orgAccessGuard, screenRepository, CLOCK).execute(orgId, screenId);

        verify(orgAccessGuard).requireManager(orgId);
        assertThat(screen.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revoke_anUnknownOrRemovedScreen_throws() {
        UUID screenId = UUID.randomUUID();
        when(screenRepository.findByIdAndOrgIdAndRevokedAtIsNull(screenId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RevokeScreenUseCase(orgAccessGuard, screenRepository, CLOCK).execute(orgId, screenId))
                .isInstanceOf(ScreenNotFoundException.class);
    }
}
