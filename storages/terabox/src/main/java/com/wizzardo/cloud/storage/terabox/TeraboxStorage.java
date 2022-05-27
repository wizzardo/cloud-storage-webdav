package com.wizzardo.cloud.storage.terabox;

import com.wizzardo.cloud.storage.FileInfo;
import com.wizzardo.cloud.storage.Storage;
import com.wizzardo.tools.cache.Cache;
import com.wizzardo.tools.http.Request;
import com.wizzardo.tools.http.Response;
import com.wizzardo.tools.io.FileTools;
import com.wizzardo.tools.io.IOTools;
import com.wizzardo.tools.json.JsonArray;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;
import com.wizzardo.tools.misc.Unchecked;
import com.wizzardo.tools.security.MD5;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TeraboxStorage implements Storage<TeraboxStorage.TeraboxFile> {

    public static final int PART_SIZE = 4194304;
    protected String ndus = "";
    protected Cache<Boolean, Session> sessionHolder = new Cache<>(-1, aBoolean -> refreshSession());
    protected Cache<String, TeraboxFile> filesByPath = new Cache<>(600);
    protected Cache<Long, TeraboxFile> filesById = new Cache<>(600);
    protected Cache<Long, List<TeraboxFile>> filesByParentId = new Cache<>(60);

    private Session refreshSession() throws IOException {
        Response response = new Request("https://www.terabox.com/disk/home#/all?path=%2F&vmode=list")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
                .cookies("ndus="+ndus)
                .get();

        String html = response.asString();
        if (response.getResponseCode() != 200) {
            System.out.println(response.getResponseCode());
            System.out.println(html);
            throw new IllegalStateException("Session refresh failed");
        }

        int from = html.indexOf("var context=");
        int to = html.indexOf("var yunData =", from);
        String context = html.substring(from + 12, to).trim();
        context = context.substring(0, context.length() - 1);
        JsonObject json = JsonTools.parse(context).asJsonObject();

        Session session = new Session(json.getAsString("bdstoken"),
                json.getAsString("sign1"),
                json.getAsString("sign2"),
                json.getAsString("sign3"),
                json.getAsLong("timestamp")
        );
        System.out.println(session);
        return session;
    }

    static class Session {
        final String bdstoken;
        final String sign1;
        final String sign2;
        final String sign5;
        final long timestamp;

        Session(String bdstoken, String sign1, String sign2, String sign5, long timestamp) {
            this.bdstoken = bdstoken;
            this.sign1 = sign1;
            this.sign2 = sign2;
            this.sign5 = sign5;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "Session{" +
                    "bdstoken='" + bdstoken + '\'' +
                    ", sign1='" + sign1 + '\'' +
                    ", sign2='" + sign2 + '\'' +
                    ", sign5='" + sign5 + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    static class ListResponse extends ApiResponse {
        List<File> list;
        String guid_info;

        static class File {
            long server_mtime;
            long server_ctime;
            long fs_id;
            long size;
            int isdir;
            String server_filename;
            String path;
            String md5;
        }
    }

    @Override
    public List<TeraboxFile> list(TeraboxFile fileInfo) throws IOException {

        {
            if (fileInfo != null) {
                List<TeraboxFile> list = filesByParentId.get(fileInfo.id);
                if (list != null)
                    return list;
            }
        }

        Session session = sessionHolder.get(true);
        ListResponse listResponse = makeGetRequest("/api/list", ListResponse.class, request -> {
            if (fileInfo != null)
                request.param("order", "name")
                        .param("desc", "0")
                        .param("showempty", "0")
                        .param("web", "1")
                        .param("page", "1")
                        .param("num", "100")
                        .param("dir", fileInfo.path)
                        .param("channel", "dubox")
                        .param("app_id", "250528")
                        .param("clienttype", "0")
                        .param("bdstoken", session.bdstoken)
                        .param("t", 0.9142615190154437);
        }, false);
        List<TeraboxFile> list = listResponse.list.stream()
                .map(file -> {
                    TeraboxFile f = new TeraboxFile();
                    f.id = file.fs_id;
                    f.path = file.path;
                    f.created = file.server_ctime;
                    f.updated = file.server_mtime;
                    f.name = file.server_filename;
                    f.size = file.size;
                    if (file.isdir == 1)
                        f.type = FileInfo.Type.FOLDER;
                    else
                        f.type = FileInfo.Type.FILE;

                    return f;
                })
                .peek(it -> {
                    filesById.put(it.id, it);
                    filesByPath.put(it.path, it);
                }).collect(Collectors.toList());

        if (fileInfo != null)
            filesByParentId.put(fileInfo.id, list);

        return list;
    }

    @Override
    public TeraboxFile getInfo(String path) throws IOException {
        TeraboxFile file = filesByPath.get(path);
        if (file != null)
            return file;

        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        int i = path.lastIndexOf("/");
        if (i == -1) {
            if (path.isEmpty()) {
                list();
                return filesByPath.get(path);
            }
            throw new IllegalArgumentException();
        }

        String name = path.substring(i + 1);
        if (name.startsWith("."))
            return null;

        String parent = path.substring(0, i);
        TeraboxFile parentInfo = getInfo(parent);
        if (parentInfo == null)
            return filesByPath.get(path);

        if ("/".equals(path)) {
            return parentInfo;
        }

        list(parentInfo);
        return filesByPath.get(path);
    }

    @Override
    public long getTotalSpace() throws IOException {
        return 0;
    }

    @Override
    public long getUsableSpace() throws IOException {
        return 0;
    }

    static class FolderCreatedResponse extends ApiResponse {
        long ctime;
        long mtime;
        long fs_id;
        int isdir;
        String name;
        String path;
    }

    @Override
    public void createFolder(String path) throws IOException {
        Session session = sessionHolder.get(true);
        makePostRequest("/api/create?a=commit&channel=dubox&web=1&app_id=250528&clienttype=0&bdstoken=" + session.bdstoken, FolderCreatedResponse.class, request -> {
            request.param("path", path);
            request.param("isdir", 1);
            request.param("block_list", "[]");
        }, false);

        //todo invalidate cache for parent folder
    }

    static class FileManagerOperationResponse extends ApiResponse {
        long taskid;
    }

    static class TaskQueryResponse extends ApiResponse {
        String status;
        int total;
        List<Path> list;

        static class Path {
            String path;
        }
    }

    @Override
    public void delete(TeraboxFile path) throws IOException {
        Session session = sessionHolder.get(true);
        String filelist = new JsonArray().append(path.path).toString();
        FileManagerOperationResponse deleteOperationResponse = makePostRequest("/api/filemanager?opera=delete&async=2&onnest=fail&channel=dubox&web=1&app_id=250528&clienttype=0&bdstoken=" + session.bdstoken, FileManagerOperationResponse.class, request -> {
            request.param("filelist", filelist);
        }, false);

        Unchecked.ignore(() -> Thread.sleep(1000));

        makePostRequest("/share/taskquery?channel=dubox&web=1&app_id=250528&clienttype=0&bdstoken=" + session.bdstoken + "&taskid=" + deleteOperationResponse.taskid, TaskQueryResponse.class, request -> {
            request.param("filelist", filelist);
        }, false);
    }

    @Override
    public void put(String path, byte[] bytes) throws IOException {
        put(path, new ByteArrayUpload(bytes));
    }

    @Override
    public void put(String path, File file) throws IOException {
        put(path, new FileUpload(file));
    }

    static class PreCreateResponse extends ApiResponse {
        int return_type;
        String path;
        String uploadid;
    }

    static class UploadResponse extends ApiResponse {
        int partseq;
        String md5;
        String uploadid;
    }

    static class CreateResponse extends ApiResponse {
        long ctime;
        long mtime;
        long fs_id;
        long size;
        int isdir;
        String server_filename;
        String path;
        String name;
        String md5;
    }

    public void put(String path, UploadPayload payload) throws IOException {
        Session session = sessionHolder.get(true);
        String folder = path.substring(0, path.lastIndexOf('/') + 1);

        System.out.println("put path: " + path + "; folder: " + folder);

        JsonArray parts = new JsonArray();
//        if (payload.size() <= 4194304)
//            parts.append("5910a591dd8fc18c32a8f3df4fdc1761");
//        else {
        parts.appendAll(getParts(payload));
//        }

        System.out.println("parts: " + parts);
        PreCreateResponse preCreateResponse = makePostRequest("/api/precreate?channel=dubox&web=1&app_id=250528&clienttype=0&bdstoken=" + session.bdstoken, PreCreateResponse.class, request -> {
            request.param("path", path)
                    .param("autoinit", 1)
                    .param("target_path", folder)
                    .param("block_list", parts.toString())
                    .param("local_mtime", System.currentTimeMillis() / 1000);
        }, false);

        JsonArray responses = new JsonArray();
        {
            byte[] buffer = new byte[(int) Math.min(PART_SIZE, payload.size())];

            InputStream in;

            if (payload.isFile())
                in = new FileInputStream(payload.asFile());
            else if (payload.isByteArray())
                in = new ByteArrayInputStream(payload.asByteArray());
            else
                throw new IllegalArgumentException("Unknown payload type");

            try {
                for (int i = 0; i < parts.size(); i++) {
                    String uploadUrl = new Request("https://c-jp.terabox.com/rest/2.0/pcs/superfile2")
                            .param("method", "upload")
                            .param("app_id", "250528")
                            .param("channel", "dubox")
                            .param("clienttype", "0")
                            .param("web", "1")
                            .param("uploadsign", "0")
                            .param("partseq", i)
                            .param("path", path)
                            .param("uploadid", preCreateResponse.uploadid)
                            .getUrl();

                    Request request = new Request(uploadUrl)
                            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
                            .cookies("ndus=" + ndus);

                    int nameSeparator = path.lastIndexOf('/');
                    String name = path.substring(nameSeparator + 1);

//                if (payload.isFile())
//                    request.addFile("file", payload.asFile().getCanonicalPath());
//                else if (payload.isByteArray())
//                    request.addByteArray("file", payload.asByteArray(), name);
//                else
//                    throw new IllegalArgumentException("Unknown payload type");

                    int offset = 0;
                    int r;
                    while ((r = in.read(buffer, offset, buffer.length - offset)) != -1) {
                        offset += r;
                        if (offset == buffer.length)
                            break;
                    }
                    request.addByteArray("file", buffer, name);

                    Response response = request.post();

                    int responseCode = response.getResponseCode();
                    String text = response.asString();
                    if (responseCode != 200)
                        throw new IllegalStateException(responseCode + " " + text);

                    System.out.println("response: " + responseCode);
                    System.out.println(text);

                    UploadResponse uploadResponse = JsonTools.parse(text, UploadResponse.class);

                    if (uploadResponse.errno != 0)
                        throw new IllegalStateException(uploadResponse.errno + " " + text);

                    responses.append(uploadResponse.md5);
                }
            } finally {
                IOTools.close(in);
            }
        }

        CreateResponse createResponse = makePostRequest("/api/create?channel=dubox&web=1&app_id=250528&clienttype=0&bdstoken=" + session.bdstoken, CreateResponse.class, request -> {
            request.param("path", path)
                    .param("size", payload.size())
                    .param("uploadid", preCreateResponse.uploadid)
                    .param("target_path", folder)
                    .param("block_list", responses.toString())
                    .param("local_mtime", System.currentTimeMillis() / 1000);
        }, false);
    }

    private List<String> getParts(UploadPayload payload) throws IOException {
        if (payload.isFile()) {
            return getParts(payload.asFile());
        }
        if (payload.isByteArray()) {
            return getParts(payload.asByteArray());
        }
        return null;
    }

    private List<String> getParts(byte[] bytes) {
        ArrayList<String> parts = new ArrayList<>();
        int length = bytes.length;
        int n = (int) (length * 1.0 / PART_SIZE + 0.5);
        for (int i = 0; i < n; i++) {
            MD5 md5 = MD5.create();
            md5.update(bytes, PART_SIZE * i, Math.min(PART_SIZE, length - PART_SIZE * i));
            parts.add(md5.asString());
        }
        return parts;
    }

    private List<String> getParts(File file) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        ArrayList<String> parts = new ArrayList<>();
        try (
                InputStream in = new FileInputStream(file);
        ) {
            long length = file.length();
            int n = (int) Math.ceil(length * 1.0 / PART_SIZE);
            for (int i = 0; i < n; i++) {
                MD5 md5 = MD5.create();
                int toRead = PART_SIZE;
                int r;
                while ((r = in.read(buffer, 0, Math.min(buffer.length, toRead))) != -1) {
                    md5.update(buffer, 0, r);
                    toRead -= r;
                    if (toRead == 0)
                        break;
                }
                parts.add(md5.asString());
            }
        }
        return parts;
    }

    @Override
    public void move(TeraboxFile file, String destination) throws IOException {
        int nameSeparator = destination.lastIndexOf('/');
        String name = destination.substring(nameSeparator + 1);
        String folder = destination.substring(0, nameSeparator);
        String filelist = new JsonArray().append(new JsonObject()
                .append("path", file.path)
                .append("dest", folder)
                .append("newname", name)).toString();

        System.out.println("move "+filelist);
        Session session = sessionHolder.get(true);
        FileManagerOperationResponse taskQueryResponse = makePostRequest("/api/filemanager?opera=move&async=2&onnest=fail&channel=dubox&web=1&app_id=250528&clienttype=0&bdstoken=" + session.bdstoken, FileManagerOperationResponse.class, request -> {
            request.param("filelist", filelist);
        }, false);

        Unchecked.ignore(() -> Thread.sleep(1000));

        makePostRequest("/share/taskquery?channel=dubox&web=1&app_id=250528&clienttype=0&bdstoken=" + session.bdstoken + "&taskid=" + taskQueryResponse.taskid, TaskQueryResponse.class, request -> {
            request.param("filelist", filelist);
        }, false);
    }

    @Override
    public byte[] getData(TeraboxFile file, long from, long to) throws IOException {
        Request request = new Request(getDownloadLink(file))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
                .cookies("ndus=" + ndus);

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
    public InputStream getStream(TeraboxFile file) throws IOException {
        Request request = new Request(getDownloadLink(file))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
                .cookies("ndus=" + ndus);

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

    static class DownloadLinkResponse extends ApiResponse {
        List<Link> dlink;

        static class Link {
            String dlink;
            long fs_id;
        }
    }

    protected String getDownloadLink(TeraboxFile file) throws IOException {
        Session session = sessionHolder.get(true);

        DownloadLinkResponse downloadLinkResponse = makeGetRequest("/api/download", DownloadLinkResponse.class, request -> {
            request.param("clienttype", 0)
                    .param("sign", base64String(sign(session.sign5, session.sign1)))
                    .param("fidlist", new JsonArray().append(file.id))
                    .param("timestamp", session.timestamp);
        }, false);

        return downloadLinkResponse.dlink.stream().filter(it -> it.fs_id == file.id).findFirst().get().dlink;
    }


    static public class TeraboxFile extends FileInfo {
        long id;
        String path;

        @Override
        public String toString() {
            return "TeraboxFile{" +
                    "id=" + id +
                    ", path='" + path + '\'' +
                    ", name='" + name + '\'' +
                    ", size=" + size +
                    ", created=" + created +
                    ", updated=" + updated +
                    ", type=" + type +
                    '}';
        }
    }

    static class ApiResponse {
        int errno;
        long request_id;
    }

    protected <T extends ApiResponse> T makePostRequest(String path, Class<T> asClass, Consumer<Request> requestConsumer, boolean retry) throws IOException {
        Request request = new Request("https://www.terabox.com" + path)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
                .cookies("ndus=" + ndus);

        requestConsumer.accept(request);

        Response response = request.post();

        int responseCode = response.getResponseCode();
        String text = response.asString();
        if (responseCode != 200)
            throw new IllegalStateException(responseCode + " " + text);

        System.out.println("response: " + responseCode);
        System.out.println(text);
        T result = JsonTools.parse(text, asClass);

        if (result.errno != 0)
            throw new IllegalStateException(result.errno + " " + text);

        return result;
    }

    protected <T extends ApiResponse> T makeGetRequest(String path, Class<T> asClass, Consumer<Request> requestConsumer, boolean retry) throws IOException {
        Request request = new Request("https://www.terabox.com" + path)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36")
                .cookies("ndus=" + ndus);

        requestConsumer.accept(request);

        Response response = request.get();

        int responseCode = response.getResponseCode();
        String text = response.asString();
        if (responseCode != 200)
            throw new IllegalStateException(responseCode + " " + text);

        System.out.println("response: " + responseCode);
        System.out.println(text);
        T result = JsonTools.parse(text, asClass);

        if (result.errno != 0)
            throw new IllegalStateException(result.errno + " " + text);

        return result;
    }


    interface UploadPayload {
        default boolean isFile() {
            return false;
        }

        default boolean isByteArray() {
            return false;
        }

        long size();

        default File asFile() {
            throw new IllegalStateException();
        }

        default byte[] asByteArray() {
            throw new IllegalStateException();
        }
    }

    static class FileUpload implements UploadPayload {
        final File file;

        FileUpload(File file) {
            this.file = file;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public long size() {
            return file.length();
        }

        @Override
        public File asFile() {
            return file;
        }
    }

    static class ByteArrayUpload implements UploadPayload {
        final byte[] bytes;

        ByteArrayUpload(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean isByteArray() {
            return true;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public byte[] asByteArray() {
            return bytes;
        }
    }

    static int[] sign(String sign5, String sign1) {
        var a = new int[256];
        var p = new int[256];
        var o = new int[sign1.length()];
        var v = sign5.length();
        for (var q = 0; q < 256; q++) {
            int beginIndex = q % v;
            a[q] = sign5.substring(beginIndex, beginIndex + 1).charAt(0);
            p[q] = q;
        }
        for (int q = 0, u = 0; q < 256; q++) {
            u = (u + p[q] + a[q]) % 256;
            var t = p[q];
            p[q] = p[u];
            p[u] = t;
        }
        for (int i = 0, u = 0, q = 0; q < sign1.length(); q++) {
            i = (i + 1) % 256;
            u = (u + p[i]) % 256;
            var t = p[i];
            p[i] = p[u];
            p[u] = t;
            int k = p[((p[i] + p[u]) % 256)];
            o[q] = sign1.charAt(q) ^ k;
        }
        return o;
    }

    static String base64String(int[] t) {
        StringBuilder e = new StringBuilder(64);
        int r;
        int n;
        int o;
        int i;
        int a;
        var s = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (n = t.length, r = 0; n > r; ) {
            o = 255 & t[r++];
            if (r == n) {
                e.append(s.charAt(o >> 2))
                        .append(s.charAt((3 & o) << 4))
                        .append("==");
                break;
            }

            i = t[r++];
            if (r == n) {
                e.append(s.charAt(o >> 2))
                        .append(s.charAt((3 & o) << 4 | (240 & i) >> 4))
                        .append(s.charAt((15 & i) << 2)).append("=");
                break;
            }

            a = t[r++];
            e.append(s.charAt(o >> 2))
                    .append(s.charAt((3 & o) << 4 | (240 & i) >> 4))
                    .append(s.charAt((15 & i) << 2 | (192 & a) >> 6))
                    .append(s.charAt(63 & a));
        }
        return e.toString();
    }
}
