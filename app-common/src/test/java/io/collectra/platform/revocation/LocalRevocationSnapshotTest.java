package io.collectra.platform.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.*;
import org.junit.jupiter.api.Test;

class LocalRevocationSnapshotTest {
    private static final String ISS="https://id.example/realms/shared";
    @Test void blockedCannotBeUndoneByOldEvent() {
        var s=new LocalRevocationSnapshot(Clock.systemUTC(),Duration.ofSeconds(30));
        s.apply(ISS,"user",3,true);
        s.apply(ISS,"user",2,false);
        assertThat(s.allows(ISS,"user")).isFalse();
    }
    @Test void unknownStateFailsClosed() {
        var s=new LocalRevocationSnapshot(Clock.systemUTC(),Duration.ofSeconds(30));
        assertThat(s.allows(ISS,"user")).isFalse();
    }
    @Test void sameVersionConflictIsRejected() {
        var s=new LocalRevocationSnapshot(Clock.systemUTC(),Duration.ofSeconds(30));
        s.apply(ISS,"user",4,true);
        assertThatThrownBy(()->s.apply(ISS,"user",4,false))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void freshActiveSnapshotAllows() {
        var s=new LocalRevocationSnapshot(Clock.systemUTC(),Duration.ofSeconds(30));
        s.apply(ISS,"user",1,false);
        assertThat(s.allows(ISS,"user")).isTrue();
    }
}
