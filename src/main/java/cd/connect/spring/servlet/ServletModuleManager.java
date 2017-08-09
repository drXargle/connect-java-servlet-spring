package cd.connect.spring.servlet;

import com.bluetrainsoftware.common.config.PreStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.inject.Inject;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class gathers all of the collected filters and servlets, sorts them into priority order and
 * registers them. This allows you to spread your filters and servlets out in a modular fashion and yet ensure
 * they will come back together in an orderly method.
 *
 * Filters particularly are always ordered "isMatchAfter" which is why it is critical that they are ordered
 * by priority.
 *
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class ServletModuleManager {
	private final ServletContext servletContext;
	private final ApplicationContext applicationContext;
	private static final Logger log = LoggerFactory.getLogger(ServletModuleManager.class);
	private boolean failed = false;

	@Inject
	public ServletModuleManager(ServletContext servletContext,
	                            ApplicationContext applicationContext) {
		this.servletContext = servletContext;
		this.applicationContext = applicationContext;
	}

	/**
	 * Here the context has been refreshed and the configuration injected, but the web app (or whatever) has not
	 * started.
	 */
	@PreStart
	public void preStart() {

		ServletModule.servletModules.values().forEach(s -> {
			try {
				s.postProcess(servletContext, applicationContext);
			} catch (Exception e) {
				log.error("Failed to initialize {}", s.getClass().getName(), e);
				failed = true;
			}
		});

		if (failed) {
			throw new RuntimeException("Unable to start application, see logs.");
		}

		try {
			postProcessFilters(servletContext, applicationContext);
			postProcessServlets(servletContext, applicationContext);
		} catch (Exception e) {
			log.error("Unable to process filters or servlets", e);
			throw new RuntimeException("Unable to start application, see logs.");
		}
	}

	private void postProcessServlets(ServletContext servletContext, ApplicationContext ctx) {
		List<ServletModule.ServletDefinition> definitionList = new ArrayList<>();

		ServletModule.servlets.values().forEach(definitionList::addAll);

		definitionList.sort(Comparator.comparingInt(Definition::getPriority));

		definitionList.forEach(sf ->
			registerServletWithServletContext(servletContext, sf,
				sf.servlet == null ? applicationContext.getBean(sf.clazz) : sf.servlet)
		);
	}

	private void registerServletWithServletContext(ServletContext servletContext, ServletModule.ServletDefinition reg, Servlet servlet) {
		if (reg.webServlet != null) {
			WebServlet ws = reg.webServlet;

			String name = ws.name().length() > 0 ? ws.name() : reg.clazz.getName();

			ServletRegistration.Dynamic registration = servletContext.addServlet(name, servlet);

			if (registration == null) {
				log.warn("Skipping duplicate servlet {} : {}", name, servlet.getClass().getName());
				return;
			}

			registration.addMapping(ws.urlPatterns());
			registration.setInitParameters(fromInitParams(ws.initParams()));
			registration.setLoadOnStartup(ws.loadOnStartup());
			registration.setAsyncSupported(ws.asyncSupported());

			log.debug("Servlet Registered @WebServlet/{} / priority {} with url(s) {}", reg.clazz.getName(),
						reg.getPriority(), ws.urlPatterns());
		} else {
			String servletName = reg.getName() == null ? reg.clazz.getName() : reg.getName();
			ServletRegistration.Dynamic registration =
				servletContext.addServlet(servletName, servlet);

			if (registration == null) {
				log.warn("Skipping duplicate servlet {} : {}", servletName, servlet.getClass().getName());
				return;
			}

			String[] urls = reg.getUrls().toArray(new String[0]);

			registration.addMapping(urls);

			log.debug("Servlet Registered {}:{} / priority {} with url(s) {}", registration.getName(), reg.clazz.getName(), reg.getPriority(), urls);

			if (reg.getParams() != null) {
				registration.setInitParameters(reg.getParams());
			}

			registration.setAsyncSupported(reg.isAsync());
		}
	}

	private void postProcessFilters(ServletContext servletContext, ApplicationContext ctx) {
		List<ServletModule.FilterDefinition> definitionList = new ArrayList<>();

		// collect all of the filters in one bundle
		ServletModule.filters.values().forEach(definitionList::addAll);

		definitionList.sort(Comparator.comparingInt(Definition::getPriority));

		definitionList.forEach(sf ->
			registerFilterWithServletContext(servletContext, sf,
				sf.filter == null ? applicationContext.getBean(sf.clazz) : sf.filter)
		);
	}

	private void registerFilterWithServletContext(ServletContext servletContext, ServletModule.FilterDefinition reg, Filter filter) {
		if (reg.webFilter != null) {
			WebFilter wf = reg.webFilter;

			String name = wf.filterName().length() == 0 ? wf.getClass().getName() : wf.filterName();

			FilterRegistration.Dynamic registration = servletContext.addFilter(name, filter);

			if (registration == null) {
				log.warn("Skipping duplicate filter {}:{}", name, filter.getClass().getName());
				return;
			}

			registration.setAsyncSupported(wf.asyncSupported());

			EnumSet<DispatcherType> dispatcherTypes = EnumSet.of(wf.dispatcherTypes()[0], wf.dispatcherTypes());

			String[] urlPatterns = wf.urlPatterns();
			if (wf.urlPatterns().length == 0) {
				urlPatterns = wf.value();
			}

			registration.addMappingForUrlPatterns(dispatcherTypes, true, urlPatterns);

			if (wf.servletNames().length != 0) {
				registration.addMappingForServletNames(dispatcherTypes, true, wf.servletNames());
			}

			registration.setInitParameters(fromInitParams(wf.initParams()));

			log.debug("Filter Registered: @WebFilter/{}, priority {} : {}", name, reg.getPriority(), wf.urlPatterns());
		} else { // lets assume the rest of the Definition fields were filled in
			String filterName = reg.getName() == null ? reg.clazz.getName() : reg.getName();
			FilterRegistration.Dynamic registration = servletContext.addFilter(
				filterName, filter);

			if (registration == null) {
				log.warn("Skipping duplicate filter {}", filterName);
				return;
			}
			String[] urlPatterns = reg.getUrls().toArray(new String[0]);
			registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, urlPatterns);

			log.debug("Filter Registered: {}, priority {} : {}", filterName, reg.getPriority(), urlPatterns);

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
