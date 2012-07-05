/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.portletbridge.context;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.faces.FacesException;
import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.render.ResponseStateManager;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.PortletResponse;
import javax.portlet.PortletSession;
import javax.portlet.faces.Bridge;
import javax.portlet.faces.BridgeDefaultViewNotSpecifiedException;

import org.jboss.portletbridge.bridge.context.BridgeContext;
import org.jboss.portletbridge.bridge.logger.BridgeLogger;
import org.jboss.portletbridge.bridge.logger.BridgeLogger.Level;
import org.jboss.portletbridge.context.map.EnumerationIterator;

/**
 * Version of the {@link ExternalContext} for a Portlet request. {@link FacesContextFactory} will create instance of this class
 * for a portal <code>action</code> phase.
 *
 * @author asmirnov
 */
public abstract class PortletExternalContextImpl extends AbstractExternalContext {

    public static final String SERVLET_PATH_ATTRIBUTE = "javax.servlet.include.servlet_path";
    public static final String PATH_INFO_ATTRIBUTE = "javax.servlet.include.path_info";
    public static final String ACTION_URL_DO_NOTHITG = "/JBossPortletBridge/actionUrl/do/nothing";
    public static final String RESOURCE_URL_DO_NOTHITG = "/JBossPortletBridge/resourceUrl/do/nothing";
    public static final String PARTIAL_URL_DO_NOTHITG = "/JBossPortletBridge/resourceUrl/do/nothing";

    private String namespace;
    private String servletPath = null;
    private String pathInfo = null;
    private String servletMappingSuffix;
    private String defaultJsfSuffix;
    private String servletMappingPrefix;
    private String viewId;
    private boolean hasNavigationRedirect = false;
    private Map<String, String[]> extraRequestParameters = new HashMap<String, String[]>();
    protected BridgeContext bridgeContext;

    public PortletExternalContextImpl(PortletContext context, PortletRequest request, PortletResponse response) {
        super(context, request, response);

        bridgeContext = BridgeContext.getCurrentInstance();
        if (null == bridgeContext) {
            throw new FacesException("No BridgeContext instance found");
        }

        String defaultRenderKitId = bridgeContext.getBridgeConfig().getDefaultRenderKitId();
        if (null != defaultRenderKitId && null == request.getParameter(ResponseStateManager.RENDER_KIT_ID_PARAM)) {
            extraRequestParameters.put(ResponseStateManager.RENDER_KIT_ID_PARAM, new String[] { defaultRenderKitId });
        }
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        // Do nothing
    }

    @Override
    public String getResponseCharacterEncoding() {
        return null;
    }

    @Override
    public String getResponseContentType() {
        return null;
    }

    @Override
    public String getRequestContentType() {
        return null;
    }

    @Override
    public PortletContext getContext() {
        return (PortletContext) super.getContext();
    }

    @Override
    public PortletRequest getRequest() {
        return (PortletRequest) super.getRequest();
    }

    @Override
    public PortletResponse getResponse() {
        return (PortletResponse) super.getResponse();
    }

    public String getInitParameter(String name) {
        return getContext().getInitParameter(name);
    }

    protected String getNamespace() {
        if (null == namespace) {
            namespace = getResponse().getNamespace();
        }
        return namespace;
    }

    public URL getResource(String path) throws MalformedURLException {
        return getContext().getResource(path);
    }

    public InputStream getResourceAsStream(String path) {
        return getContext().getResourceAsStream(path);
    }

    public Set<String> getResourcePaths(String path) {
        return getContext().getResourcePaths(path);
    }

    protected Enumeration<String> enumerateRequestParameterNames() {
        List<String> names = new ArrayList<String>();
        Enumeration<String> paramNames = getRequest().getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = (String) paramNames.nextElement();
            names.add(name);
        }
        names.addAll(extraRequestParameters.keySet());
        return Collections.enumeration(names);
    }

    protected Object getContextAttribute(String name) {
        return getContext().getAttribute(name);
    }

    protected Enumeration<String> getContextAttributeNames() {
        return getContext().getAttributeNames();
    }

    protected Enumeration<String> getInitParametersNames() {
        return getContext().getInitParameterNames();
    }

    protected Object getRequestAttribute(String name) {
        if (PATH_INFO_ATTRIBUTE.equals(name)) {
            return getRequestPathInfo();
        } else if (SERVLET_PATH_ATTRIBUTE.equals(name)) {
            return getRequestServletPath();
        } else {
            return getRequest().getAttribute(name);
        }
    }

    protected Enumeration<String> getRequestAttributeNames() {
        return getRequest().getAttributeNames();
    }

    protected String[] getRequestParameterValues(String name) {
        String[] temp = getRequest().getParameterValues(name);
        if (null == temp || temp.length == 0) {
            temp = extraRequestParameters.get(name);
        }
        return temp;
    }

    protected String getRequestHeader(String name) {
        String headerValue = getRequest().getProperty(name);
        if (null == headerValue) {
            // HACK - GateIn converts all request header names to the lower case.
            headerValue = getRequest().getProperty(name.toLowerCase());
        }
        return headerValue;
    }

    protected Enumeration<String> getRequestHeaderNames() {
        return getRequest().getPropertyNames();
    }

    protected String[] getRequestHeaderValues(String name) {
        Enumeration<String> properties = getRequest().getProperties(name);
        if (!properties.hasMoreElements()) {
            // HACK - GateIn converts all request header names to the lower case.
            properties = getRequest().getProperties(name.toLowerCase());
        }
        if (properties.hasMoreElements()) {
            List<String> values = new ArrayList<String>();
            while (properties.hasMoreElements()) {
                String value = properties.nextElement();
                values.add(value);
            }
            return (String[]) values.toArray(EMPTY_STRING_ARRAY);
        } else {
            return null;
        }
    }

    protected String getRequestParameter(String name) {
        String temp = getRequest().getParameter(name);
        if (null == temp) {
            String[] tempArray = extraRequestParameters.get(name);
            if (null != tempArray && tempArray.length > 0) {
                temp = extraRequestParameters.get(name)[0];
            }
        }
        return temp;
    }

    protected Object getSessionAttribute(String name) {
        return getSessionAttribute(name, getScopeForName(name));
    }

    protected int getScopeForName(String name) {
        return PortletSession.PORTLET_SCOPE;
    }

    protected Object getSessionAttribute(String name, int scope) {
        return getRequest().getPortletSession(true).getAttribute(name, scope);
    }

    protected Enumeration<String> getSessionAttributeNames() {
        class AttributeEnumeration implements Enumeration<String> {
            int scope;
            Enumeration<String> attributes;

            public AttributeEnumeration() {
                scope = PortletSession.PORTLET_SCOPE;
                attributes = getSessionAttributeNames(scope);
                if (!attributes.hasMoreElements()) {
                    scope = PortletSession.APPLICATION_SCOPE;
                    attributes = getSessionAttributeNames(scope);
                }
            }

            public boolean hasMoreElements() {
                return attributes.hasMoreElements();
            }

            public String nextElement() {
                final String result = attributes.nextElement();

                if (!attributes.hasMoreElements() && scope == PortletSession.PORTLET_SCOPE) {
                    scope = PortletSession.APPLICATION_SCOPE;
                    attributes = getSessionAttributeNames(scope);
                }

                return result;
            }
        }

        return new AttributeEnumeration();

        // return getSessionAttributeNames(PortletSession.PORTLET_SCOPE);
    }

    protected Enumeration<String> getSessionAttributeNames(int scope) {
        return getRequest().getPortletSession(true).getAttributeNames(scope);
    }

    protected void removeContextAttribute(String name) {
        getContext().removeAttribute(name);
    }

    protected void removeRequestAttribute(String name) {
        getRequest().removeAttribute(name);
    }

    protected void removeSessionAttribute(String name) {
        removeSessionAttribute(name, getScopeForName(name));
    }

    protected void removeSessionAttribute(String name, int scope) {
        getRequest().getPortletSession(true).removeAttribute(name, scope);
    }

    protected void setContextAttribute(String name, Object value) {
        getContext().setAttribute(name, value);
    }

    protected void setRequestAttribute(String name, Object value) {
        getRequest().setAttribute(name, value);
    }

    protected void setSessionAttribute(String name, Object value) {
        setSessionAttribute(name, value, getScopeForName(name));
    }

    protected void setSessionAttribute(String name, Object value, int scope) {
        getRequest().getPortletSession(true).setAttribute(name, value, scope);
    }

    /**
     * @param url
     * @return
     */
    protected String encodeURL(String url) {
        return getResponse().encodeURL(url);
    }

    public String getAuthType() {
        return getRequest().getAuthType();
    }

    public String getRemoteUser() {
        String user = getRequest().getRemoteUser();
        if (user == null) {
            Principal userPrincipal = getUserPrincipal();
            if (null != userPrincipal) {
                user = userPrincipal.getName();

            }
        }
        return user;
    }

    public String getRequestContextPath() {
        return getRequest().getContextPath();
    }

    public Locale getRequestLocale() {
        return getRequest().getLocale();
    }

    public Iterator<Locale> getRequestLocales() {
        return new EnumerationIterator<Locale>(getRequest().getLocales());
    }

    public String getRequestPathInfo() {
        if (null == pathInfo) {
            calculateViewId();
        }
        return pathInfo;
    }

    public String getRequestServletPath() {
        if (null == servletPath) {
            calculateViewId();
        }
        return servletPath;
    }

    public Object getSession(boolean create) {
        return getRequest().getPortletSession(create);
    }

    public Principal getUserPrincipal() {
        return getRequest().getUserPrincipal();
    }

    public boolean isUserInRole(String role) {
        return getRequest().isUserInRole(role);
    }

    public void log(String message) {
        getContext().log(message);
    }

    public void log(String message, Throwable exception) {
        getContext().log(message, exception);
    }

    protected void calculateViewId() {
        String newViewId = bridgeContext.getFacesViewIdFromRequest(false);

        if (null == newViewId) {
            // Try to get viewId from stored session attribute
            PortletSession portletSession = getRequest().getPortletSession(false);
            if (null != portletSession) {
                String historyViewId = (String) portletSession.getAttribute(Bridge.VIEWID_HISTORY + "."
                        + getRequest().getPortletMode().toString());
                if (null != historyViewId) {
                    try {
                        PortalActionURL viewIdUrl = new PortalActionURL(historyViewId);
                        newViewId = viewIdUrl.getPath();
                    } catch (MalformedURLException e) {
                        // Ignore it.
                    }
                }
            }

            if (null == newViewId) {
                newViewId = bridgeContext.getDefaultFacesViewIdForRequest(false);

                if (null == newViewId) {
                    throw new BridgeDefaultViewNotSpecifiedException();
                }
            }
        }

        if (null != newViewId && newViewId.equals(viewId)) {
            // No ViewId change
            return;
        }

        viewId = newViewId;

        calculateServletPath(viewId, bridgeContext.getBridgeConfig().getFacesServletMappings());
    }

    protected void calculateServletPath(String viewId, List<String> servletMappings) {
        if (null != servletMappings && servletMappings.size() > 0) {
            String mapping = servletMappings.get(0);
            if (mapping.startsWith("*")) {
                // Suffix Mapping
                servletMappingSuffix = mapping.substring(mapping.indexOf('.'));
                viewId = viewId.substring(0, viewId.lastIndexOf('.')) + servletMappingSuffix;
                servletPath = viewId;
                pathInfo = null;

                getRequest().setAttribute(SERVLET_PATH_ATTRIBUTE, servletPath);
                getRequest().setAttribute("com.sun.faces.INVOCATION_PATH", servletMappingSuffix);
            } else if (mapping.endsWith("*")) {
                // Prefix Mapping
                mapping = mapping.substring(0, mapping.length() - 1);
                if (mapping.endsWith("/")) {
                    mapping = mapping.substring(0, mapping.length() - 1);
                }
                servletMappingPrefix = servletPath = mapping;
                pathInfo = viewId;
                getRequest().setAttribute("com.sun.faces.INVOCATION_PATH", servletMappingSuffix);
            } else {
                servletPath = null;
                pathInfo = viewId;
            }
        } else {
            servletPath = null;
            pathInfo = viewId;
        }
    }

    /**
     * @param actionURL
     */
    protected void internalRedirect(PortalActionURL actionURL) {
        // Detect ViewId from URL and create new view for them.
        String viewId = actionURL.getParameter(Bridge.FACES_VIEW_ID_PARAMETER);
        if (null != viewId) {
            Map<String, String[]> requestParameters = actionURL.getParameters();
            if (requestParameters.size() > 0) {
                bridgeContext.setRenderRedirectQueryString(actionURL.getQueryString());
            }

        }
    }

    /**
     * @return the servletMappingSuffix
     */
    public String getServletMappingSuffix() {
        return servletMappingSuffix;
    }

    /**
     * @return the defaultJsfSuffix
     */
    public String getDefaultJsfSuffix() {
        return defaultJsfSuffix;
    }

    /**
     * @return the defaultJsfPrefix
     */
    public String getServletMappingPrefix() {
        return servletMappingPrefix;
    }

    protected String getViewIdFromUrl(PortalActionURL url) {
        String viewId;
        viewId = url.getParameter(Bridge.FACES_VIEW_ID_PARAMETER);
        if (null == viewId) {
            viewId = url.getPath();
            if (viewId.startsWith(getRequestContextPath())) {
                viewId = viewId.substring(getRequestContextPath().length());
            }
            viewId = bridgeContext.getFacesViewIdFromPath(viewId);
        }
        return viewId;
    }

    public void dispatch(String path) throws IOException {
        if (null == path) {
            throw new NullPointerException("Path to new view is null");
        }
        PortletRequestDispatcher dispatcher = getContext().getRequestDispatcher(path);
        if (null == dispatcher) {
            throw new IllegalStateException("Dispatcher for render request is not created");
        }
        // Bridge has had to set this attribute so Faces RI will skip servlet dependent
        // code when mapping from request paths to viewIds -- however we need to remove it
        // as it screws up the dispatch
        // Object oldPath = getRequestMap().remove(SERVLET_PATH_ATTRIBUTE);
        try {
            dispatcher.forward(getRequest(), getResponse());
        } catch (PortletException e) {
            throw new FacesException(e);
        } finally {
            // if(null !=oldPath){
            // getRequestMap().put(SERVLET_PATH_ATTRIBUTE, oldPath);
            // }
        }
    }

    @Override
    public String getMimeType(String file) {
        String mimeType = getContext().getMimeType(file);
        if (mimeType == null) {
            mimeType = getFallbackMimeType(file);
        }
        return mimeType;
    }

    @Override
    public String getContextName() {
        return getContext().getPortletContextName();
    }

    @Override
    public String getRealPath(String path) {
        return getContext().getRealPath(path);
    }

    @Override
    public String getRequestScheme() {
        return getRequest().getScheme();
    }

    @Override
    public String getRequestServerName() {
        return getRequest().getServerName();
    }

    @Override
    public int getRequestServerPort() {
        return getRequest().getServerPort();
    }

    @Override
    public void invalidateSession() {
        PortletSession session = getRequest().getPortletSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * @return the hasNavigationRedirect
     */
    boolean isHasNavigationRedirect() {
        return hasNavigationRedirect;
    }

    /**
     * @param hasNavigationRedirect the hasNavigationRedirect to set
     */
    void setHasNavigationRedirect(boolean hasNavigationRedirect) {
        this.hasNavigationRedirect = hasNavigationRedirect;
    }

    public String encodeActionURL(String url) {
        if (null == url) {
            getLogger().log(Level.WARNING, "Unable to encode ActionURL for url=[null]");
            return null;
        }
        String actionUrl = url;
        if (actionUrl.startsWith("portlet:")) {
            try {
                if (actionUrl.startsWith("portlet:action")) {
                    actionUrl = createActionUrl(new PortalActionURL(url));
                } else if (actionUrl.startsWith("portlet:render")) {
                    actionUrl = createRenderUrl(new PortalActionURL(url), Collections.<String, List<String>> emptyMap());
                } else {
                    actionUrl = createResourceUrl(new PortalActionURL(url));
                }
            } catch (MalformedURLException e) {
                log("Unable to create PortalActionURL from: " + url, e);
            }
        } else if (!actionUrl.startsWith("#")) {
            try {
                PortalActionURL portalUrl = new PortalActionURL(url);
                boolean inContext = isInContext(portalUrl);
                if (inContext) {
                    actionUrl = createActionUrl(portalUrl);
                } else {
                    return encodeURL(portalUrl.toString());
                }
            } catch (MalformedURLException e) {
                throw new FacesException(e);
            }
        }
        return actionUrl;
    }

    @Override
    public String encodePartialActionURL(String url) {
        if (null == url) {
            throw new NullPointerException();
        }
        String actionUrl = url;
        if (!actionUrl.startsWith("#")) {
            try {
                PortalActionURL portalUrl = new PortalActionURL(url);
                boolean inContext = isInContext(portalUrl);
                if (inContext) {
                    actionUrl = createPartialActionUrl(portalUrl);
                } else {
                    return encodeURL(portalUrl.toString());
                }
            } catch (MalformedURLException e) {
                throw new FacesException(e);
            }
        }
        return actionUrl.replaceAll("\\&amp\\;", "&");
    }

    public String encodeResourceURL(String url) {
        try {
            PortalActionURL portalUrl = new PortalActionURL(url);
            // JSR-301 chapter 6.1.3.1 requirements:
            String path = portalUrl.getPath();
            if (null != portalUrl.getProtocol() && "portlet:".equalsIgnoreCase(portalUrl.getProtocol())) {
                // Portlet Scheme URL
                portalUrl.removeParameter(Bridge.VIEW_LINK);
                encodeBackLink(portalUrl);
                return encodeActionURL(portalUrl.toString());
            } else if (null != portalUrl.getProtocol() && !path.startsWith("/")) {
                // Opaque URL
                return url;
            } else if (!isInContext(portalUrl)) {
                // Hierarchial url outside context.
                encodeBackLink(portalUrl);
                return encodeURL(portalUrl.toString());
            } else if ("true".equalsIgnoreCase(portalUrl.getParameter(Bridge.VIEW_LINK))) {
                // Hierarchical and targets a resource that is within this application
                portalUrl.removeParameter(Bridge.VIEW_LINK);
                encodeBackLink(portalUrl);
                // 1. view link. TODO - would it better to create renderURL ?
                return encodeActionURL(portalUrl.toString());
            } else {
                // For resources in the portletbridge application context add
                // namespace as URL parameter, to restore portletbridge session.
                // Remove context path from resource ID.

                portalUrl.removeParameter(Bridge.VIEW_LINK);

                if (path.startsWith("/")) {
                    if (null == portalUrl.getParameter(Bridge.NONFACES_TARGET_PATH_PARAMETER)) {
                        // absolute path, remove context path from ID.
                        portalUrl.setPath(path.substring(getRequestContextPath().length()));
                    }
                } else {
                    // resolve relative URL against current view.
                    FacesContext facesContext = FacesContext.getCurrentInstance();
                    UIViewRoot viewRoot = facesContext.getViewRoot();
                    if (null != viewRoot && null != viewRoot.getViewId() && viewRoot.getViewId().length() > 0) {
                        String viewId = viewRoot.getViewId();
                        int indexOfSlash = viewId.lastIndexOf('/');
                        if (indexOfSlash >= 0) {
                            portalUrl.setPath(viewId.substring(0, indexOfSlash + 1) + path);
                        } else {
                            portalUrl.setPath('/' + path);
                        }
                    } else {
                        // No clue where we are
                        portalUrl.setPath('/' + path);
                    }
                }
                portalUrl.setPath(URI.create(portalUrl.getPath()).normalize().getPath());

                String facesViewId = getViewIdFromUrl(portalUrl);
                if (null != portalUrl.getParameter(Bridge.IN_PROTOCOL_RESOURCE_LINK)) {
                    url = createResourceUrl(portalUrl);
                } else if (null != facesViewId) {
                    portalUrl.setParameter(Bridge.FACES_VIEW_ID_PARAMETER, facesViewId);
                    url = createResourceUrl(portalUrl);
                } else {
                    portalUrl.setPath(getRequestContextPath() + portalUrl.getPath());
                    url = encodeURL(portalUrl.toString());
                }
            }
        } catch (MalformedURLException e) {
            throw new FacesException(e);
        }
        return url.replaceAll("\\&amp\\;", "&");
    }

    @Override
    public String encodeBookmarkableURL(String baseUrl, Map<String, List<String>> parameters) {
        if (null == baseUrl) {
            throw new NullPointerException();
        }
        String actionUrl = baseUrl;
        if (!actionUrl.startsWith("#")) {
            try {
                PortalActionURL portalUrl = new PortalActionURL(baseUrl);
                boolean inContext = isInContext(portalUrl);
                if (inContext) {
                    actionUrl = createRenderUrl(portalUrl, parameters);
                } else {
                    return encodeURL(portalUrl.toString());
                }
            } catch (MalformedURLException e) {
                throw new FacesException(e);
            }
        }
        return actionUrl.replaceAll("\\&amp\\;", "&");
    }

    @Override
    public String encodeRedirectURL(String baseUrl, Map<String, List<String>> parameters) {
        try {
            PortalActionURL portalUrl = new PortalActionURL(baseUrl);
            if (null != parameters && !parameters.isEmpty()) {
                for (Entry<String, List<String>> entry : parameters.entrySet()) {
                    for (String value : entry.getValue()) {
                        portalUrl.addParameter(entry.getKey(), value);
                    }
                }
            }
            return encodeURL(portalUrl.toString());
        } catch (MalformedURLException e) {
            throw new FacesException(e);
        }
    }

    protected boolean isInContext(PortalActionURL portalUrl) {
        String directLink = portalUrl.getParameter(Bridge.DIRECT_LINK);
        if (null != directLink) {
            portalUrl.removeParameter(Bridge.DIRECT_LINK);
            if (Boolean.parseBoolean(directLink)) {
                return false;
            }
        }
        return portalUrl.isInContext(getRequestContextPath());
    }

    protected void encodeBackLink(PortalActionURL portalUrl) {
        String backLink = portalUrl.getParameter(Bridge.BACK_LINK);
        if (null != backLink) {
            portalUrl.removeParameter(Bridge.BACK_LINK);
            FacesContext facesContext = FacesContext.getCurrentInstance();
            String viewId;
            if (null != facesContext.getViewRoot() && null != (viewId = facesContext.getViewRoot().getViewId())) {
                ViewHandler viewHandler = facesContext.getApplication().getViewHandler();
                String actionURL = viewHandler.getActionURL(facesContext, viewId);
                portalUrl.addParameter(backLink, encodeActionURL(actionURL));
            }
        }
    }

    protected BridgeLogger getLogger() {
        return bridgeContext.getBridgeConfig().getLogger();
    }

    protected abstract String createRenderUrl(PortalActionURL portalUrl, Map<String, List<String>> parameters);

    protected abstract String createResourceUrl(PortalActionURL portalUrl);

    protected abstract String createPartialActionUrl(PortalActionURL portalUrl);

    protected abstract String createActionUrl(PortalActionURL url);

}
