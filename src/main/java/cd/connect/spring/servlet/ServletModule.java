package cd.connect.spring.servlet;


import cd.connect.spring.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.core.type.AnnotationMetadata;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebServlet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
abstract public class ServletModule extends Module {
  private static final Logger log = LoggerFactory.getLogger(ServletModule.class);

  static class ServletDefinition extends Definition {
    Class<? extends Servlet> clazz;
    Servlet servlet;
    WebServlet webServlet;
  }

  static class FilterDefinition extends Definition {
    Class<? extends Filter> clazz;
    Filter filter;
    WebFilter webFilter;
  }

  static Map<Class<? extends ServletModule>, List<ServletDefinition>> servlets = new HashMap<>();
  // because these are not spring objects, they can't get injected anywhere, so we need to hold onto them to clean up later
  static Map<Class<? extends ServletModule>, ServletModule> servletModules = new HashMap<>();
  static Map<Class<? extends ServletModule>, List<FilterDefinition>> filters = new HashMap<>();

	public ServletModule() {
		// we have to do this because Spring is creating multiple instances of this class, which
		// is just *weird*. So we need to de-dupe it so we don't postProcess more than once.
		servletModules.put(this.getClass(), this);
	}

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

	  reg.clazz = clazz;
	  reg.webServlet = reg.clazz.getAnnotation(WebServlet.class);

	  if (consumer == null && reg.webServlet == null) {
		  throw new RuntimeException("Servlet setup for registration by annotation has no WebServlet annotation");
	  }

	  // we can have both annotation & this
	  if (consumer != null) {
	    consumer.accept(reg);
    }

    if (register) {
      register(clazz);
    }

    addServlet(reg);
  }

  protected void servlet(Class<? extends Servlet> clazz) {
  	servlet(clazz, true, null);
  }

	protected void servlet(Class<? extends Servlet> clazz, boolean register) {
		servlet(clazz, register, null);
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
		reg.servlet = servlet;
		reg.webServlet = reg.clazz.getAnnotation(WebServlet.class);

		if (consumer == null && reg.webServlet == null) {
			throw new RuntimeException("Servlet setup for registration by annotation has no WebServlet annotation");
		}

		if (consumer != null) {
			consumer.accept(reg);
		}

		addServlet(reg);
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

  protected void filter(Class<? extends Filter> clazz, Consumer<Definition> consumer) {
    filter(clazz, true, consumer);
  }

	protected void filter(Filter filter, Consumer<Definition> consumer ) {
		FilterDefinition reg = new FilterDefinition();
		consumer.accept(reg);
		reg.clazz = filter.getClass();
		reg.filter = filter;
		addFilter(reg);
	}

}
