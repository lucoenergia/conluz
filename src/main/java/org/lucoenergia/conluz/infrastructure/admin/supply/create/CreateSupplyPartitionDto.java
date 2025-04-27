package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvCustomBindByPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

@Schema(requiredProperties = {
        "code", "coefficient"
})
public class CreateSupplyPartitionDto {

    @CsvBindByPosition(position = 0)
    @NotEmpty
    private String code;

    @CsvCustomBindByPosition(position = 1, converter = DoubleConverter.class)
    @Positive
    private Double coefficient;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(Double coefficient) {
        this.coefficient = coefficient;
    }

    public static class DoubleConverter extends AbstractBeanField<Double, CreateSupplyPartitionDto> {

        @Override
        protected Double convert(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            String normalizedValue = value.replace(',', '.');
            return Double.parseDouble(normalizedValue);
        }
    }

}
