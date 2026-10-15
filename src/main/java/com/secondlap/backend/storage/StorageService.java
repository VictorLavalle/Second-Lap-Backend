package com.secondlap.backend.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Persist an uploaded file and return the public URL under which it is served.
     * Implementations validate content type and size and generate a safe, unique
     * name. The only implementation today writes to the local filesystem; the
     * interface exists so a cloud backend could be added without touching callers.
     */
    String store(MultipartFile file);
}
