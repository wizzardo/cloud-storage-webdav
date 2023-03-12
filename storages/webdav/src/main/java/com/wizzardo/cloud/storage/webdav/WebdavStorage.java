package com.wizzardo.cloud.storage.webdav;

import com.wizzardo.cloud.storage.FileInfo;
import com.wizzardo.cloud.storage.Storage;
import com.wizzardo.tools.http.ConnectionMethod;
import com.wizzardo.tools.http.ContentType;
import com.wizzardo.tools.http.Request;
import com.wizzardo.tools.http.Response;
import com.wizzardo.tools.misc.Unchecked;
import com.wizzardo.tools.reflection.FieldReflection;
import com.wizzardo.tools.reflection.FieldReflectionFactory;
import com.wizzardo.tools.xml.Node;
import com.wizzardo.tools.xml.XmlParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class WebdavStorage implements Storage<WebdavStorage.WebdavFile> {

    enum WebdavMethod implements ConnectionMethod {
        COPY(),
        LOCK(),
        MKCOL(),
        MOVE(),
        PROPFIND(true),
        PROPPATCH(true),
        UNLOCK();

        final boolean withBody;

        WebdavMethod(boolean withBody) {
            this.withBody = withBody;
        }

        WebdavMethod() {
            this.withBody = false;
        }

        @Override
        public boolean withBody() {
            return withBody;
        }
    }

    static {
        FieldReflectionFactory reflectionFactory = new FieldReflectionFactory();
        try {
            FieldReflection fieldReflection = reflectionFactory.create(HttpURLConnection.class, "methods", true);
            reflectionFactory.removeFinalModifier(fieldReflection.getField());
            fieldReflection.setObject(null, new String[]{
                    ConnectionMethod.HTTPMethod.GET.name(),
                    ConnectionMethod.HTTPMethod.POST.name(),
                    ConnectionMethod.HTTPMethod.HEAD.name(),
                    ConnectionMethod.HTTPMethod.OPTIONS.name(),
                    ConnectionMethod.HTTPMethod.PUT.name(),
                    ConnectionMethod.HTTPMethod.DELETE.name(),
                    ConnectionMethod.HTTPMethod.TRACE.name(),
                    ConnectionMethod.HTTPMethod.CONNECT.name(),
                    ConnectionMethod.HTTPMethod.PATCH.name(),
                    WebdavMethod.COPY.name(),
                    WebdavMethod.MOVE.name(),
                    WebdavMethod.LOCK.name(),
                    WebdavMethod.UNLOCK.name(),
                    WebdavMethod.PROPPATCH.name(),
                    WebdavMethod.PROPFIND.name(),
                    WebdavMethod.MKCOL.name(),
            });
        } catch (NoSuchFieldException ignored) {
        }
    }

    public static class HttpDateFormatterHolder {

        public static ThreadLocal<SimpleDateFormat> formatter = ThreadLocal.withInitial(() -> {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            return format;
        });

        public static SimpleDateFormat get() {
            return formatter.get();
        }
    }

    protected String base;
    protected String username;
    protected String password;

    public WebdavStorage(String url, String username, String password) {
        this.base = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public List<WebdavFile> list(WebdavFile fileInfo) throws IOException {
        throw new IllegalStateException("Not implemented yet");
    }

    protected Request createRequest(String path) {
        Request request = new Request(base + path);
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            request.setBasicAuthentication(username, password);
        }
        return request;
    }

    @Override
    public WebdavFile getInfo(String path) throws IOException {
        byte[] xml = new Node("D:propfind").attr("xmlns:D", "DAV:")
                .add(
                        new Node("D:prop")
                                .add(new Node("D:getlastmodified"))
                                .add(new Node("D:getcontentlength"))
                                .add(new Node("D:creationdate"))
                                .add(new Node("D:resourcetype"))
                )
                .toXML().getBytes(StandardCharsets.UTF_8);

        Response response = createRequest(path)
                .header("Depth", "0")
                .data(xml, ContentType.XML)
                .setMethod(WebdavMethod.PROPFIND)
                .execute();

        if (response.getResponseCode() == 404)
            return null;

        if (response.getResponseCode() >= 400) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }

        Node node = new XmlParser<>().parse(response.asString("UTF-8"));
        Node dresponse = node.get(0);
        String href = dresponse.get(0).text();
        Node props = dresponse.get(1).get(1);
        WebdavFile webdavFile = new WebdavFile();
        webdavFile.path = href;
        webdavFile.name = href.substring(href.lastIndexOf('/') + 1);

        for (Node prop : props.children()) {
            if ("d:getcontentlength".equals(prop.name())) {
                webdavFile.size = Long.parseLong(prop.text());
            } else if ("d:getlastmodified".equals(prop.name())) {
                webdavFile.updated = Unchecked.call(() -> HttpDateFormatterHolder.get().parse(prop.text()).getTime());
            } else if ("d:resourcetype".equals(prop.name())) {
                webdavFile.type = prop.children().isEmpty() ? FileInfo.Type.FILE : FileInfo.Type.FOLDER;
            }
        }
        return webdavFile;
    }

    @Override
    public InputStream getStream(String path) throws IOException {
        Response response = createRequest(path).get();

        System.out.println("downloading as stream " + path);
        int contentLength = response.getContentLength();
        System.out.println(response.getResponseCode() + " length: " + contentLength);
        if (response.getResponseCode() != 200) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }

        InputStream[] stream = new InputStream[]{response.asStream()};
        return new InputStream() {
            int total = 0;

            @Override
            public int read() throws IOException {
                throw new RuntimeException("Not implemented yet");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int read = stream[0].read(b, off, len);
                if (read == -1 && total != contentLength) {
                    stream[0] = createRequest(path)
                            .header("Range", "bytes=" + (total) + "-" + (contentLength - 1))
                            .get()
                            .asStream();
                    read = stream[0].read(b, off, len);
                }
                if (read != -1) {
                    total += read;
                }

                return read;
            }

            @Override
            public void close() throws IOException {
                stream[0].close();
            }
        };
    }

    @Override
    public InputStream getStream(WebdavFile file) throws IOException {
        return getStream(file.path);
    }

    @Override
    public byte[] getData(WebdavFile file, long from, long to) throws IOException {
        Request request = createRequest(file.path);
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
    public long getTotalSpace() throws IOException {
        return 0;
    }

    @Override
    public long getUsableSpace() throws IOException {
        return 0;
    }

    @Override
    public void createFolder(String path) throws IOException {
        WebdavFile info = getInfo(path);
        if (info != null) {
            if (info.isFolder())
                return;

            throw new IllegalStateException("File with same name already exists: " + path);
        }

        String parent = path.substring(0, path.lastIndexOf('/', path.length() - 2));

        if (!parent.isEmpty())
            createFolder(parent);

        Response response = createRequest(path)
                .setMethod(WebdavMethod.MKCOL)
                .execute();

        System.out.println(response.getResponseCode() + " length: " + response.getContentLength());
        if (response.getResponseCode() >= 400) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }
    }

    @Override
    public void delete(String path) throws IOException {
        Response response = createRequest(path).delete();

        if (response.getResponseCode() == 404)
            return;

        System.out.println(response.getResponseCode() + " length: " + response.getContentLength());
        if (response.getResponseCode() >= 400) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }
    }

    @Override
    public void delete(WebdavFile file) throws IOException {
        delete(file.path);
    }

    @Override
    public void put(String path, byte[] bytes) throws IOException {
        String parent = path.substring(0, path.lastIndexOf('/', path.length() - 2));

        if (!parent.isEmpty())
            createFolder(parent);

        Response response = createRequest(path)
                .data(bytes, ContentType.BINARY)
                .put();

        System.out.println(response.getResponseCode() + " length: " + response.getContentLength());
        if (response.getResponseCode() >= 400) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }
    }

    @Override
    public void put(String path, File file) throws IOException {
        String parent = path.substring(0, path.lastIndexOf('/', path.length() - 2));

        if (!parent.isEmpty())
            createFolder(parent);

        Response response = createRequest(path)
                .data(file, ContentType.BINARY)
                .put();

        System.out.println(response.getResponseCode() + " length: " + response.getContentLength());
        if (response.getResponseCode() >= 400) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }
    }

    @Override
    public void move(WebdavFile file, String destination) throws IOException {
        String parent = destination.substring(0, destination.lastIndexOf('/', destination.length() - 2));

        if (!parent.isEmpty())
            createFolder(parent);

        Response response = createRequest(file.path)
                .header("Destination", destination)
                .method(WebdavMethod.MOVE)
                .execute();

        System.out.println(response.getResponseCode() + " length: " + response.getContentLength());
        if (response.getResponseCode() >= 400) {
            String s = response.asString();
            System.out.println(s);
            throw new IllegalStateException("Fetch is unsuccessful: " + response.getResponseCode() + " " + s);
        }
    }

    public static class WebdavFile extends FileInfo {
        String path;
    }
}
