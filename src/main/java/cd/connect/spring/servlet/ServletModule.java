package cd.connect.spring.servlet;


import com.bluetrainsoftware.common.config.PreStart;
import nz.connect.spring.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.type.AnnotationMetadata;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
abstract public class ServletModule extends Module implements ApplicationContextAware {
  private static final Logger log = LoggerFactory.getLogger(ServletModule.class);

  private static class ServletDefinition extends Definition {
    Class<? extends Servlet> clazz;
    WebServlet webServlet;
  }

  private static class FilterDefinition extends Definition {
    Class<? extends Filter> clazz;
    WebFilter webFilter;
  }

  private static Map<Class<? extends ServletModule>, List<ServletDefinition>> servlets = new HashMap<>();
  private static Map<Class<? extends ServletModule>, List<FilterDefinition>> filters = new HashMap<>();
  private ServletContext servletContext;
  private ApplicationContext applicationContext;

  private void addFilter(FilterDefinition fd) {
    List<FilterDefinition> filterDefinitions = filters.computeIfAbsent(this.getClass(), k -> new ArrayList<>());

    filterDefinitions.add(fd);
  }

  private List<FilterDefinition> getFilters() {
    List<FilterDefinition> filterDefinitions = filters.get(this.getClass());
    if (filterDefinitions == null) {
      return new ArrayList<>(); // play nice
    } else {
      return filterDefinitions;
    }
  }

  private void addServlet(ServletDefinition sd) {
    List<ServletDefinition> servletDefinitions = servlets.computeIfAbsent(this.getClass(), k -> new ArrayList<>());

    servletDefinitions.add(sd);
  }

  private List<ServletDefinition> getServlets() {
    List<ServletDefinition> servletDefinitions = servlets.get(this.getClass());
    if (servletDefinitions == null) {
      return new ArrayList<>(); // play nice
    } else {
      return servletDefinitions;
    }
  }



  @Override
  public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
    super.registerBeanDefinitions(annotationMetadata, beanDefinitionRegistry);

    // always register ourselves so we get the context aware and beans can be registered on us
    register(this.getClass());
  }

  /**
   * called when the context has been refreshed. As this is not a Spring Bean (this module) we cannot
   * tap directly into ApplicationContextAware
   */
  public void setApplicationContext(ApplicationContext ctx) throws BeansException {
    if (servletContext == null) {
      this.servletContext = ctx.getBean(ServletContext.class);
      this.applicationContext = ctx;
    }
  }

  /**
   * Here the context has been refreshed and the configuration injected, but the web app (or whatever) has not
   * started.
   */
  @PreStart
  public void preStart() {
    postProcessFilters(servletContext, applicationContext);
    postProcessServlets(servletContext, applicationContext);
    postProcess(servletContext, applicationContext);
  }

  protected void postProcess(ServletContext servletContext, ApplicationContext ctx) {
  }

  /**
   * holds the definitions and registers the class. Seems weird to do it twice.
   *
   * @param clazz
   * @param consumer
   */
  protected void servlet(Class<? extends Servlet> clazz, boolean register, Consumer<Definition> consumer) {
    ServletDefinition reg = new ServletDefinition();
    consumer.accept(reg);
    reg.clazz = clazz;
    if (register) {
      register(clazz);
    }
    addServlet(reg);
  }

  protected void servlet(Class<? extends Servlet> clazz) {
    WebServlet ws = clazz.getAnnotation(WebServlet.class);

    if (ws == null) {
      throw new RuntimeException("Servlet setup for registration by annotation has no WebServlet annotation");
    }

    ServletDefinition reg = new ServletDefinition();
    reg.webServlet = ws;
    reg.clazz = clazz;
    register(clazz);
    addServlet(reg);
  }

  protected void servlet(Class<? extends Servlet> clazz, Consumer<Definition> consumer) {
    servlet(clazz, true, consumer);
  }

  /**
   * allow servlets to be registered AFTER the refresh happens (e.g. jersey needs this to support multiple servlets)
   *
   * @param servlet
   * @param consumer
   */
  protected void servlet(Servlet servlet, Consumer<Definition> consumer) {
    ServletDefinition reg = new ServletDefinition();

    reg.clazz = servlet.getClass();
    reg.webServlet = reg.clazz.getAnnotation(WebServlet.class);

    consumer.accept(reg);

    registerServletWithServletContext(servletContext, reg, servlet);
  }

  private void postProcessServlets(ServletContext servletContext, ApplicationContext ctx) {
    for (ServletDefinition reg : getServlets()) {
      registerServletWithServletContext(servletContext, reg, ctx.getBean(reg.clazz));
    }
  }

  private void registerServletWithServletContext(ServletContext servletContext, ServletDefinition reg, Servlet servlet) {
    if (reg.webServlet != null) {
      WebServlet ws = reg.webServlet;

      String name = ws.name().length() > 0 ? ws.name() : reg.clazz.getName();

      ServletRegistration.Dynamic registration = servletContext.addServlet(name, servlet);

      registration.addMapping(ws.urlPatterns());
      registration.setInitParameters(fromInitParams(ws.initParams()));
      registration.setLoadOnStartup(ws.loadOnStartup());
      registration.setAsyncSupported(ws.asyncSupported());
    } else {
      ServletRegistration.Dynamic registration =
        servletContext.addServlet(reg.getName() == null ? reg.clazz.getName() : reg.getName(), servlet);

      String[] urls = reg.getUrls().toArray(new String[0]);

      registration.addMapping(urls);

      log.debug("Registered {}:{} with url(s) {}", registration.getName(), reg.clazz.getName(), urls);

      if (reg.getParams() != null) {
        registration.setInitParameters(reg.getParams());
      }
    }
  }

  /**
   * Register a filter but it must have an WWebFilter annotation.
   *
   * @param clazz - filter with annotation for a filter
   */

  protected void filter(Class<? extends Filter> clazz) {
    WebFilter webFilter = clazz.getAnnotation(WebFilter.class);
    if (webFilter == null) {
      throw new RuntimeException("Filter setup for registration by annotation has no WebFilter annotation");
    }

    FilterDefinition reg = new FilterDefinition();
    reg.webFilter = webFilter;
    reg.clazz = clazz;
    register(clazz);
    addFilter(reg);
  }

  /**
   * They want to register the servlet it, but they may not wish to register the class for wiring.
   *
   * @param clazz - the class to register
   * @param register - register the class for wiring (may have been done elsewhere)
   * @param consumer - the back call to allow data setup
   */
  protected void filter(Class<? extends Filter> clazz, boolean register, Consumer<Definition> consumer) {
    FilterDefinition reg = new FilterDefinition();

    reg.clazz = clazz;
    reg.webFilter = reg.clazz.getAnnotation(WebFilter.class);

    consumer.accept(reg);
    if (register) {
      register(clazz);
    }
    addFilter(reg);
  }

  protected void filter(Filter filter, Consumer<Definition> consumer ) {
    if (servletContext == null) {
      throw new RuntimeException("Can only register filter objects after context is ready.");
    }

    FilterDefinition reg = new FilterDefinition();
    consumer.accept(reg);
    reg.clazz = filter.getClass();
    registerFilterWithServletContext(servletContext, reg, filter);
  }

  protected void filter(Class<? extends Filter> clazz, Consumer<Definition> consumer) {
    filter(clazz, true, consumer);
  }

  private void postProcessFilters(ServletContext servletContext, ApplicationContext ctx) {
    for (FilterDefinition reg : getFilters()) {
      registerFilterWithServletContext(servletContext, reg, ctx.getBean(reg.clazz));
    }
  }

  private void registerFilterWithServletContext(ServletContext servletContext, FilterDefinition reg, Filter filter) {
    if (reg.webFilter != null) {
      WebFilter wf = reg.webFilter;

      String name = wf.filterName().length() == 0 ? wf.getClass().getName() : wf.filterName();

      FilterRegistration.Dynamic registration = servletContext.addFilter(name, filter);

      registration.setAsyncSupported(wf.asyncSupported());

      EnumSet<DispatcherType> dispatcherTypes = EnumSet.of(wf.dispatcherTypes()[0], wf.dispatcherTypes());

      if (wf.urlPatterns().length != 0) {
        registration.addMappingForUrlPatterns(dispatcherTypes, true, wf.urlPatterns());
      } else if (wf.value().length != 0) {
        registration.addMappingForUrlPatterns(dispatcherTypes, true, wf.value());
      }

      if (wf.servletNames().length != 0) {
        registration.addMappingForServletNames(dispatcherTypes, true, wf.servletNames());
      }

      registration.setInitParameters(fromInitParams(wf.initParams()));
    } else { // lets assume the rest of the Definition fields were filled in
      FilterRegistration.Dynamic registration = servletContext.addFilter(
        reg.getName() == null ? reg.clazz.getName() : reg.getName(), filter);

      registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, reg.getUrls().toArray(new String[0]));

      if (reg.getParams() != null) {
        registration.setInitParameters(reg.getParams());
      }
    }
  }

  private Map<String, String> fromInitParams(WebInitParam params[]) {
    Map<String, String> p = new HashMap<>();

    if (params != null) {
      for(WebInitParam param : params) {
        p.put(param.name(), param.value());
      }
    }

    return p;
  }
}
