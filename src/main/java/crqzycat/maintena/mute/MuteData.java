package crqzycat.maintena.mute;

import java.util.ArrayList;
import java.util.List;

/**
 * Gespeicherte Chat-Timeouts (mutes.json).
 */
public class MuteData {

    public List<Entry> mutes = new ArrayList<>();

    public static class Entry {
        public String uuid;
        public String name;
        public String source;
        public String reason;

        /** Zeitpunkt des Mutes (Epoch-Millisekunden). */
        public long created;

        /** Ablaufzeitpunkt (Epoch-Millisekunden), 0 = permanent. */
        public long expires;

        public boolean isPermanent() {
            return expires <= 0;
        }

        public boolean isExpired() {
            return expires > 0 && expires <= System.currentTimeMillis();
        }

        public long remainingMillis() {
            return isPermanent() ? -1 : Math.max(0, expires - System.currentTimeMillis());
        }
    }
}
