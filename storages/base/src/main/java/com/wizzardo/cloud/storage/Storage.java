package com.wizzardo.cloud.storage;

import java.io.IOException;
import java.util.List;

public interface Storage<T extends FileInfo> {
    List<T> list(T fileInfo) throws IOException;

    default List<T> list() throws IOException {
        return list(null);
    }
}
