package com.wizzardo.webdav;

import com.wizzardo.http.request.Request;
import com.wizzardo.http.response.Response;

import java.nio.charset.StandardCharsets;

public class WebDavHandler extends MultiHandler {

    private byte[] dav = "DAV: 1\r\n".getBytes(StandardCharsets.UTF_8);

    @Override
    protected Response provideHeaders(Request request, Response response) {
        return super.provideHeaders(request, response)
                .appendHeader(dav);
    }
}
