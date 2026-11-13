package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
 * <p>It is a model converter for the reason {@link NumericBoundsModelConverter} is one: swagger-core applies bean
 * validation after property customizers have run.</p>
 */
@Component
public class StringLengthModelConverter implements ModelConverter {

    private static final String SCHEMAS_REF_PREFIX = "#/components/schemas/";

    private final ObjectMapperProvider objectMapperProvider;

    public StringLengthModelConverter(ObjectMapperProvider objectMapperProvider) {
        this.objectMapperProvider = objectMapperProvider;
    }

    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema resolved = chain.next().resolve(type, context, chain);
        if (resolved == null || type.getType() == null) {
            return resolved;
        }
        // The mapper swagger-core's ModelResolver introspects with, so properties are named as in the schema.
        ObjectMapper mapper = objectMapperProvider.jsonMapper();
        JavaType javaType = mapper.constructType(type.getType());
        if (javaType.isContainerType() || javaType.isPrimitive() || javaType.isEnumType()) {
            return resolved;
        }
        Schema model = definedModel(resolved, context);
        if (model == null || model.getProperties() == null || model.getProperties().isEmpty()) {
            return resolved;
        }
        applyMinLengths(mapper.getSerializationConfig().introspect(javaType), model);
        return resolved;
    }

    private Schema definedModel(Schema resolved, ModelConverterContext context) {
        String ref = resolved.get$ref();
        if (ref == null) {
            return resolved;
        }
        if (!ref.startsWith(SCHEMAS_REF_PREFIX)) {
            return null;
        }
        return context.getDefinedModels().get(ref.substring(SCHEMAS_REF_PREFIX.length()));
    }

    private void applyMinLengths(BeanDescription bean, Schema model) {
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

    private static List<Annotation> annotationsOf(BeanPropertyDefinition property) {
        List<Annotation> annotations = new ArrayList<>();
        Stream.of(property.getField(), property.getGetter(), property.getSetter())
                .filter(Objects::nonNull)
                .map(AnnotatedMember::getAllAnnotations)
                .forEach(map -> map.annotations().forEach(annotations::add));
        return annotations;
    }

    /**
     * Request bodies are validated with {@code @Valid}, which checks the default group only; a constraint of another
     * group is not enforced on them and so is not a bound of the contract.
     */
    private static boolean appliesByDefault(Class<?>[] groups) {
        return groups.length == 0 || List.of(groups).contains(Default.class);
    }
}
