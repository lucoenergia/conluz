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
import jakarta.validation.groups.Default;
import org.springdoc.core.providers.ObjectMapperProvider;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A model converter that corrects what swagger-core publishes for the bean validation constraints of a model's
 * properties, once the whole model has been resolved.
 *
 * <p>It wraps the resolution of the model rather than being a springdoc {@code PropertyCustomizer} because
 * swagger-core applies bean validation to a property <em>after</em> property customizers have run, so a customizer's
 * correction would be written over. Wrapping the resolution of the whole model sees every property in its final
 * state.</p>
 */
abstract class BeanValidationModelConverter implements ModelConverter {

    private static final String SCHEMAS_REF_PREFIX = "#/components/schemas/";

    private final ObjectMapperProvider objectMapperProvider;

    protected BeanValidationModelConverter(ObjectMapperProvider objectMapperProvider) {
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
        correct(mapper.getSerializationConfig().introspect(javaType), model);
        return resolved;
    }

    /**
     * Corrects the properties of {@code model}, the resolved schema of {@code bean}, which has at least one property.
     */
    protected abstract void correct(BeanDescription bean, Schema model);

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

    /**
     * Every annotation of the property, on its field, getter and setter.
     */
    protected static List<Annotation> annotationsOf(BeanPropertyDefinition property) {
        List<Annotation> annotations = new ArrayList<>();
        Stream.of(property.getField(), property.getGetter(), property.getSetter())
                .filter(Objects::nonNull)
                .map(AnnotatedMember::getAllAnnotations)
                .forEach(map -> map.annotations().forEach(annotations::add));
        return annotations;
    }

    /**
     * Request bodies are validated with {@code @Valid}, which checks the default group only; a
     * constraint of another group is not enforced on them and so is not a bound of the contract.
     */
    protected static boolean appliesByDefault(Class<?>[] groups) {
        return groups.length == 0 || List.of(groups).contains(Default.class);
    }
}
