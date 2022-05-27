package com.wizzardo.cloud.storage;

import com.wizzardo.tools.http.ContentType;
import com.wizzardo.tools.http.Request;
import com.wizzardo.tools.http.Response;
import com.wizzardo.tools.io.FileTools;
import com.wizzardo.tools.misc.Unchecked;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class S3Storage implements Storage<S3Storage.S3File> {
    protected final S3RequestFactory factory;

    protected final String host;
    protected final String bucket;
    protected final String region;
    protected final CredentialsProvider credentialsProvider;

    public S3Storage(String host, String bucket, String region, CredentialsProvider credentialsProvider) {
        this.host = host;
        this.bucket = bucket;
        this.region = region;
        this.credentialsProvider = credentialsProvider;

        factory = new S3RequestFactory(credentialsProvider, host, bucket, region);
    }


    @Override
    public List<S3File> list(S3File fileInfo) throws IOException {
        return null;
    }

    @Override
    public S3File getInfo(String path) throws IOException {
        Response response = factory.createRequest(path)
                .head();

        int responseCode = response.getResponseCode();
        if (responseCode == 404)
            return null;

        if (responseCode != 200) {
            throw new IllegalStateException("Request is unsuccessful: " + responseCode + " " + response.asString());
        }

        S3File file = new S3File();
        file.path = path;
        file.size = response.getContentLength();
        file.type = FileInfo.Type.FILE;
        file.updated = Unchecked.call(() -> S3Request.dateFormatThreadLocal.getValue().parse(response.getHeader("Last-Modified")).getTime());
        return file;
    }

    @Override
    public byte[] getData(S3File file, long from, long to) throws IOException {
        Request request = factory.createRequest(file.path);

        if (from != 0 || to != file.size) {
            request.header("Range", "bytes=" + from + "-" + (to - 1));
        }

        System.out.println("downloading as byte[] " + file);
        Response response = request.get();
        System.out.println(response.getResponseCode() + " length: " + response.getContentLength());
        if (response.getResponseCode() != 200) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }
        byte[] bytes = response.asBytes();
        return bytes;
    }

    @Override
    public InputStream getStream(S3File file) throws IOException {
        Request request = factory.createRequest(file.path);

        System.out.println("downloading as stream " + file);
        Response response = request.get();
        System.out.println(response.getResponseCode() + " length: " + response.getContentLength());
        if (response.getResponseCode() != 200) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }
        return response.asStream();
    }

    @Override
    public long getTotalSpace() throws IOException {
        return 0;
    }

    @Override
    public long getUsableSpace() throws IOException {
        return 0;
    }

    @Override
    public void createFolder(String path) throws IOException {
    }

    @Override
    public void delete(S3File file) throws IOException {
        Response response = factory.createRequest(file.path)
                .delete();

        int responseCode = response.getResponseCode();
        if (responseCode != 204) {
            throw new IllegalStateException("Request is unsuccessful: " + responseCode + " " + response.asString());
        }
    }

    @Override
    public void put(String path, byte[] bytes) throws IOException {
        Response response = factory.createRequest(path)
                .data(bytes, ContentType.BINARY)
                .put();

        int responseCode = response.getResponseCode();
        if (responseCode != 200) {
            throw new IllegalStateException("Request is unsuccessful: " + responseCode + " " + response.asString());
        }
    }

    @Override
    public void put(String path, File file) throws IOException {
        Response response = factory.createRequest(path)
                .data(file, ContentType.BINARY)
                .put();

        int responseCode = response.getResponseCode();
        if (responseCode != 200) {
            throw new IllegalStateException("Request is unsuccessful: " + responseCode + " " + response.asString());
        }
    }

    @Override
    public void move(S3File file, String destination) throws IOException {
        Response response = factory.createRequest(destination)
                .header("x-amz-copy-source", "/" + factory.bucket + file.path)
                .put();

        int responseCode = response.getResponseCode();
        if (responseCode != 200) {
            throw new IllegalStateException("Request is unsuccessful: " + responseCode + " " + response.asString());
        }
        
        delete(file);
    }

    public static class S3File extends FileInfo {
        public String path;
    }
}
