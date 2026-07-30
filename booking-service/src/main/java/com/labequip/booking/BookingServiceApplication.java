package com.labequip.booking;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.labequip.booking.domain.Role;
import com.labequip.booking.domain.User;
import com.labequip.booking.repository.UserRepository;

@SpringBootApplication
@EnableFeignClients
@ConfigurationPropertiesScan
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }

    /**
     * Seeds two demo accounts so the screencast can show login + role-based access immediately,
     * without requiring a separate registration step for the admin account.
     */
    @Bean
    CommandLineRunner seedDemoUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }
            if (userRepository.findByUsername("student").isEmpty()) {
                User student = new User();
                student.setUsername("student");
                student.setPasswordHash(passwordEncoder.encode("student123"));
                student.setRole(Role.USER);
                userRepository.save(student);
            }
        };
    }
}
