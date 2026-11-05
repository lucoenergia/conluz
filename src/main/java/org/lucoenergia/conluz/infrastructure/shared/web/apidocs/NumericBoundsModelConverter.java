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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * Publishes the numeric bounds that bean validation enforces on a model's properties.
 *
 * <p>swagger-core publishes {@code @Min}, {@code @Max} and an inclusive {@code @DecimalMin} or
 * {@code @DecimalMax}, but it writes an exclusive {@code @DecimalMin} as a plain {@code minimum}
 * (its exclusive flag is the OpenAPI 3.0 boolean, which a 3.1 document drops), and it ignores
 * {@code @Positive}, {@code @PositiveOrZero}, {@code @Negative}, {@code @NegativeOrZero} and
 * {@code @Digits}. This converter recomputes, for every property that carries at least one of those
 * constraints, the strictest bound on each side and writes it in the 3.1 form: {@code minimum} or
 * {@code exclusiveMinimum}, {@code maximum} or {@code exclusiveMaximum}, never both on one side.</p>
 *
 * <p>It is a model converter rather than a springdoc {@code PropertyCustomizer} because swagger-core
 * applies bean validation to a property <em>after</em> property customizers have run, so a customizer
 * that removed the wrong {@code minimum} would see it written back. Wrapping the resolution of the
 * whole model sees every property in its final state.</p>
 *
 * <p>It covers the properties of models only. Constraints on operation parameters
 * ({@code @RequestParam}, {@code @PathVariable}, {@code @RequestHeader}) are published by springdoc
 * itself and are not touched here.</p>
 */
@Component
public class NumericBoundsModelConverter implements ModelConverter {

    private static final String SCHEMAS_REF_PREFIX = "#/components/schemas/";

    private final ObjectMapperProvider objectMapperProvider;

    public NumericBoundsModelConverter(ObjectMapperProvider objectMapperProvider) {
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
        applyBounds(mapper.getSerializationConfig().introspect(javaType), model);
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

    private void applyBounds(BeanDescription bean, Schema model) {
        Map<String, Schema> properties = model.getProperties();
        for (BeanPropertyDefinition property : bean.findProperties()) {
            List<Annotation> annotations = annotationsOf(property);
            Optional<Bound> lower = strictest(lowerBounds(annotations), Bound::isStricterLowerThan);
            Optional<Bound> upper = strictest(upperBounds(annotations), Bound::isStricterUpperThan);
            if (lower.isEmpty() && upper.isEmpty()) {
                continue;
            }
            Schema schema = properties.get(property.getName());
            if (schema == null) {
                throw new IllegalStateException(String.format(
                        "%s.%s carries a numeric constraint but its schema has no property of that name",
                        bean.getBeanClass().getName(), property.getName()));
            }
            if (!isNumeric(schema)) {
                continue;
            }
            lower.ifPresent(bound -> writeLower(schema, bound));
            upper.ifPresent(bound -> writeUpper(schema, bound));
        }
    }

    private static List<Annotation> annotationsOf(BeanPropertyDefinition property) {
        List<Annotation> annotations = new ArrayList<>();
        Stream.of(property.getField(), property.getGetter(), property.getSetter())
                .filter(Objects::nonNull)
                .map(AnnotatedMember::getAllAnnotations)
                .forEach(map -> map.annotations().forEach(annotations::add));
        return annotations;
    }

    private static List<Bound> lowerBounds(List<Annotation> annotations) {
        List<Bound> bounds = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Min min && appliesByDefault(min.groups())) {
                bounds.add(new Bound(BigDecimal.valueOf(min.value()), false));
            } else if (annotation instanceof DecimalMin min && appliesByDefault(min.groups())) {
                bounds.add(new Bound(new BigDecimal(min.value()), !min.inclusive()));
            } else if (annotation instanceof Positive positive && appliesByDefault(positive.groups())) {
                bounds.add(new Bound(BigDecimal.ZERO, true));
            } else if (annotation instanceof PositiveOrZero positive && appliesByDefault(positive.groups())) {
                bounds.add(new Bound(BigDecimal.ZERO, false));
            } else if (annotation instanceof Digits digits && appliesByDefault(digits.groups())) {
                bounds.add(new Bound(digitsLimit(digits).negate(), false));
            }
        }
        return bounds;
    }

    private static List<Bound> upperBounds(List<Annotation> annotations) {
        List<Bound> bounds = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Max max && appliesByDefault(max.groups())) {
                bounds.add(new Bound(BigDecimal.valueOf(max.value()), false));
            } else if (annotation instanceof DecimalMax max && appliesByDefault(max.groups())) {
                bounds.add(new Bound(new BigDecimal(max.value()), !max.inclusive()));
            } else if (annotation instanceof Negative negative && appliesByDefault(negative.groups())) {
                bounds.add(new Bound(BigDecimal.ZERO, true));
            } else if (annotation instanceof NegativeOrZero negative && appliesByDefault(negative.groups())) {
                bounds.add(new Bound(BigDecimal.ZERO, false));
            } else if (annotation instanceof Digits digits && appliesByDefault(digits.groups())) {
                bounds.add(new Bound(digitsLimit(digits), false));
            }
        }
        return bounds;
    }

    /**
     * The largest value with {@code integer} integer digits and {@code fraction} decimals:
     * 10^integer − 10^−fraction.
     */
    private static BigDecimal digitsLimit(Digits digits) {
        return BigDecimal.TEN.pow(digits.integer()).subtract(BigDecimal.ONE.movePointLeft(digits.fraction()));
    }

    /**
     * Request bodies are validated with {@code @Valid}, which checks the default group only; a
     * constraint of another group is not enforced on them and so is not a bound of the contract.
     */
    private static boolean appliesByDefault(Class<?>[] groups) {
        return groups.length == 0 || List.of(groups).contains(Default.class);
    }

    private static Optional<Bound> strictest(Collection<Bound> bounds,
                                             BiPredicate<Bound, Bound> isStricter) {
        return bounds.stream().reduce((a, b) -> isStricter.test(b, a) ? b : a);
    }

    private static boolean isNumeric(Schema schema) {
        if (schema.getTypes() != null) {
            return schema.getTypes().stream().anyMatch(type -> isNumericType((String) type));
        }
        return isNumericType(schema.getType());
    }

    private static boolean isNumericType(String type) {
        return "number".equals(type) || "integer".equals(type);
    }

    private static void writeLower(Schema schema, Bound bound) {
        schema.setExclusiveMinimum(null);
        if (bound.exclusive()) {
            schema.setMinimum(null);
            schema.setExclusiveMinimumValue(bound.value());
        } else {
            schema.setExclusiveMinimumValue(null);
            schema.setMinimum(bound.value());
        }
    }

    private static void writeUpper(Schema schema, Bound bound) {
        schema.setExclusiveMaximum(null);
        if (bound.exclusive()) {
            schema.setMaximum(null);
            schema.setExclusiveMaximumValue(bound.value());
        } else {
            schema.setExclusiveMaximumValue(null);
            schema.setMaximum(bound.value());
        }
    }

    private record Bound(BigDecimal value, boolean exclusive) {

        /** A higher lower bound is stricter; at the same value, an exclusive one is. */
        boolean isStricterLowerThan(Bound other) {
            int comparison = value.compareTo(other.value);
            return comparison > 0 || (comparison == 0 && exclusive && !other.exclusive);
        }

        /** A lower upper bound is stricter; at the same value, an exclusive one is. */
        boolean isStricterUpperThan(Bound other) {
            int comparison = value.compareTo(other.value);
            return comparison < 0 || (comparison == 0 && exclusive && !other.exclusive);
        }
    }
}
