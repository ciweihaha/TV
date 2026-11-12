package com.fongmi.android.tv.server.impl;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public interface Process {

    boolean isRequest(IHTTPSession session, String url);

    Response doResponse(IHTTPSession session, String url, Map<String, String> files);

    default Response response(Status status, String text) {
        Response response = NanoHTTPD.newFixedLengthResponse(status, status == Status.OK ? "application/json; charset=utf-8" : "text/plain; charset=utf-8", text);
        response.addHeader("Cache-Control", "no-store");
        return response;
    }
}
