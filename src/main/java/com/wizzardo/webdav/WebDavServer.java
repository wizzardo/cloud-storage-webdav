package com.wizzardo.webdav;

import com.wizzardo.cloud.storage.FileInfo;
import com.wizzardo.cloud.storage.Storage;
import com.wizzardo.cloud.storage.fs.LocalStorage;
import com.wizzardo.epoll.*;
import com.wizzardo.http.*;
import com.wizzardo.http.framework.WebApplication;
import com.wizzardo.http.request.Header;
import com.wizzardo.http.response.RangeResponseHelper;
import com.wizzardo.http.response.Status;
import com.wizzardo.tools.misc.Unchecked;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static com.wizzardo.http.request.Request.Method.*;

public class WebDavServer {

    public static final byte[] HELLO_WORLD = "Hello, World!".getBytes();

    static ThreadLocal<ByteBufferProvider> byteBufferProviderThreadLocal = ThreadLocal.<ByteBufferProvider>withInitial(() -> {
        ByteBufferWrapper wrapper = new ByteBufferWrapper(64 * 1024);
        return () -> wrapper;
    });

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
        System.out.println("epoll native is supported: " + EpollCore.SUPPORTED);
//        String rootPath = "/users/wizzardo/Downloads";
//        String rootPath = "/users/wizzardo/Pictures";
        String rootPath = "/users/wizzardo/projects/alpine-nginx-webdav/share";
        File publicDir = new File(rootPath);

        HttpServer<?> application = new WebApplication(args);
        Handler helloWorldHandler = (request, response) -> response.setBody(HELLO_WORLD)
                .appendHeader(Header.KV_CONTENT_TYPE_TEXT_PLAIN);

        String baseHostName = "http://localhost:8080";

        Storage storage = new LocalStorage(publicDir);
//        Storage storage = new DegooStorage("moxathedark@gmail.com", "qwerty123");
        ExecutorService executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());
            return thread;
        });


        application.getUrlMapping()
//                .append("/*", (request, response) -> {
//                    RequestBody body = request.getBody();
//                    String req = body != null ? new String(body.bytes(), StandardCharsets.UTF_8) : "";
//                    String resp = TestWebDavClient.webdav_local(request.method().name(), request.path().toString(), req, request.headers());
//                    System.out.println("webdav.request:");
//                    System.out.println(req);
//                    System.out.println("webdav.response:");
//                    System.out.println(resp);
//
//                    String[] data = resp.split("\r\n\r\n");
//
//                    String[] headers = data[0].split("\r\n");
//                    for (String header : headers) {
//                        if (header.startsWith("DAV")) {
//                            response.appendHeader((header + "\r\n").getBytes(StandardCharsets.UTF_8));
//                        }
//                        if (header.startsWith("Allow")) {
//                            response.appendHeader((header + "\r\n").getBytes(StandardCharsets.UTF_8));
//                        }
//                    }
//                    String s = resp.substring(9);
//                    s = s.substring(0, s.indexOf(" "));
//                    response.status(Status.valueOf(Integer.parseInt(s)));
//                    if (data.length > 1)
//                        response.body(data[1]);
//                    else
//                        response.body("");
//
//                    return response;
//                })
                .append("/*", new WebDavHandler()
                                .setHandler(GET, (request, response) -> {
                                    String path = request.path().toString();
                                    path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                                    System.out.println("GET path: " + path);
                                    FileInfo file = storage.getInfo(path);
                                    if (file == null)
                                        return response.setStatus(Status._404);

                                    response.appendHeader(Header.KEY_ACCEPT_RANGES, Header.VALUE_BYTES);

                                    RangeResponseHelper.Range range;
                                    String rangeHeader = request.header(Header.KEY_RANGE);

                                    if (rangeHeader != null) {
                                        long length = file.size;
                                        range = new RangeResponseHelper.Range(rangeHeader, length);
                                        if (!range.isValid()) {
                                            response.setStatus(Status._416);
                                            return response;
                                        }

                                        response.setStatus(Status._206);
                                        response.appendHeader(Header.KEY_CONTENT_RANGE, range.toString());
                                    } else {
                                        Date modifiedSince = request.headerDate(Header.KEY_IF_MODIFIED_SINCE);
                                        if (modifiedSince != null && modifiedSince.getTime() >= file.updated)
                                            return response.status(Status._304);

                                        range = new RangeResponseHelper.Range(0, file.size - 1, file.size);
                                    }

                                    response.appendHeader(Header.KEY_LAST_MODIFIED, HttpDateFormatterHolder.get().format(new Date(file.updated)));

                                    response.appendHeader(Header.KEY_CONTENT_LENGTH, String.valueOf(range.length()));
                                    response.appendHeader(Header.KEY_CONNECTION, Header.VALUE_CLOSE);

                                    String mimeType = request.connection().getServer().getMimeProvider().getMimeType(file.name);
                                    if (mimeType != null)
                                        response.appendHeader(Header.KEY_CONTENT_TYPE, mimeType);

                                    response.async();

                                    executorService.submit(() -> {
                                        try {
                                            response.setBody(storage.getData(file, range.from, range.to + 1));
                                        } catch (IOException e) {
                                            throw Unchecked.rethrow(e);
                                        }
                                        ByteBufferProvider bufferProvider = byteBufferProviderThreadLocal.get();
                                        response.commit(request.connection(), bufferProvider);
                                        request.connection().flush(bufferProvider);
                                    });

                                    return response;
                                })
                                .setHandler(PUT, (request, response) -> {
                                    String path = request.path().toString();
                                    path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                                    System.out.println("PUT path: " + path);
                                    FileInfo file = storage.getInfo(path);
//                            if (file != null)
//                                return response.setStatus(Status._400);
                                    if (request.getBody() != null)
                                        storage.put(path, request.getBody().bytes());
                                    else {
                                        long l = request.contentLength();
                                        if (l == 0) {
                                            storage.put(path, new byte[0]);
                                        } else {
                                            AtomicLong toRead = new AtomicLong(l);
                                            File tempFile = File.createTempFile("put", "tmp");
                                            FileOutputStream stream = new FileOutputStream(tempFile);
                                            byte[] buffer = new byte[64 * 1024];
                                            String finalPath = path;
                                            request.connection().onRead((connection, bufferProvider) -> {
                                                int read = connection.read(buffer, 0, (int) Math.min(buffer.length, toRead.get()), bufferProvider);
                                                boolean error = false;
                                                if (read == -1 && toRead.get() > 0)
                                                    error = true;

                                                bufferProvider.getBuffer().clear();
                                                if (!error)
                                                    stream.write(buffer, 0, read);

                                                if (error || toRead.addAndGet(-read) == 0) {
                                                    stream.close();
                                                    if (!error)
                                                        storage.put(finalPath, tempFile);

                                                    tempFile.delete();
                                                    if (error)
                                                        response.status(Status._500);
                                                    else if (file == null) {
                                                        response.status(Status._201);
                                                    } else {
                                                        response.status(Status._200);
                                                    }
                                                    response.setHeader(Header.KEY_CONNECTION, Header.VALUE_CLOSE);
                                                    response.commit((HttpConnection) connection);
                                                    ((HttpConnection<?, ?, ?>) connection).flush(bufferProvider);
                                                    connection.onRead((ReadListener<Connection>) null);
                                                    response.reset();
                                                    request.reset();
                                                }
                                            });

                                            response.async();
                                            return response;
                                        }
                                    }
                                    if (file == null) {
                                        return response.status(Status._201);
                                    } else {
                                        return response.status(Status._200);
                                    }
                                })
//                        .setHandler(POST, helloWorldHandler)
                                .setHandler(DELETE, (request, response) -> {
                                    String path = request.path().toString();
                                    path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                                    System.out.println("DELETE path: " + path);
                                    FileInfo file = storage.getInfo(path);
                                    if (file == null)
                                        return response.setStatus(Status._400);

                                    storage.delete(file);

                                    return response.status(Status._204);
                                })
//                        .setHandler(PATCH, helloWorldHandler)
//                        .setHandler(COPY, helloWorldHandler)
//                        .setHandler(LOCK, helloWorldHandler)
//                        .setHandler(UNLOCK, helloWorldHandler)
                                .setHandler(MKCOL, (request, response) -> {
                                    String path = request.path().toString();
                                    path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                                    System.out.println("MKCOL path: " + path);
                                    FileInfo file = storage.getInfo(path);
                                    if (file != null)
                                        return response.setStatus(Status._400);

                                    storage.createFolder(path);

                                    return response.status(Status._201);
                                })
                                .setHandler(MOVE, (request, response) -> {
                                    String path = request.path().toString();
                                    path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                                    System.out.println("MOVE path: " + path);
                                    FileInfo file = storage.getInfo(path);
                                    if (file == null)
                                        return response.setStatus(Status._400);

                                    String destination = request.header("Destination");
                                    if (destination == null)
                                        return response.setStatus(Status._400);

                                    storage.move(file, destination);

                                    return response.status(Status._201);
                                })
                                .setHandler(PROPFIND, new PropfindHandler(storage))
//                        .setHandler(PROPPATCH, helloWorldHandler)
                )
        ;
//        application.setIoThreadsCount(1);
//        application.setWorkersCount(4);
        application.setPort(8080);
        application.start();
//        application.setDebugOutput(false);
        application.setDebugOutput(true);
    }
}
