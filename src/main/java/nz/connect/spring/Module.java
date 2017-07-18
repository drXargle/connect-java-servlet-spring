package nz.connect.spring;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.stream.Stream;

/**
 * This forms the basis for the a class that is going to get called to register a bunch of beans
 * or that wish to register them in a special way.
 *
 * It would be unusual to use this outside of the code generation from the gen-code-scanner Maven plugin. If you are
 * doing this by hand you can just use @Import and @Configuration from Spring.
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
abstract public class Module implements ImportBeanDefinitionRegistrar {
	protected BeanDefinitionRegistry beanDefinitionRegistry;

	protected void register(Stream<Class<?>> classes) {
		classes.forEach(this::register);
	}

	protected void register(Class<?> clazz) {
		AbstractBeanDefinition bean = BeanDefinitionBuilder.genericBeanDefinition(clazz).setScope(BeanDefinition.SCOPE_SINGLETON).getRawBeanDefinition();
		beanDefinitionRegistry.registerBeanDefinition(bean.getBeanClassName(), bean);
	}

	abstract public void register();

	@Override
	public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
		this.beanDefinitionRegistry = beanDefinitionRegistry;
		register();
	}
}
