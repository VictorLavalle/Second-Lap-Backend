package com.secondlap.backend.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Classic pagination parameters shared by the admin listings. The page size is
 * clamped to a fixed allow-list so an arbitrary size (e.g. 100000) cannot be
 * used as an abuse vector; an unknown size falls back to the default and a
 * negative page to zero. Invalid values are coerced, never rejected with 500.
 */
public final class PageSupport {

    public static final int DEFAULT_SIZE = 25;
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 25, 50, 100);

    private PageSupport() {
    }

    public static Pageable toPageable(int page, int size, Sort sort) {
        int safeSize = ALLOWED_SIZES.contains(size) ? size : DEFAULT_SIZE;
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
