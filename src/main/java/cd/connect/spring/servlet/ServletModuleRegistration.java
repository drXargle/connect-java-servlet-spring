package cd.connect.spring.servlet;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.ServletContext;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@Configuration
public class ServletModuleRegistration {
	@Bean
	public ServletModuleManager servletModuleManager(ServletContext servletContext,
	                                                 ApplicationContext applicationContext) {
		return new ServletModuleManager(servletContext, applicationContext);
	}
}
