package org.enerscope.version.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.logging.AppLogger;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Service
@AllArgsConstructor
public class VersionService {

    private final VersionRepository versionRepository;
    private final AppLogger logger;

    public Version register(VersionDTO data) {
        // if (versionRepository.existsByMailIgnoreCase(data.mail())) { MAYBE DON´T LET
        //                                                              THEM CREATE A VERSION WITH THE SAME NAME IN THE SAME ORG?
        // throw new IllegalArgumentException("An account with that email already
        // exists");
        // }
        Version version = new Version(data.getName(), Instant.now(), data.getParentVersion(), data.getNodeSnapshot(),
                data.getConnectionSnapshot(), data.getConnectionChanges(), data.getNodeChanges());

        Version saved = versionRepository.save(version);
        logger.info("Registered new version {}", saved.getName());
        return saved;
    }

    public Version 

    public String delete(UUID id) {
        versionRepository.deleteById(id);
        return "Version deleted";
    }
}
