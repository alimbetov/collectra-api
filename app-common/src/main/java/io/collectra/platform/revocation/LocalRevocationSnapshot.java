package io.collectra.platform.revocation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-resource-server enforcement primitive. The caller must securely refresh
 * snapshots and ACK only after durable application. Not wired into Collectra yet.
 */
public final class LocalRevocationSnapshot {
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration freshness;
    public LocalRevocationSnapshot(Clock clock,Duration freshness) {
        this.clock=clock; this.freshness=freshness;
    }
    public void apply(String issuer,String subject,long version,boolean blocked) {
        if(version<0) throw new IllegalArgumentException("Negative version");
        String key=issuer+"\u0000"+subject;
        Instant now=clock.instant();
        entries.compute(key,(k,old)-> {
            if(old!=null && version<old.version()) return old;
            if(old!=null && version==old.version() && old.blocked()!=blocked)
                throw new IllegalStateException("Conflicting event for version");
            return new Entry(version,blocked,now);
        });
    }
    public boolean allows(String issuer,String subject) {
        Entry entry=entries.get(issuer+"\u0000"+subject);
        return entry!=null && !entry.blocked() &&
            !clock.instant().isAfter(entry.updatedAt().plus(freshness));
    }
    private record Entry(long version,boolean blocked,Instant updatedAt) {}
}
