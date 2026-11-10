package com.museotek.box.application.player;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentifyBoxQueryTest {

    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final IdentifyBoxQuery query = new IdentifyBoxQuery(boxRepository);

    @Test
    void aKnownSerial_findsTheBox() {
        Box box = new Box();
        when(boxRepository.findBySerialNumber("MB-SECRET-1")).thenReturn(Optional.of(box));

        assertThat(query.execute("MB-SECRET-1")).isSameAs(box);
    }

    @Test
    void anUnknownSerial_throws_withoutEchoingIt() {
        when(boxRepository.findBySerialNumber("MB-SECRET-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute("MB-SECRET-2"))
                .isInstanceOf(BoxNotFoundException.class)
                .hasMessageNotContaining("MB-SECRET-2");
    }
}
