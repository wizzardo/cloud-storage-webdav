package com.wizzardo.webdav;

import com.wizzardo.cloud.storage.FileInfo;
import com.wizzardo.cloud.storage.Storage;
import com.wizzardo.http.Handler;
import com.wizzardo.http.HttpConnection;
import com.wizzardo.http.HttpDateFormatterHolder;
import com.wizzardo.http.request.Header;
import com.wizzardo.http.request.Request;
import com.wizzardo.http.response.Response;
import com.wizzardo.http.response.Status;
import com.wizzardo.tools.misc.DateIso8601;
import com.wizzardo.tools.xml.Node;
import com.wizzardo.tools.xml.XmlParser;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PropfindHandler implements Handler {

    protected final Storage storage;

    public PropfindHandler(Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response handle(Request<HttpConnection, Response> request, Response response) throws IOException {
        if (request.getBody() == null)
            return response.setBody("").status(Status._404);

        String x = new String(request.getBody().bytes(), StandardCharsets.UTF_8);
        Node node = new XmlParser<>().parse(x);
        System.out.println(node);

        String path = request.path().toString();
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
        System.out.println("path: " + path + "  decoded: " + decodedPath);
        FileInfo file = storage.getInfo(decodedPath);
        if (file == null)
            return response.setStatus(Status._404);

        String depth = request.header("Depth", "0");

        if (file.isFolder() && depth.equals("0")) {
            List<Node> props = new ArrayList<>();
            Node requestedProps = node.get(0);
            for (Node child : requestedProps.children()) {
                if (child.name().equalsIgnoreCase("d:resourcetype")) {
                    props.add(new Node("D:resourcetype").add(new Node("D:collection")));
//                } else if (child.name().equalsIgnoreCase("d:quota")) {
//                    props.add(new Node("D:quota").addText(String.valueOf(storage.getTotalSpace())));
//                } else if (child.name().equalsIgnoreCase("d:quotaused")) {
//                    props.add(new Node("D:quotaused").addText(String.valueOf(storage.getTotalSpace() - storage.getUsableSpace())));
                } else if (child.name().equalsIgnoreCase("d:creationdate")) {
                    props.add(new Node("D:creationdate").addText(DateIso8601.format(new Date(file.created))));
                } else if (child.name().equalsIgnoreCase("d:quota-available-bytes")) {
                    props.add(new Node("D:quota-available-bytes").addText(String.valueOf(storage.getUsableSpace())));
                } else if (child.name().equalsIgnoreCase("d:quota-used-bytes")) {
                    props.add(new Node("D:quota-used-bytes").addText(String.valueOf(storage.getTotalSpace() - storage.getUsableSpace())));
                }
            }

            Node xml = new Node("D:multistatus").attr("xmlns:D", "DAV:")
                    .add(
                            new Node("D:response")
                                    .add(new Node("D:href").addText(path))
                                    .add(new Node("D:propstat")
                                            .add(new Node("D:status").addText("HTTP/1.1 200 OK"))
                                            .add(new Node("D:prop").addAll(props))
                                    )
                    );
            return response.setStatus(Status._207).appendHeader(Header.KV_CONTENT_TYPE_TEXT_XML).body(xml.toXML());
        }

        if (file.isFolder() && depth.equals("1")) {
            List<Node> props = new ArrayList<>();
            Node requestedProps = node.get(0);
            for (Node child : requestedProps.children()) {
                String name = readName(child).toLowerCase();
                switch (name) {
                    case "resourcetype":
                        props.add(new Node("D:resourcetype").add(new Node("D:collection")));
//                } else if (child.name().equalsIgnoreCase("d:quota")) {
//                    props.add(new Node("D:quota").addText(String.valueOf(storage.getTotalSpace())));
//                } else if (child.name().equalsIgnoreCase("d:quotaused")) {
//                    props.add(new Node("D:quotaused").addText(String.valueOf(storage.getTotalSpace() - storage.getUsableSpace())));
                        break;
                    case "creationdate":
                        props.add(new Node("D:creationdate").addText(DateIso8601.format(new Date(file.created))));
                        break;
                    case "quota-available-bytes":
                        props.add(new Node("D:quota-available-bytes").addText(String.valueOf(storage.getUsableSpace())));
                        break;
                    case "quota-used-bytes":
                        props.add(new Node("D:quota-used-bytes").addText(String.valueOf(storage.getTotalSpace() - storage.getUsableSpace())));
                        break;
                }
            }

            Node xml = new Node("D:multistatus").attr("xmlns:D", "DAV:");


            xml.add(new Node("D:response")
                    .add(new Node("D:href").addText(path))
                    .add(new Node("D:propstat")
                            .add(new Node("D:status").addText("HTTP/1.1 200 OK"))
                            .add(new Node("D:prop").addAll(props))
                    )
            );
            List<FileInfo> files = storage.list(file);
            for (FileInfo f : files) {
                boolean isFile = f.isFile();
                boolean isDirectory = f.isFolder();
                List<Node> p = new ArrayList<>();
                for (Node child : requestedProps.children()) {
                    String name = readName(child).toLowerCase();
                    switch (name) {
                        case "quota-available-bytes":
                            p.add(new Node("D:quota-available-bytes").addText(String.valueOf(storage.getUsableSpace())));
                            break;
                        case "quota-used-bytes":
                            p.add(new Node("D:quota-used-bytes").addText(String.valueOf(storage.getTotalSpace() - storage.getUsableSpace())));
                            break;
                        case "creationdate":
                            props.add(new Node("D:creationdate").addText(DateIso8601.format(new Date(f.created))));
                            break;
                    }

                    if (isFile) {
                        switch (name) {
                            case "getcontentlength":
                                p.add(new Node("D:getcontentlength").addText(String.valueOf(f.size)));
                                break;
                            case "getlastmodified":
                                p.add(new Node("D:getlastmodified").addText(HttpDateFormatterHolder.get().format(new Date(f.updated))));
                                break;
                            case "resourcetype":
                                p.add(new Node("D:resourcetype"));
                                break;
                        }
                    }

                    if (isDirectory) {
                        if ("resourcetype".equals(name)) {
                            p.add(new Node("D:resourcetype").add(new Node("D:collection")));
                        }
                    }
                }

                String filePath = path;
                if (!path.endsWith("/"))
                    filePath += "/";

                filePath += URLEncoder.encode(f.name, StandardCharsets.UTF_8).replace("+", "%20");

                xml.add(new Node("D:response")
                        .add(new Node("D:href").addText(filePath))
                        .add(new Node("D:propstat")
                                .add(new Node("D:status").addText("HTTP/1.1 200 OK"))
                                .add(new Node("D:prop").addAll(p))
                        )
                );
            }

            return response.setStatus(Status._207).appendHeader(Header.KV_CONTENT_TYPE_TEXT_XML).body(xml.toXML());
        }

        if (file.isFile()) {
            List<Node> props = new ArrayList<>();
            Node requestedProps = node.get(0);
            for (Node child : requestedProps.children()) {
                String name = readName(child).toLowerCase();
                switch (name) {
                    case "resourcetype":
                        props.add(new Node("D:resourcetype"));
                        break;
                    case "getcontentlength":
                        props.add(new Node("D:getcontentlength").addText(String.valueOf(file.size)));
                        break;
                    case "getlastmodified":
                        props.add(new Node("D:getlastmodified").addText(HttpDateFormatterHolder.get().format(new Date(file.updated))));
                        break;
                }
            }

            Node xml = new Node("D:multistatus").attr("xmlns:D", "DAV:");


            xml.add(new Node("D:response")
                    .add(new Node("D:href").addText(path))
                    .add(new Node("D:propstat")
                            .add(new Node("D:status").addText("HTTP/1.1 200 OK"))
                            .add(new Node("D:prop").addAll(props))
                    )
            );

            return response.setStatus(Status._207).appendHeader(Header.KV_CONTENT_TYPE_TEXT_XML).body(xml.toXML());
        }


        return response.setBody("").status(Status._404);

    }

    private String readName(Node child) {
        String name = child.name();
        int i = name.indexOf(":");
        if (i == -1)
            return name;
        else
            return name.substring(i + 1);
    }
}
