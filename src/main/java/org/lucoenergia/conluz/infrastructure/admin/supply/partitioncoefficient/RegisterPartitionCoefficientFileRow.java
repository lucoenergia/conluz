package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvCustomBindByPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(requiredProperties = {"cups", "coefficient"})
public class RegisterPartitionCoefficientFileRow {

    @CsvBindByPosition(position = 0)
    @NotEmpty
    private String cups;

    @CsvCustomBindByPosition(position = 1, converter = BigDecimalConverter.class)
    @PositiveOrZero
    private BigDecimal coefficient;

    public String getCups() {
        return cups;
    }

    public void setCups(String cups) {
        this.cups = cups;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(BigDecimal coefficient) {
        this.coefficient = coefficient;
    }

    public static class BigDecimalConverter extends AbstractBeanField<BigDecimal, RegisterPartitionCoefficientFileRow> {

        @Override
        protected BigDecimal convert(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            return new BigDecimal(value.replace(',', '.'));
        }
    }
}
