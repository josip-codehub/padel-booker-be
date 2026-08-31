package org.padel.booker.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "booking_attempts")
public class BookingAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    private Integer attemptNo;

    @Column(nullable = false)
    private Instant attemptAt;

    @Column(columnDefinition = "text")
    private String requestBody;

    private Integer httpStatus;

    @Column(columnDefinition = "text")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttemptOutcome outcome;

    public static BookingAttempt of(Long bookingId, int attemptNo, String requestBody,
                                    Integer httpStatus, String responseBody, AttemptOutcome outcome) {
        BookingAttempt a = new BookingAttempt();
        a.bookingId = bookingId;
        a.attemptNo = attemptNo;
        a.attemptAt = Instant.now();
        a.requestBody = requestBody;
        a.httpStatus = httpStatus;
        a.responseBody = responseBody;
        a.outcome = outcome;
        return a;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long v) { this.bookingId = v; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer v) { this.attemptNo = v; }
    public Instant getAttemptAt() { return attemptAt; }
    public void setAttemptAt(Instant v) { this.attemptAt = v; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String v) { this.requestBody = v; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer v) { this.httpStatus = v; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String v) { this.responseBody = v; }
    public AttemptOutcome getOutcome() { return outcome; }
    public void setOutcome(AttemptOutcome v) { this.outcome = v; }
}
