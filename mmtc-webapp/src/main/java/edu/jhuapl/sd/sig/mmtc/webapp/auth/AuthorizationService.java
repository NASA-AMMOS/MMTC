package edu.jhuapl.sd.sig.mmtc.webapp.auth;

import io.javalin.http.Context;

public interface AuthorizationService {
    boolean ensureAuthorized(Context ctx);
}
