package dev.banji.LibReserve.config.properties;

import dev.banji.LibReserve.exceptions.LibraryRuntimeException;
import dev.banji.LibReserve.model.AllowedFaculties;
import dev.banji.LibReserve.model.dtos.NotificationTimeDto;
import dev.banji.LibReserve.model.dtos.NotificationsConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "library.properties")
@Getter
@Setter
public final class LibraryConfigurationProperties {
    private String universityUrl;
    private Boolean acceptingBookings;
    private Long bookingTimeAllowedInMinutes;
    private Long recommendedCheckInTime; //this can be 3-5 minutes before the bookedTime.
    private Long numberOfSeats;
    private Boolean allowTimeExtension;
    private Long maximumTimeExtensionAllowedInMinutes;
    private Boolean allowLateCheckIn;
    private Long allowedLateCheckInTimeInMinutes;
    private Boolean allowEarlyCheckIn;
    private Long allowedEarlyCheckInMinutes; //this should be above the recommendedCheckIn time.
    private NotificationsConfig sendNotifications;
    private Boolean sendMessagesViaEmail;
    private List<NotificationTimeDto> notificationList;
    private Long readTimeoutInSeconds;
    private Long connectTimeoutInSeconds;
    private Set<AllowedFaculties> setOfAllowedFaculties;
    private Boolean enableSeatRandomization;
    private Boolean allowMultipleReservations;
    private Boolean allowAdvancedBookings;
    private Integer maximumLimitPerDay;
    private Boolean enableLimitPerDay;
    private Boolean reserveLibrarianSeat;
    private Long numberOfLibrarians;
    private Boolean allowMultipleTimeExtension;

    @ConstructorBinding
    public LibraryConfigurationProperties(String universityUrl, Boolean acceptingBookings, Long bookingTimeAllowedInMinutes, Long recommendedCheckInTime, Long numberOfSeats, Boolean allowTimeExtension, Long maximumTimeExtensionAllowedInMinutes, Boolean allowLateCheckIn, Long allowedLateCheckInTimeInMinutes, Boolean allowEarlyCheckIn, Long allowedEarlyCheckInMinutes, NotificationsConfig sendNotifications, List<NotificationTimeDto> notificationList, Long readTimeoutInSeconds, Long connectTimeoutInSeconds, Set<AllowedFaculties> setOfAllowedFaculties, Boolean enableSeatRandomization, Boolean allowMultipleReservations, Boolean allowAdvancedBookings, Integer maximumLimitPerDay, Boolean enableLimitPerDay, Boolean reserveLibrarianSeat, Long numberOfLibrarians, Boolean allowMultipleTimeExtension, Boolean sendMessagesViaEmail) {
        this.universityUrl = universityUrl;
        this.acceptingBookings = acceptingBookings;
        this.bookingTimeAllowedInMinutes = bookingTimeAllowedInMinutes;
        this.recommendedCheckInTime = recommendedCheckInTime;
        this.numberOfSeats = numberOfSeats;
        this.allowTimeExtension = allowTimeExtension;
        this.maximumTimeExtensionAllowedInMinutes = maximumTimeExtensionAllowedInMinutes;
        this.allowLateCheckIn = allowLateCheckIn;
        this.allowedLateCheckInTimeInMinutes = allowedLateCheckInTimeInMinutes;
        this.allowEarlyCheckIn = allowEarlyCheckIn;
        this.allowedEarlyCheckInMinutes = allowedEarlyCheckInMinutes;
        this.sendNotifications = sendNotifications;
        this.sendMessagesViaEmail = sendMessagesViaEmail;
        this.notificationList = notificationList;
        this.notificationList.sort(Comparator.comparing(NotificationTimeDto::timeLeft));
        this.readTimeoutInSeconds = readTimeoutInSeconds;
        this.connectTimeoutInSeconds = connectTimeoutInSeconds;
        this.setOfAllowedFaculties = setOfAllowedFaculties;
        this.enableSeatRandomization = enableSeatRandomization;
        this.allowMultipleReservations = allowMultipleReservations;
        this.allowAdvancedBookings = allowAdvancedBookings;
        this.maximumLimitPerDay = maximumLimitPerDay;
        this.enableLimitPerDay = enableLimitPerDay;
        this.reserveLibrarianSeat = reserveLibrarianSeat;
        this.numberOfLibrarians = numberOfLibrarians;
        this.allowMultipleTimeExtension = allowMultipleTimeExtension;
    }

    public void setRecommendedCheckInTime(Long recommendedCheckInTime) {
        if ((allowEarlyCheckIn) && (recommendedCheckInTime > allowedEarlyCheckInMinutes) && (recommendedCheckInTime > allowedLateCheckInTimeInMinutes)) // the recommendedTime cannot be above the allowedCheckInTime and the lateCheckInTIme
            throw new LibraryRuntimeException();
        this.recommendedCheckInTime = recommendedCheckInTime;
    }

    public void setAllowedEarlyCheckInMinutes(Long allowedEarlyCheckInMinutes) {
        if (allowedEarlyCheckInMinutes < recommendedCheckInTime) throw new LibraryRuntimeException();
        if (!allowEarlyCheckIn) allowEarlyCheckIn = true;
        this.allowedEarlyCheckInMinutes = allowedEarlyCheckInMinutes;
    }

    public void setAllowedLateCheckInTimeInMinutes(Long allowedLateCheckInTimeInMinutes) {
        if (allowedLateCheckInTimeInMinutes < recommendedCheckInTime) throw new LibraryRuntimeException();
        if (!allowLateCheckIn) allowLateCheckIn = true;
        this.allowedLateCheckInTimeInMinutes = allowedLateCheckInTimeInMinutes;
    }

    public void setNotificationList(List<NotificationTimeDto> notificationList) {
        notificationList.sort(Comparator.comparing(NotificationTimeDto::timeLeft));
        this.notificationList = notificationList;
    }
}