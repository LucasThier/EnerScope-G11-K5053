package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.node.repository.BaseNodeRepository;
import org.enerscope.node.repository.NodeConnectionRepository;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.enerscope.version.VersionNotFoundException;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class VersionService {

    private final VersionRepository versionRepository;
    private final NodeConnectionRepository connectionRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final AppLogger logger;

    public Version saveVersion(VersionDTO data) {
        Version parentVersion = null;
        if (data.getParentVersion() != null) {
            parentVersion = versionRepository.findById(data.getParentVersion())
                    .orElseThrow(() -> new VersionNotFoundException(data.getParentVersion()));
        }

        Version version = new Version(data.getName(),
                parentVersion,
                baseNodeRepository.findAllById(data.getNodeSnapshot()),
                connectionRepository.findAllById(data.getConnectionSnapshot()),
                new ArrayList<>(), new ArrayList<>());

        Version saved = versionRepository.save(version);

        logger.info("Registered the {} version", saved.getName());
        return saved;
    }

    public void deleteVersion(UUID id) {
        Version version = versionRepository.findById(id)
                .orElseThrow(() -> new VersionNotFoundException(id));
        versionRepository.delete(version);
        logger.info("Deleted version with id: {}", id);
    }

    public Version getVersion(UUID id) {
        return versionRepository.findById(id)
                .orElseThrow(() -> new VersionNotFoundException(id));
    }

    public Version modifyVersion(UUID id, VersionDTO data) {
        Version existingVersion = versionRepository.findById(id)
                .orElseThrow(() -> new VersionNotFoundException(id));

        Version parentVersion = null;
        if (data.getParentVersion() != null) {
            parentVersion = versionRepository.findById(data.getParentVersion())
                    .orElseThrow(() -> new VersionNotFoundException(data.getParentVersion()));
        }

        // Update the existing version's fields
        existingVersion.setName(data.getName());
        existingVersion.setParentVersion(parentVersion);
        existingVersion.setNodeSnapshot(baseNodeRepository.findAllById(data.getNodeSnapshot()));
        existingVersion.setConnectionSnapshot(connectionRepository.findAllById(data.getConnectionSnapshot()));
        // Note: connectionChanges and nodeChanges are not modified as they represent
        // differential changes from the master version, per the DTO comments

        Version saved = versionRepository.save(existingVersion);
        logger.info("Modified version with id: {}", id);
        return saved;
    }
}