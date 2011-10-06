package com.aaasec.jpdf.jsonpdiscofeed;

import biz.source_code.base64Coder.Base64Coder;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;

/** 
 *
 * @author Stefan Santesson, 3xA Security AB
 */
public class CookieHandler extends HttpServlet {

    private ServletContext context;
    private static final Logger LOG = Logger.getLogger(CookieHandler.class.getName());
    private static final String LF = System.getProperty("line.separator");

    @Override
    public void init(ServletConfig config) throws ServletException {
        this.context = config.getServletContext();
    }

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String callback = request.getParameter("callback");
        List<String> entityIDs = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            String val = request.getParameter(getId(i));
            if (val != null) {
                entityIDs.add(val);
            }
        }
        String maxAgeStr = request.getParameter("maxAge");
        String last = request.getParameter("last");

        if (callback == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.getWriter().write("");
            return;
        }

        // If no entity ID parameters - return current cookie values
        response.setContentType("application/javascript");
        if (entityIDs.isEmpty() && last == null) {
            String cookieResponse;
            try {
                cookieResponse = getResponseFromCookie(request).toString(2);
            } catch (JSONException ex) {
                LOG.log(Level.INFO, null, ex);
                cookieResponse = "";
            }
            String jsonp = callback + "(" + cookieResponse + ")";
            response.getWriter().write(jsonp);
            return;
        }

        // Else, set cookie
        int maxAge;
        try {
            maxAge = Integer.decode(maxAgeStr) * (60 * 60 * 24);
        } catch (Exception ex) {
            maxAge = -1;
        }
        String cookieResponse;
        try {
            cookieResponse = setCookie(request.getCookies(), response, last, entityIDs, maxAge).toString(2);
        } catch (JSONException ex) {
            LOG.log(Level.INFO, null, ex);
            cookieResponse = "";
        }
        String jsonp = callback + "(" + cookieResponse + ")";
        response.getWriter().write(jsonp);
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    private JSONObject getResponseFromCookie(HttpServletRequest request) {
        Map<String, String> previousIdps = new HashMap<String, String>();
        String last = "";
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                String name = cookie.getName();
                if (name.equals("previousIdPs")) {
                    try {
                        String decoded = Base64Coder.decodeString(cookie.getValue());
                        getCookieValues(decoded, previousIdps);
                    } catch (Exception ex) {
                        getCookieValues("", previousIdps);
                    }
                }
                if (name.equals("lastIdp")) {
                    last = cookie.getValue();
                }
            }
        }
        return getResponseJson(last, previousIdps, true, true);
    }

    private JSONObject getResponseJson(String last, Map<String, String> previousIdps, boolean setLast, boolean setPrev) {
        JSONObject json = new JSONObject();
        if (setLast) {
            try {
                json.accumulate("lastIdP", last);
            } catch (JSONException ex) {
                LOG.log(Level.INFO, null, ex);
            }
        }
        if (setPrev) {
            for (int i = 0; i < 10; i++) {
                if (previousIdps.containsKey(getId(i))) {
                    try {
                        JSONObject prevObj = new JSONObject();
                        prevObj.accumulate("entityID", previousIdps.get(getId(i)));
                        json.accumulate("previousIdPs", prevObj);
                    } catch (JSONException ex) {
                        LOG.log(Level.INFO, null, ex);
                    }
                }
            }
        }

        return json;
    }

    private JSONObject setCookie(Cookie[] cookies, HttpServletResponse response, String last, List<String> prevEntityIDs, int maxAge) {
        Map<String, String> previousIdps = new HashMap<String, String>();
        boolean setLast = (last != null);
        boolean setPrevious = (prevEntityIDs.size() > 0);
        last = (setLast) ? last : "";
        StringBuilder b = new StringBuilder();

        if (setLast) {
            Cookie lastCookie = getCookie(cookies, "lastIdp", response);
            lastCookie.setMaxAge(maxAge);
            lastCookie.setPath("/");
            lastCookie.setValue(last);
            response.addCookie(lastCookie);
        }

        if (setPrevious) {
            Cookie prevCookie = getCookie(cookies, "previousIdPs", response);
            prevCookie.setMaxAge(maxAge);
            prevCookie.setPath("/");
            for (int i = 0; i < prevEntityIDs.size(); i++) {
                previousIdps.put(getId(i), prevEntityIDs.get(i));
                b.append(getId(i)).append("=").append(prevEntityIDs.get(i));
                if ((i + 1) < prevEntityIDs.size()) {
                    b.append(";");
                }
            }
            String encode = Base64Coder.encodeString(b.toString());
            prevCookie.setValue(encode);
            response.addCookie(prevCookie);
        }

        return getResponseJson(last, previousIdps, setLast, setPrevious);
    }

    private void getCookieValues(String cookieValue, Map<String, String> previousIdps) {
        String[] values = cookieValue.split(";");
        for (String value : values) {
            String[] idValueSplit = value.split("=");
            if (idValueSplit.length == 2) {
                previousIdps.put(idValueSplit[0], idValueSplit[1]);
            }
        }
    }

    private String getId(int i) {
        return "p" + String.valueOf(i);
    }

    private Cookie getCookie(Cookie[] cookies, String name, HttpServletResponse response) {
        Cookie cookie = new Cookie(name, "");
        boolean found = false;
        for (Cookie ck : cookies) {
            if (ck.getName().equals(name)) {
                if (found) {
                    ck.setMaxAge(0);
                    response.addCookie(cookie);
                } else {
                    cookie = ck;
                    found = true;
                }
            }
        }
        return cookie;
    }
}