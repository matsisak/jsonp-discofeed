package com.aaasec.jpdf.jsonpdiscofeed;

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
            String cookieResponse = getResponseFromCookie(request);
            String jsonp = callback + "(" + cookieResponse + ")";
            response.getWriter().write(jsonp);
            return;
        }

        int maxAge;
        try {
            maxAge = Integer.decode(maxAgeStr) * (60 * 60 * 24);
        } catch (Exception ex) {
            maxAge = -1;
        }
        String cookieResponse = getCookieSettingResponse(response, last, entityIDs, maxAge);
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

    private String getDiscoFeed(String sourceUrl) {
        URL url;
        String json = "[]";
        try {
            url = new URL(sourceUrl);
            byte[] jsonBytes = Utils.getUrlBytes(url);
            if (jsonBytes != null) {
                json = new String(jsonBytes, Charset.forName("UTF-8"));
            }
        } catch (Exception ex) {
        }
        return json;
    }

    private String getResponseFromCookie(HttpServletRequest request) {
        Map<String, String> previousIdps = new HashMap<String, String>();
        String last = "";
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                String name = cookie.getName();
                if (name.equals("previousIdPs")) {
                    try {
                        String decoded = URLDecoder.decode(cookie.getValue(), "UTF-8");
                        getCookieValues(decoded, previousIdps);
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(CookieHandler.class.getName()).log(Level.SEVERE, null, ex);
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

    private String getResponseJson(String last, Map<String, String> previousIdps, boolean setLast, boolean setPrev) {
        StringBuilder b = new StringBuilder();
        b.append(LF);
        b.append(" {");
        if (setLast) {
            b.append(LF).append("  \"lastIdP\": \"").append(last).append("\"");
            if (setPrev) {
                b.append(",");
            }
            b.append(LF);
        }
        if (setPrev) {
            b.append("  \"previousIdPs\": [");
            boolean empty = true;
            for (int i = 0; i < 10; i++) {
                if (previousIdps.containsKey(getId(i))) {
                    b.append(LF);
                    b.append("      {\"entityId\": \"").append(previousIdps.get(getId(i))).append("\"},");
                    empty = false;
                }
            }
            if (!empty) {
                b.deleteCharAt(b.lastIndexOf(","));
                b.append(LF).append("  ");
            }
            b.append("]").append(LF);
        }
        b.append(" }").append(LF);
        return b.toString();

    }

    private String getCookieSettingResponse(HttpServletResponse response, String last, List<String> prevEntityIDs, int maxAge) {
        Map<String, String> previousIdps = new HashMap<String, String>();
        boolean setLast = (last != null);
        boolean setPrevious = (prevEntityIDs.size() > 0);
        last = (setLast) ? last : "";
        StringBuilder b = new StringBuilder();

        if (setLast) {
            Cookie cookie = new Cookie("lastIdp", last);
            cookie.setMaxAge(maxAge);
            response.addCookie(cookie);
        }

        if (setPrevious) {
            Cookie prevCookie = new Cookie("previousIdPs", "");
            prevCookie.setMaxAge(maxAge);
            for (int i = 0; i < prevEntityIDs.size(); i++) {
                previousIdps.put(getId(i), prevEntityIDs.get(i));
                b.append(getId(i)).append("=").append(prevEntityIDs.get(i));
                if ((i + 1) < prevEntityIDs.size()) {
                    b.append(";");
                }
            }
            try {
                String encode = URLEncoder.encode(b.toString(), "UTF-8");
                prevCookie.setValue(encode);
                response.addCookie(prevCookie);

            } catch (UnsupportedEncodingException ex) {
                previousIdps = new HashMap<String, String>();
            }
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
}