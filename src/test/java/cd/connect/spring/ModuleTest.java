package cd.connect.spring;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Scope;


import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ModuleTest {


	private BeanDefinitionRegistry beanDefinitionRegistry;
	private Module module;

	@Before
	public void setUp(){
		beanDefinitionRegistry = mock(BeanDefinitionRegistry.class);
		module = new TestModule(beanDefinitionRegistry);
	}

	@Test
	public void registerRequestScopedBean() throws Exception {
		module.register(RequestScopeBean.class);
		ArgumentCaptor<AbstractBeanDefinition> argumentCaptor = ArgumentCaptor.forClass(AbstractBeanDefinition.class);
		verify(beanDefinitionRegistry).registerBeanDefinition(eq(RequestScopeBean.class.getName()), argumentCaptor.capture());

		assertEquals("request", argumentCaptor.getValue().getScope());

	}

	@Test
	public void registerSessionScopedBean() throws Exception {
		module.register(SessionScopeBean.class);
		ArgumentCaptor<AbstractBeanDefinition> argumentCaptor = ArgumentCaptor.forClass(AbstractBeanDefinition.class);
		verify(beanDefinitionRegistry).registerBeanDefinition(eq(SessionScopeBean.class.getName()), argumentCaptor.capture());

		assertEquals("session", argumentCaptor.getValue().getScope());

	}

	@Test
	public void registerDefaultBean() throws Exception {
		module.register(NoScopeBean.class);
		ArgumentCaptor<AbstractBeanDefinition> argumentCaptor = ArgumentCaptor.forClass(AbstractBeanDefinition.class);
		verify(beanDefinitionRegistry).registerBeanDefinition(eq(NoScopeBean.class.getName()), argumentCaptor.capture());

		assertEquals("singleton", argumentCaptor.getValue().getScope());

	}

	private class TestModule extends Module {

		TestModule(BeanDefinitionRegistry beanDefinitionRegistry){
			this.beanDefinitionRegistry = beanDefinitionRegistry;
		}

		@Override
		public void register() {

		}
	}

	public class NoScopeBean{

	}

	@Scope("request")
	public class RequestScopeBean{

	}

	@Scope("session")
	public class SessionScopeBean{

	}
}
