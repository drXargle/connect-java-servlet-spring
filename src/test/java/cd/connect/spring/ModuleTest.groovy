package cd.connect.spring

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.annotation.Scope
import spock.lang.Specification

class ModuleTest extends Specification {

	final BeanDefinitionRegistry beanDefinitionRegistry = Mock()
	final Module module = new TestModule(beanDefinitionRegistry)

	def "register request scoped bean"() {
		when:
		module.register(RequestScopeBean.class)

		then:
		1 * beanDefinitionRegistry.registerBeanDefinition(*_) >> {
			arguments ->
				assert "request" == arguments[1].getScope()
		}
	}

	def "register session scoped bean"() {
		when:
		module.register(SessionScopeBean.class)

		then:
		1 * beanDefinitionRegistry.registerBeanDefinition(*_) >> {
			arguments ->
				assert "session" == arguments[1].getScope()
		}
	}

	def "register default scoped bean"() {
		when:
		module.register(NoScopeBean.class)

		then:
		1 * beanDefinitionRegistry.registerBeanDefinition(*_) >> {
			arguments ->
				assert "singleton" == arguments[1].getScope()
		}
	}

	private class TestModule extends Module {

		TestModule(BeanDefinitionRegistry beanDefinitionRegistry){
			this.beanDefinitionRegistry = beanDefinitionRegistry
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

