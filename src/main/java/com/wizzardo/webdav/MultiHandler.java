package com.wizzardo.webdav;

import com.wizzardo.http.Handler;
import com.wizzardo.http.request.Header;
import com.wizzardo.http.request.Request;
import com.wizzardo.http.response.Response;
import com.wizzardo.http.response.Status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import static com.wizzardo.http.request.Request.Method.OPTIONS;

public class MultiHandler implements Handler {
    private byte[] allow;

    protected EnumMap<Request.Method, Handler> handlers = new EnumMap<>(Request.Method.class);

    protected final Handler options = (request, response) -> response.appendHeader(Header.KEY_CONTENT_LENGTH, String.valueOf(0));

    {
        generateAllowHeader();
    }

    @Override
    public Response handle(Request request, Response response) throws IOException {
        Request.Method method = request.method();

        if (method == OPTIONS)
            return handle(request, response, options);

        Handler handler = handlers.get(method);
        if (handler != null)
            return handle(request, response, handler);
        return response.setStatus(Status._405);
    }

    protected Response handle(Request request, Response response, Handler handler) throws IOException {
        provideHeaders(request, response);
        if (handler == null)
            return response.setStatus(Status._405).appendHeader(Header.KEY_CONTENT_LENGTH, String.valueOf(0));
        else
            return handler.handle(request, response);
    }

    public MultiHandler setHandler(Request.Method method, Handler handler) {
        handlers.put(method, handler);
        generateAllowHeader();
        return this;
    }

    protected Response provideHeaders(Request request, Response response) {
        return response.appendHeader(allow);
    }

    private void generateAllowHeader() {
        StringBuilder sb = new StringBuilder("Allow: ");
        boolean comma = false;
        for (Map.Entry<Request.Method, Handler> entry : handlers.entrySet()) {
            Request.Method method = entry.getKey();
            Handler handler = entry.getValue();
            comma = buildAllowHeaderString(sb, handler, method.name(), comma);
        }

        comma = buildAllowHeaderString(sb, options, "OPTIONS", comma);

        allow = sb.append("\r\n").toString().getBytes(StandardCharsets.UTF_8);
    }

    private boolean buildAllowHeaderString(StringBuilder sb, Handler handler, String name, boolean comma) {
        if (handler != null) {
            if (comma)
                sb.append(", ");
            else
                comma = true;
            sb.append(name);
        }
        return comma;
    }
}