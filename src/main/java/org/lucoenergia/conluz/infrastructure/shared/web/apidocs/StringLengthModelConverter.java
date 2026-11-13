package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

/**
 * Keeps the minimum length of a model's property when a {@code @Size} publishes its maximum.
 *
 * <p>swagger-core publishes a {@code @Size} as both {@code maxLength} and {@code minLength}, the latter from
 * {@code @Size.min}, which defaults to 0. It writes it over the {@code minLength} of 1 that it publishes for
 * {@code @NotEmpty} and {@code @NotBlank}, and adds a {@code minLength} of 0 that bounds nothing. This converter
 * recomputes, for every property that carries a {@code @Size}, the strictest minimum length: the largest
 * {@code @Size.min}, or 1 when the property is also {@code @NotEmpty} or {@code @NotBlank}. It writes it as
 * {@code minLength}, or removes {@code minLength} when that minimum is 0, so that publishing a maximum length changes
 * nothing else in the property's schema (#574).</p>
 *
 * <p>It is a {@link BeanValidationModelConverter} because swagger-core applies bean validation after property
 * customizers have run.</p>
 */
@Component
public class StringLengthModelConverter extends BeanValidationModelConverter {

    public StringLengthModelConverter(ObjectMapperProvider objectMapperProvider) {
        super(objectMapperProvider);
    }

    @Override
    protected void correct(BeanDescription bean, Schema model) {
        Map<String, Schema> properties = model.getProperties();
        for (BeanPropertyDefinition property : bean.findProperties()) {
            List<Annotation> annotations = annotationsOf(property);
            List<Size> sizes = annotations.stream()
                    .filter(Size.class::isInstance)
                    .map(Size.class::cast)
                    .filter(size -> appliesByDefault(size.groups()))
                    .toList();
            if (sizes.isEmpty()) {
                continue;
            }
            Schema schema = properties.get(property.getName());
            if (schema == null) {
                throw new IllegalStateException(String.format(
                        "%s.%s carries a @Size but its schema has no property of that name",
                        bean.getBeanClass().getName(), property.getName()));
            }
            int minLength = Math.max(
                    sizes.stream().mapToInt(Size::min).max().orElse(0),
                    isNotEmpty(annotations) ? 1 : 0);
            schema.setMinLength(minLength > 0 ? minLength : null);
        }
    }

    private static boolean isNotEmpty(List<Annotation> annotations) {
        return annotations.stream().anyMatch(annotation ->
                (annotation instanceof NotEmpty notEmpty && appliesByDefault(notEmpty.groups()))
                        || (annotation instanceof NotBlank notBlank && appliesByDefault(notBlank.groups())));
    }

}
