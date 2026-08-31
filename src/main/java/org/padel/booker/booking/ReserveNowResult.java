package org.padel.booker.booking;

import java.math.BigDecimal;

/** Result of an immediate (within-window) reservation attempt. */
public record ReserveNowResult(
        boolean success,
        Long reservationId,
        BigDecimal price,
        String message
) {}
