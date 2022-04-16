package com.wizzardo.cloud.storage.degoo;

import com.wizzardo.cloud.storage.FileInfo;

public class DegooFile extends FileInfo {
    public String id;
    public String url;

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
