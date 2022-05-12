package com.wizzardo.cloud.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface Storage<T extends FileInfo> {
    List<T> list(T fileInfo) throws IOException;

    default List<T> list() throws IOException {
        return list(null);
    }

    T getInfo(String path) throws IOException;

    byte[] getData(T file, long from, long to) throws IOException;

    default byte[] getData(T file) throws IOException {
        return getData(file, 0, file.size);
    }

    default byte[] getData(String path) throws IOException {
        T file = getInfo(path);
        return getData(file, 0, file.size);
    }

    default InputStream getStream(String path) throws IOException {
        return getStream(getInfo(path));
    }

    default InputStream getStream(T file) throws IOException {
        return new ByteArrayInputStream(getData(file, 0, file.size));
    }

    long getTotalSpace() throws IOException;

    long getUsableSpace() throws IOException;

    void createFolder(String path) throws IOException;

    void delete(T path) throws IOException;

    default void delete(String path) throws IOException {
        delete(getInfo(path));
    }

    void put(String path, byte[] bytes) throws IOException;

    void put(String path, File file) throws IOException;

    void move(T file, String destination) throws IOException;

    default void move(String file, String destination) throws IOException {
        if (file.equals(destination))
            return;
        move(getInfo(file), destination);
    }

}
