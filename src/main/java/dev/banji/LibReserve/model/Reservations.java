package dev.banji.LibReserve.model;

import dev.banji.LibReserve.converters.LocalTimeToHourConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalTime;
import java.util.Date;

@Getter
@Setter
@ToString
@Entity
public class Reservations {
    @Id
    @GeneratedValue
    private Long id; //primary key
    @Temporal(value = TemporalType.DATE)
    @Column(updatable = false, nullable = false)
    private Date reservationDate;
    @Temporal(value = TemporalType.DATE)
    @Column(updatable = false)
    private Date checkOutTime;
    @Convert(converter = LocalTimeToHourConverter.class)
    @Column(updatable = false, nullable = false)
    private LocalTime hoursReserved;
    private Boolean checkedIn;
    private Boolean expired;
    @Column(updatable = false, nullable = false)
    private String reservationCode;
    private int overTime;
}
