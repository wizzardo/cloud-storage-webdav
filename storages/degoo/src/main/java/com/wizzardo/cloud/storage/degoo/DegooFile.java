package com.wizzardo.cloud.storage.degoo;

import com.wizzardo.cloud.storage.FileInfo;

public class DegooFile extends FileInfo {
    public String id;
    public String url;
    public String path;

    @Override
    public String toString() {
        return "DegooFile{" +
                "name='" + name + '\'' +
                ", size=" + size +
                ", created=" + created +
                ", updated=" + updated +
                ", type=" + type +
                ", id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", path='" + path + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DegooFile)) return false;

        DegooFile degooFile = (DegooFile) o;

        return id.equals(degooFile.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
