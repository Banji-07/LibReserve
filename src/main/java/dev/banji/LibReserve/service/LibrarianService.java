package dev.banji.LibReserve.service;

import dev.banji.LibReserve.config.properties.LibraryConfigurationProperties;
import dev.banji.LibReserve.exceptions.*;
import dev.banji.LibReserve.model.CurrentLibrarianDetailDto;
import dev.banji.LibReserve.model.LibraryOccupancyQueue;
import dev.banji.LibReserve.model.Student;
import dev.banji.LibReserve.model.StudentReservation;
import dev.banji.LibReserve.model.dtos.CurrentStudentDetailDto;
import dev.banji.LibReserve.model.dtos.StudentReservationDto;
import dev.banji.LibReserve.model.enums.ReservationStatus;
import dev.banji.LibReserve.repository.LibrarianReservationRepository;
import dev.banji.LibReserve.repository.StudentRepository;
import dev.banji.LibReserve.repository.StudentReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static dev.banji.LibReserve.model.enums.ReservationStatus.*;
import static java.time.LocalDate.now;

@Service
@RequiredArgsConstructor
public class LibrarianService {
    private final LibraryConfigurationProperties libraryConfigurationProperties;
    private final LibraryOccupancyQueue occupancyQueue;
    private final StudentReservationRepository studentReservationRepository;
    private final StudentRepository studentRepository;
    private final LibrarianReservationRepository librarianReservationRepository;
    private final JwtTokenService jwtTokenService;
    private final LibraryOccupancyQueue libraryOccupancyQueue;
    private final NotificationService notificationService;

    public void signOutLibrarian(JwtAuthenticationToken authentication) {
        var staffNumber = authentication.getName();
        Jwt jwt = authentication.getToken();
        boolean sessionSignedOut = false;
        boolean wasInSession = false;
        var librarianReservationOptional = librarianReservationRepository.findByLibrarianStaffNumber(staffNumber);
        if (librarianReservationOptional.isPresent()) {
            wasInSession = true;
            sessionSignedOut = libraryOccupancyQueue.signOutLibrarian(new CurrentLibrarianDetailDto(staffNumber, librarianReservationOptional.get()));
            librarianReservationOptional.get().setCheckOutDateAndTime(LocalDateTime.now()); //check out user...
            librarianReservationOptional.get().setReservationStatus(LIBRARIAN_CHECKED_OUT);//change librarianReservation status
            librarianReservationRepository.save(librarianReservationOptional.get());//update in repository
        }
        //add JWT to blacklist
        boolean blackListed = jwtTokenService.blacklistAccessToken(jwt);
        if ((wasInSession && !sessionSignedOut) || !blackListed) throw new LibraryRuntimeException();
    }

    private Optional<StudentReservation> allowEntry(StudentReservation studentReservation) {
        if (!studentReservation.getReservationStatus().equals(BOOKED))
            throw new InvalidReservationException("Expired Reservation");
        validateEntryTime(studentReservation); //validate the entry time
        StudentReservation updatedReservationObject = signInStudent(studentReservation); //sign-in reservation...
        occupancyQueue.isLibraryFull(); //TODO normally this shouldn't throw any exception since the student already has a reservation
        boolean isCurrentlyInLibrary = occupancyQueue.isUserPresentInLibrary(studentReservation.getStudent().getMatricNumber()).isPresent(); //if the student is already in the library...
        boolean signedIn = occupancyQueue.updateStudentSession(new CurrentStudentDetailDto(studentReservation.getStudent().getMatricNumber(), updatedReservationObject));
        return (signedIn && !isCurrentlyInLibrary) ? Optional.of(updatedReservationObject) : Optional.empty();
    }

    private StudentReservation signInStudent(StudentReservation studentReservation) {
        studentReservation.setCheckInTime(LocalTime.now());
        return updateReservationStatus(studentReservation, CHECKED_IN);
    }


    // This method still needs some work.
    // Because it simply fetches one reservation from the repo randomly I think and that might not always be the best option.
    // Since the student might have multiple reservations for the day.
    public void validateStudentEntryByMatricNumber(String matricNumber) {

        StudentReservation studentReservation = studentReservationRepository.findByDateReservationWasMadeForAndStudentMatricNumber(now(), matricNumber).orElseThrow(() -> {
            throw new ReservationDoesNotExistException();
        });
        allowEntry(studentReservation).orElseThrow(() -> {
            throw new LibraryRuntimeException();
        });
    }

    public void validateStudentEntryByReservationCode(String reservationCode) {
        StudentReservation studentReservation = studentReservationRepository.findByReservationCodeAndDateReservationWasMadeFor(reservationCode, now()).orElseThrow(() -> {
            throw new ReservationDoesNotExistException();
        });
        allowEntry(studentReservation).orElseThrow(() -> {
            throw new LibraryRuntimeException();
        });
    }

    public void invalidateStudentSessionByReservationCode(String reservationCode) {
        StudentReservation studentReservation = occupancyQueue.isStudentPresentInLibrary(reservationCode).orElseThrow(() -> {
            throw new StudentNotInLibraryException();
        });
        kickStudentOut(studentReservation);
    }

    public void invalidateStudentSessionByMatricNumber(String matricNumber) {
        StudentReservation studentReservation = (StudentReservation) occupancyQueue.isUserPresentInLibrary(matricNumber).orElseThrow(() -> {
            throw new StudentNotInLibraryException();
        });
        kickStudentOut(studentReservation);
    }


    public void blacklistStudent(String matricNumber) {
        Student student = studentRepository.findByMatricNumber(matricNumber).orElseThrow(() -> {
            throw UserNotFoundException.StudentNotFoundException();
        });
        student.getAccount().setNotLocked(false); //lock account
        if (occupancyQueue.isUserPresentInLibrary(matricNumber).isPresent()) {
            student.getStudentReservationList().add(kickStudentOut((StudentReservation) occupancyQueue.isUserPresentInLibrary(matricNumber).get()));
        }
        studentRepository.save(student);
        notificationService.studentBlackListNotification(student.getEmailAddress());
    }

    private StudentReservation kickStudentOut(StudentReservation studentReservation) {
        String matricNumber = studentReservation.getStudent().getMatricNumber();
        boolean isPresent = occupancyQueue.isUserPresentInLibrary(studentReservation.getStudent().getMatricNumber()).isPresent();
        if (!isPresent) {
            throw new StudentNotInLibraryException();
        }

        studentReservation.setReservationStatus(BLACKLISTED);
        studentReservation.setCheckOutDateAndTime(LocalDateTime.now()); //check out user...

        boolean reservationInvalidated = occupancyQueue.signOutStudent(new CurrentStudentDetailDto(studentReservation.getStudent().getMatricNumber(), studentReservation));
        if (!reservationInvalidated) throw new LibraryRuntimeException();

        notificationService.studentKickedOutNotification(matricNumber);
        return studentReservation;
    }

    private void validateEntryTime(StudentReservation studentReservation) {
        var bookedEntryTime = studentReservation.getTimeReservationWasMadeFor();
        var currentEntryTime = LocalTime.now();
        boolean isEarlyCheckInAllowed = libraryConfigurationProperties.getAllowEarlyCheckIn();
        boolean isLateCheckInAllowed = libraryConfigurationProperties.getAllowLateCheckIn();

        //the time Difference between the booked and the current time.
        Duration duration = Duration.between(bookedEntryTime, currentEntryTime);

        boolean durationNegative = duration.isNegative(); //meaning it's already a late check-in...
        long differenceInMinutes = Math.abs(duration.toMinutes()); // the total difference. this is a +ve value...


        if (durationNegative) {
            //this means the reservation is valid if it's not greater than recommended check in time.
            // or if late check-in is allowed, then if it's not greater than the late check in time.
            if (differenceInMinutes <= libraryConfigurationProperties.getRecommendedCheckInTime() || (differenceInMinutes <= libraryConfigurationProperties.getAllowedLateCheckInTimeInMinutes() && isLateCheckInAllowed)) {
                return; //valid entry
            }
            // anything below this line simply means it's a late check in...
            updateReservationStatus(studentReservation, EXPIRED); //TODO normally, this will be taken care of by the 'LibraryManagementService'
            throw new LateCheckInException();
        }

        if ((!isEarlyCheckInAllowed) && differenceInMinutes > libraryConfigurationProperties.getRecommendedCheckInTime()) {
            //meaning it's an early check-in...
            throw new EarlyCheckInException(studentReservation.getTimeReservationWasMadeFor());
        }

        //anything below this line means it's a valid entry time...
    }

    /**
     * This method simply verifies if a reservation code
     *
     * @return a dto of type "StudentReservationDto"
     */
    public StudentReservationDto verifyStudentReservationCode(String reservationCode) {
        StudentReservation studentReservation = studentReservationRepository.findByReservationCode(reservationCode).orElseThrow(() -> {
            throw new ReservationDoesNotExistException();
        });
        return new StudentReservationDto(studentReservation);
    }

    /**
     * This method will fetch the student reservations for today
     */
    public List<StudentReservationDto> fetchStudentReservationForToday(String matricNumber) {
        List<StudentReservation> studentReservationList = studentReservationRepository.findByStudentMatricNumberAndDateReservationWasMadeFor(matricNumber, now());
        return mapToSimpleStudentReservationDtos(studentReservationList);
    }

    private List<StudentReservationDto> mapToSimpleStudentReservationDtos(List<StudentReservation> studentReservationList) {
        if (studentReservationList.isEmpty()) throw new ReservationDoesNotExistException();
        return studentReservationList.stream().map(StudentReservationDto::new).toList();
    }

    private StudentReservation updateReservationStatus(StudentReservation studentReservation, ReservationStatus status) {
        studentReservation.setReservationStatus(status);
        return studentReservationRepository.save(studentReservation);
    }

    /**
     * This method will fetch all the student reservations
     */
    public List<StudentReservationDto> fetchAllStudentReservations(String matricNumber) {
        List<StudentReservation> studentReservationList = studentReservationRepository.findByStudentMatricNumber(matricNumber);
        return mapToSimpleStudentReservationDtos(studentReservationList);
    }

    public List<StudentReservationDto> fetchCurrentStudentsInLibrary() {
        return occupancyQueue.fetchOccupancyQueueAsList().stream().filter(inmemoryUserDetailDto -> inmemoryUserDetailDto instanceof CurrentStudentDetailDto).map(inmemoryUserDetailDto -> (CurrentStudentDetailDto) inmemoryUserDetailDto).map(currentStudentDetailDto -> new StudentReservationDto(currentStudentDetailDto.studentReservation())).toList();
    }

    public List<StudentReservationDto> fetchStudentListForToday() {
        return studentReservationRepository.findByDateReservationWasMadeFor(LocalDate.now()).stream().map(StudentReservationDto::new).toList();
    }
}