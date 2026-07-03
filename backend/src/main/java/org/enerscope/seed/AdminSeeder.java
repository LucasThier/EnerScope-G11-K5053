package org.enerscope.seed;

import org.enerscope.logging.AppLogger;
import org.enerscope.user.User;
import org.enerscope.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures a default administrator account exists on startup.
 *
 * <p>The account is created only if no user with the configured email is
 * present, so the seeder is idempotent and safe to run on every boot.</p>
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final AppLogger logger;

    private final String adminMail;
    private final String adminPassword;
    private final String adminFirstName;
    private final String adminLastName;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder encoder,
                       AppLogger logger,
                       @Value("${app.admin.mail:admin@enerscope.org}") String adminMail,
                       @Value("${app.admin.password:admin12345}") String adminPassword,
                       @Value("${app.admin.first-name:EnerScope}") String adminFirstName,
                       @Value("${app.admin.last-name:Admin}") String adminLastName) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.logger = logger;
        this.adminMail = adminMail;
        this.adminPassword = adminPassword;
        this.adminFirstName = adminFirstName;
        this.adminLastName = adminLastName;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByMailIgnoreCase(adminMail)) {
            logger.info("Admin user {} already exists, skipping seed", adminMail);
            return;
        }

        User admin = new User(
                adminMail,
                adminFirstName,
                adminLastName,
                encoder.encode(adminPassword)
        );
        userRepository.save(admin);
        logger.info("Seeded admin user {}", adminMail);
    }
}
