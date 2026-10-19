package org.lucoenergia.conluz.infrastructure.shared.security;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;

/**
 * Replaces the application {@link PasswordEncoder} with a BCrypt encoder of cost 4, the lowest BCrypt accepts, so
 * that hashing and checking passwords does not dominate the time of the test suite (#443).
 *
 * <p>A {@code @Primary} bean under another name is not enough: {@link ApplicationConfig} hands the encoder to the
 * authentication provider through an inter-bean {@code @Bean} method call, which resolves the bean by name, so
 * login would keep the production encoder. The bean named {@code passwordEncoder} is therefore replaced in place,
 * and stays the only {@link PasswordEncoder}, so that a {@code @MockitoSpyBean PasswordEncoder} sees the
 * comparisons of both the login and the password change paths.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class FastPasswordEncoderTestConfiguration {

    @Bean
    static BeanDefinitionRegistryPostProcessor fastPasswordEncoderPostProcessor() {
        return new FastPasswordEncoderPostProcessor();
    }

    static class FastPasswordEncoderPostProcessor implements BeanDefinitionRegistryPostProcessor {

        private static final String BEAN_NAME = "passwordEncoder";
        private static final int STRENGTH = 4;

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            if (!registry.containsBeanDefinition(BEAN_NAME)) {
                throw new IllegalStateException("The test password encoder replacement expects a production bean "
                        + "named '" + BEAN_NAME + "' (#443)");
            }
            BeanDefinition production = registry.getBeanDefinition(BEAN_NAME);
            RootBeanDefinition replacement = new RootBeanDefinition(PasswordEncoder.class,
                    () -> new BCryptPasswordEncoder(STRENGTH));
            replacement.setPrimary(production.isPrimary());
            replacement.setRole(production.getRole());
            replacement.setLazyInit(production.isLazyInit());
            // Removed before registering, so that no bean definition overriding takes place
            registry.removeBeanDefinition(BEAN_NAME);
            registry.registerBeanDefinition(BEAN_NAME, replacement);
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            String[] names = beanFactory.getBeanNamesForType(PasswordEncoder.class, true, false);
            if (!Arrays.equals(names, new String[]{BEAN_NAME})) {
                throw new IllegalStateException("The test password encoder replacement expects '" + BEAN_NAME
                        + "' to be the only PasswordEncoder bean, found " + Arrays.toString(names) + " (#443)");
            }
        }
    }
}
