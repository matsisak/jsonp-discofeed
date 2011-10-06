package com.aaasec.jpdf.jsonpdiscofeed;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 
 *
 * @author Stefan Santesson, 3xA Security AB
 */
public class Eid2DiscoFeed extends HttpServlet {

    private ServletContext context;
    private static final Logger LOG = Logger.getLogger(Eid2DiscoFeed.class.getName());
    private static final String LF = System.getProperty("line.separator");
    private String metaCacheFileName;
    private MetaData metaData;
    private JSONArray jsonData;
    private String cacheDealyMinutes;
    private long lastCache;

    @Override
    public void init(ServletConfig config) throws ServletException {
        this.context = config.getServletContext();
        this.metaCacheFileName = config.getInitParameter("MetaDataCache");
        cacheDealyMinutes = config.getInitParameter("CacheRefreshMinutes");
        metaData = new MetaData(new File(metaCacheFileName));
        jsonData = metaData.getDiscoJson();
        lastCache = System.currentTimeMillis();
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

        String action = request.getParameter("action");
        String callback = request.getParameter("callback");

        if (action == null || callback == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.getWriter().write("");
            return;
        }

        if (action.equals("discoFeed")) {
            response.setContentType("application/javascript");
            JSONArray json;
            json = getMetadataJson();
            String sourceUrl = request.getParameter("source");
            if (sourceUrl != null) {
                json = getDiscoFeed(sourceUrl);
            }
            String jsonStr = "";
            try {
                jsonStr = getExtendedFeed(json, request).toString(2);
            } catch (JSONException ex) {
                LOG.log(Level.INFO, null, ex);
            }
            String jsonp = callback + "(" + jsonStr + ")";
            response.getWriter().write(jsonp);
        }

        if (action.equals("setCookie")) {
            response.setContentType("application/javascript");
            String value = request.getParameter("entityID");
            String maxAgeStr = request.getParameter("maxAge");
            int maxAge;
            try {
                maxAge = Integer.decode(maxAgeStr) * (60 * 60 * 24);
            } catch (Exception ex) {
                maxAge = -1;
            }
            Cookie cookie = getCookie(request.getCookies(), "lastIdp", response);
            cookie.setValue(value);
            cookie.setPath("/");
            cookie.setMaxAge(maxAge);
            response.addCookie(cookie);
            String jsonp = callback + "({\"entityID\": \"" + value + "\"})";
            response.getWriter().write(jsonp);
        }

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

    private JSONArray getDiscoFeed(String sourceUrl) {
        URL url;
        JSONArray json = new JSONArray();
        try {
            url = new URL(sourceUrl);
            byte[] jsonBytes = Utils.getUrlBytes(url);
            if (jsonBytes != null) {
                String jsonstr = new String(jsonBytes, Charset.forName("UTF-8"));
                json = new JSONArray(jsonstr);
            }
        } catch (Exception ex) {
        }
        return json;
    }

    private JSONObject getExtendedFeed(JSONArray discoFeed, HttpServletRequest request) {
        JSONObject json = new JSONObject();
        String lastIdp = "";
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                String name = cookie.getName();
                if (name.equals("lastIdp")) {
                    lastIdp = cookie.getValue();
                }
            }
        }
        try {
            JSONArray lia = new JSONArray();
            lia.put((new JSONObject()).accumulate("entityID", lastIdp));
            json.put("last", lia);
        } catch (JSONException ex) {
            LOG.log(Level.INFO, null, ex);
        }
        try {
            json.put("discoFeed", discoFeed);
//            for (int i=0;i<discoFeed.length();i++){
//                json.accumulate("discoFeed", discoFeed.get(i));
//            }
//            json.append("discoFeed", discoFeed.getJSONArray("idpList"));
        } catch (JSONException ex) {
            LOG.log(Level.INFO, null, ex);
        }
        return json;
    }

    private JSONArray getMetadataJson() {
        Long currentTime = System.currentTimeMillis();
        long delay;
        try {
            delay = Long.decode(cacheDealyMinutes);
        } catch (Exception ex) {
            delay = 10;
        }
        delay = delay * 1000 * 60;

        if (currentTime > lastCache + delay) {
            // recache
            metaData = new MetaData(new File(metaCacheFileName));
        }
        return metaData.getDiscoJson();
    }

    private Cookie getCookie(Cookie[] cookies, String name, HttpServletResponse response) {
        Cookie cookie = new Cookie(name, "");
        boolean found = false;
        for (Cookie ck : cookies) {
            if (ck.getName().equals(name)) {
                if (found) {
                    ck.setMaxAge(0);
                    response.addCookie(ck);
                } else {
                    cookie = ck;
                    found = true;
                }
            }
        }
        return cookie;
    }
}