package org.enerscope.version.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VersionReadService {

    private final VersionService versionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public JsonNode getVersionJson(UUID id) {
        return objectMapper.valueToTree(versionService.getVersion(id));
    }
}
