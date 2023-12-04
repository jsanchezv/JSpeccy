package jspeccy;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySourcesPropertyResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class used to substitute properties in language translation bundles.
 */
public class BundlePropertyResolver {

    /**
     * Return a mutable list of the {@link PropertyResolver} instances that will be used to resolve placeholders.
     *
     * @param environment the environment
     * @return a mutable list of property resolvers
     */
    protected List<PropertyResolver> getPropertyResolvers(final Environment environment) {

        MutablePropertySources sources = new MutablePropertySources();
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            configurableEnvironment.getPropertySources().forEach(sources::addLast);
        }
        List<PropertyResolver> resolvers = new ArrayList<>();
        resolvers.add(new PropertySourcesPropertyResolver(sources));
        return resolvers;
    }

    /**
     * Resolve ${...} placeholders in the given text, replacing them with corresponding property values as resolved by
     * {@link PropertyResolver#getProperty}. Unresolvable placeholders with no default value are ignored and passed
     * through unchanged.
     *
     * @param text the String to resolve
     * @return the resolved String (never {@code null})
     * @throws IllegalArgumentException if given text is {@code null}
     */
    public String resolvePlaceholders(final String text, final Environment environment) {

        String resolvedText = text;
        for (PropertyResolver resolver : getPropertyResolvers(environment)) {
            resolvedText = resolver.resolvePlaceholders(resolvedText);
        }
        return resolvedText;
    }

}
