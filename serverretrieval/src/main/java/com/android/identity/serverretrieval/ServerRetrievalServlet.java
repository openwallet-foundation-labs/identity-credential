package com.android.identity.serverretrieval;

import com.android.identity.credentialtype.CredentialTypeRepository;
import com.android.identity.credentialtype.knowntypes.DrivingLicense;
import com.android.identity.mdoc.serverretrieval.ServerRetrievalUtil;
import com.android.identity.mdoc.serverretrieval.TestKeysAndCertificates;
import com.android.identity.mdoc.serverretrieval.oidc.OidcServer;
import com.android.identity.mdoc.serverretrieval.webapi.WebApiServer;
import com.android.identity.util.Logger;

import java.io.IOException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ServerRetrievalServlet extends HttpServlet {
    private static final String TAG = "ServerRetrievalServlet";

    private final CredentialTypeRepository credentialTypeRepository;
    private final WebApiServer webApiServer;
    private final OidcServer oidcServer;
    private static final long serialVersionUID = 1L;

    public ServerRetrievalServlet() {
        credentialTypeRepository = new CredentialTypeRepository();
        credentialTypeRepository.addCredentialType(DrivingLicense.INSTANCE.getCredentialType());

        webApiServer = new WebApiServer(
                TestKeysAndCertificates.INSTANCE.getJwtSignerPrivateKey(),
                TestKeysAndCertificates.INSTANCE.getJwtCertificateChain(),
                credentialTypeRepository);

        oidcServer = new OidcServer("http://localhost:8080/serverretrieval",
                TestKeysAndCertificates.INSTANCE.getJwtSignerPrivateKey(),
                TestKeysAndCertificates.INSTANCE.getJwtCertificateChain(),
                credentialTypeRepository);
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            int requestLength = req.getContentLength();
            String requestData = new String(req.getInputStream().readNBytes(requestLength));
            String responseData;
            if (req.getRequestURI().contains("/identity")) {
                responseData = webApiServer.serverRetrieval(requestData);
            } else if (req.getRequestURI().contains("/connect/register")) {
                responseData = oidcServer.clientRegistration(requestData);
            } else if (req.getRequestURI().contains("/connect/token")) {
                responseData = oidcServer.getIdToken(ServerRetrievalUtil.INSTANCE.urlToMap(requestData));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.getOutputStream().write(responseData.getBytes());
        } catch (Throwable e) {
            Logger.e(TAG, "An error occurred while doing an HTTP POST Request");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String getRemoteHost(HttpServletRequest req) {
        String remoteHost = req.getRemoteHost();
        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            remoteHost = forwardedFor;
        }
        return remoteHost;
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        try {
            Logger.d(TAG, "GET from " + getRemoteHost(req));
            String responseData;
            if (req.getRequestURI().contains(".well-known/openid-configuration")) {
                responseData = oidcServer.configuration();
            } else if (req.getRequestURI().contains("connect/authorize")) {
                responseData = oidcServer.authorization(ServerRetrievalUtil.INSTANCE.urlToMap(req.getRequestURI()));
            } else if (req.getRequestURI().contains(".well-known/jwks.json")) {
                responseData = oidcServer.validateIdToken();
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.getOutputStream().write(responseData.getBytes());
        } catch (Throwable e) {
            Logger.e(TAG, "An error occurred while doing an HTTP GET Request");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
