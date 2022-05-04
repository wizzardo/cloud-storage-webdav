package com.wizzardo.cloud.storage.fs;

import com.wizzardo.cloud.storage.FileInfo;
import com.wizzardo.cloud.storage.Storage;
import com.wizzardo.tools.io.FileTools;
import com.wizzardo.tools.io.IOTools;

import java.io.*;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

public class LocalStorage implements Storage<LocalStorage.FSFileInfo> {

    protected final File root;

    public LocalStorage(File root) {
        this.root = root;
    }

    public static class FSFileInfo extends FileInfo {
        String path;
    }

    protected FSFileInfo create(File file) throws IOException {
        FSFileInfo fi = new FSFileInfo();
        fi.path = file.getCanonicalPath();
        fi.name = file.getName();
        fi.size = file.length();
        fi.updated = file.lastModified();
        if (file.isFile())
            fi.type = FileInfo.Type.FILE;
        else if (file.isDirectory())
            fi.type = FileInfo.Type.FOLDER;

        FileTime creationTime = (FileTime) Files.getAttribute(file.toPath(), "creationTime");
        fi.created = creationTime.toMillis();

        return fi;
    }

    @Override
    public List<FSFileInfo> list(FSFileInfo fileInfo) throws IOException {
        File[] files;
        if (fileInfo == null) {
            files = root.listFiles();
        } else if (!fileInfo.path.startsWith(root.getCanonicalPath())) {
            throw new IllegalArgumentException("File is outside of served folder! " + fileInfo);
        } else
            files = new File(fileInfo.path).listFiles();

        ArrayList<FSFileInfo> list = new ArrayList<>(files.length);
        for (File file : files) {
            list.add(create(file));
        }
        return list;
    }

    @Override
    public FSFileInfo getInfo(String path) throws IOException {
        File file = getFile(path);
        if (!file.exists())
            return null;

        return create(file);
    }

    @Override
    public byte[] getData(FSFileInfo file, long from, long to) throws IOException {
        byte[] bytes = new byte[(int) (to - from)];
        try (RandomAccessFile raf = new RandomAccessFile(file.path, "r")) {
            raf.seek(from);
            int offset = 0;
            while (offset != bytes.length) {
                int read = raf.read(bytes, offset, bytes.length - offset);
                if (read == -1)
                    throw new IllegalStateException();
                offset += read;
            }
        }
        return bytes;
    }

    @Override
    public long getTotalSpace() throws IOException {
        FileStore store = Files.getFileStore(root.toPath());
        return store.getTotalSpace();
    }

    @Override
    public long getUsableSpace() throws IOException {
        FileStore store = Files.getFileStore(root.toPath());
        return store.getUsableSpace();
    }

    @Override
    public void createFolder(String path) throws IOException {
        File file = getFile(path);
        file.mkdirs();
    }

    @Override
    public void put(String path, byte[] bytes) throws IOException {
        getFile(path);
        FileTools.bytes(path, bytes);
    }

    @Override
    public void put(String path, File file) throws IOException {
        File to = getFile(path);
        try (
                FileInputStream in = new FileInputStream(file);
                FileOutputStream out = new FileOutputStream(to);
        ) {
            IOTools.copy(in, out);
        }
    }

    @Override
    public void delete(FSFileInfo fileInfo) {
        File file = new File(root, fileInfo.path);
        FileTools.deleteRecursive(file);
    }

    @Override
    public void move(FSFileInfo from, String destination) throws IOException {
        File file = getFile(destination);
        new File(root, from.path).renameTo(file);
    }

    protected File getFile(String path) throws IOException {
        File file = new File(root, path);
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath()))
            throw new IllegalArgumentException("File is outside of served folder! " + path);

        return file;
    }

    @Override
    public InputStream getStream(FSFileInfo file) throws IOException {
        return new FileInputStream(new File(root, file.path));
    }
}
