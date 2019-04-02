/*
 * Copyright Rainer Sulzbach
 */
package io.clownfish.clownfish.servlets;

import com.google.gson.Gson;
import io.clownfish.clownfish.dbentities.CfAttribut;
import io.clownfish.clownfish.dbentities.CfAttributcontent;
import io.clownfish.clownfish.dbentities.CfAttributetype;
import io.clownfish.clownfish.dbentities.CfClass;
import io.clownfish.clownfish.dbentities.CfClasscontent;
import io.clownfish.clownfish.serviceinterface.CfAttributService;
import io.clownfish.clownfish.serviceinterface.CfAttributcontentService;
import io.clownfish.clownfish.serviceinterface.CfAttributetypeService;
import io.clownfish.clownfish.serviceinterface.CfClassService;
import io.clownfish.clownfish.serviceinterface.CfClasscontentService;
import io.clownfish.clownfish.utils.PasswordUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author sulzbachr
 */
@WebServlet(name = "GetContent", urlPatterns = {"/GetContent"})
@Component
public class GetContent extends HttpServlet {
    @Autowired CfClassService cfclassService;
    @Autowired CfClasscontentService cfclasscontentService;
    @Autowired CfAttributService cfattributService;
    @Autowired CfAttributcontentService cfattributcontentService;
    @Autowired CfAttributetypeService cfattributetypeService;
    
    private @Getter @Setter String klasse;
    private @Getter @Setter HashMap<String, String> searchmap;
    private @Getter @Setter HashMap<String, String> outputmap;

    class AttributDef {
        String value;
        String type;

        public AttributDef(String value, String type) {
            this.value = value;
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        outputmap = new HashMap<>();
        Map<String, String[]> parameters = request.getParameterMap();
        for (String paramname : parameters.keySet()) {
            if (paramname.compareToIgnoreCase("class") == 0) {
                String[] values = parameters.get(paramname);
                klasse = values[0];
            }
        }
        searchmap = new HashMap<>();
        for (String paramname : parameters.keySet()) {
            if (paramname.startsWith("search$")) {
                String[] keys = paramname.split("\\$");
                String[] values = parameters.get(paramname);
                searchmap.put(keys[1], values[0]);
            }
        }
        
        //CfClass knclass = (Knclass) em.createNamedQuery("Knclass.findByName").setParameter("name", klasse).getSingleResult();
        CfClass cfclass = cfclassService.findByName(klasse);
        //List<CfClasscontent> classcontentList = em.createNamedQuery("Knclasscontent.findByClassref").setParameter("classref", knclass).getResultList();
        List<CfClasscontent> classcontentList = cfclasscontentService.findByClassref(cfclass);
        boolean found = true;
        for (CfClasscontent classcontent : classcontentList) {
            //List<CfAttributcontent> attributcontentList = em.createNamedQuery("Knattributcontent.findByClasscontentref").setParameter("classcontentref", classcontent).getResultList();
            List<CfAttributcontent> attributcontentList = cfattributcontentService.findByClasscontentref(classcontent);
            found = true;
            for (CfAttributcontent attributcontent : attributcontentList) {
                //Knattribut knattribut = (Knattribut) em.createNamedQuery("Knattribut.findById").setParameter("id", attributcontent.getAttributref().getId()).getSingleResult();
                CfAttribut knattribut = cfattributService.findById(attributcontent.getAttributref().getId());
                for (String searchcontent : searchmap.keySet()) {
                    String searchvalue = searchmap.get(searchcontent);
                    if (knattribut.getName().compareToIgnoreCase(searchcontent) == 0) {
                        long attributtypeid = knattribut.getAttributetype().getId();
                        AttributDef attributdef = getAttributContent(attributtypeid, attributcontent);
                        if (attributdef.getType().compareToIgnoreCase("hashstring") == 0) {
                            String salt = attributcontent.getSalt();
                            if (salt != null) {
                                searchvalue = PasswordUtil.generateSecurePassword(searchvalue, salt);
                            }
                        }
                        if (attributdef.getValue() == null) {
                            found = false;
                        } else {
                            if (searchvalue.compareToIgnoreCase(attributdef.getValue()) != 0) {
                                found = false;
                            }
                        }
                    }
                }
            }
            if (found) {
                outputmap.put("contentfound", "true");
                contentooutput(outputmap, attributcontentList);
                break;
            }
        }
        if (!found) {
            outputmap.put("contentfound", "false");
        }
        Gson gson = new Gson(); 
        String json = gson.toJson(outputmap);
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(json);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
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
     *
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
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    
    private AttributDef getAttributContent(long attributtypeid, CfAttributcontent attributcontent) {
        //Knattributetype knattributtype = (Knattributetype) em.createNamedQuery("Knattributetype.findById").setParameter("id", attributtypeid).getSingleResult();
        CfAttributetype knattributtype = cfattributetypeService.findById(attributtypeid);
        switch (knattributtype.getName()) {
            case "boolean":
                return new AttributDef(attributcontent.getContentBoolean().toString(), "boolean");
            case "string":
                return new AttributDef(attributcontent.getContentString(), "string");
            case "hashstring":
                return new AttributDef(attributcontent.getContentString(), "hashstring");
            case "integer":
                return new AttributDef(attributcontent.getContentInteger().toString(), "integer");
            case "real":
                return new AttributDef(attributcontent.getContentReal().toString(), "real");
            case "htmltext":
                return new AttributDef(attributcontent.getContentText(), "htmltext");
            case "datetime":
                return new AttributDef(attributcontent.getContentDate().toString(), "datetime");
            case "media":
                return new AttributDef(attributcontent.getContentInteger().toString(), "media");
            case "text":
                return new AttributDef(attributcontent.getContentInteger().toString(), "text");        
            default:
                return null;
        }
    }
    
    private void contentooutput(HashMap<String, String> outputmap, List<CfAttributcontent> attributcontentList) {
        for (CfAttributcontent attributcontent : attributcontentList) {
            //CfAttribut knattribut = (Knattribut) em.createNamedQuery("Knattribut.findById").setParameter("id", attributcontent.getAttributref().getId()).getSingleResult();
            CfAttribut knattribut = cfattributService.findById(attributcontent.getAttributref().getId());
            long attributtypeid = knattribut.getAttributetype().getId();
            AttributDef attributdef = getAttributContent(attributtypeid, attributcontent);
            if (attributdef.getType().compareToIgnoreCase("hashstring") != 0) {
                outputmap.put(knattribut.getName(), attributdef.getValue());
            }
        }
    }
}