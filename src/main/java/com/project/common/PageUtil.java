package com.project.common;

public final class PageUtil {

    public static final int MAX_SIZE = 50;

    private PageUtil() {
    }

    public static int page(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    public static int size(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, MAX_SIZE);
    }

    public static int offset(int page, int size) {
        return (page - 1) * size;
    }
}
