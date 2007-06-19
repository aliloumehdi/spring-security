/**
 * 
 */
package org.acegisecurity.config;

import java.util.ArrayList;
import java.util.List;

import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.annotation.SecurityAnnotationAttributes;
import org.acegisecurity.intercept.method.MethodDefinitionAttributes;
import org.acegisecurity.intercept.method.aopalliance.MethodDefinitionSourceAdvisor;
import org.acegisecurity.intercept.method.aopalliance.MethodSecurityInterceptor;
import org.acegisecurity.intercept.web.FilterInvocationDefinitionDecorator;
import org.acegisecurity.intercept.web.FilterInvocationDefinitionSourceMapping;
import org.acegisecurity.intercept.web.FilterSecurityInterceptor;
import org.acegisecurity.intercept.web.PathBasedFilterInvocationDefinitionMap;
import org.acegisecurity.runas.RunAsManagerImpl;
import org.acegisecurity.vote.AffirmativeBased;
import org.acegisecurity.vote.AuthenticatedVoter;
import org.acegisecurity.vote.RoleVoter;
import org.acegisecurity.vote.UnanimousBased;
import org.acegisecurity.wrapper.SecurityContextHolderAwareRequestFilter;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * Parses 'autoconfig' tag and creates all the required
 * <code>BeanDefinition</code>s with their default configurations. It also
 * resolves their dependencies and wire them together.
 * 
 * @author Vishal Puri
 * 
 */
public class AutoConfigBeanDefinitionParser implements BeanDefinitionParser {

	private RootBeanDefinition authenticationManager;

	private RootBeanDefinition rememberMeServices;

	private ManagedList decisionVoters = new ManagedList();

	public BeanDefinition parse(Element element, ParserContext parserContext) {
		// authentication manager
		this.authenticationManager = AuthenticationMechanismBeanDefinitionParser
				.createAndRegisterBeanDefinitionWithDefaults(parserContext);
		// remembermeServices
		this.rememberMeServices = RememberMeServicesBeanDefinitionParser
				.createAndRegisterBeanDefintionWithDefaults(parserContext);
		// flters
		createAndRegisterBeanDefinitionForHttpSessionContextIntegrationFilter(parserContext);
		createAndRegisterBeanDefinitionForLogoutFilter(parserContext, rememberMeServices);
		createAndRegisterBeanDefinitionForAuthenticationProcessingFilter(parserContext, authenticationManager,
				rememberMeServices);
		createAndRegisterBeanDefinitionForRememberMeProcessingFilter(parserContext, authenticationManager);
		createAndRegisterBeanDefinitionForExceptionTranslationFilter(parserContext);
		createAndRegisterBeanDefintionForSecurityContextHolderAwareRequestFilter(parserContext);

		// method interceptor
		createAndRegisterBeanDefinitinoForMethodDefinitionSourceAdvisor(parserContext, authenticationManager);
		createAndRegisterDefaultAdvisorAutoProxyCreator(parserContext);

		// filter security interceptor
		createAndRegisterBeanDefinitionForFilterSecurityInterceptor(parserContext, authenticationManager);
		return null;
	}

	private void createAndRegisterBeanDefintionForSecurityContextHolderAwareRequestFilter(ParserContext parserContext) {
		RootBeanDefinition beanDefinition = new RootBeanDefinition(SecurityContextHolderAwareRequestFilter.class);
		registerBeanDefinition(parserContext, beanDefinition);
	}

	/**
	 * Creates <code>FilterSecurityInterceptor</code> bean definition and
	 * register it with the <code>ParserContext</code>
	 * 
	 * @param parserContext To register the bean definition with
	 * @param authenticationManager The <code>AuthenticationManager</code> to
	 * set as a property in the bean definition
	 */
	private void createAndRegisterBeanDefinitionForFilterSecurityInterceptor(ParserContext parserContext,
			RootBeanDefinition authenticationManager) {
		RootBeanDefinition filterInvocationInterceptor = new RootBeanDefinition(FilterSecurityInterceptor.class);
		filterInvocationInterceptor.getPropertyValues()
				.addPropertyValue("authenticationManager", authenticationManager);
		RootBeanDefinition accessDecisionManager = createAccessDecisionManagerAffirmativeBased();
		filterInvocationInterceptor.getPropertyValues()
				.addPropertyValue("accessDecisionManager", accessDecisionManager);

		FilterInvocationDefinitionDecorator source = new FilterInvocationDefinitionDecorator();
		source.setDecorated(new PathBasedFilterInvocationDefinitionMap());

		FilterInvocationDefinitionSourceMapping mapping = new FilterInvocationDefinitionSourceMapping();

		String url1 = "/acegilogin.jsp";
		String value1 = "IS_AUTHENTICATED_ANONYMOUSLY";

		String url2 = "/**";
		String value2 = "IS_AUTHENTICATED_REMEMBERED";

		mapping.setUrl(url1);
		mapping.addConfigAttribute(value1);

		mapping.setUrl(url2);
		mapping.addConfigAttribute(value2);

		List mappings = new ArrayList();
		mappings.add(mapping);
		source.setMappings(mappings);
		filterInvocationInterceptor.getPropertyValues().addPropertyValue("objectDefinitionSource",
				source.getDecorated());
		registerBeanDefinition(parserContext, filterInvocationInterceptor);
	}

	private RootBeanDefinition createAccessDecisionManagerAffirmativeBased() {
		RootBeanDefinition accessDecisionManager = new RootBeanDefinition(AffirmativeBased.class);
		accessDecisionManager.getPropertyValues().addPropertyValue("allowIfAllAbstainDecisions", Boolean.FALSE);
		RootBeanDefinition authenticatedVoter = new RootBeanDefinition(AuthenticatedVoter.class);
		this.decisionVoters.add(authenticatedVoter);
		accessDecisionManager.getPropertyValues().addPropertyValue("decisionVoters", decisionVoters);
		return accessDecisionManager;
	}

	private void createAndRegisterDefaultAdvisorAutoProxyCreator(ParserContext parserContext) {
		registerBeanDefinition(parserContext, new RootBeanDefinition(DefaultAdvisorAutoProxyCreator.class));
	}

	private void createAndRegisterBeanDefinitinoForMethodDefinitionSourceAdvisor(ParserContext parserContext,
			RootBeanDefinition authenticationManager) {
		RootBeanDefinition methodSecurityAdvisor = new RootBeanDefinition(MethodDefinitionSourceAdvisor.class);

		RootBeanDefinition securityInterceptor = createMethodSecurityInterceptor(authenticationManager);
		methodSecurityAdvisor.getConstructorArgumentValues().addIndexedArgumentValue(0, securityInterceptor);
		registerBeanDefinition(parserContext, methodSecurityAdvisor);

	}

	private RootBeanDefinition createAccessDecisionManagerUnanimousBased() {
		RootBeanDefinition accessDecisionManager = new RootBeanDefinition(UnanimousBased.class);
		accessDecisionManager.getPropertyValues().addPropertyValue("allowIfAllAbstainDecisions", Boolean.FALSE);
		RootBeanDefinition roleVoter = createRoleVoter();
		decisionVoters.add(roleVoter);
		accessDecisionManager.getPropertyValues().addPropertyValue("decisionVoters", decisionVoters);
		return accessDecisionManager;
	}

	private RootBeanDefinition createRoleVoter() {
		return new RootBeanDefinition(RoleVoter.class);
	}

	private RootBeanDefinition createMethodSecurityInterceptor(RootBeanDefinition authenticationManager) {
		RootBeanDefinition securityInterceptor = new RootBeanDefinition(MethodSecurityInterceptor.class);
		securityInterceptor.getPropertyValues().addPropertyValue("authenticationManager", authenticationManager);
		RootBeanDefinition accessDecisionManager = createAccessDecisionManagerUnanimousBased();
		securityInterceptor.getPropertyValues().addPropertyValue("accessDecisionManager", accessDecisionManager);
		securityInterceptor.getPropertyValues().addPropertyValue("validateConfigAttributes", Boolean.FALSE);
		RootBeanDefinition runAsManager = createRunAsManager();
		securityInterceptor.getPropertyValues().addPropertyValue("runAsManager", runAsManager);
		RootBeanDefinition objectDefinitionSource = createMethodDefinitionAttributes();
		securityInterceptor.getPropertyValues().addPropertyValue("objectDefinitionSource", objectDefinitionSource);
		return securityInterceptor;
	}

	private RootBeanDefinition createMethodDefinitionAttributes() {
		RootBeanDefinition objectDefinitionSource = new RootBeanDefinition(MethodDefinitionAttributes.class);
		RootBeanDefinition attributes = createSecurityAnnotationAttributes();
		objectDefinitionSource.getPropertyValues().addPropertyValue("attributes", attributes);
		return objectDefinitionSource;
	}

	private RootBeanDefinition createSecurityAnnotationAttributes() {
		return new RootBeanDefinition(SecurityAnnotationAttributes.class);
	}

	private RootBeanDefinition createRunAsManager() {
		RootBeanDefinition runAsManager = new RootBeanDefinition(RunAsManagerImpl.class);
		runAsManager.getPropertyValues().addPropertyValue("key", "my_run_as_password");
		return runAsManager;
	}

	private void createAndRegisterBeanDefinitionForExceptionTranslationFilter(ParserContext parserContext) {
		registerBeanDefinition(parserContext, ExceptionTranslationFilterBeanDefinitionParser
				.createBeanDefinitionWithDefaults());
	}

	private void createAndRegisterBeanDefinitionForRememberMeProcessingFilter(ParserContext parserContext,
			RootBeanDefinition authenticationManager) {
		registerBeanDefinition(parserContext, RememberMeFilterBeanDefinitionParser.createBeanDefinitionWithDefaults(
				parserContext, authenticationManager));
	}

	private void createAndRegisterBeanDefinitionForAuthenticationProcessingFilter(ParserContext parserContext,
			RootBeanDefinition authenticationManager, RootBeanDefinition rememberMeServices) {
		RootBeanDefinition defintion = AuthenticationProcessingFilterBeanDefinitionParser
				.createBeandefinitionWithDefaults(parserContext, authenticationManager, rememberMeServices);
		registerBeanDefinition(parserContext, defintion);
	}

	private void createAndRegisterBeanDefinitionForLogoutFilter(ParserContext parserContext,
			RootBeanDefinition rememberMeServices) {
		RootBeanDefinition defintion = LogoutFilterBeanDefinitionParser
				.createBeanDefinitionWithDefaults(rememberMeServices);
		registerBeanDefinition(parserContext, defintion);
	}

	private void createAndRegisterBeanDefinitionForHttpSessionContextIntegrationFilter(ParserContext parserContext) {
		RootBeanDefinition defintion = ContextIntegrationBeanDefinitionParser.createBeanDefinitionWithDefaults();
		registerBeanDefinition(parserContext, defintion);
		// retrieveBeanDefinition(parserContext, o)
	}

	/**
	 * @param parserContext
	 * @param defintion
	 */
	private void registerBeanDefinition(ParserContext parserContext, RootBeanDefinition defintion) {
		parserContext.getRegistry().registerBeanDefinition(
				parserContext.getReaderContext().generateBeanName(defintion), defintion);
	}

	/**
	 * Returns a <code>BeanDefinition</code> of the specified type.
	 * 
	 * @param parserContext
	 * @param type
	 * @return
	 */
	private RootBeanDefinition retrieveBeanDefinition(ParserContext parserContext, Class type) {
		String[] names = parserContext.getRegistry().getBeanDefinitionNames();
		for (String name : names) {
			BeanDefinition beanDefinition = parserContext.getRegistry().getBeanDefinition(name);
			if (type.isInstance(beanDefinition)) {
				return (RootBeanDefinition) beanDefinition;
			}
		}
		return null;
	}

	private Class ss(Object o) {
		return o.getClass();
	}
}