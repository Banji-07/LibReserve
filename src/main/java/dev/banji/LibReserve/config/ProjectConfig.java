package dev.banji.LibReserve.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import dev.banji.LibReserve.config.tokens.LibrarianAuthenticationToken;
import dev.banji.LibReserve.config.tokens.StudentAuthenticationToken;
import dev.banji.LibReserve.config.userDetails.LibrarianSecurityDetails;
import dev.banji.LibReserve.config.userDetails.StudentSecurityDetails;
import dev.banji.LibReserve.exceptions.UserNotFoundException;
import dev.banji.LibReserve.model.Librarian;
import dev.banji.LibReserve.model.dtos.LibrarianLoginDetailsDto;
import dev.banji.LibReserve.model.dtos.StudentLoginDetailsDto;
import dev.banji.LibReserve.repository.LibrarianRepository;
import dev.banji.LibReserve.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Configuration
public class ProjectConfig {
    @Value("${library.properties.numberOfSeats}")
    private Long numberOfSeats;
    @Value("${library.properties.readTimeoutInSeconds}")
    private Long readTimeoutInSeconds;
    @Value("${library.properties.connectTimeoutInSeconds}")
    private Long connectTimeoutInSeconds;

    //Request Matchers
    @Bean
    RequestMatcher librarianJwtTokenPathRequestMatcher() { //this request matcher is for token generation service
        return new AntPathRequestMatcher("/api/lib-reserve/token/librarian", "POST");
    }

    @Bean
    RequestMatcher studentJwtTokenPathRequestMatcher() { //this request matcher is for the token generation service
        return new AntPathRequestMatcher("/api/lib-reserve/token/student", "POST");
    }

    //UserDetailsService
    @Bean
    public UserDetailsService librarianUserDetailsService(LibrarianRepository librarianRepository) {
        return (staffNumber) -> {
            var user = librarianRepository.findByStaffNumber(staffNumber.trim().toLowerCase());
            Librarian librarian = user.orElseThrow(() -> new BadCredentialsException("Bad Credentials"));
            return new LibrarianSecurityDetails(librarian);
        };
    }

    @Bean
    public UserDetailsService studentUserDetailsService(StudentRepository studentRepository) {
        //first check if student already exists as a user in the database...
        return (matricNumber) -> {
            var user = studentRepository.findByMatricNumber(matricNumber.trim().toLowerCase());
            return user.map(StudentSecurityDetails::new).orElseThrow(UserNotFoundException::StudentNotFoundException);
        };
    }

    @Bean
    @PreAuthorize("hasAuthority('SCOPE_LIBRARIAN')")
    List<Jwt> blackListedJwtTokenList() {
        return new ArrayList<>();
    }

    @Bean
    @PreAuthorize("hasAuthority('SCOPE_LIBRARIAN')")
    LinkedList<String> libraryWaitingQueue() {
        return new LinkedList<>(); //holds the matric number of the students...
    }

    //Authentication converters
    @Bean
    AuthenticationConverter librarianAuthConverter() {
        return (httpServletRequest) -> {
            String staffNumber = "";
            String password = "";
            try {
                var reduce = httpServletRequest.getReader().readLine();
                LibrarianLoginDetailsDto value = new ObjectMapper().readValue(reduce, LibrarianLoginDetailsDto.class);
                staffNumber = value.staffNumber();
                password = value.password();
            } catch (RuntimeException | IOException ignored) {
            }
            if ((!staffNumber.isBlank() && !password.isBlank()))
                return LibrarianAuthenticationToken.unauthenticated(staffNumber.trim(), password.trim()); //trimmed to remove whitespaces
            return LibrarianAuthenticationToken.unauthenticated(null, null);
        };
    }

    //@Bean //TODO
    //public Long seatNumberGenerator() {
    //  return 0L;
    //}
//
    @Bean
    AuthenticationConverter studentAuthConverter() {
        return (httpServletRequest) -> {
            String matricNumber = "";
            String password = "";
            try {
                var reduce = httpServletRequest.getReader().readLine();
                StudentLoginDetailsDto value = new ObjectMapper().readValue(reduce, StudentLoginDetailsDto.class);
                matricNumber = value.matricNumber();
                password = value.password();
            } catch (RuntimeException | IOException ignored) {
            }
            if ((!matricNumber.isBlank() && !password.isBlank()))
                return StudentAuthenticationToken.unAuthenticatedToken(matricNumber.trim(), password.trim()); //trimmed to remove whitespaces
            return StudentAuthenticationToken.unAuthenticatedToken(null, null);
        };
    }

    //Authentication Success and Failure Handlers
    @Bean
    AuthenticationFailureHandler authenticationFailureHandler() {
        return (httpServletRequest, httpServletResponse, authenticationException) -> {
            httpServletResponse.setContentType("application/json");
            String errorJson = new ObjectMapper().writeValueAsString(authenticationException.getMessage());
            httpServletResponse.getWriter().write(errorJson);
            if (authenticationException instanceof BadCredentialsException || authenticationException instanceof UserNotFoundException)
                httpServletResponse.setStatus(401);
            else if (authenticationException instanceof DisabledException || authenticationException instanceof LockedException)
                httpServletResponse.setStatus(423);
            else
                httpServletResponse.setStatus(400);
        };
    }

    @Bean
    AuthenticationSuccessHandler authenticationSuccessHandler() { //this still needs work
        return (request, response, authentication) -> request.getRequestDispatcher(request.getRequestURI()).forward(request, response);
    }

    //Rest Template
    @Bean
    public RestTemplate restTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout((int) (readTimeoutInSeconds * 1000));
        factory.setConnectTimeout((int) (connectTimeoutInSeconds * 1000));
        return new RestTemplate(factory);
    }

    //Password Encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    //Command Line Runner
//    @Bean
//    CommandLineRunner commandLineRunner(LibrarianRepository librarianRepository, StudentRepository studentRepository, PasswordEncoder passwordEncoder) {
//       return (args) -> {
//            var librarian = new Librarian("librarian" +
//                    "FirstName", "librarian" +
//                    "MiddleName", "librarian" +
//                    "LastName", "male", "+234814929303",
//                    "librarianEmail@test.com", "testLga", "testState",
//                    "testCountry", new Account(true, true), "1234567890", passwordEncoder.encode("test123"));
//            var librarian1 = new Librarian("librarian" +
//                    "FirstName", "librarian" +
//                    "MiddleName", "librarian" +
//                    "LastName", "male", "+23481143929303",
//                    "librarian1Email@test.com", "testLga", "testState",
//                    "testCountry", new Account(false, true), "123456890", passwordEncoder.encode("test123"));
//            var librarian3 = new Librarian("librarian" +
//                    "FirstName", "librarian" +
//                    "MiddleName", "librarian" +
//                    "LastName", "male", "+234819929303",
//                    "librarian4Email@test.com", "testLga", "testState",
//                    "testCountry", new Account(true, false), "134567890", passwordEncoder.encode("test123"));
//            librarianRepository.saveAll(List.of(librarian, librarian1, librarian3));
//
//            Student student = new Student("student" +
//                    "FirstName", "student" +
//                    "MiddleName", "student" +
//                    "LastName", "male", "+2348113929303",
//                    "studentEmail@test.com", "testLga", "testState",
//                    "testCountry", new Account(true, true), new ArrayList<>(), "01234567890", "234", "100", passwordEncoder.encode("test123"));
//            studentRepository.save(student);
//        };
//    }
}

