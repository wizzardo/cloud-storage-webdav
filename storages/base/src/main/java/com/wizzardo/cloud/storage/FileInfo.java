package com.wizzardo.cloud.storage;

public class FileInfo {
    public String name;
    public long size;
    public long created;
    public long updated;
    public Type type;

    public enum Type {
        FILE, FOLDER, ROOT_FOLDER
    }

    public boolean isFolder() {
        return type == FileInfo.Type.FOLDER || type == Type.ROOT_FOLDER;
    }

    public boolean isFile() {
        return type == FileInfo.Type.FILE;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "name='" + name + '\'' +
                ", size=" + size +
                ", created=" + created +
                ", updated=" + updated +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileInfo)) return false;

        FileInfo fileInfo = (FileInfo) o;

        return name.equals(fileInfo.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
