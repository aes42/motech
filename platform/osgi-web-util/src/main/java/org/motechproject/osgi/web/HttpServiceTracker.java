package org.motechproject.osgi.web;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.DispatcherServlet;

import java.util.Map;

class HttpServiceTracker extends ServiceTracker {
    private static Logger logger = LoggerFactory.getLogger(ServiceTracker.class);

    public static final String HEADER_CONTEXT_PATH = "Context-Path";

    private ServiceReference httpServiceRef;
    private String contextPath;
    private Map<String, String> resourceMapping;

    public HttpServiceTracker(BundleContext context, Map<String, String> resourceMapping) {
        super(context, HttpService.class.getName(), null);
        this.resourceMapping = resourceMapping;
    }

    @Override
    public Object addingService(ServiceReference serviceReference) {
        Object service = super.addingService(serviceReference);
        httpServiceRef = serviceReference;
        register((HttpService) service);
        return service;
    }

    @Override
    public void removedService(ServiceReference ref, Object service) {
        unregister((HttpService) service);
        super.removedService(ref, service);
    }

    public void unregister() {
        if (httpServiceRef != null) {
            unregister((HttpService) context.getService(httpServiceRef));
        }
    }

    private void unregister(HttpService service) {
        if (contextPath != null && service != null) {
            service.unregister(contextPath);
            logger.debug("Servlet unregistered");
            contextPath = null;
        }
    }

    public void register(HttpService service) {
        if (contextPath == null) {
            try {
                DispatcherServlet dispatcherServlet = new OsgiDispatcherServlet(context);
                contextPath = getContextPath(context);
                dispatcherServlet.setContextClass(MotechOsgiWebApplicationContext.class);
                dispatcherServlet.setContextConfigLocation(getContextLocation());
                ClassLoader old = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
                    service.unregister(contextPath);
                    service.registerServlet(contextPath, dispatcherServlet, null, null);
                    if (resourceMapping!=null) {
                        for (String key : resourceMapping.keySet()){
                            service.registerResources(key, resourceMapping.get(key),null);
                        }
                    }
                    logger.debug("Servlet registered");
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    Thread.currentThread().setContextClassLoader(old);
                }
            } catch (Exception e) {
                throw new ServletRegistrationException(e);
            }
        }
    }

    private String getContextLocation() {
        final String contextLocation = getHeaderValue("Context-File");
        return contextLocation != null ? contextLocation : Activator.DEFAULT_CONTEXT_CONFIG_LOCATION;
    }

    private String getContextPath(BundleContext bundleContext) {
        final String path = getHeaderValue(HEADER_CONTEXT_PATH);
        if (path != null) {
            return addRootPath(path);
        } else {
            return addRootPath(bundleContext.getBundle().getSymbolicName());
        }
    }

    private String getHeaderValue(String headerContextPath) {
        if (context.getBundle().getHeaders() == null) {
            return null;
        }
        return (String) context.getBundle().getHeaders().get(headerContextPath);
    }

    private String addRootPath(String path) {
        return "/" + path;
    }
}