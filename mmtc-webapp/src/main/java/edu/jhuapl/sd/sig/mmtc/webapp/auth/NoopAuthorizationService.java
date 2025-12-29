package edu.jhuapl.sd.sig.mmtc.webapp.auth;

import io.javalin.http.Context;

public class NoopAuthorizationService implements AuthorizationService {
    @Override
    public boolean ensureAuthorized(Context ctx) {
        return true;
    }
}
