package com.google.sps.servlets;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// imports for Datastore
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Text;

import com.google.sps.servlets.RequestServlet;
import com.google.sps.servlets.ServletConsts;

/**
 * This servlet returns the JSON String containing data from DeviceResponse, on the condition
 * that it has already been received and parsed in RequestServlet.java.
 */
@WebServlet("/device-response")
public class DeviceResponseServlet extends HttpServlet {

    private DatastoreService datastore;

    @Override
    public void init() {
        datastore = DatastoreServiceFactory.getDatastoreService();
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;");
        Key datastoreKey = RequestServlet.datastoreKey;
        if (datastoreKey == null) {
            response.getWriter().println("Device Response has not yet been generated.");
        } else {
            Entity entity;
            try {
                entity = datastore.get(datastoreKey);
            } catch (EntityNotFoundException e) {
                throw new IllegalStateException("Entity could not be found in database", e);
            }
            if (!entity.hasProperty(ServletConsts.DEVICE_RESPONSE_PROP)) {
                response.getWriter().println("Device Response has not yet been generated.");
            } else {
                Text deviceResponse = (Text) entity.getProperty(ServletConsts.DEVICE_RESPONSE_PROP);
                String deviceResponseString = deviceResponse.getValue();
                response.getWriter().println(deviceResponseString);
            }
        }
    }
}
